package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.util.Map;

/**
 * Tracks the number of jobs that have completed the system.
 * <p>
 * A completion is counted when a {@link Event.Type#DEPARTURE} event
 * corresponds to a routing rule whose next target is {@code EXIT}.
 * </p>
 */
public class CompletionsEstimator {

    /** The total number of completed jobs. */
    public int count = 0;

    /** Routing matrix used to determine whether a departure is an exit. */
    private final Map<String, Map<String, TargetClass>> routing;

    /**
     * Creates a new completion estimator that listens for departure events.
     *
     * @param sched   the event scheduler
     * @param routing the routing matrix (node -> job class -> target class)
     */
    public CompletionsEstimator(NextEventScheduler sched, Map<String, Map<String, TargetClass>> routing) {
        this.routing = routing;
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    /**
     * Handles departure events and increments the completion count if the
     * job is leaving the system.
     *
     * @param e the departure event
     * @param s the event scheduler
     */
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (isExit(e.getServer(), e.getJobClass())) {
            count += 1;
        }
    }

    /**
     * Determines whether a given node/class pair routes to {@code EXIT}.
     *
     * @param server   the node name
     * @param jobClass the job class
     * @return {@code true} if the job exits the system, {@code false} otherwise
     */
    protected boolean isExit(String server, int jobClass) {
        Map<String, TargetClass> m = routing.get(server);
        if (m == null) return false;
        TargetClass tc = m.get(Integer.toString(jobClass));
        return tc != null && "EXIT".equalsIgnoreCase(tc.eventClass());
    }
}
