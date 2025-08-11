package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

public class PopulationEstimator {
    protected int pop = 0;
    public final WelfordEstimator w = new WelfordEstimator();

    public PopulationEstimator(NextEventScheduler sched) {
        sched.subscribe(Event.Type.ARRIVAL, this::inc);
        sched.subscribe(Event.Type.DEPARTURE, this::dec);
    }

    protected void inc(Event e, NextEventScheduler s) {
        pop += 1;
        w.add(pop);
    }

    protected void dec(Event e, NextEventScheduler s) {
        pop -= 1;
        w.add(pop);
    }
}
