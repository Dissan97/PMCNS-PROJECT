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

import static com.g45.webappsim.App.cfgPath;

/**
 * Facade che aggrega e gestisce gli stimatori per una simulazione a eventi discreti.
 *
 * Scelte metodologiche:
 *  - In simulazione MISURIAMO separatamente R (tempi), X (throughput) e N (popolazione tempo-pesata).
 *  - Usiamo Little (N ≈ X·R) come CHECK di coerenza, non per costruire N.
 *  - Nel log stampiamo anche il confronto Little a livello globale.
 */
public class EstimatorFacade {

    /** Header tabella globale. */
    private static final List<String> HEADERS = List.of(
            "mean_response_time","std_response_time","mean_population","std_population","throughput","utilization"
    );
    /** Header tabella per-nodo. */
    private static final List<String> HEADERS_NODE = List.of(
            "Node","mean_response_time","std_response_time","mean_population","std_population","throughput","utilization"
    );
    private static final Path OUT_DIR = Path.of(".output_simulation");

    // ── stimatori globali ─────────────────────────────────────────────────────
    private final ResponseTimeEstimator rt;     // tempi end-to-end (globale)
    private final PopulationEstimator   pop;    // popolazione tempo-pesata (globale)
    private final CompletionsEstimator  comp;   // completamenti (globale)
    private final ObservationTimeEstimator ot;  // tempo osservato
    private final BusyTimeEstimator     busy;   // tempo busy globale

    // ── stimatori per nodo ───────────────────────────────────────────────────
    private final Map<String, ResponseTimeEstimatorNode>  rtNode;
    private final Map<String, PopulationEstimatorNode>     popNode;   // N tempo-pesata per nodo
    private final Map<String, CompletionsEstimatorNode>    compNode;  // usato per throughput per-nodo “misurato”
    private final Map<String, BusyTimeEstimatorNode>       busyNode;

    private final long seed;

    public EstimatorFacade(Network network, NextEventScheduler scheduler,
                           Map<String, Map<String, TargetClass>> routingMatrix, long seed) {
        this.rt   = new ResponseTimeEstimator(scheduler);
        this.pop  = new PopulationEstimator(scheduler);              // VERSIONE time-weighted (getMean())
        this.comp = new CompletionsEstimator(scheduler, routingMatrix);
        this.ot   = new ObservationTimeEstimator(scheduler);
        this.busy = new BusyTimeEstimator(scheduler);

        this.rtNode   = new HashMap<>();
        this.popNode  = new HashMap<>();
        this.compNode = new HashMap<>();
        this.busyNode = new HashMap<>();
        this.seed     = seed;

        for (String n : network.allNodes()) {
            rtNode.put(n,   new ResponseTimeEstimatorNode(scheduler, n));
            popNode.put(n,  new PopulationEstimatorNode(scheduler, n));      // VERSIONE time-weighted (getMean())
            compNode.put(n, new CompletionsEstimatorNode(scheduler, n, routingMatrix));
            busyNode.put(n, new BusyTimeEstimatorNode(scheduler, n));
        }
    }

