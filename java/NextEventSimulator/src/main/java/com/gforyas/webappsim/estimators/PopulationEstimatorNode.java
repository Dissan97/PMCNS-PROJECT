package com.gforyas.webappsim.estimators;

import com.gforyas.webappsim.simulator.Event;
import com.gforyas.webappsim.simulator.NextEventScheduler;
import java.util.Set;
import java.util.HashSet;


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

    private final String node;
    private final Set<Integer> inNode = new HashSet<>();

    public PopulationEstimatorNode(NextEventScheduler sched, String node) {
        this.node = node;
        this.startTime = sched.getCurrentTime();
        this.lastTime  = this.startTime;
        sched.subscribe(Event.Type.ARRIVAL,   this::nodeTickThenInc);
        sched.subscribe(Event.Type.DEPARTURE, this::nodeTickThenDec);
    }

    private boolean nodeEquals(Event e) {
        String server = e.getServer();
        return server != null && server.equals(node);
    }

    private void nodeTickThenInc(Event e, NextEventScheduler s) {
        tick(s); // integra SEMPRE fino a "now"
        if (!nodeEquals(e)) return;
        int id = e.getJobId();
        if (id >= 0 && inNode.add(id)) {
            pop += 1;
            if (pop > max) max = pop;
        }
    }

    private void nodeTickThenDec(Event e, NextEventScheduler s) {
        tick(s);
        if (!nodeEquals(e)) return;
        int id = e.getJobId();
        if (id >= 0 && inNode.remove(id)) {
            pop -= 1;
            if (pop < min) min = pop;
        }
    }

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

