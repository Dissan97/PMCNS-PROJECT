package com.g45.webappsim.estimators;

import com.g45.webappsim.logging.SysLogger;
import com.g45.webappsim.simulator.Network;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.Simulation;
import com.g45.webappsim.simulator.TargetClass;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestLine;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.g45.webappsim.App.getCfgPath;

/**
 * Facade che colleziona metriche globali e per-nodo.
 *
 * Aggiunte:
 * - per A/B/P usa ResponseTimeEstimatorNode (versione con somme per-job)
 * - ResponseTimePerJobCollector salva per-job T_A, T_B, T_P, T_total e conserva
 * i campioni
 * - calcolo var/cov empirici → std_response_time_cov, std_population_cov
 * (colonne extra nel CSV)
 */
public class EstimatorFacade {

    // header tabelle
    private static final List<String> HEADERS = List.of(
            "mean_response_time", "std_response_time",
            "mean_population", "std_population",
            "throughput", "utilization",
            "std_response_time_cov", "std_population_cov", // NEW
            "simulation_time(H)");
    private static final List<String> HEADERS_NODE = List.of(
            "Node", "mean_response_time", "std_response_time",
            "mean_population", "std_population",
            "throughput", "utilization");
    private static final Path OUT_DIR = Path.of(".output_simulation");

    // stimatori globali
    private final ResponseTimeEstimator rt; // end-to-end (ARRIVAL ext -> EXIT)
    private final PopulationEstimator pop; // popolazione tempo-pesata (tracking jobId)
    private final CompletionsEstimator comp; // completamenti a EXIT
    private final ObservationTimeEstimator ot; // finestra osservata
    private final BusyTimeEstimator busy; // busy time globale

    // stimatori per-nodo
    private final Map<String, ResponseTimeEstimatorNode> rtNode = new HashMap<>();
    private final Map<String, PopulationEstimatorNode> popNode = new HashMap<>();
    private final Map<String, CompletionsEstimatorNode> compNode = new HashMap<>();
    private final Map<String, BusyTimeEstimatorNode> busyNode = new HashMap<>();

    // collector per tempi per-job (A,B,P)
    private ResponseTimePerJobCollector perJobCollector;

    private final long seed;

    public EstimatorFacade(Network network,
            NextEventScheduler scheduler,
            Map<String, Map<String, TargetClass>> routingMatrix,
            long seed) {

        // Globale
        this.rt = new ResponseTimeEstimator(scheduler, routingMatrix);
        this.pop = new PopulationEstimator(scheduler, routingMatrix); // tracking jobId (ingresso primo ARRIVAL >=0,
                                                                      // uscita EXIT)
        this.comp = new CompletionsEstimator(scheduler, routingMatrix);
        this.ot = new ObservationTimeEstimator(scheduler);
        this.busy = new BusyTimeEstimator(scheduler, routingMatrix);

        this.seed = seed;

        // ---- per-nodo ----
        for (String n : network.allNodes()) {
            // RT per nodo:
            if (n.equals("A") || n.equals("B") || n.equals("P")) {
                // creiamo subito sotto le versioni A/B/P (con somme per-job)
            } else {
                rtNode.put(n, new ResponseTimeEstimatorNode(scheduler, n, routingMatrix));
            }

            popNode.put(n, new PopulationEstimatorNode(scheduler, n));
            compNode.put(n, new CompletionsEstimatorNode(scheduler, n, routingMatrix));
            busyNode.put(n, new BusyTimeEstimatorNode(scheduler, n));
        }

        // A/B/P: usa la versione ResponseTimeEstimatorNode "with sums"
        ResponseTimeEstimatorNode rtA = new ResponseTimeEstimatorNode(scheduler, "A", routingMatrix);
        ResponseTimeEstimatorNode rtB = new ResponseTimeEstimatorNode(scheduler, "B", routingMatrix);
        ResponseTimeEstimatorNode rtP = new ResponseTimeEstimatorNode(scheduler, "P", routingMatrix);
        rtNode.put("A", rtA);
        rtNode.put("B", rtB);
        rtNode.put("P", rtP);

        // Collector per i tempi per-job (CSV + RAM per var/cov)
        Path perJobCsv = OUT_DIR.resolve("per_job_times.csv");
        perJobCollector = new ResponseTimePerJobCollector(
                scheduler, routingMatrix, rtA, rtB, rtP, perJobCsv);
    }

    // util: tabella ascii
    public static String makeTable(List<String> headers, List<List<Object>> rows) {
        AsciiTable at = new AsciiTable();
        at.getRenderer().setCWC(new CWC_LongestLine());
        at.setTextAlignment(TextAlignment.CENTER);
        at.addRule();
        at.addRow(headers.toArray());
        at.addRule();

        for (List<Object> row : rows) {
            if (row.size() != headers.size()) {
                throw new IllegalArgumentException(
                        "Row size " + row.size() + " does not match headers size " + headers.size());
            }
            Object[] formattedRow = row.stream().map(val -> {
                if (val instanceof Number)
                    return String.format(Locale.ROOT, "%.6f", ((Number) val).doubleValue());
                return val != null ? val.toString() : "";
            }).toArray();
            at.addRow(formattedRow);
            at.setTextAlignment(TextAlignment.CENTER);
            at.addRule();
        }
        return at.render();
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.6f", d);
    }

