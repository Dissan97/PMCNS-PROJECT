package com.gforyas.webappsim.simulator;

import com.gforyas.webappsim.estimators.EstimatorFacade;
import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.lemer.Rvms;
import com.gforyas.webappsim.logging.SysLogger;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Next-Event Simulation.
 * Warm-up ONLY by completions: measurement starts after warmupCompletions EXIT.
 */
public class Simulation {

    public static final AtomicInteger SIMULATION_COUNTER = new AtomicInteger(0);
    private static final Rvms RVMS = Rvms.getInstance();

    private final Map<String, Map<String, TargetClass>> routingMatrix;
    private final int maxEvents;
    private final NextEventScheduler scheduler = new NextEventScheduler();
    private final Network network;
    private final Rngs rng;
    private final ArrivalGenerator arrivalGenerator;

    private int totalExternalArrivals = 0;
    private int totalCompletedJobs = 0;
    private boolean arrivalsStopped = false;

    private final EstimatorFacade facade;

    /** Soglia di completamenti (EXIT) per terminare il warm-up. */
    private final int warmupCompletions;

    /** True quando abbiamo iniziato a misurare (post warm-up). */
    private boolean measuring = false;

    public Simulation(SimulationConfig cfg, long seed) {
        double arrivalRate = cfg.getArrivalRate();
        Map<String, Map<String, Double>> serviceRates = cfg.getServiceRates();
        this.routingMatrix = cfg.getRoutingMatrix();
        this.maxEvents = cfg.getMaxEvents();
        this.network = new Network(serviceRates);
        this.rng = cfg.getRngs();
        this.rng.putSeed(seed);
        this.warmupCompletions = cfg.getWarmupCompletions();

        generateBootstrap(cfg);

        this.arrivalGenerator = new ArrivalGenerator(scheduler, arrivalRate, "A", 1, rng);
        this.facade = new EstimatorFacade(network, scheduler, routingMatrix, seed);

        scheduler.subscribe(Event.Type.ARRIVAL, this::onArrival);
        scheduler.subscribe(Event.Type.DEPARTURE, this::onDeparture);

        // Se warmup <= 0, misura da subito
        if (warmupCompletions <= 0) {
            facade.startMeasurement(scheduler);
            measuring = true;
            SysLogger.getInstance().getLogger().info("Warm-up disattivato: misura avviata subito.");
        }
    }

    private void generateBootstrap(SimulationConfig config) {
        for (int i = 0; i < config.getInitialArrival(); i++) {
            Event bootstrapEvent = new BootstrapEvent(0.0, Event.Type.ARRIVAL, "A", -1, 1);
            scheduler.scheduleAt(bootstrapEvent, 0.0);
        }
    }

    private void onArrival(Event e, NextEventScheduler s) {
        Node node = network.getNode(e.getServer());
        if (e.getJobId() == -1) {
            // External arrival
            int cls = e.getJobClass();
            Double meanService = node.getServiceMeans().get(cls);
            if (meanService == null) {
                SysLogger.getInstance().getLogger().severe("Missing service mean for node " + e.getServer() + " class " + cls);
                return;
            }
            double svc = RVMS.idfExponential(meanService, rng.random());
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

    private void onDeparture(Event e, NextEventScheduler s) {
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
                facade.startMeasurement(s);
                measuring = true;
                SysLogger.getInstance().getLogger().info(
                    String.format("Warm-up (completamenti) terminato dopo %d EXIT a t=%.3f s (%.3f h)",
                        totalCompletedJobs, s.getCurrentTime(), s.getCurrentTime() / 3600.0)
                );
            }

            // libera la jobTable per evitare leak
            s.getJobTable().remove(job.getId());
            return;
        }

        String nextNode = tc.serverTarget();
        int nextClass = Integer.parseInt(tc.eventClass());
        job.setJobClass(nextClass);
        Double meanService = network.getNode(nextNode).getServiceMeans().get(nextClass);
        if (meanService == null) {
            SysLogger.getInstance().getLogger().severe("Missing service mean for node " + nextNode + " class " + nextClass);
            return;
        }
        double svc = RVMS.idfExponential(meanService, rng.random());
        job.setRemainingService(svc);

        Event next = new Event(s.getCurrentTime(), Event.Type.ARRIVAL, nextNode, job.getId(), nextClass);
        s.schedule(next);
    }

    private TargetClass lookupRouting(String node, int cls) {
        Map<String, TargetClass> m = routingMatrix.get(node);
        if (m == null) return null;
        return m.get(Integer.toString(cls));
    }

    public void run() {
        int cnt = 0;
        long wallStart = System.nanoTime();

        while (scheduler.hasNext()) {
            scheduler.next();
            cnt++;

            // Stop nuovi arrivi quando superi la soglia eventi (margine +5000)
            if (!arrivalsStopped && cnt >= maxEvents + 5000) {
                arrivalGenerator.setActive(false);
                arrivalsStopped = true;
            }
        }

        if (!measuring) {
            SysLogger.getInstance().getLogger()
                .warning("Warm-up non attivato: statistiche raccolte dall'inizio della simulazione.");
        }

        facade.calculateStats(scheduler, network);

        long wallEnd = System.nanoTime();
        SysLogger.getInstance().getLogger().info(
            String.format("Simulation %d Completed: external=%d, done=%d, took=%f s",
                SIMULATION_COUNTER.incrementAndGet(),
                totalExternalArrivals, totalCompletedJobs, (wallEnd - wallStart) / 1e9)
        );

        Event.reset();
        Job.reset();
    }
}
