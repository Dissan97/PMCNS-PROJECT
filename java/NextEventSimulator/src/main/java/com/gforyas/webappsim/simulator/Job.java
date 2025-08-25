package com.gforyas.webappsim.simulator;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a job (request/task) in the simulation.
 * <p>
 * Each job has:
 * <ul>
 *     <li>A unique ID assigned at creation time</li>
 *     <li>A job class (mutable, can change during routing)</li>
 *     <li>An arrival time (immutable)</li>
 *     <li>A remaining service time (mutable, changes as the job is served)</li>
 * </ul>
 * </p>
 */
public final class Job {

    /**
     * Global atomic counter to assign unique IDs to jobs.
     */
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    /**
     * Unique identifier for the job.
     */
    private final int id;

    /**
     * Current job class.
     * <p>
     * This value can change if the job is routed to a different class
     * during the simulation.
     * </p>
     */
    private int jobClass;

    /**
     * Simulation time when the job first arrived in the system.
     */
    private final double arrivalTime;

    /**
     * Remaining service time for the job.
     * <p>
     * This value decreases as the job is processed and is reset when
     * the job is routed to a new service stage.
     * </p>
     */
    private double remainingService;

    private String route;

    /**
     * Creates a new job with the given parameters.
     *
     * @param jobClass     the class/type of the job (e.g., 1, 2, 3...)
     * @param arrivalTime  the simulation time at which the job arrived
     * @param serviceTime  the initial total service time required
     */
    public Job(int jobClass, double arrivalTime, double serviceTime) {
        this.id = SEQ.getAndIncrement();
        this.jobClass = jobClass;
        this.arrivalTime = arrivalTime;
        this.remainingService = serviceTime;
    }

    /**
     * Returns the unique job ID.
     *
     * @return job ID
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the current class of the job.
     *
     * @return job class
     */
    public int getJobClass() {
        return jobClass;
    }

    /**
     * Updates the job's class.
     *
     * @param c new job class
     */
    public void setJobClass(int c) {
        this.jobClass = c;
    }

    /**
     * Returns the time when the job first entered the system.
     *
     * @return arrival time
     */
    public double getArrivalTime() {
        return arrivalTime;
    }

    /**
     * Returns the remaining service time required for this job.
     *
     * @return remaining service time
     */
    public double getRemainingService() {
        return remainingService;
    }

    /**
     * Updates the remaining service time for the job.
     *
     * @param v new remaining service time
     */
    public void setRemainingService(double v) {
        this.remainingService = v;
    }

    /**
     * Returns a string representation of the job.
     *
     * @return formatted string with job details
     */
    @Override
    public String toString() {
        return String.format("Job(id=%d, class=%d, arr=%.6f, rem=%.6f)",
                id, jobClass, arrivalTime, remainingService);
    }

    public String getRoute() {
        return route;
    }
    public void setRoute(String route) {
        this.route = route;
    }
    /**
     * Resets the global job counter.
     * <p>
     * Useful when restarting a simulation so that job IDs start from 0.
     * </p>
     */
    public static void reset() {
        SEQ.set(0);
    }
}
