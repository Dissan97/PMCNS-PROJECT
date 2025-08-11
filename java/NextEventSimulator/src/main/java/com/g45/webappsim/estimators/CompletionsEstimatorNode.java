package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.util.Map;

public class CompletionsEstimatorNode extends CompletionsEstimator {
    private final String node;

    public CompletionsEstimatorNode(NextEventScheduler sched, String node, Map<String, Map<String, TargetClass>> routing) {
        super(sched, routing);
        this.node = node;
    }

    @Override
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) super.onDeparture(e, s);
    }
}
