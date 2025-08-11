package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

/**
 * A specialization of {@link PopulationEstimator} that tracks the population
 * only for a specific node in the network.
 * <p>
 * It listens to {@link Event.Type#ARRIVAL} and {@link Event.Type#DEPARTURE}
 * events, but only updates statistics when the event's {@code server} matches
 * the target node specified in the constructor.
 * </p>
 */
public class PopulationEstimatorNode extends PopulationEstimator {

    /** The node name this estimator is bound to. */
    private final String node;

    /**
     * Creates a new population estimator for a specific node.
     *
     * @param sched the event scheduler to subscribe to
     * @param node  the node name to filter events by
     */
    public PopulationEstimatorNode(NextEventScheduler sched, String node) {
        super(sched);
        this.node = node;
    }

    /**
     * Handles an arrival event only if it belongs to the target node.
     *
     * @param e the arrival event
     * @param s the event scheduler
     */
    @Override
    protected void inc(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) {
            super.inc(e, s);
        }
    }

    /**
     * Handles a departure event only if it belongs to the target node.
     *
     * @param e the departure event
     * @param s the event scheduler
     */
    @Override
    protected void dec(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) {
            super.dec(e, s);
        }
    }
}
