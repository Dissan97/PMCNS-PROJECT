package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.util.Map;

/**
 * <p><strong>Purpose.</strong> This estimator counts system completions, defined as
 * <em>DEPARTURE</em> events that lead to an <code>EXIT</code> state in the routing map.</p>
 *
 * <p><strong>Warm-up support.</strong> The estimator provides a mechanism to reset a baseline
 * after the warm-up phase. From that point onward, only completions beyond the baseline
 * are measured, ensuring that transient effects are excluded from statistics.</p>
 *
 * <p><strong>Integration.</strong> At construction time, this class subscribes to
 * {@link Event.Type#DEPARTURE} events on the provided {@link NextEventScheduler}. Every time
 * a departure leading to an <code>EXIT</code> is observed, the internal completion counter
 * is incremented.</p>
 */
public class CompletionsEstimator {

    /** Total number of completions observed since the beginning of the simulation. */
    private int totalCount = 0;

    /** Baseline count recorded at the end of the warm-up phase. */
    private int baseCount = 0;

    private final Map<String, Map<String, TargetClass>> routing;

    /**
     * <p>Creates a new completions estimator and subscribes it to DEPARTURE events
     * on the given scheduler.</p>
     *
     * @param sched   the event scheduler providing the simulation clock and event bus
     * @param routing the routing map used to determine whether a departure leads to {@code EXIT}
     */
    public CompletionsEstimator(NextEventScheduler sched,
                                Map<String, Map<String, TargetClass>> routing) {
        this.routing = routing;
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    /**
     * <p>Resets the baseline counter at the end of the warm-up phase.
     * Further calls to {@link #getCountSinceStart()} will return the number of completions
     * observed since this reset point.</p>
     */
    public void startCollecting() {
        baseCount = totalCount;
    }

    /**
     * <p>Returns the total number of completions observed since the beginning of the simulation.</p>
     *
     * @return the total completion count
     */
    public int getTotalCount() {
        return totalCount;
    }

    /**
     * <p>Returns the number of completions observed since the last call to
     * {@link #startCollecting()}, which is typically invoked at the end of the warm-up phase.</p>
     *
     * @return the completion count measured after the baseline reset
     */
    public int getCountSinceStart() {
        return totalCount - baseCount;
    }

    /**
     * <p>Handles a DEPARTURE event and increments the total completion counter
     * if the event corresponds to an <code>EXIT</code> in the routing map.</p>
     *
     * @param e the departure event
     * @param s the scheduler providing the current simulation time
     */
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (isExit(e.getServer(), e.getJobClass())) {
            totalCount += 1;
        }
    }

    /**
     * <p>Determines whether a departure from the given server and job class
     * corresponds to an <code>EXIT</code> in the routing map.</p>
     *
     * @param server   the logical server name
     * @param jobClass the job class identifier
     * @return {@code true} if the route leads to an <code>EXIT</code>, {@code false} otherwise
     */
    protected boolean isExit(String server, int jobClass) {
        Map<String, TargetClass> m = routing.get(server);
        if (m == null) return false;
        TargetClass tc = m.get(Integer.toString(jobClass));
        return tc != null && "EXIT".equalsIgnoreCase(tc.eventClass());
    }
}
