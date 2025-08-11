package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

/**
 * Tracks the instantaneous population (number of jobs) in the system
 * over time and computes statistical measures using {@link WelfordEstimator}.
 * <p>
 * This estimator listens to {@link Event.Type#ARRIVAL} and
 * {@link Event.Type#DEPARTURE} events to update the population count.
 * Every time the population changes, the new value is fed into the
 * Welford estimator to maintain running statistics (mean, variance, etc.).
 * </p>
 */
public class PopulationEstimator {

    /** Current number of jobs in the system. */
    protected int pop = 0;

    /** Welford estimator for computing mean and variance of population over time. */
    public final WelfordEstimator w = new WelfordEstimator();

    /**
     * Creates a new population estimator and subscribes it to ARRIVAL and DEPARTURE events.
     *
     * @param sched the event scheduler to subscribe to
     */
    public PopulationEstimator(NextEventScheduler sched) {
        sched.subscribe(Event.Type.ARRIVAL, this::inc);
        sched.subscribe(Event.Type.DEPARTURE, this::dec);
    }

    /**
     * Handles an arrival event by incrementing the population and updating statistics.
     *
     * @param e the arrival event
     * @param s the event scheduler
     */
    protected void inc(Event e, NextEventScheduler s) {
        pop += 1;
        w.add(pop);
    }

    /**
     * Handles a departure event by decrementing the population and updating statistics.
     *
     * @param e the departure event
     * @param s the event scheduler
     */
    protected void dec(Event e, NextEventScheduler s) {
        pop -= 1;
        w.add(pop);
    }
}
