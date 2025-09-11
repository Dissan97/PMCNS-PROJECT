
package com.gforyas.webappsim.estimators;

import com.gforyas.webappsim.simulator.Event;
import com.gforyas.webappsim.simulator.Network;
import com.gforyas.webappsim.simulator.NextEventScheduler;
import com.gforyas.webappsim.simulator.SimulationConfig;
import com.gforyas.webappsim.simulator.TargetClass;
import com.gforyas.webappsim.util.SinkBatchToCsv;

import java.util.*;

/**
 * Implementazione del metodo delle "Batch Means" basato sul conteggio di
 * eventi.
 *
 * <p>
 * L'idea è dividere l'orizzonte di osservazione in N batch consecutivi,
 * ciascuno
 * contenente un numero fissato di <b>completamenti di sistema</b>
 * (DEPARTURE-&gt;EXIT).
 * Ogni batch produce statistiche indipendenti (circa) su grandezze globali
 * come throughput, tempo di risposta, popolazione media pesata nel tempo e
 * frazione di tempo occupato (busy).
 * </p>
 *
 * <p>
 * <b>Batch boundary.</b> Il confine del batch è determinato dal
 * contatore {@link CompletionsEstimator} (globale) che raggiunge
 * {@code batchSize}.
 * Alla chiusura del batch vengono:
 * <ul>
 * <li>finalizzati gli stimatori time-weighted (popolazione, busy);</li>
 * <li>campionato il tempo osservato del batch;</li>
 * <li>calcolate le metriche del batch;</li>
 * <li>azzerate le baseline degli stimatori per il batch successivo.</li>
 * </ul>
 * </p>
 *
 * <p>
 * - {@link #startMeasurement(NextEventScheduler)} avvia il primo batch;
 * - {@link #calculateStats(NextEventScheduler, Network)} finalizza al termine
 * della simulazione (non aggiunge batch parziali).
 * </p>
 *
 * <p>
 * <b>Nota integrazione:</b> per supportare EXIT espliciti (non via routing) è
 * esposto {@link #forwardExitToEstimators(int, double)}, con la stessa firma
 * usata
 * in {@link StatsCollector}. Se la Simulation chiama già quel metodo su
 * StatsCollector,
 * puoi sostituire l'istanza con questa classe senza cambiare la call-site.
 * </p>
 */
public class BatchMeans extends StatsCollector {

    // === Parametri di batching ===
    private final int batchSize; // completamenti per batch
    private final int batchCount; // numero di batch da produrre
    SinkBatchToCsv sinkBatchToCsv = null;

    // === Stato batching ===
    protected final List<BatchStats> batches = new ArrayList<>();
    protected final Map<String, List<BatchStats>> batchPerNode = new HashMap<>();
    protected boolean active = false; // true dopo startMeasurement
    protected boolean finishedAll = false; // true quando sono stati raccolti N batch

    // === Routing (per completamenti/EXIT) ===
    protected final Map<String, Map<String, TargetClass>> routing;

    /**
     * Ctor che legge i parametri da {@link SimulationConfig}.
     * Si aspettano accessor come getBatchSize()/getBatchCount(); se la tua classe
     * usa nomi diversi, considera l'altro costruttore overload.
     */

    public BatchMeans(Network network,
            NextEventScheduler scheduler,
            Map<String, Map<String, TargetClass>> routingMatrix, SimulationConfig config) {
        this.batchSize = config.getBatchSize();
        this.batchCount = config.getBatchCount();
        this.routing = Objects.requireNonNull(routingMatrix, "routingMatrix");

        // Istanzia stimatori globali e si registra agli eventi
        this.rt = new ResponseTimeEstimator(scheduler, routingMatrix);
        this.pop = new PopulationEstimator(scheduler, routingMatrix);
        this.comp = new CompletionsEstimator(scheduler, routingMatrix);
        this.ot = new ObservationTimeEstimator(scheduler);
        this.busy = new BusyTimeEstimator(scheduler, routingMatrix);

        for (String n : network.allNodes()) {

            rtNode.put(n, new ResponseTimeEstimatorNode(scheduler, n, routingMatrix));

            popNode.put(n, new PopulationEstimatorNode(scheduler, n));
            compNode.put(n, new CompletionsEstimatorNode(scheduler, n, routingMatrix));
            busyNode.put(n, new BusyTimeEstimatorNode(scheduler, n));
            batchPerNode.put(n, new ArrayList<>());
        }
        // Gate sui confini batch: dopo ogni ARRIVAL/DEPARTURE controllo la soglia
        // scheduler.subscribe(Event.Type.ARRIVAL, this::onAnyEvent);
        if (config.getSink() instanceof SinkBatchToCsv b2Csv) {
            this.sinkBatchToCsv = b2Csv;
        } else {
            this.sinkBatchToCsv = new SinkBatchToCsv("foo.json");
        }
        this.arrivalRate = config.getArrivalRate();
        scheduler.subscribe(Event.Type.DEPARTURE, this::onAnyEvent);
    }

