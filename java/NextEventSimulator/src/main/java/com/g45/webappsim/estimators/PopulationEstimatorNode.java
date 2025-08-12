package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import java.util.function.BiConsumer;

/**
 * Estimates the *time-weighted* average population for a single node.
 * Always updates the area on every system event (tick),
 * but increments/decrements the population ONLY if the event
 * belongs to the monitored node.
 */
public class PopulationEstimatorNode {

    private final String node;  // name of the monitored node

    private int pop = 0;
    private final double startTime;
    private double lastTime;
    private double area = 0.0;

    private int min = 0;
    private int max = 0;

    private final BiConsumer<Event, NextEventScheduler> onArrival = this::nodeTickThenInc;
    private final BiConsumer<Event, NextEventScheduler> onDeparture = this::nodeTickThenDec;

    public PopulationEstimatorNode(NextEventScheduler sched, String node) {
        this.node = node;
        this.startTime = sched.getCurrentTime();
        this.lastTime = this.startTime;

        // Subscribe to ARRIVAL and DEPARTURE events
        // (always tick, increment/decrement only if event.server == node)
        sched.subscribe(Event.Type.ARRIVAL, onArrival);
        sched.subscribe(Event.Type.DEPARTURE, onDeparture);
    }

    /**
     * Updates the accumulated area up to the current time (global tick).
     */
    private void tick(NextEventScheduler s) {
        double now = s.getCurrentTime();
        double dt = now - lastTime;
        if (dt > 0.0) {
            area += pop * dt;
            lastTime = now;
        } else {
            lastTime = now;
        }
    }

    private void nodeTickThenInc(Event e, NextEventScheduler s) {
        tick(s); // Tick on ANY event
        // Increment only if ARRIVAL is for this node
        if (nodeEquals(e)) {
            pop += 1;
            if (pop > max) max = pop;
        }
    }

    private void nodeTickThenDec(Event e, NextEventScheduler s) {
        tick(s); // Tick on ANY event
        // Decrement only if DEPARTURE is for this node
        if (nodeEquals(e)) {
            pop -= 1;
            if (pop < min) min = pop;
        }
    }

    private boolean nodeEquals(Event e) {
        // Assumes Event has getServer() returning the node name (String)
        // Adjust if your Event class uses a different property name.
        String server = e.getServer();
        return server != null && server.equals(node);
    }

    /**
     * Returns the time-weighted average population for this node.
     */
    public double getMean() {
        double elapsed = lastTime - startTime;
        return (elapsed > 0.0) ? (area / elapsed) : 0.0;
    }

    public int getMin() { return min; }
    public int getMax() { return max; }

    public String getNode() { return node; }

    /**
     * Finalizes the area integration up to a given time
     * (e.g., at the end of simulation) before reading the mean.
     */
    public void finalizeAt(double currentTime) {
        double dt = currentTime - lastTime;
        if (dt > 0.0) {
            area += pop * dt;
            lastTime = currentTime;
        }
    }
}
