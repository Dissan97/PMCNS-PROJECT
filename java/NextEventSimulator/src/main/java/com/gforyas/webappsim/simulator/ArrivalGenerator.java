package com.gforyas.webappsim.simulator;

import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.lemer.Rvms;

/**
 * Generates external arrivals for the simulation using a Poisson process.
 * <p>
 * This class models an external arrival source that injects jobs into the system
 * at a specified Poisson rate. It schedules new arrival events into the
 * {@link NextEventScheduler}, targeting a specific node and job class.
 * </p>
 *
 * <p>
 * Each external arrival is followed by scheduling the next arrival according to
 * an exponential inter-arrival time distribution, derived from the given rate λ.
 * </p>
 */
public class ArrivalGenerator {

    /**
     * External Poisson arrival rate λ (jobs per unit time).
     */
    private final double rate;

    /**
     * Name of the target node that will receive the arrivals (e.g., "A").
     */
    private final String targetNode;

    /**
     * Job class identifier for generated arrivals (e.g., 1).
     */
    private final int jobClass;

    /**
     * Lehmer-based random number generator instance for reproducible randomness.
     */
    private final Rngs rng;

    /**
     * Singleton RVMS utility used for statistical distribution sampling.
     */
    private static final Rvms RVMS = Rvms.getInstance();

    /**
     * Flag indicating whether the generator is active and should keep producing arrivals.
     */
    private volatile boolean active = true;

    /**
     * Constructs a new {@code ArrivalGenerator} and schedules the first external arrival event.
     *
     * @param scheduler  The {@link NextEventScheduler} used to schedule events in the simulation.
     * @param rate       External Poisson arrival rate λ (jobs per unit time).
     * @param targetNode Name of the node that will receive the generated arrivals.
     * @param jobClass   Job class ID assigned to each generated arrival.
     * @param rng        {@link Rngs} Lehmer-based random number generator for sampling inter-arrival times.
     * @throws IllegalArgumentException if {@code rng} is {@code null}.
     */
    public ArrivalGenerator(NextEventScheduler scheduler, double rate,
                            String targetNode, int jobClass, Rngs rng) {
        if (rng == null) throw new IllegalArgumentException("Lemer RNG must be provided");
        this.rate = rate;
        this.targetNode = targetNode;
        this.jobClass = jobClass;
        this.rng = rng;

        // Subscribe to arrival events for continuous scheduling
        scheduler.subscribe(Event.Type.ARRIVAL, this::onArrival);

        // Schedule the first external arrival event at time zero
        Event first = new Event(0.0, Event.Type.ARRIVAL, targetNode, -1, jobClass);
        scheduler.scheduleAt(first, 0.0);
    }

    /**
     * Event handler for arrival events. Schedules the next external arrival
     * when an external source (-1 jobId) arrival occurs for the target node.
     *
     * @param ev         The arrival event being processed.
     * @param scheduler  The simulation's {@link NextEventScheduler}.
     */
    private void onArrival(Event ev, NextEventScheduler scheduler) {
        if (!active || ev instanceof BootstrapEvent) return;
        if (ev.getJobId() == -1 && targetNode.equals(ev.getServer())) {
            double ia = RVMS.idfExponential(1.0 / rate, rng.random());
            Event nextExternal = new Event(0.0, Event.Type.ARRIVAL, targetNode, -1, jobClass);
            scheduler.scheduleAt(nextExternal, scheduler.getCurrentTime() + ia);
        }
    }

    /**
     * Enables or disables the generator.
     *
     * @param v {@code true} to enable generation of new arrivals; {@code false} to stop.
     */
    public void setActive(boolean v) {
        this.active = v;
    }

    /**
     * Checks whether the generator is currently active.
     *
     * @return {@code true} if the generator is producing arrivals, {@code false} otherwise.
     */
    public boolean isActive() {
        return active;
    }
}
