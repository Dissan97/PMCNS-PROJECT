package com.gforyas.webappsim.estimators;

import com.gforyas.webappsim.simulator.Network;
import com.gforyas.webappsim.simulator.NextEventScheduler;
import com.gforyas.webappsim.simulator.SimulationConfig;
import com.gforyas.webappsim.simulator.TargetClass;

import java.util.Map;

public enum StatsType {
    NORMAL,
    CONVERGENCE,
    BATCH,
    FINITE;


        public static StatsCollector build(Network network, NextEventScheduler scheduler, Map<String, Map<String, TargetClass>> routingMatrix, SimulationConfig cfg) {
            switch (cfg.getStatsType()) {
                case NORMAL -> {
                    return new StatsCollector(network, scheduler, routingMatrix, cfg, cfg.getArrivalRate());
                }
                case BATCH -> {
                    return new BatchMeans(network, scheduler, routingMatrix, cfg);
                }
                default -> throw new IllegalArgumentException("Stats type not recognized");
            }

    }
}
