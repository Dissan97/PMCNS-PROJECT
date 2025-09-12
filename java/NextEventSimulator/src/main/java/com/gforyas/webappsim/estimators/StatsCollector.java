package com.gforyas.webappsim.estimators;

import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.*;
import com.gforyas.webappsim.util.SinkConvergenceToCsv;
import com.gforyas.webappsim.util.SinkToCsv;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestLine;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;

import static com.gforyas.webappsim.util.SinkToCsv.OUT_DIR;

/**
 * <p>
 * <strong>Scopo.</strong> Questa facade orchestra stimatori globali e per-nodo
 * per calcolare e riportare metriche di performance della rete simulata.
 * </p>
 *
 * <p>
 * <strong>Funzionalità.</strong>
 * </p>
 * <ul>
 * <li>Stimatori globali: tempo di risposta end-to-end, popolazione pesata nel
 * tempo,
 * completamenti di sistema, finestra di osservazione e busy time globale.</li>
 * <li>Stimatori per nodo: tempo di risposta, popolazione pesata nel tempo,
 * partenze e busy time.</li>
 * <li>Per i nodi <code>A</code>, <code>B</code> e <code>P</code> usa una
 * variante
 * {@link ResponseTimeEstimatorNode} con aggregazione per-job.</li>
 * <li>Un {@link ResponseTimePerJobCollector} cattura tempi per-job
 * (T<sub>A</sub>, T<sub>B</sub>, T<sub>P</sub>, T<sub>total</sub>) e conserva
 * campioni
 * per stime empiriche di varianza/covarianza.</li>
 * <li>Esporta metriche addizionali basate su covarianze
 * (<code>std_response_time_cov</code>, <code>std_population_cov</code>) nel
 * CSV.</li>
 * </ul>
 *
 * <p>
 * <strong>Integrazione.</strong> Istanziato con network, scheduler, matrice di
 * routing e config.
 * Collega gli stimatori, e fornisce utility per aprire la misura, finalizzare
 * le grandezze
 * time-weighted, loggare tabelle ASCII e scrivere CSV di riepilogo.
 * </p>
 */
public class StatsCollector {

    public static final String MEAN_RESPONSE_TIME = "mean_response_time";
    public static final String STD_RESPONSE_TIME = "std_response_time";
    public static final String MEAN_POPULATION = "mean_population";
    public static final String STD_POPULATION = "std_population";
    public static final String THROUGHPUT = "throughput";
    public static final String UTILIZATION = "utilization";
    // Intestazioni per le tabelle ASCII (solo log, non influiscono sui CSV)
    private static final List<String> HEADERS = List.of(
            MEAN_RESPONSE_TIME, STD_RESPONSE_TIME,
            MEAN_POPULATION, STD_POPULATION,
            THROUGHPUT, UTILIZATION,
            "std_response_time_cov", "std_population_cov", // NEW
            "simulation_time(H)");
    private static final List<String> HEADERS_NODE = List.of(
            "Node", MEAN_RESPONSE_TIME, STD_RESPONSE_TIME,
            MEAN_POPULATION, STD_POPULATION,
            THROUGHPUT, UTILIZATION);
    public static final String OVERALL = "OVERALL";

    // --- Stimatori globali ---
    protected ResponseTimeEstimator rt; // end-to-end (ARRIVAL esterno -> EXIT)
    protected PopulationEstimator pop; // popolazione pesata nel tempo (traccia jobId)
    protected CompletionsEstimator comp; // completamenti a EXIT
    protected ObservationTimeEstimator ot; // finestra osservata
    protected BusyTimeEstimator busy; // busy time globale

    // --- Stimatori per nodo ---
    protected final Map<String, ResponseTimeEstimatorNode> rtNode = new HashMap<>();
    protected final Map<String, PopulationEstimatorNode> popNode = new HashMap<>();
    protected final Map<String, CompletionsEstimatorNode> compNode = new HashMap<>();
    protected final Map<String, BusyTimeEstimatorNode> busyNode = new HashMap<>();

    // --- Collector per tempi per-job (A, B, P) ---
    private ResponseTimePerJobCollector perJobCollector;

    protected double arrivalRate;

