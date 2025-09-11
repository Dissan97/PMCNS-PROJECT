package com.gforyas.webappsim.estimators;

import com.gforyas.webappsim.simulator.Event;
import com.gforyas.webappsim.simulator.NextEventScheduler;
import com.gforyas.webappsim.simulator.TargetClass;

import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Weighted-time estimator of the TOTAL system population N(t),
 * aligned with the end-to-end response-time window:
 * we start counting a job at its FIRST known ARRIVAL (id >= 0) and
 * stop counting it at EXIT. Internal hops do not change N(t).
 *
 * Integration occurs BEFORE modifying N(t): area = ∫N(t)dt, area2 = ∫N(t)^2 dt.
 */
public class PopulationEstimator {

    /** Current system population (jobs with id>=0 currently “in measurement”). */
    protected int pop = 0;

    /** Start time of the measurement window. */
    protected double startTime;
    /** Last update timestamp. */
    protected double lastTime;
    /** Integrated area ∫N(t)dt. */
    protected double area = 0.0;
    /** Integrated squared area ∫N(t)^2 dt. */
    protected double area2 = 0.0;

    /** Minimum/maximum population observed. */
    protected int min = 0, max = 0;

    /** Routing matrix used to recognize EXIT transitions. */
    private final Map<String, Map<String, TargetClass>> routing;

    /** Set of job ids (id >= 0) currently in the system. */
    private final Set<Integer> inSystem = new HashSet<>();

    public PopulationEstimator(NextEventScheduler sched,
                               Map<String, Map<String, TargetClass>> routing) {
        this.routing = routing;
        this.startTime = sched.getCurrentTime();
        this.lastTime  = this.startTime;

        // Integrate BEFORE state changes
        sched.subscribe(Event.Type.ARRIVAL,   this::tickThenMaybeEnter);
        sched.subscribe(Event.Type.DEPARTURE, this::tickThenMaybeExit);
    }

    public PopulationEstimator() {
        this.routing = null;
    }

    /** Integrate area and area2 up to current sim time. */
    protected void tick(NextEventScheduler s) {
        double now = s.getCurrentTime();
        double dt  = now - lastTime;
        if (dt > 0.0) {
            area  += pop * dt;
            area2 += (double) pop * (double) pop * dt;
        }
        lastTime = now;
    }

    /** Bridge API: integrate up to arbitrary time (without scheduler). */
    private void tickTo(double now) {
        double dt = now - lastTime;
        if (dt > 0.0) {
            area  += pop * dt;
            area2 += (double) pop * (double) pop * dt;
        }
        lastTime = now;
    }

    /**
     * ARRIVAL:
     * - Ignore anonymous arrivals (id < 0).
     * - On FIRST known id (id >= 0 not seen before) => increment N and record id.
     * - Internal hops (same id seen again) => no change.
     */
    private void tickThenMaybeEnter(Event e, NextEventScheduler s) {
        tick(s);
        int id = e.getJobId();

        if (id < 0) {
            // Align with response time: we DO NOT start counting at anonymous arrival.
            return;
        }
        // First known entrance of this id?
        if (inSystem.add(id)) {
            pop += 1;
            if (pop > max) max = pop;
        }
    }

    /**
     * DEPARTURE:
     * - Decrement only if (server, class) routes to EXIT AND the id is currently in the system.
     * - Remove id from inSystem on EXIT.
     */
    private void tickThenMaybeExit(Event e, NextEventScheduler s) {
        tick(s);
        if (routesToExit(e.getServer(), e.getJobClass())) {
            int id = e.getJobId();
            if (id >= 0 && inSystem.remove(id)) {
                pop -= 1;
                if (pop < min) min = pop;
            }
        }
    }

    private boolean routesToExit(String server, int jobClass) {
        if (routing == null) return false;
        Map<String, TargetClass> m = routing.get(server);
        if (m == null) return false;
        TargetClass tc = m.get(Integer.toString(jobClass));
        return tc != null && "EXIT".equalsIgnoreCase(tc.eventClass());
    }

    /** Explicit EXIT notification (bridge API): safe decrement only if we were counting this id. */
    public void notifyExit(int jobId, double now) {
        tickTo(now);
        if (jobId >= 0 && inSystem.remove(jobId)) {
            pop -= 1;
            if (pop < min) min = pop;
        }
    }

    // ---------- Public getters (unchanged) ----------

    public double elapsed() { return lastTime - startTime; }

    public double getMean() {
        double time = elapsed();
        return (time > 0.0) ? (area / time) : 0.0;
    }

    public double getVariance() {
        double time = elapsed();
        if (time <= 0.0) return 0.0;
        double m1 = area / time;
        double m2 = area2 / time;
        double v  = m2 - m1 * m1;
        return Math.max(v, 0.0);
    }

    public double getStd() { return Math.sqrt(getVariance()); }

    public int getMin() { return min; }
    public int getMax() { return max; }

    public void finalizeAt(double currentTime) {
        double dt = currentTime - lastTime;
        if (dt > 0.0) {
            area  += pop * dt;
            area2 += (double) pop * (double) pop * dt;
        }
        lastTime = currentTime;
    }

    /** Restart measurement window; do NOT reset pop nor inSystem. */
    public void startCollecting(double now) {
        this.area = 0.0;
        this.area2 = 0.0;
        this.startTime = now;
        this.lastTime  = now;
        this.min = this.pop;
        this.max = this.pop;
    }
}
