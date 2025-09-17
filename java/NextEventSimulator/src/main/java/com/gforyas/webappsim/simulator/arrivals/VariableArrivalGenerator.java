package com.gforyas.webappsim.simulator.arrivals;

import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.simulator.BootstrapEvent;
import com.gforyas.webappsim.simulator.Event;
import com.gforyas.webappsim.simulator.NextEventScheduler;

public class VariableArrivalGenerator extends ArrivalGenerator{


    private final double spikeDuration;
    private final double whenSpike;

    public double getWhenSpike() {
        return whenSpike;
    }

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

    public VariableArrivalGenerator(NextEventScheduler scheduler, double rate, String targetNode, int jobClass, Rngs rng) {
        super(scheduler, rate, targetNode, jobClass, rng);

        int spikeRngsIndex = Rngs.getStreamId();
        this.spikeDuration = RVMS.idfUniform(0, 3600, rng.random(spikeRngsIndex));
        this.whenSpike = RVMS.idfUniform(43200, 86400, rng.random(spikeRngsIndex));
        System.out.println("spike duration: " + spikeDuration);
        System.out.println("when spike: " + whenSpike);
    }

    /**
     * Event handler for arrival events. Schedules the next external arrival
     * when an external source (-1 jobId) arrival occurs for the target node.
     *
     * @param ev         The arrival event being processed.
     * @param scheduler  The simulation's {@link NextEventScheduler}.
     */
    @Override
    protected void onArrival(Event ev, NextEventScheduler scheduler) {
        if (!active || ev instanceof BootstrapEvent) return;
        double currentRate = rate;
        double sTime = scheduler.getCurrentTime();
        if (sTime >= whenSpike && sTime <= whenSpike + spikeDuration) {
            currentRate = 1.4;
        }
        if (ev.getJobId() == -1 && targetNode.equals(ev.getServer())) {
            double ia = RVMS.idfExponential(1.0 / currentRate, rng.random(this.streamId));
            Event nextExternal = new Event(scheduler.getCurrentTime() + ia , Event.Type.ARRIVAL, targetNode,
                    -1, jobClass);
            scheduler.scheduleAt(nextExternal, nextExternal.getTime());
        }
    }
}