    // --- Convergenze ---
    protected Map<Pair<String, String>, Double> convergence = new HashMap<>();
    private static final int EVENT_COUNT = 1000;
    private static int LIMIT = 100;
    private int times = 0;
    private boolean initial = true;
    private int counter = 0;
    private SinkToCsv sink;
    private SinkConvergenceToCsv sinkConvergence;

    // Parametri tempo
    private static final double EARLY_TMAX_S = 60.0; // primi 60 s
    private static final double EARLY_DT_S = 1.0; // 1 punto/s nei primi 60 s
    private static final double LATE_DT_S = 400.0; // poi ogni 400 s
    private static final int LATE_EVERY_EVENTS = 500; // fallback

    // Stato
    private double lastEmitTime = Double.NaN;
    private int eventsSinceLastEmit = 0;

    // --- NEW: metadati/metriche routing ---
    // Abilitazione export metriche di percorso (solo in probabilistico)
    private boolean routingPathStatsEnabled = false;
    // Conteggi percorsi aggregati (forniti da Simulation)
    private int pathAB = 0;
    private int pathABAPA = 0;
    private int pathABABForced = 0;
    // Modalità routing per reporting CSV ("deterministic" | "probabilistic")
    private String routingMode;

    public StatsCollector() {
    }

    /**
     * Crea la facade e registra tutti gli stimatori/collector.
     */
    public StatsCollector(Network network,
            NextEventScheduler scheduler,
            Map<String, Map<String, TargetClass>> routingMatrix,
            SimulationConfig cfg, double arrivalRate) {

        // Global
        this.rt = new ResponseTimeEstimator(scheduler, routingMatrix);
        this.pop = new PopulationEstimator(scheduler, routingMatrix);
        this.comp = new CompletionsEstimator(scheduler, routingMatrix);
        this.ot = new ObservationTimeEstimator(scheduler);
        this.busy = new BusyTimeEstimator(scheduler, routingMatrix);
        this.arrivalRate = arrivalRate;
        this.sink = cfg.getSink();

        // Per-nodo
        for (String n : network.allNodes()) {

            rtNode.put(n, new ResponseTimeEstimatorNode(scheduler, n, routingMatrix));
            popNode.put(n, new PopulationEstimatorNode(scheduler, n));
            compNode.put(n, new CompletionsEstimatorNode(scheduler, n, routingMatrix));
            busyNode.put(n, new BusyTimeEstimatorNode(scheduler, n));
        }

        // A/B/P con variante per-job
        ResponseTimeEstimatorNode rtA = new ResponseTimeEstimatorNode(scheduler, "A", routingMatrix);
        ResponseTimeEstimatorNode rtB = new ResponseTimeEstimatorNode(scheduler, "B", routingMatrix);
        ResponseTimeEstimatorNode rtP = new ResponseTimeEstimatorNode(scheduler, "P", routingMatrix);
        // Collector per-job (CSV + campioni in memoria)
        Path perJobCsv = OUT_DIR.resolve("per_job_times.csv");
        perJobCollector = new ResponseTimePerJobCollector(
                scheduler, routingMatrix, rtA, rtB, rtP, perJobCsv);

        prepareConvergence(scheduler);
        this.sinkConvergence = cfg.getSinkConv();

        // NEW: memorizza la modalità di routing per reporting CSV
        this.routingMode = cfg.isProbabilistic() ? "probabilistic" : "deterministic";


    }

    // ---------------- Convergenza (immutata) ----------------

    private void prepareConvergence(NextEventScheduler scheduler) {
        prepareConvergence(OVERALL);
        for (var node : rtNode.keySet()) {
            prepareConvergence(node);
        }
        scheduler.subscribe(Event.Type.DEPARTURE, this::updateConvergence);
    }

