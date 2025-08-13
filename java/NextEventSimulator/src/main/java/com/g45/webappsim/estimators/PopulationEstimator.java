package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Time-weighted estimator of TOTAL system population N(t), robust to internal routing.
 *
 * Rule:
 * - On ARRIVAL: if jobId >= 0 and we see that jobId for the FIRST time, N++.
 * - On DEPARTURE: if the routing from (server, jobClass) is EXIT, N-- for that jobId.
 *
 * We integrate:
 *   area  = ∫ N(t) dt
 *   area2 = ∫ [N(t)]^2 dt
 * updating BEFORE changing N.
 */
public class PopulationEstimator {

    // current total population in system
    private int pop = 0;

    // time integration
    private final double startTime;
    private double lastTime;
    private double area  = 0.0; // ∫ N(t) dt
    private double area2 = 0.0; // ∫ [N(t)]^2 dt

    // diagnostics
    private int min = 0;
    private int max = 0;

    // routing to detect EXIT on departures
    private final Map<String, Map<String, TargetClass>> routing;

    // set of jobs currently inside the system (by jobId)
    private final Set<Integer> inSystem = new HashSet<>();

    // handlers
    private final BiConsumer<Event, NextEventScheduler> onArrival   = this::tickThenMaybeEnter;
    private final BiConsumer<Event, NextEventScheduler> onDeparture = this::tickThenMaybeExit;

    public PopulationEstimator(NextEventScheduler sched,
                               Map<String, Map<String, TargetClass>> routing) {
        this.routing = routing;

        this.startTime = sched.getCurrentTime();
        this.lastTime  = this.startTime;

        // listen to arrivals and departures; always tick BEFORE changing N
        sched.subscribe(Event.Type.ARRIVAL,   onArrival);
        sched.subscribe(Event.Type.DEPARTURE, onDeparture);
    }

    /** Update area and area2 up to "now" without changing pop. */
    private void tick(NextEventScheduler s) {
        double now = s.getCurrentTime();
        double dt  = now - lastTime;
        if (dt > 0.0) {
            area  += pop * dt;
            area2 += (double) pop * (double) pop * dt;
        }
        lastTime = now;
    }

    /** On ARRIVAL: first time we see a real job (jobId >= 0) → enter system. */
    private void tickThenMaybeEnter(Event e, NextEventScheduler s) {
        tick(s);
        int id = e.getJobId();
        if (id >= 0 && inSystem.add(id)) {
            pop += 1;
            if (pop > max) max = pop;
        }
        // internal arrivals (same id seen before) do NOT change N
        // external trigger events use jobId == -1 → ignored here
    }

    /** On DEPARTURE: decrement only when routing to EXIT. */
    private void tickThenMaybeExit(Event e, NextEventScheduler s) {
        tick(s);
        if (routesToExit(e.getServer(), e.getJobClass())) {
            int id = e.getJobId();
            if (id >= 0 && inSystem.remove(id)) {
                pop -= 1;
                if (pop < min) min = pop;
            }
        }
        // internal departures do NOT change N
    }

    private boolean routesToExit(String server, int jobClass) {
        Map<String, TargetClass> m = routing.get(server);
        if (m == null) return false;
        TargetClass tc = m.get(Integer.toString(jobClass));
        return tc != null && "EXIT".equalsIgnoreCase(tc.eventClass());
    }

    /** Observed time span so far. */
    public double elapsed() {
        return lastTime - startTime;
    }

    /** Time-weighted mean E[N]. */
    public double getMean() {
        double T = elapsed();
        return (T > 0.0) ? (area / T) : 0.0;
    }

    /** Time-weighted variance Var[N] = E[N^2] - (E[N])^2. */
    public double getVariance() {
        double T = elapsed();
        if (T <= 0.0) return 0.0;
        double mean  = area  / T;
        double mean2 = area2 / T;
        double var = mean2 - mean * mean;
        return (var > 0.0) ? var : 0.0;
    }

    /** Time-weighted standard deviation. */
    public double getStd() {
        return Math.sqrt(getVariance());
    }

    public int getMin() { return min; }
    public int getMax() { return max; }

    /** Close the last interval at currentTime BEFORE reading mean/std. */
    public void finalizeAt(double currentTime) {
        double dt = currentTime - lastTime;
        if (dt > 0.0) {
            area  += pop * dt;
            area2 += (double) pop * (double) pop * dt;
        }
        lastTime = currentTime;
    }
}
