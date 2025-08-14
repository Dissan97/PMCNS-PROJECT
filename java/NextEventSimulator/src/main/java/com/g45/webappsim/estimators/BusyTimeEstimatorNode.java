package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

/**
 * Tempo busy del SINGOLO nodo: il nodo è busy quando ha almeno un job nel nodo.
 * Non usa EXIT: incrementa/decrementa su eventi del nodo.
 */
public class BusyTimeEstimatorNode extends BusyTimeEstimator {

    private final String node;

    public BusyTimeEstimatorNode(NextEventScheduler sched, String node) {
        // il super richiede routing, ma per il nodo non serve: passiamo null
        super(sched, null);
        this.node = node;
    }

    @Override
    protected void onArrival(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) {
            if (pop == 0) {
                busy = true;
                last = s.getCurrentTime();
            }
            pop += 1;
        }
    }

    @Override
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) {
            pop -= 1;
            if (pop == 0 && busy) {
                total += s.getCurrentTime() - last;
                busy = false;
            }
        }
    }
}
