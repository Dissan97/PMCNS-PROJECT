package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

/**
 * Node-specific version of {@link ResponseTimeEstimator}.
 * <p>
 * This estimator tracks and computes response times only for jobs
 * processed by the specified node.
 * </p>
 */
public class ResponseTimeEstimatorNode extends ResponseTimeEstimator {

    /** The node name whose jobs should be tracked. */
    private final String node;

    /**
     * Creates a node-specific response time estimator.
     *
     * @param sched the simulation event scheduler
     * @param node  the node name to filter on
     */
    public ResponseTimeEstimatorNode(NextEventScheduler sched, String node) {
        super(sched);
        this.node = node;
    }

    /**
     * Records the arrival time for a job at this node, if it has a valid job ID.
     *
     * @param e the arrival event
     * @param s the scheduler
     */
    @Override
    protected void onArrival(Event e, NextEventScheduler s) {
        if (e.getJobId() != -1 && node.equals(e.getServer())) {
            arr.put(e.getJobId(), e.getTime());
        }
    }

    /**
     * Computes and updates the response time only if the departure
     * occurs at this node.
     *
     * @param e the departure event
     * @param s the scheduler
     */
    @Override
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) {
            super.onDeparture(e, s);
        }
    }
}
