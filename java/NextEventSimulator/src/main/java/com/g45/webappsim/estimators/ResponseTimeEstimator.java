package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

import java.util.HashMap;
import java.util.Map;

/**
 * Estimates response time statistics for jobs in the simulation
 * using an online variance algorithm ({@link WelfordEstimator}).
 * <p>
 * This estimator tracks the arrival time for each job when it enters the
 * system and removes it upon departure. The difference between the departure
 * time and stored arrival time is recorded in {@link WelfordEstimator}
 * to compute mean and variance of response times.
 * </p>
 */
public class ResponseTimeEstimator {

    /** Maps job ID to its recorded arrival time. */
    protected final Map<Integer, Double> arr = new HashMap<>();

    /** Online estimator for mean and variance of response times. */
    public final WelfordEstimator w = new WelfordEstimator();

    /**
     * Creates a response time estimator that subscribes to arrival
     * and departure events in the scheduler.
     *
     * @param sched the simulation event scheduler
     */
    public ResponseTimeEstimator(NextEventScheduler sched) {
        sched.subscribe(Event.Type.ARRIVAL, this::onArrival);
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    /**
     * Records the arrival time for a job when it enters the system.
     * Only tracks jobs with a valid job ID (non -1).
     *
     * @param e the arrival event
     * @param s the scheduler
     */
    protected void onArrival(Event e, NextEventScheduler s) {
        if (e.getJobId() != -1) {
            arr.put(e.getJobId(), e.getTime());
        }
    }

    /**
     * Computes the response time for a departing job and updates statistics.
     *
     * @param e the departure event
     * @param s the scheduler
     */
    protected void onDeparture(Event e, NextEventScheduler s) {
        Double at = arr.remove(e.getJobId());
        if (at != null) {
            w.add(e.getTime() - at);
        }
    }
}
