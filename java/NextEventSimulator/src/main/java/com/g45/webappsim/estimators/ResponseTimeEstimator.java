package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import java.util.HashMap;
import java.util.Map;

/**
 * Stimatore globale dei tempi di risposta end-to-end (job → job).
 * Usa Welford per media e varianza online.
 */
public class ResponseTimeEstimator {

    protected final Map<Integer, Double> arr = new HashMap<>();
    public final WelfordEstimator w = new WelfordEstimator();

    public ResponseTimeEstimator(NextEventScheduler sched) {
        sched.subscribe(Event.Type.ARRIVAL,   this::onArrival);
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    protected void onArrival(Event e, NextEventScheduler s) {
        if (e.getJobId() != -1) {
            arr.put(e.getJobId(), e.getTime());
        }
    }

    protected void onDeparture(Event e, NextEventScheduler s) {
        Double at = arr.remove(e.getJobId());
        if (at != null) {
            w.add(e.getTime() - at);
        }
    }
}
