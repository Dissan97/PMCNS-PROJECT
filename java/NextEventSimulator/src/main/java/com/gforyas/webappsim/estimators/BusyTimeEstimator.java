package com.gforyas.webappsim.estimators;

import com.gforyas.webappsim.simulator.Event;
import com.gforyas.webappsim.simulator.NextEventScheduler;

import java.util.HashMap;
import java.util.Map;

/** Busy di SISTEMA = unione dei busy dei nodi (OR logico). */
public class BusyTimeEstimator {

    // pop per nodo; busyNodes = quanti nodi hanno pop>0
    private final Map<String, Integer> popByNode = new HashMap<>();
    private int busyNodes = 0;

    // stato busy globale
    protected boolean busy = false;
    protected double last = 0.0;
    protected double total = 0.0;

    public BusyTimeEstimator(NextEventScheduler sched,
                             Map<String, Map<String, com.gforyas.webappsim.simulator.TargetClass>> ignored) {
        // routing non serve più
        this.last = sched.getCurrentTime();
        sched.subscribe(Event.Type.ARRIVAL,   this::onArrival);
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    private void onArrival(Event e, NextEventScheduler s) {
        String node = e.getServer();
        if (node == null) return;
        int k = popByNode.getOrDefault(node, 0);
        if (k == 0) {               // nodo passa 0→1
            if (busyNodes == 0) {   // sistema passa idle→busy
                busy = true;
                last = s.getCurrentTime();
            }
            busyNodes++;
        }
        popByNode.put(node, k + 1);
    }

    private void onDeparture(Event e, NextEventScheduler s) {
        String node = e.getServer();
        if (node == null) return;
        int k = popByNode.getOrDefault(node, 0);
        if (k <= 0) return;         // guard
        k -= 1;
        popByNode.put(node, k);
        if (k == 0) {               // nodo passa 1→0
            busyNodes--;
            if (busyNodes == 0 && busy) { // sistema passa busy→idle
                total += s.getCurrentTime() - last;
                busy = false;
            }
        }
    }

    /** (ri)avvia la finestra di misura; riapri se il sistema è già occupato */
    public void startCollecting(double now) {
        this.total = 0.0;
        this.last  = now;
        if (busyNodes > 0) {
            busy = true;
            last = now;
        } else {
            busy = false;
        }
    }

    /** chiudi eventuale intervallo aperto a 'currentTime' */
    public void finalizeBusy(double currentTime) {
        if (busyNodes > 0 && busy) {
            total += currentTime - last;
            busy = false;
        }
    }

    public double getBusyTime() { return total; }
}
