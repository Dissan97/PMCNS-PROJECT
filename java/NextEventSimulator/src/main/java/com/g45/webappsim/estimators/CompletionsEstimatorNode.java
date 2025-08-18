package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.util.Map;

/**
 * <p><strong>Purpose.</strong> This estimator counts all <em>DEPARTURE</em> events occurring
 * at a single node, regardless of their destination. It provides support for warm-up handling
 * by allowing the definition of a baseline counter.</p>
 *
 * <p><strong>Note.</strong> Although the historical name refers to "Completions," in this
 * context the counter is used as a per-node departure counter to estimate the throughput
 * of the node. The global system throughput should instead be measured using
 * {@link CompletionsEstimator}, which only counts departures leading to <code>EXIT</code>.</p>
 *
 * <p><strong>Integration.</strong> At construction time, this class subscribes to
 * {@link Event.Type#DEPARTURE} events on the provided {@link NextEventScheduler}.
 * Whenever a departure event is observed for the specified node, the counter is incremented.</p>
 */
public class CompletionsEstimatorNode {

    private final String node;

    /**
     * A routing map kept for compatibility, but not used in this class.
     */
    @SuppressWarnings("unused")
    private final Map<String, Map<String, TargetClass>> routing;

    /** Total number of departures observed at this node since the beginning of the simulation. */
    private int totalCount = 0;

    /** Baseline counter value used to measure departures only after the warm-up phase. */
    private int baseCount  = 0;

    /**
     * <p>Creates a node-level departures estimator and subscribes it to DEPARTURE events
     * on the given scheduler.</p>
     *
     * @param sched   the event scheduler providing the simulation clock and event bus
     * @param node    the logical name of the node being monitored
     * @param routing the routing map (not used here, kept for compatibility with system estimators)
     */
    public CompletionsEstimatorNode(NextEventScheduler sched,
                                    String node,
                                    Map<String, Map<String, TargetClass>> routing) {
        this.node = node;
        this.routing = routing;
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    /**
     * <p>Resets the baseline counter at the beginning of the measurement window
     * (typically right after the warm-up phase).</p>
     */
    public void startCollecting() {
        baseCount = totalCount;
    }

    /**
     * <p>Returns the total number of departures observed at this node
     * since the beginning of the simulation.</p>
     *
     * @return the total departure count
     */
    public int getTotalCount() {
        return totalCount;
    }

    /**
     * <p>Returns the number of departures observed at this node since the last call to
     * {@link #startCollecting()}.</p>
     *
     * @return the departure count measured after the baseline reset
     */
    public int getCountSinceStart() {
        return totalCount - baseCount;
    }

    /**
     * <p>Handles a DEPARTURE event. If the event corresponds to the tracked node,
     * increments the counter of departures.</p>
     *
     * @param e the departure event
     * @param s the scheduler providing the current simulation time
     */
    private void onDeparture(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) {
            totalCount += 1;
        }
    }
}
