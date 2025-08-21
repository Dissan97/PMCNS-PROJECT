package com.gforyas.webappsim.estimators;

import com.gforyas.webappsim.simulator.Event;
import com.gforyas.webappsim.simulator.NextEventScheduler;
import com.gforyas.webappsim.simulator.TargetClass;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * <p><strong>Purpose.</strong> This estimator tracks the total <em>busy time</em> of the whole
 * system, defined as the time intervals during which the network population is non-empty
 * (i.e., at least one job is present anywhere in the network).</p>
 *
 * <p><strong>Modeling assumptions.</strong> Jobs can appear as anonymous placeholders (with
 * {@code id < 0}) on external arrivals and later get a known identifier (with {@code id >= 0})
 * on their first classified arrival. The estimator maintains a consistent system population
 * while avoiding double counts across internal hops.</p>
 *
 * <p><strong>Operational rules.</strong></p>
 * <ul>
 *   <li><strong>External ARRIVAL</strong> ({@code id < 0}):
 *     <ul>
 *       <li>Enters immediately; if population transitions from 0 to 1, the system becomes busy, and
 *       the opening timestamp is recorded.</li>
 *       <li>Increments both the total population and the counter of pending anonymous jobs
 *       ({@code pendingAnon}).</li>
 *     </ul>
 *   </li>
 *   <li><strong>First ARRIVAL with known id</strong> ({@code id >= 0}):
 *     <ul>
 *       <li>If {@code pendingAnon > 0}, consume one anonymous placeholder and register the job id
 *       without changing the population.</li>
 *       <li>Otherwise, treat it as a direct first entry with a known id: if population transitions
 *       from 0 to 1, open busy; then increment population.</li>
 *       <li>Later internal hops of the same id are ignored for population accounting.</li>
 *     </ul>
 *   </li>
 *   <li><strong>DEPARTURE</strong>:
 *     <ul>
 *       <li>Population decreases <em>only</em> when the routing indicates an <code>EXIT</code>
 *       for the job's class from the given server.</li>
 *       <li>When the population returns to 0, the busy interval is closed and added to the total.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><strong>Integration.</strong> The estimator subscribes to {@link Event.Type#ARRIVAL} and
 * {@link Event.Type#DEPARTURE} on the provided {@link NextEventScheduler} at construction time.
 * Use {@link #startCollecting(double)} to reset/initialize the accumulator, query the current
 * aggregate with {@link #getBusyTime()}, and call {@link #finalizeBusy(double)} at the end of the
 * simulation to close any open busy interval.</p>
 */
public class BusyTimeEstimator {

    // System population for busy logic
    protected int pop = 0;

    // Busy state and timestamps
    protected boolean busy = false;
    protected double last;
    protected double total = 0.0;

    // Routing to detect EXIT
    private final Map<String, Map<String, TargetClass>> routing;

    // Jobs with id>=0 currently in the system (to avoid double increments/decrements)
    private final Set<Integer> inSystem = new HashSet<>();

    // Number of jobs entered as anonymous (id<0) waiting for the first known id>=0
    private int pendingAnon = 0;

    /**
     * <p>Creates a new busy-time estimator and subscribes it to ARRIVAL and DEPARTURE events
     * on the given scheduler.</p>
     *
     * @param sched   the event scheduler providing the simulation clock and event bus
     * @param routing the routing map used to determine whether a departure leads to {@code EXIT}
     */
    public BusyTimeEstimator(NextEventScheduler sched,
                             Map<String, Map<String, TargetClass>> routing) {
        this.routing = routing;
        this.last = sched.getCurrentTime();
        sched.subscribe(Event.Type.ARRIVAL,   this::onArrival);
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    /**
     * <p>Handles system ARRIVAL events and updates population/busy state according to the rules:
     * anonymous external arrivals ({@code id < 0}) increase population immediately and open busy if needed;
     * the first known id ({@code id >= 0}) either matches a pending anonymous placeholder (no population change)
     * or increments population on a direct entry.</p>
     *
     * @param e the arrival event
     * @param s the scheduler providing the current simulation time
     */
    protected void onArrival(Event e, NextEventScheduler s) {
        int id = e.getJobId();

        if (id < 0) {
            // External ARRIVAL: enters NOW and registers an anonymous job
            if (pop == 0) {
                busy = true;
                last = s.getCurrentTime();
            }
            pop += 1;
            pendingAnon += 1;
            return;
        }

        // id >= 0: if already seen, internal hop → ignore
        if (inSystem.contains(id)) {
            return;
        }

        // First known id for this job
        if (pendingAnon > 0) {
            // Matches a previous anonymous job: DO NOT increment pop, just register the id
            pendingAnon -= 1;
            inSystem.add(id);
        } else {
            // Direct entry with known id
            inSystem.add(id);
            if (pop == 0) {
                busy = true;
                last = s.getCurrentTime();
            }
            pop += 1;
        }
    }

    /**
     * <p>Handles system DEPARTURE events. Population is decremented <em>only</em> when the
     * routing indicates an {@code EXIT} for the given server/class. When the population reaches 0,
     * the busy interval is closed and added to the total.</p>
     *
     * @param e the departure event
     * @param s the scheduler providing the current simulation time
     */
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (!routesToExit(e.getServer(), e.getJobClass())) {
            return; // Internal departures ignored
        }

        int id = e.getJobId();
        if (id >= 0) {
            inSystem.remove(id);
        }

        pop -= 1;
        if (pop == 0 && busy) {
            total += s.getCurrentTime() - last;
            busy = false;
        }
    }

    /**
     * <p>Resets the accumulator and initializes the reference time for the next measurement window.</p>
     *
     * @param now the timestamp (simulation time) to be used as the new reference
     */
    public void startCollecting(double now) {
        this.total = 0.0;
        this.last  = now;
    }

    /**
     * <p>Returns whether a departure from the given server and job class routes to {@code EXIT}.</p>
     *
     * @param server   the logical server name
     * @param jobClass the job class identifier
     * @return {@code true} if the route is an {@code EXIT}; {@code false} otherwise
     */
    private boolean routesToExit(String server, int jobClass) {
        Map<String, TargetClass> m = routing.get(server);
        if (m == null) return false;
        TargetClass tc = m.get(Integer.toString(jobClass));
        return tc != null && "EXIT".equalsIgnoreCase(tc.eventClass());
    }

    /**
     * <p>Gets the total busy time accumulated since the last call to
     * {@link #startCollecting(double)} (or since construction), excluding any still-open
     * interval that has not yet been finalized.</p>
     *
     * @return the accumulated busy time
     */
    public double getBusyTime() { return total; }

    /**
     * <p>Finalizes any open busy interval by closing it at {@code currentTime} and
     * updating the total. Safe to call at the end of the simulation.</p>
     *
     * @param currentTime the timestamp (simulation time) used to close the open interval
     */
    public void finalizeBusy(double currentTime) {
        if (pop > 0 && busy) {
            total += currentTime - last;
            busy = false;
        }
    }
}
