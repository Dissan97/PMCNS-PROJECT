package com.g45.webappsim.estimators;

import com.g45.webappsim.logging.SysLogger;
import com.g45.webappsim.simulator.Network;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.Simulation;
import com.g45.webappsim.simulator.TargetClass;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestLine;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.g45.webappsim.App.getCfgPath;

/**
 * <p><strong>Purpose.</strong> This facade orchestrates global and per-node estimators
 * to compute and report performance metrics for the simulated network.</p>
 *
 * <p><strong>Features.</strong></p>
 * <ul>
 *   <li>Global estimators: end-to-end response time, time-weighted population, system completions,
 *       observation window, and global busy time.</li>
 *   <li>Per-node estimators: response time, time-weighted population, departures, and busy time.</li>
 *   <li>For nodes <code>A</code>, <code>B</code>, and <code>P</code>, uses a
 *       {@link ResponseTimeEstimatorNode} variant that supports per-job aggregation.</li>
 *   <li>A {@link ResponseTimePerJobCollector} captures per-job times
 *       (T<sub>A</sub>, T<sub>B</sub>, T<sub>P</sub>, T<sub>total</sub>) and keeps samples
 *       for empirical variance/covariance estimates.</li>
 *   <li>Exports additional covariance-based metrics
 *       (<code>std_response_time_cov</code>, <code>std_population_cov</code>) in CSV output.</li>
 * </ul>
 *
 * <p><strong>Integration.</strong> Instantiate with the network, scheduler, routing matrix, and seed.
 * The facade wires up all estimators to the scheduler, and provides utilities to start measurement,
 * finalize time-weighted quantities, log ASCII tables, and write CSV summaries.</p>
 */
public class EstimatorFacade {

    // Table headers
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

    // Global estimators
    private final ResponseTimeEstimator rt;   // end-to-end (external ARRIVAL -> EXIT)
    private final PopulationEstimator pop;    // time-weighted population (tracks jobId)
    private final CompletionsEstimator comp;  // completions at EXIT
    private final ObservationTimeEstimator ot; // observed time window
    private final BusyTimeEstimator busy;     // global busy time

    // Per-node estimators
    private final Map<String, ResponseTimeEstimatorNode> rtNode = new HashMap<>();
    private final Map<String, PopulationEstimatorNode> popNode = new HashMap<>();
    private final Map<String, CompletionsEstimatorNode> compNode = new HashMap<>();
    private final Map<String, BusyTimeEstimatorNode> busyNode = new HashMap<>();

    // Collector for per-job times (A, B, P)
    private final ResponseTimePerJobCollector perJobCollector;

    private final long seed;

    /**
     * <p>Creates the estimator facade and registers all estimators/collectors.</p>
     *
     * @param network       the simulated network providing the set of nodes
     * @param scheduler     the event scheduler (clock and event bus)
     * @param routingMatrix the routing matrix used by system-level estimators
     * @param seed          the random seed for reproducibility
     */
    public EstimatorFacade(Network network,
                           NextEventScheduler scheduler,
                           Map<String, Map<String, TargetClass>> routingMatrix,
                           long seed) {

        // Global estimators
        this.rt = new ResponseTimeEstimator(scheduler, routingMatrix);
        this.pop = new PopulationEstimator(scheduler, routingMatrix); // tracks jobId (first ARRIVAL >= 0 -> EXIT)
        this.comp = new CompletionsEstimator(scheduler, routingMatrix);
        this.ot = new ObservationTimeEstimator(scheduler);
        this.busy = new BusyTimeEstimator(scheduler, routingMatrix);

        this.seed = seed;

        // Per-node estimators
        for (String n : network.allNodes()) {
            // Per-node response time (exclude A/B/P here; they use the per-job variant below)
            if (!(n.equals("A") || n.equals("B") || n.equals("P"))) {
                rtNode.put(n, new ResponseTimeEstimatorNode(scheduler, n, routingMatrix));
            }

            popNode.put(n, new PopulationEstimatorNode(scheduler, n));
            compNode.put(n, new CompletionsEstimatorNode(scheduler, n, routingMatrix));
            busyNode.put(n, new BusyTimeEstimatorNode(scheduler, n));
        }

        // A/B/P: use the ResponseTimeEstimatorNode variant "with sums" (per-job aggregation)
        ResponseTimeEstimatorNode rtA = new ResponseTimeEstimatorNode(scheduler, "A", routingMatrix);
        ResponseTimeEstimatorNode rtB = new ResponseTimeEstimatorNode(scheduler, "B", routingMatrix);
        ResponseTimeEstimatorNode rtP = new ResponseTimeEstimatorNode(scheduler, "P", routingMatrix);
        rtNode.put("A", rtA);
        rtNode.put("B", rtB);
        rtNode.put("P", rtP);

        // Per-job collector (CSV + in-memory samples for var/cov)
        Path perJobCsv = OUT_DIR.resolve("per_job_times.csv");
        perJobCollector = new ResponseTimePerJobCollector(
                scheduler, routingMatrix, rtA, rtB, rtP, perJobCsv);
    }

