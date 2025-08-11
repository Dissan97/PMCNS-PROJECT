package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

public class PopulationEstimatorNode extends PopulationEstimator {
    private final String node;

    public PopulationEstimatorNode(NextEventScheduler sched, String node) {
        super(sched);
        this.node = node;
    }

    @Override
    protected void inc(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) super.inc(e, s);
    }

    @Override
    protected void dec(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) super.dec(e, s);
    }
}
