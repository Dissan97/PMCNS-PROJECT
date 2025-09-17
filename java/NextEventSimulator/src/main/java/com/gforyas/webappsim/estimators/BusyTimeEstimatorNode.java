package com.gforyas.webappsim.estimators;

import com.gforyas.webappsim.simulator.Event;
import com.gforyas.webappsim.simulator.NextEventScheduler;

/**
 * Busy-time estimator per un singolo nodo (indipendente dal globale).
 * Il nodo è busy quando pop>0. Integra gli intervalli di busy nel tempo.
 */
public class BusyTimeEstimatorNode {

    private final String node;

    /** popolazione locale del nodo (conteggio di job presenti sul nodo) */
    private int pop = 0;

    /** stato busy + timestamp ultimo cambio di stato */
    private boolean busy = false;
    private double last = 0.0;

    /** busy time accumulato nella finestra di misura corrente */
    private double total = 0.0;

    public BusyTimeEstimatorNode(NextEventScheduler sched, String node) {
        this.node = node;
        this.last = sched.getCurrentTime();

        // Sottoscrizione eventi del SIM
        sched.subscribe(Event.Type.ARRIVAL,   this::onArrival);
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    private void onArrival(Event e, NextEventScheduler s) {
        if (!node.equals(e.getServer())) return;
        if (pop == 0) {         // 0 -> 1 : apre intervallo busy
            busy = true;
            last = s.getCurrentTime();
        }
        pop += 1;
    }

    private void onDeparture(Event e, NextEventScheduler s) {
        if (!node.equals(e.getServer())) return;
        if (pop <= 0) return;   // guard
        pop -= 1;
        if (pop == 0 && busy) { // 1 -> 0 : chiude intervallo busy
            total += s.getCurrentTime() - last;
            busy = false;
        }
    }

    /** (ri)avvia la finestra di misura; se il nodo è già occupato riapre subito l'intervallo */
    public void startCollecting(double now) {
        this.total = 0.0;
        this.last  = now;
        if (pop > 0) {
            busy = true;
            last = now;
        } else {
            busy = false;
        }
    }

    /** finalizza eventuale intervallo aperto al tempo corrente */
    public void finalizeBusy(double currentTime) {
        if (pop > 0 && busy) {
            total += currentTime - last;
            busy = false;
        }
    }

    public double getBusyTime() { return total; }
}
