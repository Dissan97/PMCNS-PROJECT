package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.util.Map;

/**
 * Specialized completion estimator that only counts completions
 * for a specific node in the network.
 * <p>
 * This class extends {@link CompletionsEstimator} but filters events so
 * that only {@link Event.Type#DEPARTURE} events originating from the
 * specified node are considered.
 * </p>
 */
public class CompletionsEstimatorNode extends CompletionsEstimator {

    /** The node this estimator is bound to. */
    private final String node;

    /**
     * Creates a new node-specific completion estimator.
     *
     * @param sched   the event scheduler
     * @param node    the node name to track
     * @param routing the routing matrix (node -> job class -> target class)
     */
    public CompletionsEstimatorNode(NextEventScheduler sched,
                                    String node,
                                    Map<String, Map<String, TargetClass>> routing) {
        super(sched, routing);
        this.node = node;
    }

    /**
     * Handles departure events, but only processes them if the
     * event's server matches the configured node.
     *
     * @param e the departure event
     * @param s the event scheduler
     */
    @Override
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) {
            count+=1;
        }
    }
}