    private void updateConvergence(Event event, NextEventScheduler scheduler) {
        double now = scheduler.getCurrentTime();

        // Decidi il passo target in base all'epoca (early vs late)
        double targetDt = (now <= EARLY_TMAX_S) ? EARLY_DT_S : LATE_DT_S;

        // Due condizioni per emettere: o è passato abbastanza TEMPO, o sono passati
        // abbastanza EVENTI (fallback)
        boolean dueByTime = Double.isNaN(lastEmitTime) || (now - lastEmitTime) >= targetDt;
        boolean dueByEvent = (++eventsSinceLastEmit) >= LATE_EVERY_EVENTS;

        if (!(dueByTime || dueByEvent)) {
            return; // non ancora ora di scrivere
        }

        // reset contatori di gating
        lastEmitTime = now;
        eventsSinceLastEmit = 0;

        // === Tuo blocco di emissione invariato ===
        Set<Pair<String, String>> pairs = this.convergence.keySet();

        for (var pair : pairs) {
            double value;
            if (pair.getLeft().equals(OVERALL)) {
                value = convergenceOverall(scheduler, pair);
                long departures = comp.getCountSinceStart();

                sinkConvergence.appendConvRecord(SinkConvergenceToCsv.CsvHeaderConv.SCOPE, OVERALL);
                sinkConvergence.appendConvRecord(SinkConvergenceToCsv.CsvHeaderConv.METRIC, pair.getRight());
                sinkConvergence.appendConvRecord(SinkConvergenceToCsv.CsvHeaderConv.VALUE, fmt(value));
                sinkConvergence.appendConvRecord(SinkConvergenceToCsv.CsvHeaderConv.NUM_DEPARTURES,
                        String.valueOf(departures));
                sinkConvergence.appendConvRecord(SinkConvergenceToCsv.CsvHeaderConv.ARRIVAL_RATE,
                        String.valueOf(arrivalRate));
                sinkConvergence.appendConvRecord(SinkConvergenceToCsv.CsvHeaderConv.TIME, fmt(now));
                sinkConvergence.lineConvRecord();

            } else {
                value = convergencePerNode(scheduler, pair);
                long departures = compNode.get(pair.getLeft()).getCountSinceStart();

                sinkConvergence.appendConvRecord(SinkConvergenceToCsv.CsvHeaderConv.SCOPE, "NODE_" + pair.getLeft());
                sinkConvergence.appendConvRecord(SinkConvergenceToCsv.CsvHeaderConv.METRIC, pair.getRight());
                sinkConvergence.appendConvRecord(SinkConvergenceToCsv.CsvHeaderConv.VALUE, fmt(value));
                sinkConvergence.appendConvRecord(SinkConvergenceToCsv.CsvHeaderConv.NUM_DEPARTURES,
                        String.valueOf(departures));
                sinkConvergence.appendConvRecord(SinkConvergenceToCsv.CsvHeaderConv.ARRIVAL_RATE,
                        String.valueOf(arrivalRate));
                sinkConvergence.appendConvRecord(SinkConvergenceToCsv.CsvHeaderConv.TIME, fmt(now));
                sinkConvergence.lineConvRecord();
            }
        }
    }

    private double convergenceOverall(NextEventScheduler scheduler, Pair<String, String> pair) {
        double result = 0.0;
        switch (pair.getRight()) {
            // case MEAN_RESPONSE_TIME -> result = calculateOverallRtByVisits();
            case MEAN_RESPONSE_TIME -> result = rt.getWelfordEstimator().getMean();
            case STD_RESPONSE_TIME -> result = rt.welfordEstimator.getStddev();
            case MEAN_POPULATION -> result = pop.getMean();
            case STD_POPULATION -> result = pop.getStd();
            case THROUGHPUT -> {
                double elapsed = scheduler.getCurrentTime();
                result = elapsed > 0 ? comp.getCountSinceStart() / elapsed : 0.0;
            }
            case UTILIZATION -> {
                double elapsed = scheduler.getCurrentTime();
                result = (elapsed > 0 ? busy.getBusyTime() / elapsed : 0.0);
            }
            default -> {
                /* nessuna azione */ }
        }
        convergence.put(pair, result);
        return result;
    }