    /** Calcola e stampa le metriche (globali e per-nodo) e salva il CSV. */
    public void calculateStats(NextEventScheduler scheduler, Network network) {
        // finalizza busy e popolazioni fino a "now"
        busy.finalizeBusy(scheduler.getCurrentTime());
        for (BusyTimeEstimator b : busyNode.values()) {
            b.finalizeBusy(scheduler.getCurrentTime());
        }
        pop.finalizeAt(scheduler.getCurrentTime());
        for (PopulationEstimatorNode pn : popNode.values()) {
            pn.finalizeAt(scheduler.getCurrentTime());
        }

        // orizzonte osservato
        double elapsed = ot.elapsed();

        // throughput globale da completamenti a EXIT
        // DOPO (safe: se non chiami mai startCollecting(), è comunque == totale/tempo)
        double throughput = elapsed > 0 ? (double) comp.getCountSinceStart() / elapsed : 0.0;

        // tempo di risposta overall: mean via somma per visite; std via Welford globale
        // end-to-end
        double meanRtOverall = calculateOverallRtByVisits();
        double stdRtOverall = rt.w.getStddev();

        // popolazione globale tempo-pesata
        double meanPop = pop.getMean();
        double stdPop = pop.getStd();

        // utilizzazione globale (tempo occupato / tempo)
        double utilization = elapsed > 0 ? busy.getBusyTime() / elapsed : 0.0;

        // ---- STD con covarianze stimate dai campioni per-job (A,B,P) ----
        double stdRtCov = Double.NaN;
        double stdPopCov = Double.NaN;
        if (perJobCollector != null && perJobCollector.size() > 1) {
            double[] TA = perJobCollector.getTA();
            double[] TB = perJobCollector.getTB();
            double[] TP = perJobCollector.getTP();

            double varA = sampleVariance(TA);
            double varB = sampleVariance(TB);
            double varP = sampleVariance(TP);
            double covAB = sampleCovariance(TA, TB);
            double covAP = sampleCovariance(TA, TP);
            double covBP = sampleCovariance(TB, TP);

            double varSys = Math.max(0.0, varA + varB + varP + 2.0 * (covAB + covAP + covBP));
            stdRtCov = Math.sqrt(varSys);
            // Little con X ≈ completamenti/tempo: std_N ≈ X * std_R
            stdPopCov = throughput * stdRtCov;
        }

        // tabella globale
        
        double global_time = elapsed / 3600.0;
        List<Object> globalRow = List.of(
                fmt(meanRtOverall), fmt(stdRtOverall),
                fmt(meanPop), fmt(stdPop),
                fmt(throughput), fmt(utilization),
                (Double.isNaN(stdRtCov) ? "-" : fmt(stdRtCov)),
                (Double.isNaN(stdPopCov) ? "-" : fmt(stdPopCov)),
                fmt(global_time));
        SysLogger.getInstance().getLogger()
                .info("Global metrics (measured)\n" + makeTable(HEADERS, List.of(globalRow)));

        // per-nodo
        List<List<Object>> perNodeRows = new ArrayList<>();
        for (String n : network.allNodes().stream().sorted().toList()) {
            WelfordEstimator wrt = rtNode.get(n).w;
            double Wn = wrt.getMean();
            double stdWn = wrt.getStddev();

            double Nn = popNode.get(n).getMean();
            double stdNn = popNode.get(n).getStd();

            int completedAtNode = compNode.get(n).getCountSinceStart();
            double Xn = elapsed > 0 ? (double) completedAtNode / elapsed : 0.0;

            double util = elapsed > 0 ? busyNode.get(n).getBusyTime() / elapsed : 0.0;

            perNodeRows.add(List.of(
                    n,
                    fmt(Wn), fmt(stdWn),
                    fmt(Nn), fmt(stdNn),
                    fmt(Xn), fmt(util)));
        }
        SysLogger.getInstance().getLogger()
                .info("Per-node metrics (measured)\n" + makeTable(HEADERS_NODE, perNodeRows));

        // salva CSV
        saveToCsv(network, meanRtOverall, stdRtOverall, meanPop, stdPop,
                throughput, utilization, stdRtCov, stdPopCov, elapsed);
        if (perJobCollector != null) {
            perJobCollector.flushToDisk();
        }
    }

