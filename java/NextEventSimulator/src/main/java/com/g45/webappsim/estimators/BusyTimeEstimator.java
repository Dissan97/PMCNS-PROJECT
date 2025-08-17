package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tempo "busy" del SISTEMA: rete non vuota.
 *
 * Regole:
 *  - ARRIVAL esterno (id<0): entra SUBITO → se passiamo da 0 a 1, inizia busy; incrementa "pendingAnon".
 *  - Prima ARRIVAL con id>=0:
 *      * se "pendingAnon" > 0 → consuma uno e registra l'id (nessun incremento pop);
 *      * altrimenti → primo ingresso diretto con id noto (se passiamo da 0 a 1, apri busy; incrementa pop).
 *  - DEPARTURE: scala SOLO su EXIT; se pop torna a 0, chiudi busy.
 */
public class BusyTimeEstimator {

    // popolazione di sistema per la logica busy
    protected int pop = 0;

    // stato busy e tempi
    protected boolean busy = false;
    protected double last;
    protected double total = 0.0;

    // routing per riconoscere EXIT
    private final Map<String, Map<String, TargetClass>> routing;

    // job con id>=0 attualmente nel sistema (per evitare doppi incrementi/decrementi)
    private final Set<Integer> inSystem = new HashSet<>();

    // numero di job entrati come anonimi (id<0) in attesa del primo id>=0
    private int pendingAnon = 0;

    public BusyTimeEstimator(NextEventScheduler sched,
                             Map<String, Map<String, TargetClass>> routing) {
        this.routing = routing;
        this.last = sched.getCurrentTime();
        sched.subscribe(Event.Type.ARRIVAL,   this::onArrival);
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    /** ARRIVAL di sistema: apre busy se passiamo da 0 a 1. */
    protected void onArrival(Event e, NextEventScheduler s) {
        int id = e.getJobId();

        if (id < 0) {
            // ARRIVAL esterno: entra ORA e registra un anonimo
            if (pop == 0) {
                busy = true;
                last = s.getCurrentTime();
            }
            pop += 1;
            pendingAnon += 1;
            return;
        }

        // id >= 0: se già visto, hop interno → ignora
        if (inSystem.contains(id)) {
            return;
        }

        // primo id>=0 per questo job
        if (pendingAnon > 0) {
            // matcha un anonimo precedente: NON incrementare pop, ma registra l'id
            pendingAnon -= 1;
            inSystem.add(id);
        } else {
            // ingresso diretto con id noto
            inSystem.add(id);
            if (pop == 0) {
                busy = true;
                last = s.getCurrentTime();
            }
            pop += 1;
        }
    }

    /** DEPARTURE di sistema: scala solo su EXIT; chiude busy quando pop torna a 0. */
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (!routesToExit(e.getServer(), e.getJobClass())) {
            return; // partenze interne ignorate
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

     public void startCollecting(double now) {
        this.total = 0.0;
        this.last  = now;
    }

    private boolean routesToExit(String server, int jobClass) {
        Map<String, TargetClass> m = routing.get(server);
        if (m == null) return false;
        TargetClass tc = m.get(Integer.toString(jobClass));
        return tc != null && "EXIT".equalsIgnoreCase(tc.eventClass());
    }

    /** Tempo totale in cui il sistema è stato non vuoto. */
    public double getBusyTime() { return total; }

    /** Finalizza l'eventuale intervallo busy aperto. */
    public void finalizeBusy(double currentTime) {
        if (pop > 0 && busy) {
            total += currentTime - last;
            busy = false;
        }
    }
}
