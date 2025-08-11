package com.g45.webappsim.estimators;

import com.g45.webappsim.logging.SysLogger;
import com.g45.webappsim.simulator.Network;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestLine;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EstimatorFacade {
    private static final List<String> HEADERS_NODE = List.of("Node","mean_response_time","std_response_time",
            "mean_population", "std_population","throughput","utilization");
    private final ResponseTimeEstimator rt;
    private final PopulationEstimator pop;
    private final CompletionsEstimator comp;
    private final ObservationTimeEstimator ot;
    private final BusyTimeEstimator busy;
    private static final List<String> HEADERS = List.of("mean_response_time","std_response_time","mean_population",
            "std_population","throughput","utilization");
    private final Map<String, ResponseTimeEstimatorNode> rtNode;
    private final Map<String, PopulationEstimatorNode> popNode;
    private final Map<String, CompletionsEstimatorNode> compNode;
    private final Map<String, BusyTimeEstimatorNode> busyNode;

    public EstimatorFacade(Network network, NextEventScheduler scheduler,
                           Map<String, Map<String, TargetClass>> routingMatrix) {
        this.rt = new ResponseTimeEstimator(scheduler);
        this.pop = new PopulationEstimator(scheduler);
        this.comp = new CompletionsEstimator(scheduler, routingMatrix);
        this.ot = new ObservationTimeEstimator(scheduler);
        this.busy = new BusyTimeEstimator(scheduler);
        this.rtNode = new HashMap<>();
        this.popNode = new HashMap<>();
        this.compNode = new HashMap<>();
        this.busyNode = new HashMap<>();
        for (String n : network.allNodes()) {
            rtNode.put(n, new ResponseTimeEstimatorNode(scheduler, n));
            popNode.put(n, new PopulationEstimatorNode(scheduler, n));
            compNode.put(n, new CompletionsEstimatorNode(scheduler, n, routingMatrix));
            busyNode.put(n, new BusyTimeEstimatorNode(scheduler, n));
        }
    }





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



    private static String fmt(double d) {
        return String.format("%.6f", d);
    }

    public void calculateStats(NextEventScheduler scheduler, Network network) {
        busy.finalizeBusy(scheduler.getCurrentTime());
        for (BusyTimeEstimator b : busyNode.values()) {
            b.finalizeBusy(scheduler.getCurrentTime());
        }
        // 4) METRICS COLLECTION (optional): compute & log like Python
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
        SysLogger.getInstance().getLogger().info("Global metrics\n" + makeTable( HEADERS, globalRows));

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

        SysLogger.getInstance().getLogger().info("Per-node metrics\n"+ makeTable(HEADERS_NODE, perNodeRows));

    }



    private double calculateOverallRt() {
        // Mirrors Python: RT_A * 3 + RT_B + RT_P
        double a = rtNode.containsKey("A") ? rtNode.get("A").w.getMean() : 0.0;
        double b = rtNode.containsKey("B") ? rtNode.get("B").w.getMean() : 0.0;
        double p = rtNode.containsKey("P") ? rtNode.get("P").w.getMean() : 0.0;
        return a * 3 + b + p;
    }

}
