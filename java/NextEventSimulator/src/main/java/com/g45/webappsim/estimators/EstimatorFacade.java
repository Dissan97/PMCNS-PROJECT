package com.g45.webappsim.estimators;

import com.g45.webappsim.App;
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


/**
 * Facade che colleziona metriche globali e per-nodo.
 *
 * Coerenza con l'analitico:
 * - mean_response_time (OVERALL) = somma per visite: 3*W_A + W_B + W_P
 * (misurati per-nodo)
 * - std_response_time (OVERALL) = stimatore globale end-to-end (Welford)
 * - mean_population = popolazione tempo-pesata misurata (NON derivata con
 * Little)
 * - throughput = completamenti / tempo osservato
 * - utilization = busy_time / tempo osservato
 *
 * Nota: il CSV NON include righe di "Little check".
 */
public class EstimatorFacade {

    // header tabelle
    private static final List<String> HEADERS = List.of(
            "mean_response_time", "std_response_time", "mean_population", "std_population", "throughput",
            "utilization");
    private static final List<String> HEADERS_NODE = List.of(
            "Node", "mean_response_time", "std_response_time", "mean_population", "std_population", "throughput",
            "utilization");
    private static final Path OUT_DIR = Path.of(".output_simulation");

    // stimatori globali
    private final ResponseTimeEstimator rt;
    private final PopulationEstimator pop;
    private final CompletionsEstimator comp;
    private final ObservationTimeEstimator ot;
    private final BusyTimeEstimator busy;

    // stimatori per-nodo
    private final Map<String, ResponseTimeEstimatorNode> rtNode;
    private final Map<String, PopulationEstimatorNode> popNode;
    private final Map<String, CompletionsEstimatorNode> compNode;
    private final Map<String, BusyTimeEstimatorNode> busyNode;

    private final long seed;

    public EstimatorFacade(Network network, NextEventScheduler scheduler,
            Map<String, Map<String, TargetClass>> routingMatrix, long seed) {
        this.rt = new ResponseTimeEstimator(scheduler);
        this.pop = new PopulationEstimator(scheduler); // media tempo-pesata
        this.comp = new CompletionsEstimator(scheduler, routingMatrix);
        this.ot = new ObservationTimeEstimator(scheduler);
        this.busy = new BusyTimeEstimator(scheduler);

        this.rtNode = new HashMap<>();
        this.popNode = new HashMap<>();
        this.compNode = new HashMap<>();
        this.busyNode = new HashMap<>();
        this.seed = seed;

        for (String n : network.allNodes()) {
            rtNode.put(n, new ResponseTimeEstimatorNode(scheduler, n));
            popNode.put(n, new PopulationEstimatorNode(scheduler, n)); // media tempo-pesata per nodo
            compNode.put(n, new CompletionsEstimatorNode(scheduler, n, routingMatrix));
            busyNode.put(n, new BusyTimeEstimatorNode(scheduler, n));
        }
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

    /**
     * Calcola e stampa le metriche (globali e per-nodo) e salva il CSV.
     * Manteniamo la coerenza con la validazione analitica.
     */
    public void calculateStats(NextEventScheduler scheduler, Network network) {
        // Chiudi i periodi busy e finalizza la popolazione tempo-pesata
        busy.finalizeBusy(scheduler.getCurrentTime());
        for (BusyTimeEstimator b : busyNode.values()) {
            b.finalizeBusy(scheduler.getCurrentTime());
        }
        // NEW: finalizza gli stimatori di popolazione per integrare fino a "now"
        pop.finalizeAt(scheduler.getCurrentTime());
        for (PopulationEstimatorNode pn : popNode.values()) {
            pn.finalizeAt(scheduler.getCurrentTime());
        }

        // Finestra di osservazione
        double elapsed = ot.elapsed();

        // Throughput globale misurato
        double throughput = elapsed > 0 ? (double) comp.count / elapsed : 0.0;

        // Tempo di risposta globale (somma per visite: A*3 + B + P)
        double meanRtOverall = calculateOverallRtByVisits();
        double stdRtOverall = rt.w.getStddev();

        // Popolazione globale tempo-pesata (e sua std tempo-pesata)
        double meanPop = pop.getMean();
        double stdPop = pop.getStd(); // NEW: std tempo-pesata

        // Utilizzazione globale
        double utilization = elapsed > 0 ? busy.getBusyTime() / elapsed : 0.0;

        // Tabella globale
        List<List<Object>> globalRows = List.of(
                List.of(fmt(meanRtOverall), fmt(stdRtOverall), fmt(meanPop), fmt(stdPop),
                        fmt(throughput), fmt(utilization)));
        SysLogger.getInstance().getLogger().info("Global metrics (measured)\n" + makeTable(HEADERS, globalRows));

        // Per-nodo: R_n misurato, N_n tempo-pesata (con std tempo-pesata), X_n misurato
        List<List<Object>> perNodeRows = new ArrayList<>();
        for (String n : network.allNodes().stream().sorted().toList()) {
            WelfordEstimator wrt = rtNode.get(n).w;
            double Wn = wrt.getMean();
            double stdWn = wrt.getStddev();

            double Nn = popNode.get(n).getMean();
            double stdNn = popNode.get(n).getStd(); // NEW: std tempo-pesata per nodo

            int completedAtNode = compNode.get(n).count;
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

        // Salva CSV con std popolazione tempo-pesata (globale e per nodo)
        saveToCsv(network, meanRtOverall, stdRtOverall, meanPop, stdPop, throughput, utilization, elapsed);
    }

    private void saveToCsv(Network network, double meanRtOverall, double stdRtOverall,
            double meanPop, double stdPop, double throughput, double utilization, double elapsed) {
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String cfgFile = App.getCfgPath().replace(".json", "");
            int dot = cfgFile.lastIndexOf('.');
            String cfgStem = (dot > 0 ? cfgFile.substring(0, dot) : cfgFile);

            String name = String.format("results_%s_run%03d_seed%s_%s.csv",
                    cfgStem, Simulation.SIMULATION_COUNTER.get(), String.valueOf(seed), ts);
            Path outPath = OUT_DIR.resolve(name);

            Files.createDirectories(OUT_DIR);

            StringBuilder csv = new StringBuilder();
            csv.append(
                    "scope,mean_response_time,std_response_time,mean_population,std_population,throughput,utilization\n");

            // Riga OVERALL
            csv.append(String.join(",",
                    "OVERALL",
                    fmt(meanRtOverall),
                    fmt(stdRtOverall),
                    fmt(meanPop),
                    fmt(stdPop), // NEW: std popolazione tempo-pesata globale
                    fmt(throughput),
                    fmt(utilization)))
                    .append('\n');

            // Righe per nodo
            for (String n : network.allNodes().stream().sorted().toList()) {
                WelfordEstimator wrt = rtNode.get(n).w;
                double Wn = wrt.getMean();
                double stdWn = wrt.getStddev();

                double Nn = popNode.get(n).getMean();
                double stdNn = popNode.get(n).getStd(); // NEW: std popolazione tempo-pesata nodo

                int completedAtNode = compNode.get(n).count;
                double Xn = elapsed > 0 ? (double) completedAtNode / elapsed : 0.0;

                double util = elapsed > 0 ? busyNode.get(n).getBusyTime() / elapsed : 0.0;

                csv.append(String.join(",",
                        "NODE_" + n,
                        fmt(Wn), fmt(stdWn),
                        fmt(Nn), fmt(stdNn), // NEW: std popolazione per nodo
                        fmt(Xn), fmt(util)))
                        .append('\n');
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
}
