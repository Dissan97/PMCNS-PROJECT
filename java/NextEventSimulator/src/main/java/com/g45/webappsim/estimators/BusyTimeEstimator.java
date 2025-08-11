package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

/**
 * Estimates the total busy time of a system or resource during a simulation run.
 * <p>
 * A resource is considered "busy" when at least one job is present. This estimator:
 * <ul>
 *   <li>Subscribes to {@link Event.Type#ARRIVAL} and {@link Event.Type#DEPARTURE} events.</li>
 *   <li>Tracks the number of jobs currently in the system ({@code pop}).</li>
 *   <li>Accumulates the total time the system is busy.</li>
 * </ul>
 * The accumulated time can be retrieved via {@link #getBusyTime()} at any point,
 * and must be finalized with {@link #finalizeBusy(double)} at the end of the simulation.
 * </p>
 */
public class BusyTimeEstimator {

    /** Current population in the resource. */
    protected int pop = 0;

    /** Flag indicating if the resource is currently busy. */
    protected boolean busy = false;

    /** Last timestamp when the busy state changed. */
    protected double last;

    /** Accumulated busy time. */
    protected double total = 0.0;

    /**
     * Creates a busy time estimator and subscribes it to the given scheduler.
     *
     * @param sched the event scheduler driving the simulation
     */
    public BusyTimeEstimator(NextEventScheduler sched) {
        this.last = sched.getCurrentTime();
        sched.subscribe(Event.Type.ARRIVAL, this::onArrival);
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    /**
     * Handles an arrival event: if the resource was idle, it becomes busy.
     *
     * @param e the event
     * @param s the scheduler
     */
    protected void onArrival(Event e, NextEventScheduler s) {
        if (pop == 0) {
            busy = true;
            last = s.getCurrentTime();
        }
        pop += 1;
    }

    /**
     * Handles a departure event: if the last job leaves, the busy time is updated.
     *
     * @param e the event
     * @param s the scheduler
     */
    protected void onDeparture(Event e, NextEventScheduler s) {
        pop -= 1;
        if (pop == 0 && busy) {
            total += s.getCurrentTime() - last;
            busy = false;
        }
    }

    /**
     * Returns the total accumulated busy time.
     *
     * @return the busy time in simulation time units
     */
    public double getBusyTime() {
        return total;
    }

    /**
     * Finalizes the busy time calculation at the end of the simulation.
     * <p>
     * This method should be called once at the end to ensure any ongoing busy period is accounted for.
     * </p>
     *
     * @param currentTime the simulation's current time
     */
    public void finalizeBusy(double currentTime) {
        if (pop > 0 && busy) {
            total += currentTime - last;
            busy = false;
        }
    }
}