    /**
     * <p>Utility: render an ASCII table given column headers and rows.</p>
     *
     * @param headers the list of column headers
     * @param rows    the list of rows; each row must match the header size
     * @return the rendered ASCII table
     * @throws IllegalArgumentException if a row size does not match the header size
     */
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

    /**
     * <p>Formats a double using a fixed-point, six-decimal representation with
     * the <em>ROOT</em> locale.</p>
     *
     * @param d the value
     * @return the formatted value
     */
    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.6f", d);
    }

    /**
     * <p>Finalizes time-weighted quantities at the current simulation time, computes global and
     * per-node metrics, logs human-readable ASCII tables, and writes a CSV summary. Also flushes
     * per-job samples to disk when the collector is present.</p>
     *
     * @param scheduler the event scheduler providing the current simulation time
     * @param network   the network whose nodes are iterated for per-node metrics
     */
    public void calculateStats(NextEventScheduler scheduler, Network network) {
        // Finalize busy intervals and time-weighted populations up to "now"
        busy.finalizeBusy(scheduler.getCurrentTime());
        for (BusyTimeEstimator b : busyNode.values()) {
            b.finalizeBusy(scheduler.getCurrentTime());
        }
        pop.finalizeAt(scheduler.getCurrentTime());
        for (PopulationEstimatorNode pn : popNode.values()) {
            pn.finalizeAt(scheduler.getCurrentTime());
        }

        // Observed horizon
        double elapsed = ot.elapsed();

        // Global throughput from completions to EXIT
        // Safe: if startCollecting() was never called, this still equals total/time
        double throughput = elapsed > 0 ? comp.getCountSinceStart() / elapsed : 0.0;

        // Overall response time: mean via visit-weighted sum; std via global Welford
        double meanRtOverall = calculateOverallRtByVisits();
        double stdRtOverall = rt.welfordEstimator.getStddev();

        // Global time-weighted population
        double meanPop = pop.getMean();
        double stdPop = pop.getStd();

        // Global utilization (busy time / elapsed time)
        double utilization = elapsed > 0 ? busy.getBusyTime() / elapsed : 0.0;

        // Standard deviations using empirical per-job covariance (A, B, P)
        double stdRtCov = Double.NaN;
        double stdPopCov = Double.NaN;
        if (perJobCollector != null && perJobCollector.size() > 1) {
            double varSys = getVarSys();
            stdRtCov = Math.sqrt(varSys);
            // Little’s law with X ≈ completions / time: std_N ≈ X * std_R
            stdPopCov = throughput * stdRtCov;
        }

        // Global table
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

        // Per-node table
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

        // Save CSV + flush per-job samples
        saveToCsv(network, meanRtOverall, stdRtOverall, meanPop, stdPop,
                throughput, utilization, stdRtCov, stdPopCov, elapsed);
        if (perJobCollector != null) {
            perJobCollector.flushToDisk();
        }
    }

    /**
     * <p>Builds per-node metrics for a given node over an observed horizon.</p>
     *
     * @param n       the node identifier
     * @param elapsed the observed elapsed time
     * @return an immutable record containing per-node results
     */
    private @NotNull PerNodeResult getPerNodeResult(String n, double elapsed) {
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

    /**
     * <p>Per-node metrics container.</p>
     */
    private record PerNodeResult(double sampleWait, double stdWn, double samplePopulation, double stdNn, double sampleMean, double util) { }

    /**
     * <p>Estimates the system response-time variance using per-job samples from nodes A, B, and P,
     * including pairwise covariances. The result is clamped to be non-negative.</p>
     *
     * @return the non-negative variance estimate for the system response time
     */
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

    /**
     * <p>Writes the global and per-node metrics to a CSV file under
     * <code>.output_simulation</code>. The filename embeds the configuration stem,
     * the run counter, the seed, and a timestamp.</p>
     *
     * @param network         the network used to iterate nodes
     * @param meanRtOverall   the visit-weighted mean response time
     * @param stdRtOverall    the global response-time standard deviation (Welford)
     * @param meanPop         the global mean population (time-weighted)
     * @param stdPop          the global population standard deviation (time-weighted)
     * @param throughput      the global throughput (completions per unit time)
     * @param utilization     the global utilization (busy/elapsed)
     * @param stdRtCov        the covariance-based response-time standard deviation
     * @param stdPopCov       the covariance-based population standard deviation
     * @param elapsed         the observed elapsed time
     */
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
                    cfgStem, Simulation.SIMULATION_COUNTER.get(), seed, ts);
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

            // Per-node
            for (String n : network.allNodes().stream().sorted().toList()) {
                PerNodeResult result = getPerNodeResult(n, elapsed);

                csv.append(String.join(",",
                        "NODE_" + n,
                        fmt(result.sampleWait()), fmt(result.stdWn()),
                        fmt(result.samplePopulation()), fmt(result.stdNn()),
                        fmt(result.sampleMean()), fmt(result.util()),
                        "-", "-" // no covariance metrics per node
                )).append('\n');
            }

            Files.writeString(outPath, csv.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            String info = "✓ CSV salvato → " + name; // log message kept as-is (not a comment)
            SysLogger.getInstance().getLogger().info(info);
        } catch (IOException e) {
            SysLogger.getInstance().getLogger().warning("CSV save failed: " + e.getMessage());
        }
    }

    /**
     * <p>Computes the overall mean response time using a visit-weighted sum of per-node means:
     * <code>3 * W_A + W_B + W_P</code>, where <code>W_*</code> are the measured per-node means.</p>
     *
     * @return the overall visit-weighted mean response time
     */
    private double calculateOverallRtByVisits() {
        double a = rtNode.containsKey("A") ? rtNode.get("A").welfordEstimator.getMean() : 0.0;
        double b = rtNode.containsKey("B") ? rtNode.get("B").welfordEstimator.getMean() : 0.0;
        double p = rtNode.containsKey("P") ? rtNode.get("P").welfordEstimator.getMean() : 0.0;
        return a * 3.0 + b + p;
    }

    // ---- statistical helpers (sample variance and covariance, ddof = 1) ----

    /**
     * <p>Computes the unbiased sample variance (ddof = 1).</p>
     *
     * @param x the sample array
     * @return the sample variance, or 0.0 if the sample size is ≤ 1
     */
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

    /**
     * <p>Computes the unbiased sample covariance (ddof = 1) using paired samples of equal
     * or different length (the minimum length is used).</p>
     *
     * @param x the first sample array
     * @param y the second sample array
     * @return the sample covariance, or 0.0 if the paired sample size is ≤ 1
     */
    private static double sampleCovariance(double[] x, double[] y) {
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

    /**
     * <p>Starts a coordinated collection phase for all estimators, typically invoked
     * immediately after warm-up. Time-weighted estimators receive the current timestamp.</p>
     *
     * @param sched the event scheduler providing the current simulation time
     */
    public void startCollectingAll(NextEventScheduler sched) {
        double now = sched.getCurrentTime();

        // Completions
        comp.startCollecting();
        for (CompletionsEstimatorNode c : compNode.values())
            c.startCollecting();

        // Populations (time-weighted)
        pop.startCollecting(now);
        for (PopulationEstimatorNode pn : popNode.values())
            pn.startCollecting(now);

        // Busy time (time-weighted)
        busy.startCollecting(now);
        for (BusyTimeEstimatorNode bn : busyNode.values())
            bn.startCollecting(now);

        // Response times
        rt.startCollecting();
        for (ResponseTimeEstimatorNode rn : rtNode.values())
            rn.startCollecting();

        // Observation window
        ot.startCollecting(now);
    }

    /**
     * <p>Starts a measurement window: resets the observation window, global busy/population
     * accumulators, EXIT-based completions baseline, and response-time accumulators; then
     * resets per-node estimators and the per-job collector if present.</p>
     *
     * @param scheduler the event scheduler providing the current simulation time
     */
    public void startMeasurement(NextEventScheduler scheduler) {
        double now = scheduler.getCurrentTime();

        // Global
        ot.startCollecting(now);
        busy.startCollecting(now);
        pop.startCollecting(now);
        comp.startCollecting();          // baseline for throughput
        rt.startCollecting();            // restart end-to-end std_RT

        // Per-node
        for (PopulationEstimatorNode pn : popNode.values()) pn.startCollecting(now);
        for (CompletionsEstimatorNode cn : compNode.values()) cn.startCollecting();
        for (ResponseTimeEstimatorNode rn : rtNode.values()) rn.startCollecting();

        // Per-job
        if (perJobCollector != null) perJobCollector.startCollecting();
    }
}
