package com.gforyas.webappsim.simulator;

import com.gforyas.webappsim.estimators.StatsCollector;
import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.router.DeterministicRouter;
import com.gforyas.webappsim.simulator.router.ProbabilisticRouter;

import java.util.HashMap;
import java.util.Map;

public class SimulationHyperExp extends Simulation {

    public SimulationHyperExp(SimulationConfig cfg, long seed, double p) {
        double arrivalRate = cfg.getArrivalRate();
        Map<String, Map<String, Double>> serviceRates = cfg.getServiceRates();
        this.routingMatrix = cfg.getRoutingMatrix();
        this.maxEvents = cfg.getMaxEvents();
        this.network = new Network(serviceRates);
        this.rng = cfg.getRngs();
        this.warmupCompletions = cfg.getWarmupCompletions();

        // NEW: costruzione router (retro-compatibile)
        if (cfg.isProbabilistic()) {
            this.router = new ProbabilisticRouter(cfg.getProbRoutingTable());
        } else {
            this.router = new DeterministicRouter(this.routingMatrix);
        }

        this.safetyMaxHops = cfg.getSafetyMaxHops();
        this.pathTrackers = router.isProbabilistic() ? new HashMap<>() : null;

        generateBootstrap(cfg);

        this.arrivalGenerator = new HyperExpArrGen(scheduler, arrivalRate, "A", 1, rng, p);
        scheduler.subscribe(Event.Type.ARRIVAL, this::onArrival);
        scheduler.subscribe(Event.Type.DEPARTURE, this::onDeparture);
        this.statsCollector = new StatsCollector(network, scheduler, routingMatrix,
                cfg, arrivalRate);

        // Se warmup <= 0, misura da subito
        if (warmupCompletions <= 0) {
            statsCollector.startMeasurement(scheduler);
            measuring = true;
            SysLogger.getInstance().getLogger().info("Warm-up disattivato: misura avviata subito.");
        }
    }
}
