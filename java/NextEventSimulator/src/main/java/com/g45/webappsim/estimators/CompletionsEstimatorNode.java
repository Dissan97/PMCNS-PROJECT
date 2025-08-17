package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.util.Map;

/**
 * Conta le DEPARTURE del SINGOLO nodo (tutte, indipendentemente dalla destinazione),
 * con supporto al warm-up tramite baseline.
 *
 * Nota: anche se il nome storico dice "Completions", qui usiamo il contatore
 * come "departures per nodo" per stimare il throughput del nodo.
 * Il throughput globale resta basato su CompletionsEstimator (EXIT).
 */
public class CompletionsEstimatorNode {

    private final String node;
    @SuppressWarnings("unused")
    private final Map<String, Map<String, TargetClass>> routing; // tenuto per compatibilità, non usato

    private int totalCount = 0; // tutte le departure del nodo dall'inizio
    private int baseCount  = 0; // baseline per finestra post-warmup

    public CompletionsEstimatorNode(NextEventScheduler sched,
                                    String node,
                                    Map<String, Map<String, TargetClass>> routing) {
        this.node = node;
        this.routing = routing;
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    /** Chiamare all'inizio della finestra di misura (post warm-up). */
    public void startCollecting() {
        baseCount = totalCount;
    }

    /** Totale assoluto (dall'inizio simulazione). */
    public int getTotalCount() {
        return totalCount;
    }

    /** Conteggio nella finestra post-warmup. */
    public int getCountSinceStart() {
        return totalCount - baseCount;
    }

    /** Conta TUTTE le departure che avvengono su questo nodo. */
    private void onDeparture(Event e, NextEventScheduler s) {
        if (node.equals(e.getServer())) {
            totalCount += 1;
        }
    }
}
