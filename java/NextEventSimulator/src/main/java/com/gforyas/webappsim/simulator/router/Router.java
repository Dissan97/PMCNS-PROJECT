package com.gforyas.webappsim.simulator.router;

import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.simulator.TargetClass;

/**
 * Strategia di routing: deterministica o probabilistica.
 * Ritorna un {@link TargetClass} compatibile con la pipeline attuale:
 * - se l'evento è EXIT: new TargetClass(serverCorrente, "EXIT")
 * - altrimenti: new TargetClass(target, classe)
 */
public interface Router {

    /**
     * Seleziona il prossimo hop dato (server corrente, classe corrente).
     * @param currentServer nome del nodo corrente (es. "A", "B", "P")
     * @param currentClass classe del job nel nodo corrente
     * @param rng generatore RNG condiviso della simulazione
     * @return TargetClass del prossimo hop, oppure null se non definito
     */
    TargetClass next(String currentServer, int currentClass, Rngs rng);

    /**
     * @return true se la policy è probabilistica, false se deterministica.
     */
    boolean isProbabilistic();
}
