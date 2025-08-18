package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.util.HashMap;
import java.util.Map;

/**
 * Global end-to-end response time estimator (first external ARRIVAL -> EXIT).
 * Uses Welford for mean/variance online.
 */
public class ResponseTimeEstimator {

    // start time per jobId (global scope)
    protected final Map<Integer, Double> jobMap = new HashMap<>();
    protected WelfordEstimator welfordEstimator = new WelfordEstimator();

    // routing to detect EXIT on departures
    private final Map<String, Map<String, TargetClass>> routing;

    private boolean collecting = false;

    public ResponseTimeEstimator(NextEventScheduler sched,
                                 Map<String, Map<String, TargetClass>> routing) {
        this.routing = routing;
        sched.subscribe(Event.Type.ARRIVAL,   this::onArrival);
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    /** Record FIRST time we see this jobId (external entry). */
    protected void onArrival(Event e, NextEventScheduler s) {
        if (!collecting) return; 
        int id = e.getJobId();
        if (id >= 0) {
            jobMap.putIfAbsent(id, e.getTime());
        }
    }

    /** Only on EXIT: close the end-to-end response time. */
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (!collecting) return;  
        if (!routesToExit(e.getServer(), e.getJobClass())) return;
        int id = e.getJobId();
        Double at = jobMap.remove(id);
        if (at != null) {
            welfordEstimator.add(e.getTime() - at);
        }
    }

    protected boolean routesToExit(String server, int jobClass) {
        Map<String, TargetClass> m = routing.get(server);
        if (m == null) return false;
        TargetClass tc = m.get(Integer.toString(jobClass));
        return tc != null && "EXIT".equalsIgnoreCase(tc.eventClass());
    }

    public void startCollecting() {
        collecting = true;
        this.welfordEstimator.reset();
        this.jobMap.clear();
    }
}
