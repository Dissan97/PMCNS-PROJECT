package com.gforyas.webappsim.simulator;

import com.gforyas.webappsim.lemer.Rngs;

public class HyperExpArrGen extends ArrivalGenerator {

    private final int hypExpStateStream;
    private final double p;
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
    public HyperExpArrGen(NextEventScheduler scheduler, double rate, String targetNode,
                          int jobClass, Rngs rng, double p) {
        super(scheduler, rate, targetNode, jobClass, rng);
        hypExpStateStream = Rngs.getStreamId();
        this.p = p;
    }

    @Override
    protected void onArrival(Event ev, NextEventScheduler scheduler) {
        if (!active || ev instanceof BootstrapEvent) return;
        if (ev.getJobId() == -1 && targetNode.equals(ev.getServer())) {
            double ia = RVMS.idfHyperExponential2SameMean(1.0 / rate, this.p, rng.random(hypExpStateStream),
                    rng.random(this.streamId));
            Event nextExternal = new Event(0.0, Event.Type.ARRIVAL, targetNode, -1, jobClass);
            scheduler.scheduleAt(nextExternal, scheduler.getCurrentTime() + ia);
        }
    }
}
