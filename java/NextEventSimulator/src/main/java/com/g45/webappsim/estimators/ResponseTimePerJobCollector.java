package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p><strong>Purpose.</strong> Collects per-job response-time components at <code>EXIT</code>:
 * T<sub>A</sub>, T<sub>B</sub>, T<sub>P</sub>, and their total.</p>
 *
 * <p><strong>Behavior.</strong></p>
 * <ul>
 *   <li>Accumulates all CSV rows in memory (via a {@link StringBuilder}); no I/O is performed during the simulation.</li>
 *   <li>Maintains in-memory samples for variance/covariance analysis (aligned by job index).</li>
 *   <li>At the end of the run, call {@link #flushToDisk()} to write everything in one shot.</li>
 * </ul>
 *
 * <p><strong>Integration.</strong> The collector subscribes to {@link Event.Type#DEPARTURE}
 * and records data only for departures that route to <code>EXIT</code> with a known job id
 * (i.e., {@code jobId >= 0}). Per-node accumulators are drained via
 * {@link ResponseTimeEstimatorNode#takeAndClear(int)} at job completion.</p>
 */
public class ResponseTimePerJobCollector {

    private final Map<String, Map<String, TargetClass>> routing;
    private final ResponseTimeEstimatorNode estA;
    private final ResponseTimeEstimatorNode estB;
    private final ResponseTimeEstimatorNode estP;

    private final Path outPath;

    // In-memory buffer for CSV rows (without header)
    private final StringBuilder sb = new StringBuilder(1 << 20); // ~1 MB initial capacity

    // In-memory samples for var/cov (aligned by index)
    private final List<Double> tA = new ArrayList<>();
    private final List<Double> tB = new ArrayList<>();
    private final List<Double> tP = new ArrayList<>();

    /**
     * <p>Creates a per-job response-time collector and subscribes it to DEPARTURE events.</p>
     *
     * @param sched   the event scheduler providing the current simulation time and event bus
     * @param routing the routing map used to detect whether a departure leads to {@code EXIT}
     * @param estA    the per-node response-time estimator for node A
     * @param estB    the per-node response-time estimator for node B
     * @param estP    the per-node response-time estimator for node P
     * @param outCsv  the output CSV path where rows will be appended during {@link #flushToDisk()}
     */
    public ResponseTimePerJobCollector(NextEventScheduler sched,
                                       Map<String, Map<String, TargetClass>> routing,
                                       ResponseTimeEstimatorNode estA,
                                       ResponseTimeEstimatorNode estB,
                                       ResponseTimeEstimatorNode estP,
                                       Path outCsv) {
        this.routing = routing;
        this.estA = estA; this.estB = estB; this.estP = estP;
        this.outPath = outCsv;

        // No I/O here: header is written in flushToDisk() if needed
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    /**
     * <p>Handles a DEPARTURE event. If the job has a known id and the route leads to
     * {@code EXIT}, drains per-node accumulators for A/B/P, appends a CSV row to the in-memory
     * buffer, and retains samples for covariance computations.</p>
     *
     * @param e the departure event
     * @param s the scheduler providing the current simulation time
     */
    private void onDeparture(Event e, NextEventScheduler s) {
        if (e.getJobId() < 0) return;
        if (!routesToExit(e.getServer(), e.getJobClass())) return;

        int id = e.getJobId();
        double timeA = estA.takeAndClear(id);
        double timeB = estB.takeAndClear(id);
        double timeP = estP.takeAndClear(id);
        double timeTotal = timeA + timeB + timeP;

        // Append in memory only
        sb.append(id).append(',')
                .append(timeA).append(',')
                .append(timeB).append(',')
                .append(timeP).append(',')
                .append(timeTotal).append(',')
                .append(e.getTime()).append('\n');

        // Keep samples for covariance
        tA.add(timeA); tB.add(timeB); tP.add(timeP);
    }

    /**
     * <p>Determines whether a (server, jobClass) pair routes to {@code EXIT}.</p>
     *
     * @param server   the logical server name
     * @param jobClass the job class identifier
     * @return {@code true} if the transition leads to {@code EXIT}, {@code false} otherwise
     */
    private boolean routesToExit(String server, int jobClass) {
        Map<String, TargetClass> m = routing.get(server);
        if (m == null) return false;
        TargetClass tc = m.get(Integer.toString(jobClass));
        return tc != null && "EXIT".equalsIgnoreCase(tc.eventClass());
    }

    // ---- API exposed to EstimatorFacade ----

    /**
     * <p>Returns the number of collected job samples.</p>
     *
     * @return the sample count
     */
    public int size() { return tA.size(); }

    /**
     * <p>Returns a copy of the collected T<sub>A</sub> samples as a primitive array.</p>
     *
     * @return the T_A samples
     */
    public double[] getTA() { return toArray(tA); }

    /**
     * <p>Returns a copy of the collected T<sub>B</sub> samples as a primitive array.</p>
     *
     * @return the T_B samples
     */
    public double[] getTB() { return toArray(tB); }

    /**
     * <p>Returns a copy of the collected T<sub>P</sub> samples as a primitive array.</p>
     *
     * @return the T_P samples
     */
    public double[] getTP() { return toArray(tP); }

    /**
     * <p>Writes the buffered rows to disk in a single operation:</p>
     * <ul>
     *   <li>creates the output directory if necessary.</li>
     *   <li>writes the header if the file does not already exist.</li>
     *   <li>appends the accumulated buffer of rows.</li>
     * </ul>
     *
     * <p>After a successful writing, the internal buffer is cleared to free memory.</p>
     *
     * @throws UncheckedIOException if an I/O error occurs
     */
    public void flushToDisk() {
        try {
            Files.createDirectories(outPath.getParent());
            boolean exists = Files.exists(outPath);
            try (BufferedWriter w = Files.newBufferedWriter(
                    outPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                if (!exists) {
                    w.write("job_id,T_A,T_B,T_P,T_total,t_complete\n");
                }
                w.write(sb.toString());
            }
            // Optionally free memory if the collector keeps running
            sb.setLength(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * <p>Copies a list of {@link Double} into a primitive {@code double[]}.</p>
     *
     * @param src the source list
     * @return the resulting primitive array
     */
    private static double[] toArray(List<Double> src) {
        double[] a = new double[src.size()];
        for (int i = 0; i < src.size(); i++) a[i] = src.get(i);
        return a;
    }

    /**
     * <p>Resets the in-memory buffers to start a fresh collection window. This method clears
     * the CSV buffer and the sample lists. Per-job per-node accumulators live inside the node
     * estimators and are expected to have been reset upstream when necessary.</p>
     */
    public void startCollecting() {
        sb.setLength(0);
        tA.clear();
        tB.clear();
        tP.clear();
        // Nothing else to reset: per-job sums live in the node estimators (already reset upstream)
    }
}
