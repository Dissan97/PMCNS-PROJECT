package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

/**
 * Tracks the observation period for the simulation.
 * <p>
 * The observation time is defined as the elapsed simulation time between
 * the first subscribed event (either {@link Event.Type#ARRIVAL} or
 * {@link Event.Type#DEPARTURE}) and the last processed event of the same types.
 * </p>
 * <p>
 * This estimator subscribes to ARRIVAL and DEPARTURE events to keep its
 * end time updated.
 * </p>
 */
public class ObservationTimeEstimator {

    /** The simulation time at which observation started. */
    private final double start;

    /** The simulation time of the last processed ARRIVAL or DEPARTURE event. */
    private double end;

    /**
     * Creates a new observation time estimator and subscribes it to
     * ARRIVAL and DEPARTURE events.
     *
     * @param sched the event scheduler from which simulation time is retrieved
     */
    public ObservationTimeEstimator(NextEventScheduler sched) {
        this.start = sched.getCurrentTime();
        this.end = this.start;
        sched.subscribe(Event.Type.ARRIVAL, this::upd);
        sched.subscribe(Event.Type.DEPARTURE, this::upd);
    }

    /**
     * Updates the last observed time based on the scheduler's current time.
     *
     * @param e the event triggering the update
     * @param s the scheduler providing the current time
     */
    private void upd(Event e, NextEventScheduler s) {
        this.end = s.getCurrentTime();
    }

    /**
     * Returns the total elapsed simulation time observed.
     *
     * @return the elapsed time between the first and last observed event
     */
    public double elapsed() {
        return end - start;
    }
}
