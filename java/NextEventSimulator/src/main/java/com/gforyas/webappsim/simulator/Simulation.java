package com.gforyas.webappsim.simulator;

import com.gforyas.webappsim.estimators.BatchMeans;
import com.gforyas.webappsim.estimators.StatsCollector;
import com.gforyas.webappsim.estimators.StatsType;
import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.lemer.Rvms;
import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.arrivals.ArrivalGenerator;
import com.gforyas.webappsim.simulator.router.DeterministicRouter;
import com.gforyas.webappsim.simulator.router.ProbabilisticRouter;
import com.gforyas.webappsim.simulator.router.Router;
import com.gforyas.webappsim.util.Printer;

import java.util.Map;
import java.util.HashMap; // NEW
import java.util.concurrent.atomic.AtomicInteger;

import static com.gforyas.webappsim.util.PersonalPrintStream.renderProgress;

/**
 * Next-Event Simulation.
 * Warm-up ONLY by completions: measurement starts after warmupCompletions EXIT.
 */
public class Simulation {

    public static final AtomicInteger SIMULATION_COUNTER = new AtomicInteger(0);
    private static final Rvms RVMS = Rvms.getInstance();

    // --- NEW: router strategy (deterministico o probabilistico) ---
    protected Router router;

    // --- esistente ---
    protected Map<String, Map<String, TargetClass>> routingMatrix;
    protected int maxEvents;
    protected final NextEventScheduler scheduler = new NextEventScheduler();
    protected Network network;
    protected Rngs rng;
    protected ArrivalGenerator arrivalGenerator;

    protected int totalExternalArrivals = 0;
    protected int totalCompletedJobs = 0;
    protected boolean arrivalsStopped = false;

    protected StatsCollector statsCollector;

    /** Soglia di completamenti (EXIT) per terminare il warm-up. */
    protected int warmupCompletions;

    /** True quando abbiamo iniziato a misurare (post warm-up). */
    protected boolean measuring = false;

    // --- NEW: safety max hops (solo prob.) ---
    protected Integer safetyMaxHops;

    // --- NEW: tracking percorsi SOLO in probabilistico ---
    protected Map<Integer, PathTracker> pathTrackers; // jobId -> tracker
    private int countAB = 0;
    private int countABAPA = 0;
    private int countABABForced = 0;

    public Simulation(SimulationConfig cfg) {
        double arrivalRate = cfg.getArrivalRate();
        Map<String, Map<String, Double>> serviceRates = cfg.getServiceRates();
        this.routingMatrix = cfg.getRoutingMatrix();
        this.maxEvents = cfg.getMaxEvents();
        this.network = new Network(serviceRates);
        this.rng = cfg.getRngs();
        this.warmupCompletions = cfg.getWarmupCompletions();

        // NEW: costruzione router (retro-compatibile)
        if (cfg.isProbabilistic()) {
            ProbabilisticRouter pr = new ProbabilisticRouter(cfg.getProbRoutingTable());
            this.router = pr;
        } else {
            this.router = new DeterministicRouter(this.routingMatrix);
        }

        this.safetyMaxHops = cfg.getSafetyMaxHops();
        this.pathTrackers = router.isProbabilistic() ? new HashMap<>() : null;

        generateBootstrap(cfg);

        this.arrivalGenerator = new ArrivalGenerator(scheduler, arrivalRate, "A", 1, rng);
        scheduler.subscribe(Event.Type.ARRIVAL, this::onArrival);
        scheduler.subscribe(Event.Type.DEPARTURE, this::onDeparture);
        this.statsCollector =  StatsType.build(network,scheduler, routingMatrix, cfg);
        // Se warmup <= 0, misura da subito
        if (warmupCompletions <= 0) {
            statsCollector.startMeasurement(scheduler);
            measuring = true;
            SysLogger.getInstance().getLogger().info("Warm-up disattivato: misura avviata subito.");
        }
    }

    public Simulation(){
    }

    protected void generateBootstrap(SimulationConfig config) {
        for (int i = 0; i < config.getInitialArrival(); i++) {
            Event bootstrapEvent = new BootstrapEvent(0.0, Event.Type.ARRIVAL, "A", -1, 1);
            scheduler.scheduleAt(bootstrapEvent, 0.0);
        }
    }

