package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Stima tempo-pesata della popolazione TOTALE di sistema N(t), robusta al
 * routing interno.
 *
 * Regole:
 * - ARRIVAL esterno (id<0): entra SUBITO; incrementa pop e incrementa
 * "pendingAnon".
 * - Prima ARRIVAL con id>=0:
 * * se "pendingAnon" > 0 → consuma uno (matching dell'anonimo) e aggiungi l'id
 * al set, MA non incrementare pop;
 * * altrimenti (nessun anonimo pendente) → primo ingresso con id noto →
 * incrementa pop e registra l'id.
 * - DEPARTURE: scala SOLO se (server, jobClass) porta a EXIT; se id>=0 rimuovi
 * dal set.
 *
 * Integriamo PRIMA di modificare N: area = ∫N(t)dt, area2 = ∫N(t)^2 dt.
 */
public class PopulationEstimator {

    // popolazione corrente di sistema
    private int pop = 0;

    // integrazione nel tempo
    private double startTime;
    private double lastTime;
    private double area = 0.0; // ∫ N(t) dt
    private double area2 = 0.0; // ∫ [N(t)]^2 dt

    // diagnostica
    private int min = 0;
    private int max = 0;

    // routing per riconoscere l'EXIT
    private final Map<String, Map<String, TargetClass>> routing;

    // job (id>=0) già entrati nel sistema
    private final Set<Integer> inSystem = new HashSet<>();

    // numero di job entrati come anonimi (id<0) che non hanno ancora avuto il primo
    // id>=0
    private int pendingAnon = 0;

    // handler
    private final BiConsumer<Event, NextEventScheduler> onArrival = this::tickThenMaybeEnter;
    private final BiConsumer<Event, NextEventScheduler> onDeparture = this::tickThenMaybeExit;

    public PopulationEstimator(NextEventScheduler sched,
            Map<String, Map<String, TargetClass>> routing) {
        this.routing = routing;
        this.startTime = sched.getCurrentTime();
        this.lastTime = this.startTime;

        // ascolto eventi; integrare SEMPRE prima di cambiare N
        sched.subscribe(Event.Type.ARRIVAL, onArrival);
        sched.subscribe(Event.Type.DEPARTURE, onDeparture);
    }

    /** Integra area/area2 fino a "ora" senza cambiare pop. */
    private void tick(NextEventScheduler s) {
        double now = s.getCurrentTime();
        double dt = now - lastTime;
        if (dt > 0.0) {
            area += pop * dt;
            area2 += (double) pop * (double) pop * dt;
        }
        lastTime = now;
    }

    /**
     * Gestione ARRIVAL di sistema con matching tra anonimi (id<0) e primo id>=0.
     */
    private void tickThenMaybeEnter(Event e, NextEventScheduler s) {
        tick(s);
        int id = e.getJobId();

        if (id < 0) {
            // ARRIVAL esterno: entra ORA e registra un anonimo in attesa del primo id
            pop += 1;
            pendingAnon += 1;
            if (pop > max)
                max = pop;
            return;
        }

        // id >= 0: se già visto, è hop interno → ignora
        if (inSystem.contains(id)) {
            return;
        }

        // primo id>=0 per questo job:
        if (pendingAnon > 0) {
            // questo id chiude un "anonimo" entrato prima → non incrementare pop, ma marca
            // l'id come presente
            pendingAnon -= 1;
            inSystem.add(id);
        } else {
            // nessun anonimo pendente: primo ingresso "diretto" con id noto
            inSystem.add(id);
            pop += 1;
            if (pop > max)
                max = pop;
        }
    }

    /** DEPARTURE di sistema: scala solo su EXIT; rimuovi id dal set se presente. */
    private void tickThenMaybeExit(Event e, NextEventScheduler s) {
        tick(s);
        if (routesToExit(e.getServer(), e.getJobClass())) {
            int id = e.getJobId();
            if (id >= 0)
                inSystem.remove(id);
            pop -= 1;
            if (pop < min)
                min = pop;
        }
    }

    /** Riconosce se (server, jobClass) instrada a EXIT. */
    private boolean routesToExit(String server, int jobClass) {
        Map<String, TargetClass> m = routing.get(server);
        if (m == null)
            return false;
        TargetClass tc = m.get(Integer.toString(jobClass));
        return tc != null && "EXIT".equalsIgnoreCase(tc.eventClass());
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
        if (T <= 0.0)
            return 0.0;
        double m1 = area / T;
        double m2 = area2 / T;
        double v = m2 - m1 * m1;
        return (v > 0.0) ? v : 0.0;
    }

    /** Deviazione standard tempo-pesata. */
    public double getStd() {
        return Math.sqrt(getVariance());
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    /** Finalizza l’ultimo intervallo prima di leggere mean/std. */
    public void finalizeAt(double currentTime) {
        double dt = currentTime - lastTime;
        if (dt > 0.0) {
            area += pop * dt;
            area2 += (double) pop * (double) pop * dt;
        }
        lastTime = currentTime;
    }

    // aggiungi nel tuo PopulationEstimator
    public void startCollecting(double now) {
        // non toccare pop / inSystem / pendingAnon
        this.area = 0.0;
        this.area2 = 0.0;
        this.startTime = now;
        this.lastTime = now;

        // opzionale: reimposta i bound a partire dallo stato corrente
        this.min = this.pop;
        this.max = this.pop;
    }

}
