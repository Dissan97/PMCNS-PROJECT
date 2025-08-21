package com.gforyas.webappsim.simulator;

import com.gforyas.webappsim.estimators.StatsCollector;
import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.lemer.Rvms;
import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.util.Printer;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.gforyas.webappsim.util.PersonalPrintStream.renderProgress;

/**
 * Next-Event Simulation.
 * Warm-up ONLY by completions: measurement starts after warmupCompletions EXIT.
 */
public class Simulation {

    public static final AtomicInteger SIMULATION_COUNTER = new AtomicInteger(0);
    private static final Rvms RVMS = Rvms.getInstance();

    protected  final Map<String, Map<String, TargetClass>> routingMatrix;
    protected final int maxEvents;
    protected final NextEventScheduler scheduler = new NextEventScheduler();
    protected final Network network;
    protected final Rngs rng;
    private final ArrivalGenerator arrivalGenerator;

    protected int totalExternalArrivals = 0;
    protected int totalCompletedJobs = 0;
    protected boolean arrivalsStopped = false;

    protected final StatsCollector statsCollector;

    /**
     * Soglia di completamenti (EXIT) per terminare il warm-up.
     */
    protected final int warmupCompletions;

    /**
     * True quando abbiamo iniziato a misurare (post warm-up).
     */
    protected boolean measuring = false;

    public Simulation(SimulationConfig cfg, long seed) {
        double arrivalRate = cfg.getArrivalRate();
        Map<String, Map<String, Double>> serviceRates = cfg.getServiceRates();
        this.routingMatrix = cfg.getRoutingMatrix();
        this.maxEvents = cfg.getMaxEvents();
        this.network = new Network(serviceRates);
        this.rng = cfg.getRngs();
        this.rng.plantSeeds(seed);
        this.warmupCompletions = cfg.getWarmupCompletions();

        generateBootstrap(cfg);

        this.arrivalGenerator = new ArrivalGenerator(scheduler, arrivalRate, "A", 1, rng);
        this.statsCollector = new StatsCollector(network, scheduler, routingMatrix,
                cfg, arrivalRate);

        scheduler.subscribe(Event.Type.ARRIVAL, this::onArrival);
        scheduler.subscribe(Event.Type.DEPARTURE, this::onDeparture);

        // Se warmup <= 0, misura da subito
        if (warmupCompletions <= 0) {
            statsCollector.startMeasurement(scheduler);
            measuring = true;
            SysLogger.getInstance().getLogger().info("Warm-up disattivato: misura avviata subito.");
        }

    }

    protected  void generateBootstrap(SimulationConfig config) {
        for (int i = 0; i < config.getInitialArrival(); i++) {
            Event bootstrapEvent = new BootstrapEvent(0.0, Event.Type.ARRIVAL, "A", -1, 1);
            scheduler.scheduleAt(bootstrapEvent, 0.0);
        }
    }

    protected  void onArrival(Event e, NextEventScheduler s) {
        Node node = network.getNode(e.getServer());
        if (e.getJobId() == -1) {
            // External arrival
            int cls = e.getJobClass();
            Double meanService = node.getServiceMeans().get(cls);
            if (meanService == null) {
                String severe = "Missing service mean for node " + e.getServer() + " class " + cls;
                SysLogger.getInstance().getLogger().severe(severe);
                return;
            }
            double svc = RVMS.idfExponential(meanService, rng.random(node.getStreamId()));
            Job job = new Job(cls, s.getCurrentTime(), svc);
            s.getJobTable().put(job.getId(), job);
            totalExternalArrivals++;
            node.arrival(job, s);
        } else {
            // Internal routing
            Job job = s.getJobTable().get(e.getJobId());
            if (job == null) return;
            node.arrival(job, s);
        }
    }

    protected  void onDeparture(Event e, NextEventScheduler s) {
        Node node = network.getNode(e.getServer());
        Job job = s.getJobTable().get(e.getJobId());
        if (job == null) return;

        node.departure(job, s);

        TargetClass tc = lookupRouting(e.getServer(), job.getJobClass());
        if (tc == null) return;

        if ("EXIT".equalsIgnoreCase(tc.eventClass())) {
            totalCompletedJobs++;

            // avvia la misura al raggiungimento della soglia di completamenti
            if (!measuring && totalCompletedJobs >= warmupCompletions) {
                statsCollector.startMeasurement(s);
                measuring = true;

                String info = String.format("Warm-up (completamenti) terminato dopo %d EXIT a t=%.3f s (%.3f h)",
                        totalCompletedJobs, s.getCurrentTime(), s.getCurrentTime() / 3600.0);
                SysLogger.getInstance().getLogger().info(
                        info
                );

            }

            // libera la jobTable per evitare leak
            s.getJobTable().remove(job.getId());
            return;
        }

        scheduleTheTarget(s, job, tc, RVMS);
    }

