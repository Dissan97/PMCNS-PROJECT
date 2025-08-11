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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.g45.webappsim.App.cfgPath;

/**
 * Facade that aggregates and manages multiple performance estimators for
 * a Next-Event-Simulation of a network of service nodes.
 * <p>
 * This class collects global and per-node metrics such as:
 * <ul>
 *   <li>Mean and standard deviation of response time</li>
 *   <li>Mean and standard deviation of population</li>
 *   <li>Throughput</li>
 *   <li>Utilization</li>
 * </ul>
 * It also produces ASCII tables summarizing these statistics for logging.
 */
public class EstimatorFacade {

    /** Headers for global metrics table. */
    private static final List<String> HEADERS = List.of("mean_response_time","std_response_time","mean_population",
            "std_population","throughput","utilization");
    /** Headers for per-node metrics table. */
    private static final List<String> HEADERS_NODE = List.of("Node","mean_response_time","std_response_time",
            "mean_population", "std_population","throughput","utilization");
    private static final Path OUT_DIR = Path.of(".output_simulation");

    /** Global response time estimator. */
    private final ResponseTimeEstimator rt;

    /** Global population estimator. */
    private final PopulationEstimator pop;

    /** Global completions estimator. */
    private final CompletionsEstimator comp;

    /** Observation time estimator to measure elapsed simulation time. */
    private final ObservationTimeEstimator ot;

    /** Global busy time estimator. */
    private final BusyTimeEstimator busy;

    /** Per-node response time estimators. */
    private final Map<String, ResponseTimeEstimatorNode> rtNode;

    /** Per-node population estimators. */
    private final Map<String, PopulationEstimatorNode> popNode;

    /** Per-node completions estimators. */
    private final Map<String, CompletionsEstimatorNode> compNode;

    /** Per-node busy time estimators. */
    private final Map<String, BusyTimeEstimatorNode> busyNode;
    private final long seed;

