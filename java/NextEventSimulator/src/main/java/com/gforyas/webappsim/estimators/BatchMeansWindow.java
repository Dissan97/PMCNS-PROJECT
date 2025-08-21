package com.gforyas.webappsim.estimators;

import com.gforyas.webappsim.simulator.Event;
import com.gforyas.webappsim.simulator.Network;
import com.gforyas.webappsim.simulator.NextEventScheduler;
import com.gforyas.webappsim.simulator.TargetClass;

import java.util.*;

/**
 * Batch-means controller that slices the observation horizon into fixed-length
 * non-overlapping batches and computes per-batch performance metrics using the
 * same estimators used for the global run (response time, population, throughput,
 * and utilization). Results are kept in memory and can be exported to CSV by the caller.
 *
 * <p><strong>How it works</strong></p>
 * <ul>
 *   <li>This class owns a <em>separate</em> set of estimators that are reset at the
 *       beginning of each batch. They subscribe to the scheduler and update themselves
 *       on every ARRIVAL/DEPARTURE, just like the global estimators.</li>
 *   <li>On every simulation event, the controller checks whether the current simulation
 *       time crossed the next batch boundary. If so, it finalizes the current batch,
 *       records its metrics, resets the estimators, and starts the next batch.</li>
 *   <li>Batches are time-based (length {@code batchLength}). If the simulation ends
 *       in the middle of a batch, {@link #finalizeAt(double)} can be called to close
 *       the partial batch (it will be recorded only if it has positive duration).</li>
 * </ul>
 *
 * <p><strong>Why a separate set of estimators?</strong> We do not mutate the global
 * estimators used by {@link StatsCollector}. Using dedicated batch estimators avoids
 * interference and does not require changes to existing estimator classes.</p>
 */
public class BatchMeansWindow {

    private boolean started = false;     // true after startAt(...)
    private boolean full  = false;     // true after reaching maxBatches
    private static final double EPS = 1e-12;
    /** Immutable view of a single batch result. */
    public static final class Result {
        public final int index;
        public final double tStart;
        public final double tEnd;
        public final double meanRt;
        public final double stdRt;
        public final double meanN;
        public final double stdN;
        public final double throughput;
        public final double utilization;
        /** System completions within this batch (EXIT events). */
        public final int completions;

        Result(int index, TimeManage timeManage,
               MeanStdMetric rt, MeanStdMetric pop,
               double throughput, double utilization, int completions) {
            this.index = index;
            this.tStart = timeManage.start;
            this.tEnd = timeManage.end;
            this.meanRt = rt.mean;
            this.stdRt = rt.std;
            this.meanN = pop.mean;
            this.stdN = pop.std;
            this.throughput = throughput;
            this.utilization = utilization;
            this.completions = completions;
        }
    }

    private record MeanStdMetric(double mean, double std) {
    }

    private record TimeManage(double start, double end) {}

    private final double batchLength;
    private final int maxBatches;

    // Separate estimators used only for batch computation
    private final ResponseTimeEstimator rt;
    private final PopulationEstimator pop;
    private final CompletionsEstimator comp;
    private final BusyTimeEstimator busy;
    private final Map<String, ResponseTimeEstimatorNode> rtNode = new HashMap<>();
    private final Map<String, PopulationEstimatorNode> popNode = new HashMap<>();
    private final Map<String, CompletionsEstimatorNode> compNode = new HashMap<>();
    private final Map<String, BusyTimeEstimatorNode> busyNode = new HashMap<>();

    // Book-keeping
    private double tStart;
    private double tNext;
    private int batchIndex = 0;
    private final List<Result> results = new ArrayList<>();

