package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import java.util.function.BiConsumer;

/**
 * Stima della popolazione media *tempo-pesata* e della sua deviazione standard.
 * Integriamo area = ∫ N(t) dt e area2 = ∫ N(t)^2 dt aggiornando ad ogni evento
 * PRIMA di modificare la popolazione. Poi:
 *   mean = area / T,  E[N^2] = area2 / T,  var = E[N^2] - mean^2,  std = sqrt(var).
 */
public class PopulationEstimator {

    // popolazione corrente
    private int pop = 0;

    // integrazione nel tempo
    private final double startTime;
    private double lastTime;
    private double area  = 0.0; // ∫ N(t) dt
    private double area2 = 0.0; // ∫ N(t)^2 dt

    // opzionali (diagnostica)
    private int min = 0;
    private int max = 0;

    // handler registrati
    private final BiConsumer<Event, NextEventScheduler> onArrival   = this::tickThenInc;
    private final BiConsumer<Event, NextEventScheduler> onDeparture = this::tickThenDec;

    public PopulationEstimator(NextEventScheduler sched) {
        this.startTime = sched.getCurrentTime();
        this.lastTime  = this.startTime;

        // ascolta arrivi e partenze (tick sempre prima di cambiare pop)
        sched.subscribe(Event.Type.ARRIVAL,   onArrival);
        sched.subscribe(Event.Type.DEPARTURE, onDeparture);
    }

    /** Aggiorna area e area2 fino a "ora" senza cambiare pop. */
    private void tick(NextEventScheduler s) {
        double now = s.getCurrentTime();
        double dt  = now - lastTime;
        if (dt > 0.0) {
            area  += pop * dt;
            area2 += (double)pop * (double)pop * dt;
            lastTime = now;
        } else {
            lastTime = now; // stesso timestamp, nessuna integrazione
        }
    }

    private void tickThenInc(Event e, NextEventScheduler s) {
        tick(s);
        pop += 1;
        if (pop > max) max = pop;
    }

    private void tickThenDec(Event e, NextEventScheduler s) {
        tick(s);
        pop -= 1;
        if (pop < min) min = pop;
    }

    /** Tempo osservato finora. */
    public double elapsed() {
        return lastTime - startTime;
    }

    /** Media tempo-pesata E[N]. */
    public double getMean() {
        double T = elapsed();
        return (T > 0.0) ? (area / T) : 0.0;
    }

    /** Varianza tempo-pesata Var[N] = E[N^2] - (E[N])^2. */
    public double getVariance() {
        double T = elapsed();
        if (T <= 0.0) return 0.0;
        double mean  = area  / T;
        double mean2 = area2 / T;
        double var = mean2 - mean * mean;
        return (var > 0.0) ? var : 0.0; // clamp per numerica
    }

    /** Deviazione standard tempo-pesata. */
    public double getStd() {
        return Math.sqrt(getVariance());
    }

    // opzionali
    public int getMin() { return min; }
    public int getMax() { return max; }

    /**
     * Finalizza l’integrazione fino a currentTime (es. fine simulazione)
     * prima di leggere media/std.
     */
    public void finalizeAt(double currentTime) {
        double dt = currentTime - lastTime;
        if (dt > 0.0) {
            area  += pop * dt;
            area2 += (double)pop * (double)pop * dt;
            lastTime = currentTime;
        }
    }
}
