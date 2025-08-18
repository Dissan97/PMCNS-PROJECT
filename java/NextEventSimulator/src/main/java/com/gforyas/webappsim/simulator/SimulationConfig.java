package com.gforyas.webappsim.simulator;

import com.gforyas.webappsim.lemer.Rngs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Holds configuration parameters for a single simulation run.
 * <p>
 * This class encapsulates the input parameters required to set up
 * the simulation, including arrival rate, service rates, routing rules,
 * and maximum number of events to process.
 * </p>
 */
public class SimulationConfig {

    private int warmupCompletions = 10_000; // default sensato

    public int getWarmupCompletions() {
        return warmupCompletions;
    }

    public void setWarmupCompletions(int warmupCompletions) {
        this.warmupCompletions = warmupCompletions;
    }

    /**
     * External arrival rate λ (jobs per unit time).
     */
    private double arrivalRate;

    /**
     * Service rates for each node and class.
     * Format: nodeName → (classId → mean service time E[S]).
     */
    private Map<String, Map<String, Double>> serviceRates;

    /**
     * Routing matrix defining the forwarding logic between nodes.
     * Format: nodeName → (classId → {@link TargetClass}).
     */
    private Map<String, Map<String, TargetClass>> routingMatrix;

    /**
     * Maximum number of events to process before stopping arrivals.
     */
    private int maxEvents;

    /**
     * Special constant used in routing matrices to indicate job exit.
     */
    public static final String EXIT = "EXIT";

    /**
     * Random number generator set for the simulation.
     */
    private Rngs rngs = new Rngs();

    /**
     * Initial Arrival Events in the system
     */
    private int initialArrival = 0;

    /**
     * the seeds for the simulation
     */
    private List<Integer> seeds = new ArrayList<>(Arrays.asList(314159265, 271828183, 141421357,
            1732584193, 123456789));

    /**
     * Creates a default empty configuration.
     *
     * @return a default {@code SimulationConfig} (currently uninitialized)
     */
    public static SimulationConfig createDefault() {
        return null;
    }

    /**
     * @return the external arrival rate λ
     */
    public double getArrivalRate() {
        return arrivalRate;
    }

    /**
     * @return the service rates map (node → class → mean service time)
     */
    public Map<String, Map<String, Double>> getServiceRates() {
        return serviceRates;
    }

    /**
     * @return the routing matrix (node → class → {@link TargetClass})
     */
    public Map<String, Map<String, TargetClass>> getRoutingMatrix() {
        return routingMatrix;
    }

    /**
     * @return the maximum number of events before arrivals stop
     */
    public int getMaxEvents() {
        return maxEvents;
    }

    /**
     * Sets the external arrival rate λ.
     *
     * @param arrivalRate the arrival rate in jobs per unit time
     */
    public void setArrivalRate(double arrivalRate) {
        this.arrivalRate = arrivalRate;
    }

    /**
     * Sets the service rates for the network.
     *
     * @param serviceRates a map node → class → mean service time
     */
    public void setServiceRates(Map<String, Map<String, Double>> serviceRates) {
        this.serviceRates = serviceRates;
    }

    /**
     * Sets the routing matrix.
     *
     * @param routingMatrix a map node → class → {@link TargetClass}
     */
    public void setRoutingMatrix(Map<String, Map<String, TargetClass>> routingMatrix) {
        this.routingMatrix = routingMatrix;
    }

    /**
     * Sets the maximum number of events before stopping arrivals.
     *
     * @param maxEvents the maximum event count
     */
    public void setMaxEvents(int maxEvents) {
        this.maxEvents = maxEvents;
    }

    /**
     * @return the RNG set used for this simulation
     */
    public Rngs getRngs() {
        return rngs;
    }

    /**
     * Sets the RNG set to be used in the simulation.
     *
     * @param rngs the RNG instance
     */
    public void setRngs(Rngs rngs) {
        this.rngs = rngs;
    }

    public void setInitialArrival(int initialArrival) {
        this.initialArrival = initialArrival;
    }

    public int getInitialArrival() {
        return this.initialArrival;
    }

    public List<Integer> getSeeds() {
        return seeds;
    }

    public void setSeeds(List<Integer> seeds) {
        this.seeds = seeds;
    }

    /**
     * @return a string representation of the configuration for debugging
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{\n\t");
        routingMatrix.keySet().stream().sorted().forEach(
                (key) -> sb.append(key).append("=").append(routingMatrix.get(key)).append("\n\t"));
        sb.append('}');
        return "SimulationConfig={" +
                "\narrivalRate=" + arrivalRate +
                "\n, serviceRates=" + serviceRates +
                "\n, routingMatrix=" + sb.toString() +
                "\n, maxEvents=" + maxEvents +
                "\n, initialArrival=" + initialArrival +
                "\n, seeds=" + seeds +
                "\n, warmupCompletions=" + warmupCompletions +
                "\n}";
    }
}
