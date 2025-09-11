package com.gforyas.webappsim.estimators;

import com.gforyas.webappsim.simulator.Event;
import com.gforyas.webappsim.simulator.NextEventScheduler;
import com.gforyas.webappsim.simulator.TargetClass;

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

    /** Record FIRST time we see this jobId (global first known ARRIVAL id>=0). */
    protected void onArrival(Event e, NextEventScheduler s) {
        if (!collecting) return;
        int id = e.getJobId();
        if (id >= 0) {
            jobMap.putIfAbsent(id, s.getCurrentTime());
        }
    }

    /** Only on EXIT: close the end-to-end response time. */
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (!collecting) return;
        if (!routesToExit(e.getServer(), e.getJobClass())) return;
        int id = e.getJobId();
        Double at = jobMap.remove(id);
        if (at != null) {
            welfordEstimator.add(s.getCurrentTime() - at);
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
        //this.jobMap.clear();
    }

    public WelfordEstimator getWelfordEstimator() {
        return welfordEstimator;
    }

    // ===== BRIDGE API per probabilistico =====

    /**
     * <p>Notifica esplicita dell'istante di "prima vista" del job (ARRIVAL con id>=0).
     * In probabilistico <b>non è obbligatorio</b> chiamarlo se l'estimator riceve comunque
     * gli ARRIVAL normali; è fornito per simmetria.</p>
     */
    public void notifyFirstKnownArrival(int jobId, double now) {
        if (!collecting) return;
        if (jobId >= 0) {
            jobMap.putIfAbsent(jobId, now);
        }
    }

    /**
     * <p>Notifica esplicita di un EXIT (senza passare dalla routing matrix).</p>
     * Riuso <b>identico</b> della logica onDeparture su EXIT: chiude il campione e aggiorna Welford.
     */
    public void notifyExit(int jobId, double now) {
        if (!collecting) return;
        Double at = jobMap.remove(jobId);
        if (at != null) {
            welfordEstimator.add(now - at);
        }
    }
}
