package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

/**
 * Weighted-time estimator of the population of a SINGLE node.
 *
 * <p><b>Rules:</b></p>
 * <ul>
 *   <li>The integration of the area (∫N(t)dt and ∫N(t)²dt) is performed
 *       <b>before</b> modifying the population.</li>
 *   <li>The population is incremented or decremented <b>only</b> if the event
 *       targets the monitored node.</li>
 * </ul>
 *
 * <p>This class complements {@link PopulationEstimator}, which accounts for the
 * whole system population. Here, the logic is restricted to a specific node and
 * ignores routing to EXIT.</p>
 */
public class PopulationEstimatorNode extends PopulationEstimator {

    /** The monitored node identifier. */
    private final String node;

    /**
     * Creates a node-level population estimator bound to a given node.
     * The estimator subscribes to ARRIVAL and DEPARTURE events on the provided scheduler.
     *
     * @param sched the scheduler providing the simulation clock and event bus
     * @param node  the logical name of the node to be tracked
     */
    public PopulationEstimatorNode(NextEventScheduler sched, String node) {
        this.node = node;
        this.startTime = sched.getCurrentTime();
        this.lastTime  = this.startTime;

        // Subscribe to node-level ARRIVAL and DEPARTURE events
        sched.subscribe(Event.Type.ARRIVAL,   this::nodeTickThenInc);
        sched.subscribe(Event.Type.DEPARTURE, this::nodeTickThenDec);
    }

    /**
     * Checks whether a given event belongs to the monitored node.
     *
     * @param e the event
     * @return {@code true} if the event is for the monitored node, {@code false} otherwise
     */
    private boolean nodeEquals(Event e) {
        String server = e.getServer();
        return server != null && server.equals(node);
    }

    /**
     * Handles an ARRIVAL event: integrates the area up to the event time,
     * and increments the node population if the event belongs to the node.
     */
    private void nodeTickThenInc(Event e, NextEventScheduler s) {
        tick(s);
        if (nodeEquals(e)) {
            pop += 1;
            if (pop > max) max = pop;
        }
    }

    /**
     * Handles a DEPARTURE event: integrates the area up to the event time,
     * and decrements the node population if the event belongs to the node.
     */
    private void nodeTickThenDec(Event e, NextEventScheduler s) {
        tick(s);
        if (nodeEquals(e)) {
            pop -= 1;
            if (pop < min) min = pop;
        }
    }

    /**
     * Returns the identifier of the monitored node.
     *
     * @return the node name
     */
    public String getNode() { return node; }

    /**
     * Finalizes the integration up to the given simulation time.
     *
     * @param currentTime current simulation time
     */
    @Override
    public void finalizeAt(double currentTime) {
        double dt = currentTime - lastTime;
        if (dt > 0.0) {
            area  += pop * dt;
            area2 += (double) pop * (double) pop * dt;
        }
        lastTime = currentTime;
    }
}