    private double convergencePerNode(NextEventScheduler scheduler, Pair<String, String> pair) {
        double result = 0.0;
        switch (pair.getRight()) {
            case MEAN_RESPONSE_TIME ->
                result = rtNode.get(pair.getLeft()).welfordEstimator.getMean();

            case STD_RESPONSE_TIME ->
                result = rtNode.get(pair.getLeft()).welfordEstimator.getStddev();
            case MEAN_POPULATION ->
                result = popNode.get(pair.getLeft()).getMean();
            case STD_POPULATION ->
                result = popNode.get(pair.getLeft()).getStd();
            case THROUGHPUT -> {
                double elapsed = scheduler.getCurrentTime();
                result = elapsed > 0
                        ? compNode.get(pair.getLeft()).getCountSinceStart() / elapsed
                        : 0.0;
            }
            case UTILIZATION -> {
                double elapsed = scheduler.getCurrentTime();
                result = elapsed > 0
                        ? busyNode.get(pair.getLeft()).getBusyTime() / elapsed
                        : 0.0;
            }
            default -> {
                /* nessuna azione */ }
        }
        convergence.put(pair, result);
        return result;
    }

    private void prepareConvergence(String node) {
        convergence.put(Pair.of(node, MEAN_RESPONSE_TIME), 0.0);
        convergence.put(Pair.of(node, STD_RESPONSE_TIME), 0.0);
        convergence.put(Pair.of(node, MEAN_POPULATION), 0.0);
        convergence.put(Pair.of(node, STD_POPULATION), 0.0);
        convergence.put(Pair.of(node, THROUGHPUT), 0.0);
        convergence.put(Pair.of(node, UTILIZATION), 0.0);
    }

