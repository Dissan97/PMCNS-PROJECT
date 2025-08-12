package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

import java.util.function.BiConsumer;

/**
 * Estimates the *time-weighted* average system population.
 * Idea: integrate the area under the pop(t) curve by updating on every event,
 * BEFORE changing the population. The average is area / observed_time.
 */
public class PopulationEstimator {

    // Current system population (number of jobs in the system)
    private int pop = 0;

    // Time variables to integrate the area under the curve
    private final double startTime;  // observation start time
    private double lastTime;         // last time the estimator was updated
    private double area = 0.0;       // ∫ pop(t) dt

    // Optional (for diagnostics)
    private int min = 0;
    private int max = 0;

    // Handlers registered to the scheduler
    private final BiConsumer<Event, NextEventScheduler> onArrival = this::tickThenInc;
    private final BiConsumer<Event, NextEventScheduler> onDeparture = this::tickThenDec;

    public PopulationEstimator(NextEventScheduler sched) {
        this.startTime = sched.getCurrentTime();
        this.lastTime = this.startTime;

        // Subscribe to ARRIVAL and DEPARTURE events
        sched.subscribe(Event.Type.ARRIVAL, onArrival);
        sched.subscribe(Event.Type.DEPARTURE, onDeparture);
        
    }

    /**
     * Update the accumulated area up to the current time,
     * without changing the population.
     * Always call this BEFORE incrementing/decrementing pop.
     */
    private void tick(NextEventScheduler s) {
        double now = s.getCurrentTime();
        double dt = now - lastTime;
        if (dt > 0.0) {
            area += pop * dt;
            lastTime = now;
        } else {
            // If dt == 0, no integration: same timestamp
            lastTime = now;
        }
    }

    private void tickThenInc(Event e, NextEventScheduler s) {
        tick(s);
        pop += 1;
        if (pop > max) max = pop;
    }

    private void tickThenDec(Event e, NextEventScheduler s) {
        tick(s);
        pop -= 1;
        if (pop < min) min = pop;
    }

    /**
     * Returns the *time-weighted* average population:
     *   N_bar = area / (lastTime - startTime)
     */
    public double getMean() {
        double elapsed = lastTime - startTime;
        return (elapsed > 0.0) ? (area / elapsed) : 0.0;
    }

    // Optional getters for diagnostics
    public int getMin() { return min; }
    public int getMax() { return max; }

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
