package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

public class ResponseTimeEstimatorNode extends ResponseTimeEstimator {
    private final String node;

    public ResponseTimeEstimatorNode(NextEventScheduler sched, String node) {
        super(sched);
        this.node = node;
    }

    @Override
    protected void onArrival(Event e, NextEventScheduler s) {
        if (e.getJobId() != -1 && node.equals(e.getServer())) {
            arr.put(e.getJobId(), e.getTime());
        }
    }

    @Override
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) super.onDeparture(e, s);
    }
}
