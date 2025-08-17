package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

/**
 * Stima tempo-pesata della popolazione del SINGOLO nodo.
 * Regole:
 *  - integriamo l'area PRIMA di modificare pop;
 *  - incrementiamo/decrementiamo pop SOLO se l'evento riguarda il nodo monitorato.
 */
public class PopulationEstimatorNode {

    private final String node; // nodo monitorato

    private int pop = 0;

    private double startTime;
    private double lastTime;
    private double area  = 0.0; // ∫ N_n(t) dt
    private double area2 = 0.0; // ∫ [N_n(t)]^2 dt

    private int min = 0;
    private int max = 0;

    public PopulationEstimatorNode(NextEventScheduler sched, String node) {
        this.node = node;
        this.startTime = sched.getCurrentTime();
        this.lastTime  = this.startTime;

        // tick su QUALSIASI evento; +/- solo se e.getServer() == node
        sched.subscribe(Event.Type.ARRIVAL,   this::nodeTickThenInc);
        sched.subscribe(Event.Type.DEPARTURE, this::nodeTickThenDec);
    }

    public void startCollecting(double now) {
        this.area = 0.0;
        this.area2 = 0.0;
        this.startTime = now;
        this.lastTime = now;
        this.min = this.pop;
        this.max = this.pop;
    }

    /** Tick globale: integra area e area2 fino a "ora". */
    private void tick(NextEventScheduler s) {
        double now = s.getCurrentTime();
        double dt  = now - lastTime;
        if (dt > 0.0) {
            area  += pop * dt;
            area2 += (double) pop * (double) pop * dt;
        }
        lastTime = now;
    }

    private boolean nodeEquals(Event e) {
        String server = e.getServer();
        return server != null && server.equals(node);
    }

    private void nodeTickThenInc(Event e, NextEventScheduler s) {
        tick(s);                 // sempre
        if (nodeEquals(e)) {     // solo eventi del nodo
            pop += 1;
            if (pop > max) max = pop;
        }
    }

    private void nodeTickThenDec(Event e, NextEventScheduler s) {
        tick(s);                 // sempre
        if (nodeEquals(e)) {     // solo eventi del nodo
            pop -= 1;
            if (pop < min) min = pop;
        }
    }

    /** Tempo osservato finora. */
    public double elapsed() { return lastTime - startTime; }

    /** Media tempo-pesata E[N_n]. */
    public double getMean() {
        double T = elapsed();
        return (T > 0.0) ? (area / T) : 0.0;
    }

    /** Varianza tempo-pesata per il nodo. */
    public double getVariance() {
        double T = elapsed();
        if (T <= 0.0) return 0.0;
        double mean  = area  / T;
        double mean2 = area2 / T;
        double var = mean2 - mean * mean;
        return (var > 0.0) ? var : 0.0;
    }

    /** Deviazione standard tempo-pesata per il nodo. */
    public double getStd() { return Math.sqrt(getVariance()); }

    // opzionali
    public int getMin() { return min; }
    public int getMax() { return max; }
    public String getNode() { return node; }

    /** Finalizza l’integrazione fino a currentTime. */
    public void finalizeAt(double currentTime) {
        double dt = currentTime - lastTime;
        if (dt > 0.0) {
            area  += pop * dt;
            area2 += (double) pop * (double) pop * dt;
        }
        lastTime = currentTime;
    }
}
