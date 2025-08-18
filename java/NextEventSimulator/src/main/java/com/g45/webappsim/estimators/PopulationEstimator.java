package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Weighted-time estimator of the TOTAL system population N(t), robust to
 * internal routing.
 *
 * <p><b>Rules:</b></p>
 * <ul>
 *   <li><b>ARRIVAL with id &lt; 0 (external):</b> enters immediately, increments the population,
 *   and increments {@code pendingAnon}.</li>
 *   <li><b>First ARRIVAL with id &gt;= 0:</b>
 *     <ul>
 *       <li>If {@code pendingAnon} &gt; 0 → consumes one pending anonymous job, adds the id
 *       to the set, but does not increment the population.</li>
 *       <li>Otherwise (no pending anonymous jobs) → first entrance with known id → increments
 *       the population and records the id.</li>
 *     </ul>
 *   </li>
 *   <li><b>DEPARTURE:</b> decreases the population only if (server, jobClass) leads to EXIT;
 *   if id &gt;= 0, also removes it from the set.</li>
 * </ul>
 *
 * <p>Integration occurs <b>before</b> modifying N(t): area = ∫N(t)dt,
 * area2 = ∫N(t)<sup>2</sup> dt.</p>
 */
public class PopulationEstimator {

    /**
     * Current system population.
     */
    protected int pop = 0;

    /** Start time of the measurement window. */
    protected double startTime;
    /** Last update timestamp. */
    protected double lastTime;
    /** Integrated area ∫N(t)dt. */
    protected double area = 0.0;
    /** Integrated squared area ∫N(t)<sup>2</sup>dt. */
    protected double area2 = 0.0;

    /** Minimum population observed. */
    protected int min = 0;
    /** Maximum population observed. */
    protected int max = 0;

    /** Routing matrix used to recognize EXIT transitions. */
    private Map<String, Map<String, TargetClass>> routing;

    /** Set of job ids (id &gt;= 0) currently in the system. */
    private final Set<Integer> inSystem = new HashSet<>();

    /**
     * Number of anonymous jobs (id &lt; 0) that entered but have not yet been
     * matched to their first known id (id &gt;= 0).
     */
    private int pendingAnon = 0;

    public PopulationEstimator(NextEventScheduler sched,
                               Map<String, Map<String, TargetClass>> routing) {
        this.routing = routing;
        this.startTime = sched.getCurrentTime();
        this.lastTime = this.startTime;

        // Subscribe to events; always integrate BEFORE modifying N
        sched.subscribe(Event.Type.ARRIVAL, this::tickThenMaybeEnter);
        sched.subscribe(Event.Type.DEPARTURE, this::tickThenMaybeExit);
    }

    public PopulationEstimator() {
    }

    /**
     * Integrates {@code area} and {@code area2} up to the current time
     * without modifying the population.
     */
    protected void tick(NextEventScheduler s) {
        double now = s.getCurrentTime();
        double dt = now - lastTime;
        if (dt > 0.0) {
            area += pop * dt;
            area2 += (double) pop * (double) pop * dt;
        }
        lastTime = now;
    }

    /**
     * Handles an ARRIVAL event with matching between anonymous jobs (id &lt; 0)
     * and the first known id (id &gt;= 0).
     */
    private void tickThenMaybeEnter(Event e, NextEventScheduler s) {
        tick(s);
        int id = e.getJobId();

        if (id < 0) {
            // External ARRIVAL: enters now and registers one pending anonymous job
            pop += 1;
            pendingAnon += 1;
            if (pop > max)
                max = pop;
            return;
        }

        // id >= 0: if already seen, this is an internal hop → ignore
        if (inSystem.contains(id)) {
            return;
        }

        // First entrance with known id
        if (pendingAnon > 0) {
            // Matches one previously anonymous job → do not increment population, just mark id
            pendingAnon -= 1;
            inSystem.add(id);
        } else {
            // No pending anonymous job: direct entrance with known id
            inSystem.add(id);
            pop += 1;
            if (pop > max)
                max = pop;
        }
    }

    /**
     * Handles a DEPARTURE event: decreases population only if the
     * (server, jobClass) leads to EXIT. Removes id from the set if present.
     */
    private void tickThenMaybeExit(Event e, NextEventScheduler s) {
        tick(s);
        if (routesToExit(e.getServer(), e.getJobClass())) {
            int id = e.getJobId();
            if (id >= 0)
                inSystem.remove(id);
            pop -= 1;
            if (pop < min)
                min = pop;
        }
    }

    /**
     * Recognizes if a (server, jobClass) pair routes to EXIT.
     *
     * @param server server identifier
     * @param jobClass job class id
     * @return {@code true} if this transition leads to EXIT, {@code false} otherwise
     */
    private boolean routesToExit(String server, int jobClass) {
        Map<String, TargetClass> m = routing.get(server);
        if (m == null)
            return false;
        TargetClass tc = m.get(Integer.toString(jobClass));
        return tc != null && "EXIT".equalsIgnoreCase(tc.eventClass());
    }

    /**
     * Returns the elapsed observation time.
     *
     * @return elapsed time
     */
    public double elapsed() {
        return lastTime - startTime;
    }

    /**
     * Returns the weighted-time mean population E[N].
     *
     * @return mean population
     */
    public double getMean() {
        double time = elapsed();
        return (time > 0.0) ? (area / time) : 0.0;
    }

    /**
     * Returns the weighted-time variance Var[N] = E[N²] − (E[N])².
     *
     * @return variance of population
     */
    public double getVariance() {
        double time = elapsed();
        if (time <= 0.0)
            return 0.0;
        double m1 = area / time;
        double m2 = area2 / time;
        double v = m2 - m1 * m1;
        return Math.max(v, 0.0);
    }

    /**
     * Returns the weighted-time standard deviation.
     *
     * @return standard deviation of population
     */
    public double getStd() {
        return Math.sqrt(getVariance());
    }

    /**
     * Returns the minimum observed population.
     *
     * @return minimum population
     */
    public int getMin() {
        return min;
    }

    /**
     * Returns the maximum observed population.
     *
     * @return maximum population
     */
    public int getMax() {
        return max;
    }

    /**
     * Finalizes the last interval before reading mean or standard deviation.
     *
     * @param currentTime current simulation time
     */
    public void finalizeAt(double currentTime) {
        double dt = currentTime - lastTime;
        if (dt > 0.0) {
            area += pop * dt;
            area2 += (double) pop * (double) pop * dt;
        }
        lastTime = currentTime;
    }

    /**
     * Starts a new measurement window from {@code now}. This does not reset
     * the current population, {@code inSystem}, or {@code pendingAnon}.
     *
     * @param now current simulation time
     */
    public void startCollecting(double now) {
        this.area = 0.0;
        this.area2 = 0.0;
        this.startTime = now;
        this.lastTime = now;

        // Optionally reset min and max based on current population
        this.min = this.pop;
        this.max = this.pop;
    }

}