    // === MetricCollection ===

    @Override
    public void startMeasurement(NextEventScheduler scheduler) {
        // Avvia il PRIMO batch: azzera baseline e timer
        double now = scheduler.getCurrentTime();
        batches.clear();
        finishedAll = false;
        active = true;

        ot.startCollecting(now);
        busy.startCollecting(now);
        pop.startCollecting(now);
        comp.startCollecting(); // baseline per throughput (conteggio completamenti nel batch)
        rt.startCollecting(); // azzera Welford del tempo di risposta del batch corrente
        batchPerNode.forEach((node, batch) -> {
            busyNode.get(node).startCollecting(now);
            popNode.get(node).startCollecting(now);
            compNode.get(node).startCollecting();
            rtNode.get(node).startCollecting();
        });
    }

    @Override
    public void calculateStats(NextEventScheduler scheduler, Network network) {
        // Non aggiunge batch parziali: se l'ultimo non ha raggiunto batchSize, lo
        // scarta.
        // Finalizza soltanto le code aperte per lasciare coerenza agli stimatori.
        double now = scheduler.getCurrentTime();
        busy.finalizeBusy(now);
        pop.finalizeAt(now);
        this.batchPerNode.forEach((s, nodes) -> {
            System.out.println("\n nodo: " + s + " summary=" + Summary.from(nodes));
        });
        this.sinkBatchToCsv.sink(batches, batchPerNode, super.arrivalRate);
    }

    // === API accessorie ===

    /** Restituisce la lista in ordine dei batch raccolti (solo batch COMPLETI). */
    public List<BatchStats> getBatches() {
        return batches;
    }

    /**
     * Comodo riassunto su media e deviazione standard tra i batch delle principali
     * metriche.
     */
    public Summary summary() {
        return Summary.from(batches);
    }

    /** Allinea il comportamento di StatsCollector per EXIT "esterni". */
    public void forwardExitToEstimators(int jobId, double now) {
        comp.notifyExit(now);
        rt.notifyExit(jobId, now);
        pop.notifyExit(jobId, now);
    }

    // === Internals ===

    /**
     * Chiamato su OGNI evento (ARRIVAL/DEPARTURE). Chiude/riapre i batch quando
     * serve.
     */
    private void onAnyEvent(Event e, NextEventScheduler s) {
        if (!active || finishedAll)
            return;

        // Gate: batch chiuso quando i completamenti del batch corrente raggiungono
        // batchSize
        long done = comp.getCountSinceStart();
        if (done >= batchSize) {
            closeCurrentBatchAndMaybeStartNext(s);
            // System.out.println(batches.getLast());
        }
    }

    /**
     * Chiude il batch corrente, memorizza le statistiche, poi apre il successivo
     * (se serve).
     */
    private void closeCurrentBatchAndMaybeStartNext(NextEventScheduler scheduler) {
        // 1) Finalizza al timestamp corrente per coerenza con gli stimatori
        // time-weighted
        double now = scheduler.getCurrentTime();
        busy.finalizeBusy(now);
        pop.finalizeAt(now);

        // 2) Estrae metriche del batch
        double elapsed = ot.elapsed();
        long completions = comp.getCountSinceStart(); // dovrebbe eguagliare batchSize (o superarlo di pochissimo)
        double thr = (elapsed > 0.0) ? (completions / elapsed) : 0.0;

        double meanPop = pop.getMean();
        double stdPop = pop.getStd();
        double util = (elapsed > 0.0) ? (busy.getBusyTime() / elapsed) : 0.0;

        double meanRT = rt.welfordEstimator.getMean();
        double stdRT = rt.welfordEstimator.getStddev();

        batches.add(new BatchStats(batches.size() + 1, elapsed, completions, thr,
                meanRT, stdRT, meanPop, stdPop, util));

        batchPerNode.forEach((node, batch) -> {
            // FINALIZZA per-nodo al confine del batch (EVITA perdita di area/busy)
            busyNode.get(node).finalizeBusy(now);
            popNode.get(node).finalizeAt(now);

            PerNodeResult result = getPerNodeResult(node, elapsed);
            batch.add(new BatchStats(
                    batches.size() + 1, // meglio usare l'indice batch reale
                    elapsed,
                    compNode.get(node).getCountSinceStart(),
                    result.sampleMean(),
                    result.sampleWait(), result.stdWn(),
                    result.samplePopulation(), result.stdNn(),
                    result.util()));

            // RIAVVIA per il batch successivo
            busyNode.get(node).startCollecting(now); // vedi Patch 2 qui sotto
            popNode.get(node).startCollecting(now);
            compNode.get(node).startCollecting();
            rtNode.get(node).startCollecting();
        });
        // 3) Se abbiamo raggiunto il numero desiderato di batch, chiudiamo
        // definitivamente
        if (batches.size() >= batchCount) {
            finishedAll = true;
            active = false;
            return;
        }

        // 4) Altrimenti riavvia baseline/timer per il prossimo batch
        ot.startCollecting(now);
        busy.startCollecting(now);
        pop.startCollecting(now);
        comp.startCollecting(); // azzera baseline dei completamenti
        rt.startCollecting(); // azzera Welford del tempo di risposta
    }