    protected void scheduleTheTarget(NextEventScheduler s, Job job, TargetClass tc, Rvms rvms) {
        String nextNode = tc.serverTarget();
        int nextClass = Integer.parseInt(tc.eventClass());
        job.setJobClass(nextClass);
        Node nextTarget = network.getNode(nextNode);
        Double meanService = nextTarget.getServiceMeans().get(nextClass);
        if (meanService == null) {
            String severe = "Missing service mean for node " + nextNode + " class " + nextClass;
            SysLogger.getInstance().getLogger().severe(severe);
            return;
        }
        double svc = rvms.idfExponential(meanService, rng.random(nextTarget.getStreamId()));
        job.setRemainingService(svc);

        Event next = new Event(s.getCurrentTime(), Event.Type.ARRIVAL, nextNode, job.getId(), nextClass);
        s.schedule(next);
    }

    protected  TargetClass lookupRouting(String node, int cls) {
        Map<String, TargetClass> m = routingMatrix.get(node);
        if (m == null) return null;
        return m.get(Integer.toString(cls));
    }


    public void run() {
        int cnt = 0;
        long wallStart = System.nanoTime();

        // --- progress config ---
        final int stepPercent = 10;            // update every 10%
        int lastStep = -1;                     // last emitted step bucket
        final int barWidth = 28;               // width of the ASCII bar
        final long target = Math.max(1L, maxEvents); // avoid /0

        // initial line (0%)


        while (scheduler.hasNext()) {
            scheduler.next();
            cnt++;

            // stop external arrivals after a small tail over maxEvents (unchanged)
            if (!arrivalsStopped && cnt >= maxEvents + 5000) {
                arrivalGenerator.setActive(false);
                arrivalsStopped = true;
            }

            // emit progress every 10%
            int pct = (int) Math.min(100L, (cnt * 100L) / target);
            int stepNow = pct / stepPercent;
            lastStep = showRender(stepNow, lastStep, wallStart, cnt, target, pct);
        }

        // final stats + close progress line at 100%
        double elapsedSec = (System.nanoTime() - wallStart) / 1e9;
        double rateEvPerSec = elapsedSec > 0 ? cnt / elapsedSec : 0.0;
        Printer.out().progress(renderProgress(100, rateEvPerSec, 0.0, barWidth));
        Printer.out().progressDone();

        if (!measuring) {
            SysLogger.getInstance().getLogger()
                    .warning("Warm-up non attivato: statistiche raccolte dall'inizio della simulazione.");
        }

        statsCollector.calculateStats(scheduler, network);

        long wallEnd = System.nanoTime();
        String finalOutput = String.format("Simulation %d Completed: external=%d, done=%d, took=%f s",
                SIMULATION_COUNTER.incrementAndGet(),
                totalExternalArrivals, totalCompletedJobs, (wallEnd - wallStart) / 1e9);
        SysLogger.getInstance().getLogger().info(finalOutput);
    }

    protected  int showRender(int stepNow, int lastStep, long wallStart, int cnt, long target, int pct) {
        if (stepNow != lastStep) {
            lastStep = stepNow;

            double elapsedSec = (System.nanoTime() - wallStart) / 1e9;
            double rateEvPerSec = elapsedSec > 0 ? cnt / elapsedSec : 0.0;
            long remaining = Math.max(0L, target - cnt);
            double etaSec = rateEvPerSec > 0 ? remaining / rateEvPerSec : Double.NaN;
            Printer.out().progress(
                    renderProgress(pct, rateEvPerSec, etaSec, 28)
            );
        }
        return lastStep;
    }

    @Override
    public String toString() {
        return "Simulation{" +
                "routingMatrix=" + routingMatrix +
                ", maxEvents=" + maxEvents +
                ", scheduler=" + scheduler +
                ", network=" + network +
                ", rng=" + rng +
                ", arrivalGenerator=" + arrivalGenerator +
                ", totalExternalArrivals=" + totalExternalArrivals +
                ", totalCompletedJobs=" + totalCompletedJobs +
                ", arrivalsStopped=" + arrivalsStopped +
                ", statsCollector=" + statsCollector +
                ", warmupCompletions=" + warmupCompletions +
                ", measuring=" + measuring +
                '}';
    }
}
