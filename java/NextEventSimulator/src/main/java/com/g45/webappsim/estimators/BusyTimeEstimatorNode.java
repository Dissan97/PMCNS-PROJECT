package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

public class BusyTimeEstimatorNode extends BusyTimeEstimator {

    private final String node;

    public BusyTimeEstimatorNode(NextEventScheduler sched, String node) {
        super(sched);
        this.node = node;
    }

    @Override
    protected void onArrival(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) super.onArrival(e, s);
    }

    @Override
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) super.onDeparture(e, s);
    }
}
