package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

/**
 * A node-specific extension of {@link BusyTimeEstimator}.
 * <p>
 * This estimator tracks the busy time for a single node in the network, rather than
 * for the entire system. It filters {@link Event.Type#ARRIVAL} and {@link Event.Type#DEPARTURE}
 * events so that only those belonging to the specified node are considered.
 * </p>
 */
public class BusyTimeEstimatorNode extends BusyTimeEstimator {

    /** The name of the node being monitored. */
    private final String node;

    /**
     * Creates a busy time estimator for a specific node.
     *
     * @param sched the event scheduler driving the simulation
     * @param node  the name of the node to track
     */
    public BusyTimeEstimatorNode(NextEventScheduler sched, String node) {
        super(sched);
        this.node = node;
    }

    /**
     * Handles an arrival event, but only if it belongs to the monitored node.
     *
     * @param e the event
     * @param s the scheduler
     */
    @Override
    protected void onArrival(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) {
            super.onArrival(e, s);
        }
    }

    /**
     * Handles a departure event, but only if it belongs to the monitored node.
     *
     * @param e the event
     * @param s the scheduler
     */
    @Override
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) {
            super.onDeparture(e, s);
        }
    }
}
