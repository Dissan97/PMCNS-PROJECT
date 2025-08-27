package com.gforyas.webappsim.simulator;

import com.gforyas.webappsim.estimators.StatsCollector;
import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.router.DeterministicRouter;
import com.gforyas.webappsim.simulator.router.ProbabilisticRouter;

import java.util.HashMap;
import java.util.Map;

public class SimulationCoxian extends Simulation {

    public SimulationCoxian(SimulationConfig cfg, long seed, double[] mu, double[] p) {
        double arrivalRate = cfg.getArrivalRate(); // kept for compatibility / reporting
        Map<String, Map<String, Double>> serviceRates = cfg.getServiceRates();
        this.routingMatrix = cfg.getRoutingMatrix();
        this.maxEvents = cfg.getMaxEvents();
        this.network = new Network(serviceRates);
        this.rng = cfg.getRngs();
        this.rng.plantSeeds(seed);
        this.warmupCompletions = cfg.getWarmupCompletions();

        // Router selection (keeps backward compatibility)
        if (cfg.isProbabilistic()) {
            this.router = new ProbabilisticRouter(cfg.getProbRoutingTable());
        } else {
            this.router = new DeterministicRouter(this.routingMatrix);
        }

        this.safetyMaxHops = cfg.getSafetyMaxHops();
        this.pathTrackers = router.isProbabilistic() ? new HashMap<>() : null;

        generateBootstrap(cfg);

        // Coxian arrivals
        this.arrivalGenerator = new CoxianArrGen(scheduler, arrivalRate, "A", 1, rng, mu, p);

        scheduler.subscribe(Event.Type.ARRIVAL, this::onArrival);
        scheduler.subscribe(Event.Type.DEPARTURE, this::onDeparture);

        this.statsCollector = new StatsCollector(network, scheduler, routingMatrix, cfg, arrivalRate);

        if (warmupCompletions <= 0) {
            statsCollector.startMeasurement(scheduler);
            measuring = true;
            SysLogger.getInstance().getLogger().info("Warm-up disabled: measurement started immediately.");
        }
    }
}
