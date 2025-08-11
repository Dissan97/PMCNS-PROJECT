package com.g45.webappsim.simulator;

import com.g45.webappsim.lemer.Rngs;

import java.util.Map;

public class SimulationConfig {

    private double arrivalRate;
    private Map<String, Map<String, Double>> serviceRates;
    private Map<String, Map<String, TargetClass>> routingMatrix;
    private int maxEvents;
    public static final String EXIT = "EXIT";
    private Rngs rngs = new Rngs();

    public static SimulationConfig createDefault() {
        return null;
    }

    public double getArrivalRate() {
        return arrivalRate;
    }

    public Map<String, Map<String, Double>> getServiceRates() {
        return serviceRates;
    }

    public Map<String, Map<String, TargetClass>> getRoutingMatrix() {
        return routingMatrix;
    }

    public int getMaxEvents() {
        return maxEvents;
    }

    public void setArrivalRate(double arrivalRate) {
        this.arrivalRate = arrivalRate;
    }

    public void setServiceRates(Map<String, Map<String, Double>> serviceRates) {
        this.serviceRates = serviceRates;
    }

    public void setRoutingMatrix(Map<String, Map<String, TargetClass>> routingMatrix) {
        this.routingMatrix = routingMatrix;
    }

    public void setMaxEvents(int maxEvents) {
        this.maxEvents = maxEvents;
    }

    @Override
    public String toString() {
        return "SimulationConfig{" +
                "arrivalRate=" + arrivalRate +
                ", serviceRates=" + serviceRates +
                ", routingMatrix=" + routingMatrix +
                ", maxEvents=" + maxEvents +
                '}';
    }

    public Rngs getRngs() {
        return rngs;
    }

    public void setRngs(Rngs rngs) {
        this.rngs = rngs;
    }
}
