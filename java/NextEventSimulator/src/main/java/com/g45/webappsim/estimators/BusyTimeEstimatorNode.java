package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

/**
 * <p><strong>Purpose.</strong> This estimator tracks the <em>busy time</em> of a single node.
 * The node is considered busy whenever at least one job is present at that node.</p>
 *
 * <p><strong>Scope.</strong> Unlike the system-wide estimator, this class does not use routing
 * information or the notion of <code>EXIT</code>. It updates the node population directly on
 * the node's own <em>ARRIVAL</em> and <em>DEPARTURE</em> events.</p>
 *
 * <p><strong>Behavior.</strong></p>
 * <ul>
 *   <li>On an arrival in the tracked node, if the population transitions from 0 to 1,
 *       the busy interval opens, and the timestamp is recorded; the population is then incremented.</li>
 *   <li>On a departure from the tracked node, the population is decremented; if it reaches 0,
 *       the busy interval is closed, and its duration is accumulated.</li>
 * </ul>
 */
public class BusyTimeEstimatorNode extends BusyTimeEstimator {

    private final String node;

    /**
     * <p>Creates a node-level busy-time estimator bound to a specific node name and subscribes
     * it to ARRIVAL and DEPARTURE events on the provided scheduler.</p>
     *
     * <p><em>Note.</em> The superclass requires a routing map for system-level accounting, which
     * is not needed here; therefore, a <code>null</code> routing reference is passed.</p>
     *
     * @param sched the event scheduler providing the simulation clock and event bus
     * @param node  the logical name of the node to be tracked
     */
    public BusyTimeEstimatorNode(NextEventScheduler sched, String node) {
        // The superclass requires routing for system-level logic; for a node we do not need it, so pass null.
        super(sched, null);
        this.node = node;
    }

    /**
     * <p>Handles an ARRIVAL event. If the event targets the tracked node and the current
     * population is zero, the method opens a new busy interval by recording the current time.
     * The node population is then incremented.</p>
     *
     * @param e the arrival event
     * @param s the scheduler providing the current simulation time
     */
    @Override
    protected void onArrival(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) {
            if (pop == 0) {
                busy = true;
                last = s.getCurrentTime();
            }
            pop += 1;
        }
    }

    /**
     * <p>Handles a DEPARTURE event. If the event targets the tracked node, the method decrements
     * the node population. When the population reaches zero while the node is busy, the method
     * closes the busy interval and accumulates its duration.</p>
     *
     * @param e the departure event
     * @param s the scheduler providing the current simulation time
     */
    @Override
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) {
            pop -= 1;
            if (pop == 0 && busy) {
                total += s.getCurrentTime() - last;
                busy = false;
            }
        }
    }
}
