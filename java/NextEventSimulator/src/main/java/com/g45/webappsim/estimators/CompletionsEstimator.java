package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.util.Map;

public class CompletionsEstimator {

    public int count = 0;
    private final Map<String, Map<String, TargetClass>> routing;

    public CompletionsEstimator(NextEventScheduler sched, Map<String, Map<String, TargetClass>> routing) {
        this.routing = routing;
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    protected void onDeparture(Event e, NextEventScheduler s) {
        if (isExit(e.getServer(), e.getJobClass())) {
            count += 1;
        }
    }

    protected boolean isExit(String server, int jobClass) {
        Map<String, TargetClass> m = routing.get(server);
        if (m == null) return false;
        TargetClass tc = m.get(Integer.toString(jobClass));
        return tc != null && "EXIT".equalsIgnoreCase(tc.eventClass());
    }
}
