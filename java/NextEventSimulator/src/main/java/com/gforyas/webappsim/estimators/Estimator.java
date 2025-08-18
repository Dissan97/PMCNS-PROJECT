package com.gforyas.webappsim.estimators;

import com.gforyas.webappsim.simulator.Network;
import com.gforyas.webappsim.simulator.NextEventScheduler;

public interface Estimator {
    /**
     * Allow the calculation of the stats depending on implementation
     * @param scheduler the NextEventScheduler
     * @param network the server network
     * {@link NextEventScheduler}
     * {@link Network}
     */
    void calculateStats(NextEventScheduler scheduler, Network network);

}