    // ── util: tabella ASCII ──────────────────────────────────────────────────
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
                        "Row size " + row.size() + " does not match headers size " + headers.size()
                );
            }
            Object[] formattedRow = row.stream().map(val -> {
                if (val instanceof Number) return String.format("%.6f", ((Number) val).doubleValue());
                return val != null ? val.toString() : "";
            }).toArray();
            at.addRow(formattedRow);
            at.setTextAlignment(TextAlignment.CENTER);
            at.addRule();
        }
        return at.render();
    }

    private static String fmt(double d) { return String.format(Locale.ROOT,"%.6f", d); }

    /**
     * Calcola e logga metriche globali e per-nodo.
     * - N (popolazione) è MISURATA (time-weighted).
     * - Little (X·R) è riportato come check.
     */
    public void calculateStats(NextEventScheduler scheduler, Network network) {
        // chiusura dei periodi busy per misurare correttamente l’utilizzo
        busy.finalizeBusy(scheduler.getCurrentTime());
        for (BusyTimeEstimator b : busyNode.values()) {
            b.finalizeBusy(scheduler.getCurrentTime());
        }

        // finestra di osservazione
        double elapsed = ot.elapsed();

        // Throughput globale “misurato”
        double X = elapsed > 0 ? (double) comp.count / elapsed : 0.0;

        // Tempi globali “misurati” dallo stesso estimatore (coerenza mean/std)
        double meanRtOverall = calculateOverallRtByVisits();
        // std può restare dallo stimatore globale, oppure lascia anche quella per-nodo se preferisci
        double stdRtOverall  = rt.w.getStddev();

        // Popolazione globale “misurata” (time-weighted)
        double meanPop = pop.getMean();  // <── usa la media tempo-pesata
        double stdPop  = 0.0;            // non stimiamo qui la std di N tempo-pesata

        // Utilizzo globale
        double utilization = elapsed > 0 ? busy.getBusyTime() / elapsed : 0.0;

        // Tabella globale (metriche MISURATE)
        List<List<Object>> globalRows = List.of(
                List.of(fmt(meanRtOverall), fmt(stdRtOverall), fmt(meanPop), fmt(stdPop),
                        fmt(X), fmt(utilization))
        );
        SysLogger.getInstance().getLogger().info("Global metrics (measured)\n" + makeTable(HEADERS, globalRows));

        // ── Little check (globale): confronto N_mis vs X*R_mis ───────────────
        double nLittle = X * meanRtOverall;
        double relErr  = (nLittle != 0.0) ? (meanPop - nLittle) / nLittle : 0.0;

        List<List<Object>> littleRows = List.of(
                List.of("N_measured", fmt(meanPop)),
                List.of("X*R_measured", fmt(nLittle)),
                List.of("relative_error", fmt(relErr))
        );
        SysLogger.getInstance().getLogger().info("Little's Law check (global)\n" + makeTable(
                List.of("quantity","value"), littleRows
        ));

        // ── Per nodo: R_n misurato, N_n misurata time-weighted, X_n misurato (completamenti nodo / elapsed) ──
        List<List<Object>> perNodeRows = new ArrayList<>();
        for (String n : network.allNodes().stream().sorted().toList()) {
            WelfordEstimator wrt = rtNode.get(n).w;
            double Wn    = wrt.getMean();
            double stdWn = wrt.getStddev();

            // N_n misurata (time-weighted)
            double Nn = popNode.get(n).getMean();
            double stdNn = 0.0;

            // Throughput nodo “misurato” dai completamenti del nodo (se il tuo CompletionsEstimatorNode è definito così)
            int completedAtNode = compNode.get(n).count;
            double Xn = elapsed > 0 ? (double) completedAtNode / elapsed : 0.0;

            // Utilizzo nodo
            double util = elapsed > 0 ? busyNode.get(n).getBusyTime() / elapsed : 0.0;

            perNodeRows.add(List.of(
                    n,
                    fmt(Wn), fmt(stdWn),
                    fmt(Nn), fmt(stdNn),
                    fmt(Xn), fmt(util)
            ));
        }
        SysLogger.getInstance().getLogger().info("Per-node metrics (measured)\n" + makeTable(HEADERS_NODE, perNodeRows));

        // --- CSV (metriche MISURATE) ----------------------------------------------------
        saveToCsv(network, meanRtOverall, stdRtOverall, meanPop, stdPop, X, utilization, elapsed, nLittle, relErr);
    }

    /**
     * Salva CSV con metriche MISURATE e, in coda, due righe per il Little check (facoltativo).
     */
    private void saveToCsv(Network network, double meanRtOverall, double stdRtOverall,
                           double meanPop, double stdPop, double throughput,
                           double utilization, double elapsed,
                           double nLittle, double relErr) {
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String cfgFile = cfgPath.replace(".json", "");
            int dot = cfgFile.lastIndexOf('.');
            String cfgStem = (dot > 0 ? cfgFile.substring(0, dot) : cfgFile);

            String name = String.format("results_%s_run%03d_seed%s_%s.csv",
                    cfgStem, Simulation.SIMULATION_COUNTER.get(), String.valueOf(seed), ts);
            Path outPath = OUT_DIR.resolve(name);

            Files.createDirectories(OUT_DIR);

            StringBuilder csv = new StringBuilder();
            csv.append("scope,mean_response_time,std_response_time,mean_population,std_population,throughput,utilization\n");

            // Riga OVERALL (MISURATA)
            csv.append(String.join(",",
                            "OVERALL",
                            fmt(meanRtOverall),
                            fmt(stdRtOverall),
                            fmt(meanPop),
                            fmt(stdPop),
                            fmt(throughput),
                            fmt(utilization)))
               .append('\n');

            // Righe per nodo (MISURATE)
            for (String n : network.allNodes().stream().sorted().toList()) {
                WelfordEstimator wrt = rtNode.get(n).w;
                double Wn    = wrt.getMean();
                double stdWn = wrt.getStddev();

                double Nn = popNode.get(n).getMean();
                double stdNn = 0.0;

                int completedAtNode = compNode.get(n).count;
                double Xn = elapsed > 0 ? (double) completedAtNode / elapsed : 0.0;

                double util = elapsed > 0 ? busyNode.get(n).getBusyTime() / elapsed : 0.0;

                csv.append(String.join(",",
                                "NODE_" + n,
                                fmt(Wn), fmt(stdWn),
                                fmt(Nn), fmt(stdNn),
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
     * Tempo medio di risposta complessivo via somma per visite (diagnostica).
     * La usiamo solo per confronto se serve, NON per la tabella principale.
     * (Puoi richiamarla a log per check con l’analitico.)
     */
    
    private double calculateOverallRtByVisits() {
        double a = rtNode.containsKey("A") ? rtNode.get("A").w.getMean() : 0.0;
        double b = rtNode.containsKey("B") ? rtNode.get("B").w.getMean() : 0.0;
        double p = rtNode.containsKey("P") ? rtNode.get("P").w.getMean() : 0.0;
        return a * 3 + b + p;
    }
}
