package com.g45.webappsim.simulator;

import com.g45.webappsim.lemer.Rngs;
import com.g45.webappsim.lemer.Rvms;

public class ArrivalGenerator {
    private final double rate;          // external Poisson rate λ
    private final String targetNode;    // usually "A"
    private final int jobClass;         // usually 1
    private final Rngs rng;
    private static final Rvms RVMS = Rvms.getInstance();
    private volatile boolean active = true;

    public ArrivalGenerator(NextEventScheduler scheduler, double rate,
                            String targetNode, int jobClass, Rngs rng) {
        if (rng == null) throw new IllegalArgumentException("Lemer RNG must be provided");
        this.rate = rate;
        this.targetNode = targetNode;
        this.jobClass = jobClass;
        this.rng = rng;

        scheduler.subscribe(Event.Type.ARRIVAL, this::onArrival);

        Event first = new Event(0.0, Event.Type.ARRIVAL, targetNode, -1, jobClass);
        scheduler.scheduleAt(first, 0.0);
    }

    private void onArrival(Event ev, NextEventScheduler scheduler) {
        if (!active) return;
        if (ev.getJobId() == -1 && targetNode.equals(ev.getServer())) {
            double ia = RVMS.idfExponential(1.0 / rate, rng.random());
            Event nextExternal = new Event(0.0, Event.Type.ARRIVAL, targetNode, -1, jobClass);
            scheduler.scheduleAt(nextExternal, scheduler.getCurrentTime() + ia);
        }
    }

    public void setActive(boolean v) {
        this.active = v;
    }

    public boolean isActive() {
        return active;
    }
}
