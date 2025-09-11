package com.gforyas.webappsim.simulator;

import com.gforyas.webappsim.lb.LoadBalance;
import com.gforyas.webappsim.lemer.Rvms;
import com.gforyas.webappsim.logging.SysLogger;

import java.util.*;

/**
 * Simulation with load-balanced routing: when a (node,class) has multiple targets
 * in the routing matrix, a LoadBalance policy decides which TargetClass to pick.
 * This subclass reuses the base Simulation logic and only overrides the routing
 * decision after departures. External arrivals and measurement logic are preserved.
 */
public class SimulationLB extends Simulation {

    /** LB-aware routing matrix: node -> class -> list of TargetClass. */
    protected final Map<String, Map<String, List<TargetClass>>> routingMatrixLB;

    /** Load balancing policy. */
    protected final LoadBalance policy;

    private static final Rvms RVMS = Rvms.getInstance();

    /**
     * Create a load-balanced simulation.
     *
     * @param cfg   simulation config (must provide routingMatrixLB; if null, it is built from singleton base matrix)
     * @param seed  seed for RNG
     * @param lb    load balancing policy
     */
    public SimulationLB(SimulationConfig cfg, long seed, LoadBalance lb) {
        super(cfg);
        this.policy = Objects.requireNonNull(lb, "LoadBalance policy must not be null");

        Map<String, Map<String, List<TargetClass>>> fromCfg = cfg.getRoutingMatrixLB();
        if (fromCfg != null && !fromCfg.isEmpty()) {
            this.routingMatrixLB = fromCfg;
        } else {
            // Backward-compat: wrap single TargetClass into singleton lists
            this.routingMatrixLB = new HashMap<>();
            for (var eNode : routingMatrix.entrySet()) {
                Map<String, List<TargetClass>> perClass = new HashMap<>();
                for (var eCls : eNode.getValue().entrySet()) {
                    perClass.put(eCls.getKey(), List.of(eCls.getValue()));
                }
                this.routingMatrixLB.put(eNode.getKey(), perClass);
            }
        }
    }

    /**
     * Overridden departure handler that performs load-balanced routing
     * when multiple targets exist for (node,class).
     */
    @Override
    protected void onDeparture(Event e, NextEventScheduler s) {
        Node node = network.getNode(e.getServer());
        Job job = s.getJobTable().get(e.getJobId());
        if (job == null) return;

        // Service completion on current node
        node.departure(job, s);

        // ----- LB-aware lookup
        List<TargetClass> candidates = lookupRoutingLB(e.getServer(), job.getJobClass());

        if (candidates == null || candidates.isEmpty()) {
            // Fallback to base single-target behavior
            TargetClass tc = super.lookupRouting(e.getServer(), job.getJobClass());
            if (tc == null) return;
            handleRouting(tc, job, s);
            return;
        }

        // Single candidate behaves as base
        if (candidates.size() == 1) {
            handleRouting(candidates.getFirst(), job, s);
            return;
        }

        // Multiple candidates -> use policy
        TargetClass chosen = policy.choose(e.getServer(), job.getJobClass(), candidates, network, s);
        handleRouting(chosen, job, s);
    }

    /**
     * Route the job according to the chosen TargetClass, handling EXIT or forward.
     */
    private void handleRouting(TargetClass tc, Job job, NextEventScheduler s) {
        if ("EXIT".equalsIgnoreCase(tc.eventClass())) {
            totalCompletedJobs++;

            // Start measurement if warm-up by completions is configured
            if (!measuring && totalCompletedJobs >= warmupCompletions) {
                statsCollector.startMeasurement(s);
                measuring = true;
                String info = String.format(
                        "Warm-up (completions) ended after %d EXIT at t=%.3f s (%.3f h)",
                        totalCompletedJobs, s.getCurrentTime(), s.getCurrentTime() / 3600.0);
                SysLogger.getInstance().getLogger().info(info);
            }

            // Remove from job table to avoid leaks
            s.getJobTable().remove(job.getId());
            return;
        }

        // Forward to the next node/class as usual
        scheduleTheTarget(s, job, tc, RVMS);
    }

    /**
     * Helper to get LB-aware candidates list for (node, class).
     */
    protected List<TargetClass> lookupRoutingLB(String node, int cls) {
        Map<String, List<TargetClass>> m = routingMatrixLB.get(node);
        if (m == null) return Collections.emptyList();
        return m.get(Integer.toString(cls));
    }

    @Override
    public String toString() {
        return "SimulationLB{" +
                "routingMatrixLB=" + routingMatrixLB +
                ", policy=" + policy +
                ", routingMatrix=" + routingMatrix +
                ", maxEvents=" + maxEvents +
                ", scheduler=" + scheduler +
                ", network=" + network +
                ", rng=" + rng +
                ", totalExternalArrivals=" + totalExternalArrivals +
                ", totalCompletedJobs=" + totalCompletedJobs +
                ", arrivalsStopped=" + arrivalsStopped +
                ", statsCollector=" + statsCollector +
                ", warmupCompletions=" + warmupCompletions +
                ", measuring=" + measuring +
                '}';
    }
}