    // ---------------- Utility di rendering tabellare (immutato) ----------------

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
                if (val instanceof Number number)
                    return String.format(Locale.ROOT, "%.6f", (number).doubleValue());
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
     * Finalizza gli stimatori time-weighted, costruisce le metriche globali e
     * per-nodo,
     * logga le tabelle ASCII e scrive il CSV di riepilogo.
     */
    public void calculateStats(NextEventScheduler scheduler, Network network) {
        // Finalizza busy intervals e popolazioni pesate al tempo corrente
        busy.finalizeBusy(scheduler.getCurrentTime());
        for (var b : busyNode.values()) {
            b.finalizeBusy(scheduler.getCurrentTime());
        }
        pop.finalizeAt(scheduler.getCurrentTime());
        for (PopulationEstimatorNode pn : popNode.values()) {
            pn.finalizeAt(scheduler.getCurrentTime());
        }

        // Orizzonte osservato
        double elapsed = ot.elapsed();

        // === MODIFICA: completamenti OVERALL "effettivi"
        // Se externalCompletions è valorizzato (probabilistico), usalo; altrimenti
        // resta il contatore interno.
        long completedOverall = comp.getCountSinceStart();

        // Throughput globale da completamenti a EXIT
        double throughput = elapsed > 0 ? completedOverall / elapsed : 0.0;

        // Tempo di risposta overall: media via somma pesata per visite; std via Welford
        // globale
        // double meanRtOverall = calculateOverallRtByVisits();
        double meanRtOverall = rt.getWelfordEstimator().getMean();
        double stdRtOverall = rt.welfordEstimator.getStddev();

        // Popolazione globale pesata nel tempo
        double meanPop = pop.getMean();
        double stdPop = pop.getStd();

        // Utilizzazione globale
        double utilization = elapsed > 0 ? busy.getBusyTime() / elapsed : 0.0;

        // Deviazioni standard via covarianze per-job (A,B,P)
        double stdRtCov = Double.NaN;
        double stdPopCov = Double.NaN;
        if (perJobCollector != null && perJobCollector.size() > 1) {
            double varSys = getVarSys();
            stdRtCov = Math.sqrt(varSys);
            // Legge di Little con X ≈ completions/time: std_N ≈ X * std_R
            stdPopCov = throughput * stdRtCov;
        }

        // Tabella globale (solo log)
        double globalTime = elapsed / 3600.0;
        List<Object> globalRow = List.of(
                fmt(meanRtOverall), fmt(stdRtOverall),
                fmt(meanPop), fmt(stdPop),
                fmt(throughput), fmt(utilization),
                (Double.isNaN(stdRtCov) ? "-" : fmt(stdRtCov)),
                (Double.isNaN(stdPopCov) ? "-" : fmt(stdPopCov)),
                fmt(globalTime));
        String info = "Global metrics (measured)\n" + makeTable(HEADERS, List.of(globalRow));
        SysLogger.getInstance().getLogger().info(info);

        // Tabella per nodo (solo log)
        List<List<Object>> perNodeRows = new ArrayList<>();
        for (String n : network.allNodes().stream().sorted().toList()) {
            PerNodeResult result = getPerNodeResult(n, elapsed);
            perNodeRows.add(List.of(
                    n,
                    fmt(result.sampleWait()), fmt(result.stdWn()),
                    fmt(result.samplePopulation()), fmt(result.stdNn()),
                    fmt(result.sampleMean()), fmt(result.util())));
        }
        info = "Per-node metrics (measured)\n" + makeTable(HEADERS_NODE, perNodeRows);
        SysLogger.getInstance().getLogger().info(info);
        // --- Riga OVERALL nel CSV ---
        sink.appendRecord(SinkToCsv.CsvHeader.SCOPE, OVERALL);
        sink.appendRecord(SinkToCsv.CsvHeader.ARRIVAL_RATE, fmt(arrivalRate));
        sink.appendRecord(SinkToCsv.CsvHeader.MEAN_RESPONSE_TIME, fmt(meanRtOverall));
        sink.appendRecord(SinkToCsv.CsvHeader.STD_RESPONSE_TIME, fmt(stdRtOverall));
        sink.appendRecord(SinkToCsv.CsvHeader.MEAN_POPULATION, fmt(meanPop));
        sink.appendRecord(SinkToCsv.CsvHeader.STD_POPULATION, fmt(stdPop));
        sink.appendRecord(SinkToCsv.CsvHeader.THROUGHPUT, fmt(throughput));
        sink.appendRecord(SinkToCsv.CsvHeader.UTILIZATION, fmt(utilization));
        sink.appendRecord(SinkToCsv.CsvHeader.STD_RESPONSE_TIME_COV, fmt(stdRtCov));
        sink.appendRecord(SinkToCsv.CsvHeader.STD_POPULATION_COV, fmt(stdPopCov));
        // NEW: modalità routing sempre riportata
        sink.appendRecord(SinkToCsv.CsvHeader.ROUTING_MODE, routingMode);
        // NEW: metriche di percorso solo se abilitate (probabilistico)
        if (routingPathStatsEnabled) {
            sink.appendRecord(SinkToCsv.CsvHeader.PATH_AB, String.valueOf(pathAB));
            sink.appendRecord(SinkToCsv.CsvHeader.PATH_ABAPA, String.valueOf(pathABAPA));
            sink.appendRecord(SinkToCsv.CsvHeader.PATH_ABAB_FORCED, String.valueOf(pathABABForced));
        }
        sink.lineRecord();

        // --- Righe per-nodo nel CSV ---
        for (String n : network.allNodes().stream().sorted().toList()) {
            PerNodeResult result = getPerNodeResult(n, elapsed);
            sink.appendRecord(SinkToCsv.CsvHeader.SCOPE, "NODE_" + n);
            sink.appendRecord(SinkToCsv.CsvHeader.ARRIVAL_RATE, fmt(arrivalRate));
            sink.appendRecord(SinkToCsv.CsvHeader.MEAN_RESPONSE_TIME, fmt(result.sampleWait()));
            sink.appendRecord(SinkToCsv.CsvHeader.STD_RESPONSE_TIME, fmt(result.stdWn()));
            sink.appendRecord(SinkToCsv.CsvHeader.MEAN_POPULATION, fmt(result.samplePopulation()));
            sink.appendRecord(SinkToCsv.CsvHeader.STD_POPULATION, fmt(result.stdNn()));
            sink.appendRecord(SinkToCsv.CsvHeader.THROUGHPUT, fmt(result.sampleMean()));
            sink.appendRecord(SinkToCsv.CsvHeader.UTILIZATION, fmt(result.util()));
            sink.appendRecord(SinkToCsv.CsvHeader.STD_RESPONSE_TIME_COV, "-");
            sink.appendRecord(SinkToCsv.CsvHeader.STD_POPULATION_COV, "-");
            // NEW: riporta comunque la modalità come promemoria
            sink.appendRecord(SinkToCsv.CsvHeader.ROUTING_MODE, routingMode);
            // Le path per-nodo non sono definite: lasciale "-" grazie al default del Sink
            sink.lineRecord();
        }

        // Se vuoi, qui potresti flushare i per-job: per ora resta disattivato
        // if (perJobCollector != null) { perJobCollector.flushToDisk(); }
    }