    /**
     * @param index            Indice progressivo del batch (1-based).
     * @param elapsed          Tempo osservato nel batch.
     * @param completions      Completamenti conteggiati nel batch.
     * @param throughput       Throughput batch (= completions / elapsed).
     * @param meanResponseTime Tempo di risposta medio (Welford) nel batch.
     * @param stdResponseTime  Deviazione standard del tempo di risposta nel batch.
     * @param meanPopulation   Popolazione media pesata nel tempo nel batch.
     * @param stdPopulation    Deviazione standard della popolazione nel batch.
     * @param utilization      Frazione di tempo occupato (busy time / elapsed).
     */ // === DTO delle statistiche per-batch ===
    public record BatchStats(int index, double elapsed, long completions, double throughput, double meanResponseTime,
            double stdResponseTime, double meanPopulation, double stdPopulation, double utilization) {
    }

    // === Riassunto aggregato tra batch (media e std delle mean per-batch) ===
    public static final class Summary {
        public final int n;
        public final double meanThroughput;
        public final double stdThroughput;
        public final double meanResponseTime;
        public final double stdResponseTime;
        public final double meanPopulation;
        public final double stdPopulation;
        public final double meanUtilization;
        public final double stdUtilization;

        private Summary(int n, double meanThroughput, double stdThroughput,
                double meanResponseTime, double stdResponseTime,
                double meanPopulation, double stdPopulation,
                double meanUtilization, double stdUtilization) {
            this.n = n;
            this.meanThroughput = meanThroughput;
            this.stdThroughput = stdThroughput;
            this.meanResponseTime = meanResponseTime;
            this.stdResponseTime = stdResponseTime;
            this.meanPopulation = meanPopulation;
            this.stdPopulation = stdPopulation;
            this.meanUtilization = meanUtilization;
            this.stdUtilization = stdUtilization;
        }

        public static Summary from(List<BatchStats> batches) {
            int n = batches.size();
            if (n == 0) {
                return new Summary(0, 0, 0, 0, 0, 0, 0, 0, 0);
            }
            double[] thr = new double[n];
            double[] rtm = new double[n];
            double[] pop = new double[n];
            double[] bus = new double[n];
            for (int i = 0; i < n; i++) {
                BatchStats b = batches.get(i);
                thr[i] = b.throughput;
                rtm[i] = b.meanResponseTime;
                pop[i] = b.meanPopulation;
                bus[i] = b.utilization;
            }
            return new Summary(n,
                    mean(thr), std(thr),
                    mean(rtm), std(rtm),
                    mean(pop), std(pop),
                    mean(bus), std(bus));
        }

        private static double mean(double[] a) {
            double s = 0.0;
            for (double v : a)
                s += v;
            return s / a.length;
        }

        private static double std(double[] a) {
            int n = a.length;
            if (n <= 1)
                return 0.0;
            double m = mean(a);
            double s2 = 0.0;
            for (double v : a) {
                double d = v - m;
                s2 += d * d;
            }
            return Math.sqrt(s2 / (n - 1));
        }

        @Override
        public String toString() {
            return "Summary{" +
                    "n=" + n +
                    ", meanThroughput=" + meanThroughput +
                    ", stdThroughput=" + stdThroughput +
                    ", meanResponseTime=" + meanResponseTime +
                    ", stdResponseTime=" + stdResponseTime +
                    ", meanPopulation=" + meanPopulation +
                    ", stdPopulation=" + stdPopulation +
                    ", meanBusyFraction=" + meanUtilization +
                    ", stdBusyFraction=" + stdUtilization +
                    '}';
        }
    }

}