    private void saveToCsv(Network network,
            double meanRtOverall, double stdRtOverall,
            double meanPop, double stdPop,
            double throughput, double utilization,
            double stdRtCov, double stdPopCov,
            double elapsed) {
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String cfgFile = getCfgPath().replace(".json", "");
            int dot = cfgFile.lastIndexOf('.');
            String cfgStem = (dot > 0 ? cfgFile.substring(0, dot) : cfgFile);

            String name = String.format("results_%s_run%03d_seed%s_%s.csv",
                    cfgStem, Simulation.SIMULATION_COUNTER.get(), String.valueOf(seed), ts);
            Path outPath = OUT_DIR.resolve(name);

            Files.createDirectories(OUT_DIR);

            StringBuilder csv = new StringBuilder();
            csv.append(
                    "scope,mean_response_time,std_response_time,mean_population,std_population,throughput,utilization,std_response_time_cov,std_population_cov\n");

            // OVERALL
            csv.append(String.join(",",
                    "OVERALL",
                    fmt(meanRtOverall),
                    fmt(stdRtOverall),
                    fmt(meanPop),
                    fmt(stdPop),
                    fmt(throughput),
                    fmt(utilization),
                    (Double.isNaN(stdRtCov) ? "-" : fmt(stdRtCov)),
                    (Double.isNaN(stdPopCov) ? "-" : fmt(stdPopCov)))).append('\n');

            // per-nodo
            for (String n : network.allNodes().stream().sorted().toList()) {
                WelfordEstimator wrt = rtNode.get(n).w;
                double Wn = wrt.getMean();
                double stdWn = wrt.getStddev();

                double Nn = popNode.get(n).getMean();
                double stdNn = popNode.get(n).getStd();

                int completedAtNode = compNode.get(n).getCountSinceStart();
                double Xn = elapsed > 0 ? (double) completedAtNode / elapsed : 0.0;

                double util = elapsed > 0 ? busyNode.get(n).getBusyTime() / elapsed : 0.0;

                csv.append(String.join(",",
                        "NODE_" + n,
                        fmt(Wn), fmt(stdWn),
                        fmt(Nn), fmt(stdNn),
                        fmt(Xn), fmt(util),
                        "-", "-" // no cov per nodo
                )).append('\n');
            }

            Files.writeString(outPath, csv.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            SysLogger.getInstance().getLogger().info("✓ CSV salvato → " + name);
        } catch (IOException e) {
            SysLogger.getInstance().getLogger().warning("CSV save failed: " + e.getMessage());
        }
    }

    /**
     * Tempo medio di risposta complessivo via somma per visite:
     * 3*W_A + W_B + W_P, dove W_* sono medie per-nodo misurate.
     */
    private double calculateOverallRtByVisits() {
        double a = rtNode.containsKey("A") ? rtNode.get("A").w.getMean() : 0.0;
        double b = rtNode.containsKey("B") ? rtNode.get("B").w.getMean() : 0.0;
        double p = rtNode.containsKey("P") ? rtNode.get("P").w.getMean() : 0.0;
        return a * 3.0 + b + p;
    }

    // ---- helper statistiche (varianza e covarianza campionaria, ddof=1) ----

    private static double sampleVariance(double[] x) {
        int n = x.length;
        if (n <= 1)
            return 0.0;
        double mean = 0.0;
        for (double v : x)
            mean += v;
        mean /= n;
        double s2 = 0.0;
        for (double v : x) {
            double d = v - mean;
            s2 += d * d;
        }
        return s2 / (n - 1);
    }

    private static double sampleCovariance(double[] x, double[] y) {
        int n = Math.min(x.length, y.length);
        if (n <= 1)
            return 0.0;
        double mx = 0.0, my = 0.0;
        for (int i = 0; i < n; i++) {
            mx += x[i];
            my += y[i];
        }
        mx /= n;
        my /= n;
        double s = 0.0;
        for (int i = 0; i < n; i++) {
            s += (x[i] - mx) * (y[i] - my);
        }
        return s / (n - 1);
    }

    public void startCollectingAll(NextEventScheduler sched) {
        double now = sched.getCurrentTime();

        // completamenti
        comp.startCollecting();
        for (CompletionsEstimatorNode c : compNode.values())
            c.startCollecting();

        // popolazioni (tempo-pesate)
        pop.startCollecting(now);
        for (PopulationEstimatorNode pn : popNode.values())
            pn.startCollecting(now);

        // busy time (tempo occupato)
        busy.startCollecting(now);
        for (BusyTimeEstimatorNode bn : busyNode.values())
            bn.startCollecting(now);

        // tempi di risposta
        rt.startCollecting();
        for (ResponseTimeEstimatorNode rn : rtNode.values())
            rn.startCollecting();

        // finestra di osservazione
        ot.startCollecting(now);

        // opzionale: se vuoi che anche le cov per-job siano solo post-warmup
        // perJobCollector.startCollecting();
    }


    public void startMeasurement(NextEventScheduler scheduler) {
        double now = scheduler.getCurrentTime();

        // global
        ot.startCollecting(now);
        busy.startCollecting(now);
        pop.startCollecting(now);
        comp.startCollecting();          // baseline per throughput
        rt.startCollecting();            // std_RT end-to-end riparte

        // per-nodo
        for (PopulationEstimatorNode pn : popNode.values()) pn.startCollecting(now);
        for (CompletionsEstimatorNode cn : compNode.values()) cn.startCollecting();
        for (ResponseTimeEstimatorNode rn : rtNode.values()) rn.startCollecting();

        // per-job
        if (perJobCollector != null) perJobCollector.startCollecting();
    }

}