    /**
     * Creates a batch-means controller.
     *
     * @param network     the network (nodes are used to build per-node estimators)
     * @param sched       the scheduler (event bus + simulation clock)
     * @param routing     the routing matrix, used by system-level estimators
     * @param batchLength fixed length of each batch (simulation time units, must be &gt; 0)
     * @param maxBatches  optional upper bound on the number of batches (&le; 0 means "no bound")
     * @throws IllegalArgumentException if {@code batchLength &le; 0}
     */
    public BatchMeansWindow(Network network,
                            NextEventScheduler sched,
                            Map<String, Map<String, TargetClass>> routing,
                            double batchLength,
                            int maxBatches) {
        if (batchLength <= 0.0) {
            throw new IllegalArgumentException("batchLength must be > 0");
        }
        this.batchLength = batchLength;
        this.maxBatches = maxBatches;

        // Build the batch-scoped estimators and subscribe them
        this.rt = new ResponseTimeEstimator(sched, routing);
        this.pop = new PopulationEstimator(sched, routing);
        this.comp = new CompletionsEstimator(sched, routing);
        this.busy = new BusyTimeEstimator(sched, routing);

        for (String n : network.allNodes()) {
            rtNode.put(n, new ResponseTimeEstimatorNode(sched, n, routing));
            popNode.put(n, new PopulationEstimatorNode(sched, n));
            compNode.put(n, new CompletionsEstimatorNode(sched, n, routing));
            busyNode.put(n, new BusyTimeEstimatorNode(sched, n));
        }

        // Watch the event stream to detect batch boundaries
        sched.subscribe(Event.Type.ARRIVAL, this::maybeRotate);
        sched.subscribe(Event.Type.DEPARTURE, this::maybeRotate);
    }

    /**
     * Starts the first batch at the provided time. Typically called right after warm-up.
     * Resets all batch estimators.
     *
     * @param now current simulation time
     */
    public void startAt(double now) {
        this.started = true;
        this.tStart = now;
        this.tNext  = now + batchLength;
        this.batchIndex = 0;

        extractCollection(now);
    }

    private void extractCollection(double now) {
        rt.startCollecting();
        pop.startCollecting(now);
        comp.startCollecting();
        busy.startCollecting(now);
        for (var pn : popNode.values()) pn.startCollecting(now);
        for (var cn : compNode.values()) cn.startCollecting();
        for (var rn : rtNode.values()) rn.startCollecting();
        for (var bn : busyNode.values()) bn.startCollecting(now);
    }

    /** Returns an immutable snapshot of finished batch results. */
    public List<Result> getResults() { return Collections.unmodifiableList(results); }

    /**
     * Closes the current batch at {@code currentTime}, records it if the batch length
     * is positive, and starts the next batch (unless {@code maxBatches} has been reached).
     */
    private void rotate(double currentTime) {
        // Finalize time-weighted estimators exactly at the boundary
        pop.finalizeAt(currentTime);
        for (var pn : popNode.values()) pn.finalizeAt(currentTime);
        busy.finalizeBusy(currentTime);
        for (var bn : busyNode.values()) bn.finalizeBusy(currentTime);

        double len = currentTime - tStart;
        if (len > 0.0) {
            // Prefer exact completions from the estimator baseline of this batch
            int comps = comp.getCountSinceStart();
            double thr = comps / len;

            double meanRt = rt.getWelfordEstimator().getMean();
            double stdRt  = rt.getWelfordEstimator().getStddev();
            double meanN  = pop.getMean();
            double stdN   = pop.getStd();
            double util   = busy.getBusyTime() / len;

            results.add(new Result(++batchIndex, new TimeManage(tStart, currentTime),
                    new MeanStdMetric(meanRt, stdRt), new MeanStdMetric(meanN, stdN), thr, util, comps));
        }

        // Stop if max batches reached
        if (maxBatches > 0 && batchIndex >= maxBatches) {
            full = true;
            return; // do NOT open a new batch; residual tail is ignored
        }

        // Reset estimators and open next batch
        tStart = currentTime;
        tNext  = currentTime + batchLength;

        extractCollection(currentTime);
    }

    /**
     * Event hook: if the current time crossed one or more batch boundaries,
     * rotate batches accordingly.
     */
    private void maybeRotate(Event e, NextEventScheduler s) {
        if (!started || full) return;
        double now = s.getCurrentTime();
        while (now + EPS >= tNext) {
            rotate(tNext);
            if (full) return;
        }
    }

    /**
     * Finalizes the current batch at the given time. Useful at the end of the simulation.
     * If the partial batch has positive duration, it is recorded as the last entry.
     *
     * @param currentTime the timestamp used to close the batch
     */
    public void finalizeAt(double currentTime) {
        if (currentTime > tStart) {
            rotate(currentTime);
        }
    }
}