    protected void onArrival(Event e, NextEventScheduler s) {
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
            Job job = new Job(cls, e.getTime(), svc);
            s.getJobTable().put(job.getId(), job);
            e.setJobId(job.getId());
            totalExternalArrivals++;
            node.arrival(job, s);
        } else {
            // Internal routing
            Job job = s.getJobTable().get(e.getJobId());
            if (job == null) return;
            node.arrival(job, s);
        }
    }

    protected void onDeparture(Event e, NextEventScheduler s) {
        Node node = network.getNode(e.getServer());
        Job job = s.getJobTable().get(e.getJobId());
        if (job == null) return;

        node.departure(job, s);

        TargetClass tc = router.next(e.getServer(), job.getJobClass(), rng);
        if (tc == null) return;

        // SOLO in probabilistico: proiettiamo la decisione nella mappa
        if (router.isProbabilistic()) {
            routingMatrix
                    .computeIfAbsent(e.getServer(), k -> new java.util.HashMap<>())
                    .put(Integer.toString(job.getJobClass()), tc);
        }


        // --- EXIT robusto: vale se o la class o il target sono "EXIT"
        boolean isExit =
                "EXIT".equalsIgnoreCase(tc.eventClass()) ||
                        "EXIT".equalsIgnoreCase(tc.serverTarget());

        // --- tracking SOLO in probabilistico (transizioni tra nodi, no class-check) ---
        if (router.isProbabilistic()) {
            PathTracker pt = pathTrackers.computeIfAbsent(job.getId(), k -> new PathTracker());

            // safety hops
            pt.hops++;
            if (safetyMaxHops != null && pt.hops > safetyMaxHops) {
                countABABForced++;
                finalizeExit(job, s);
                pathTrackers.remove(job.getId());
                return;
            }

            String curNode = e.getServer();
            String nextNode = isExit ? "EXIT" : tc.serverTarget();

            // B -> EXIT ⇒ AB
            if ("B".equalsIgnoreCase(curNode) && "EXIT".equalsIgnoreCase(nextNode)) {
                pt.exitedAfterB = true;
            }
            // A -> B ⇒ loop ABAB (conteggia ogni rientro in B da A)
            if ("A".equalsIgnoreCase(curNode) && "B".equalsIgnoreCase(nextNode)) {
                pt.loops++;
            }
            // A -> P ⇒ traccia ABAPA (ha raggiunto P dopo A)
            if ("A".equalsIgnoreCase(curNode) && "P".equalsIgnoreCase(nextNode)) {
                pt.reachedP = true;
            }
        }

        // --- gestione EXIT unificata ---
        if (isExit) {
            totalCompletedJobs++;
            e.cancel();
            // contabilizza percorso SOLO in probabilistico
            if (router.isProbabilistic()) {
                PathTracker pt = pathTrackers.remove(job.getId());
                if (pt != null) {
                    if (pt.exitedAfterB) {
                        countAB++;
                        job.setRoute("AB");
                    } else if (pt.reachedP) {
                        countABAPA++;
                        job.setRoute("ABAPA");
                    } else if (pt.loops > 0) {
                        countABABForced++;
                    }
                }
            }

            // warm-up by completions
            if (!measuring && totalCompletedJobs >= warmupCompletions) {
                statsCollector.startMeasurement(s);
                measuring = true;
                String info = String.format(
                        "Warm-up (completamenti) terminato dopo %d EXIT a t=%.3f s (%.3f h)",
                        totalCompletedJobs, s.getCurrentTime(), s.getCurrentTime() / 3600.0
                );
                SysLogger.getInstance().getLogger().info(info);
            }

            s.getJobTable().remove(job.getId()); // cleanup
            return;
        }

        // --- routing normale ---
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

    // --- LEGACY: lasciata per compatibilità, non più usata direttamente ---
    protected TargetClass lookupRouting(String node, int cls) {
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

        if (!measuring) {
            SysLogger.getInstance().getLogger()
                    .warning("Warm-up non attivato: statistiche raccolte dall'inizio della simulazione.");
        }

        // --- NEW: passa le metriche di percorso allo StatsCollector SOLO se probabilistico ---
        if (router.isProbabilistic()) {
            //statsCollector.enableRoutingPathStats(true);
            //statsCollector.setRoutingPathCounts(countAB, countABAPA, countABABForced);
        }

        statsCollector.calculateStats(scheduler, network);

        // --- NEW: stampa compatta delle statistiche di percorso SOLO in probabilistico ---
        if (router.isProbabilistic()) {
            String msg = String.format(
                    "RoutingPathStats: AB=%d, ABAPA=%d, ABAB_forced=%d",
                    countAB, countABAPA, countABABForced
            );
            SysLogger.getInstance().getLogger().info(msg);
        }
        if (this.statsCollector instanceof BatchMeans bm) {
            System.out.println(bm.summary());
        }
        long wallEnd = System.nanoTime();
        String finalOutput = String.format("Simulation %d Completed: external=%d, done=%d, took=%f s",
                SIMULATION_COUNTER.incrementAndGet(),
                totalExternalArrivals, totalCompletedJobs, (wallEnd - wallStart) / 1e9);
        SysLogger.getInstance().getLogger().info(finalOutput);
    }


    protected int showRender(int stepNow, int lastStep, long wallStart, int cnt, long target, int pct) {
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
                // NEW: info minima routing
                ", routerProbabilistic=" + router.isProbabilistic() +
                '}';
    }

    // --- NEW: piccolo tracker per i percorsi (solo probabilistico) ---
    private static final class PathTracker {
        int hops = 0;
        boolean exitedAfterB = false;
        boolean reachedP = false;
        int loops = 0;
    }

    // --- NEW: uscita forzata (riusa la stessa logica del ramo EXIT) ---
    private void finalizeExit(Job job, NextEventScheduler s) {
        totalCompletedJobs++;

        if (!measuring && totalCompletedJobs >= warmupCompletions) {
            statsCollector.startMeasurement(s);
            measuring = true;

            String info = String.format("Warm-up (completamenti) terminato dopo %d EXIT a t=%.3f s (%.3f h)",
                    totalCompletedJobs, s.getCurrentTime(), s.getCurrentTime() / 3600.0);
            SysLogger.getInstance().getLogger().info(info);
        }

        s.getJobTable().remove(job.getId());
    }
}
