package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

public class ObservationTimeEstimator {

    private final double start;
    private double end;

    public ObservationTimeEstimator(NextEventScheduler sched) {
        this.start = sched.getCurrentTime();
        this.end = this.start;
        sched.subscribe(Event.Type.ARRIVAL, this::upd);
        sched.subscribe(Event.Type.DEPARTURE, this::upd);
    }

    private void upd(Event e, NextEventScheduler s) {
        this.end = s.getCurrentTime();
    }

    public double elapsed() {
        return end - start;
    }
}