    protected @NotNull PerNodeResult getPerNodeResult(String n, double elapsed) {
        WelfordEstimator wrt = rtNode.get(n).welfordEstimator;
        double sampleWait = wrt.getMean();
        double stdWn = wrt.getStddev();

        double samplePopulation = popNode.get(n).getMean();
        double stdNn = popNode.get(n).getStd();

        int completedAtNode = compNode.get(n).getCountSinceStart();
        double sampleMean = elapsed > 0 ? completedAtNode / elapsed : 0.0;

        double util = elapsed > 0 ? busyNode.get(n).getBusyTime() / elapsed : 0.0;
        return new PerNodeResult(sampleWait, stdWn, samplePopulation, stdNn, sampleMean, util);
    }

    protected record PerNodeResult(double sampleWait, double stdWn, double samplePopulation, double stdNn,
            double sampleMean, double util) {
    }

    private double getVarSys() {
        double[] timeA = perJobCollector.getTA();
        double[] timeB = perJobCollector.getTB();
        double[] timeP = perJobCollector.getTP();

        double varA = sampleVariance(timeA);
        double varB = sampleVariance(timeB);
        double varP = sampleVariance(timeP);
        double covAB = sampleCovariance(timeA, timeB);
        double covAP = sampleCovariance(timeA, timeP);
        double covBP = sampleCovariance(timeB, timeP);

        return Math.max(0.0, varA + varB + varP + 2.0 * (covAB + covAP + covBP));
    }

    private double calculateOverallRtByVisits() {
        double a = rtNode.containsKey("A") ? rtNode.get("A").welfordEstimator.getMean() : 0.0;
        double b = rtNode.containsKey("B") ? rtNode.get("B").welfordEstimator.getMean() : 0.0;
        double p = rtNode.containsKey("P") ? rtNode.get("P").welfordEstimator.getMean() : 0.0;
        return a * 3.0 + b + p;
    }

    @Contract(pure = true)
    private static double sampleVariance(double @NotNull [] x) {
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

    @Contract(pure = true)
    private static double sampleCovariance(double @NotNull [] x, double @NotNull [] y) {
        int n = Math.min(x.length, y.length);
        if (n <= 1)
            return 0.0;
        double mx = 0.0;
        double my = 0.0;
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

    public void startMeasurement(NextEventScheduler scheduler) {
        double now = scheduler.getCurrentTime();

        // Global
        ot.startCollecting(now);
        busy.startCollecting(now);
        pop.startCollecting(now);
        comp.startCollecting(); // baseline per throughput
        rt.startCollecting(); // restart std_RT end-to-end

        // Per-nodo
        for (PopulationEstimatorNode pn : popNode.values())
            pn.startCollecting(now);
        for (CompletionsEstimatorNode cn : compNode.values())
            cn.startCollecting();
        for (ResponseTimeEstimatorNode rn : rtNode.values())
            rn.startCollecting();

        // Per-job
        if (perJobCollector != null)
            perJobCollector.startCollecting();
    }

    // --- NEW: API per abilitare/riempire le metriche di percorso nel CSV ---

    /**
     * Abilita/disabilita l'esportazione delle metriche di percorso (usato solo in
     * probabilistico).
     */
    public void enableRoutingPathStats(boolean enabled) {
        this.routingPathStatsEnabled = enabled;
    }

    /** Imposta i conteggi aggregati dei percorsi (forniti dalla Simulation). */
    public void setRoutingPathCounts(int ab, int abapa, int ababForced) {
        this.pathAB = ab;
        this.pathABAPA = abapa;
        this.pathABABForced = ababForced;
    }

    // dentro StatsCollector
    public void forwardExitToEstimators(int jobId, double now) {
        // completions
        comp.notifyExit(now);
        // response time end-to-end
        rt.notifyExit(jobId, now);
        // popolazione globale
        pop.notifyExit(jobId, now);
        // per-job (A,B,P)
        if (perJobCollector != null) {
            perJobCollector.notifyExit(jobId, now);
        }
    }

}