    /**
     * Constructs an {@code EstimatorFacade} for the given network and scheduler,
     * and initializes all estimators (both global and per-node).
     *
     * @param network       the simulated network
     * @param scheduler     the event scheduler
     * @param routingMatrix the routing matrix mapping nodes and classes to next destinations
     * @param seed          simulator seed
     */
    public EstimatorFacade(Network network, NextEventScheduler scheduler,
                           Map<String, Map<String, TargetClass>> routingMatrix, long seed) {
        this.rt = new ResponseTimeEstimator(scheduler);
        this.pop = new PopulationEstimator(scheduler);
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
            popNode.put(n, new PopulationEstimatorNode(scheduler, n));
            compNode.put(n, new CompletionsEstimatorNode(scheduler, n, routingMatrix));
            busyNode.put(n, new BusyTimeEstimatorNode(scheduler, n));
        }
    }

    /**
     * Creates an ASCII table with the specified headers and rows.
     * Columns are automatically sized to fit the longest cell content,
     * and numeric values are formatted to six decimal places.
     *
     * @param headers the column headers
     * @param rows    the table rows; each row must match the header size
     * @return a string representation of the ASCII table
     * @throws IllegalArgumentException if a row size does not match the header size
     */
    public static String makeTable(List<String> headers, List<List<Object>> rows) {
        AsciiTable at = new AsciiTable();

        // Use auto-size column width based on longest line
        at.getRenderer().setCWC(new CWC_LongestLine());

        // Center align all cells by default
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

            // Format numeric values with fixed precision
            Object[] formattedRow = row.stream().map(val -> {
                if (val instanceof Number) {
                    return String.format("%.6f", ((Number) val).doubleValue());
                } else {
                    return val != null ? val.toString() : "";
                }
            }).toArray();

            at.addRow(formattedRow);
            at.setTextAlignment(TextAlignment.CENTER);
            at.addRule();
        }
        return at.render();
    }

    /**
     * Formats a double value to six decimal places.
     *
     * @param d the value to format
     * @return the formatted string
     */
    private static String fmt(double d) {
        return String.format("%.6f", d);
    }

    /**
     * Calculates and logs global and per-node simulation statistics.
     * <p>
     * This method finalizes busy time estimators, computes metrics such as
     * mean/standard deviation of response time and population, throughput,
     * and utilization, and logs them in ASCII table format.
     *
     * @param scheduler the simulation scheduler
     * @param network   the simulated network
     */
    public void calculateStats(NextEventScheduler scheduler, Network network) {
        busy.finalizeBusy(scheduler.getCurrentTime());
        for (BusyTimeEstimator b : busyNode.values()) {
            b.finalizeBusy(scheduler.getCurrentTime());
        }
        // METRICS COLLECTION
        double elapsed = ot.elapsed();
        double meanRtOverall = calculateOverallRt();
        double stdRtOverall = rt.w.getStddev();
        double meanPop = pop.w.getMean();
        double stdPop = pop.w.getStddev();
        double throughput = elapsed > 0 ? (double) comp.count / elapsed : 0.0;
        double utilization = elapsed > 0 ? busy.getBusyTime() / elapsed : 0.0;

        List<List<Object>> globalRows = List.of(
                List.of(fmt(meanRtOverall), fmt(stdRtOverall), fmt(meanPop), fmt(stdPop),
                        fmt(throughput), fmt(utilization))
        );
        SysLogger.getInstance().getLogger().info("Global metrics\n" + makeTable(HEADERS, globalRows));

        List<List<Object>> perNodeRows = new ArrayList<>();
        for (String n : network.allNodes().stream().sorted().toList()) {
            WelfordEstimator wrt = rtNode.get(n).w;
            WelfordEstimator wpop = popNode.get(n).w;
            BusyTimeEstimatorNode b = busyNode.get(n);
            int completed = compNode.get(n).count;
            double th = elapsed > 0 ? (double) completed / elapsed : 0.0;
            double util = elapsed > 0 ? b.getBusyTime() / elapsed : 0.0;
            perNodeRows.add(List.of(
                    n,
                    fmt(wrt.getMean()), fmt(wrt.getStddev()),
                    fmt(wpop.getMean()), fmt(wpop.getStddev()),
                    fmt(th), fmt(util)
            ));
        }

        SysLogger.getInstance().getLogger().info("Per-node metrics\n" + makeTable(HEADERS_NODE, perNodeRows));

        // --- save CSV ----------------------------------------------------------
        saveToCsv(network, meanRtOverall, stdRtOverall, meanPop, stdPop, throughput, utilization, elapsed);
    }

    private void saveToCsv(Network network, double meanRtOverall, double stdRtOverall, double meanPop, double stdPop,
                           double throughput, double utilization, double elapsed) {
        try {
            // Build timestamp like Python: YYYYMMDD_HHMMSS
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

            // Extract stem from CFG_PATH (filename without extension)
            String cfgFile = cfgPath.replace(".json", "");
            int dot = cfgFile.lastIndexOf('.');
            String cfgStem = (dot > 0 ? cfgFile.substring(0, dot) : cfgFile);

            // results_{CFG_PATH.stem}_run{run_idx:03}_seed{seed}_{ts}.csv
            String name = String.format("results_%s_run%03d_seed%s_%s.csv", cfgStem,
                    Simulation.SIMULATION_COUNTER.get(), String.valueOf(seed), ts);
            Path outPath = OUT_DIR.resolve(name);

            // Ensure output directory exists
            Files.createDirectories(OUT_DIR);

            // Compose CSV: scope,node,mean_rt,std_rt,mean_pop,std_pop,throughput,utilization
            StringBuilder csv = new StringBuilder();
            csv.append("scope,mean_response_time,std_response_time,mean_population,std_population,throughput,utilization\n");

            // OVERALL row (scope="OVERALL")
            csv.append(String.join(",",
                            "OVERALL",
                            fmt(meanRtOverall),
                            fmt(stdRtOverall),
                            fmt(meanPop),
                            fmt(stdPop),
                            fmt(throughput),
                            fmt(utilization)))
                    .append('\n');

            // Per-node rows (scope="NODE_{n}")
            for (String n : network.allNodes().stream().sorted().toList()) {
                WelfordEstimator wrt = rtNode.get(n).w;
                WelfordEstimator wpop = popNode.get(n).w;
                BusyTimeEstimatorNode b = busyNode.get(n);
                int completed = compNode.get(n).count;
                double th = elapsed > 0 ? (double) completed / elapsed : 0.0;
                double util = elapsed > 0 ? b.getBusyTime() / elapsed : 0.0;

                csv.append(String.join(",",
                                "NODE_" + n,
                                fmt(wrt.getMean()), fmt(wrt.getStddev()),
                                fmt(wpop.getMean()), fmt(wpop.getStddev()),
                                fmt(th), fmt(util)))
                        .append('\n');
            }

            // Write CSV
            Files.writeString(outPath, csv.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            SysLogger.getInstance().getLogger().info("✓ CSV salvato → " + name);
        } catch (IOException e) {
            SysLogger.getInstance().getLogger().warning("CSV save failed: " + e.getMessage());
        }
    }

    /**
     * Computes the overall mean response time for the system based on per-node means.
     * <p>
     * Formula: {@code RT_A * 3 + RT_B + RT_P}, which reflects a specific routing pattern
     * where node A is visited three times per job.
     *
     * @return the calculated overall response time
     */
    private double calculateOverallRt() {
        // Mirrors Python: RT_A * 3 + RT_B + RT_P
        double a = rtNode.containsKey("A") ? rtNode.get("A").w.getMean() : 0.0;
        double b = rtNode.containsKey("B") ? rtNode.get("B").w.getMean() : 0.0;
        double p = rtNode.containsKey("P") ? rtNode.get("P").w.getMean() : 0.0;
        return a * 3 + b + p;
    }
}
