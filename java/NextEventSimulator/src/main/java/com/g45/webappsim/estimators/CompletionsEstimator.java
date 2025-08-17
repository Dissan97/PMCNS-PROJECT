package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.util.Map;

/**
 * Conta i completamenti di sistema (DEPARTURE che porta a EXIT),
 * con supporto al warm-up coordinato.
 */
public class CompletionsEstimator {

    /** Conteggio totale da inizio simulazione. */
    private int totalCount = 0;

    /** Baseline per conteggi "post warm-up". */
    private int baseCount = 0;

    private final Map<String, Map<String, TargetClass>> routing;

    public CompletionsEstimator(NextEventScheduler sched,
                                Map<String, Map<String, TargetClass>> routing) {
        this.routing = routing;
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    /** Chiamare al termine del warm-up: da qui in poi si misura. */
    public void startCollecting() {
        baseCount = totalCount;
    }

    /** Completamenti da inizio simulazione. */
    public int getTotalCount() {
        return totalCount;
    }

    /** Completamenti "post warm-up". */
    public int getCountSinceStart() {
        return totalCount - baseCount;
    }

    protected void onDeparture(Event e, NextEventScheduler s) {
        if (isExit(e.getServer(), e.getJobClass())) {
            totalCount += 1;
        }
    }

    protected boolean isExit(String server, int jobClass) {
        Map<String, TargetClass> m = routing.get(server);
        if (m == null) return false;
        TargetClass tc = m.get(Integer.toString(jobClass));
        return tc != null && "EXIT".equalsIgnoreCase(tc.eventClass());
    }
}
