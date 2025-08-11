package com.g45.webappsim.simulator;

import com.g45.webappsim.estimators.EstimatorFacade;
import com.g45.webappsim.lemer.Rngs;
import com.g45.webappsim.lemer.Rvms;
import com.g45.webappsim.logging.SysLogger;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Simulation {

    private static final AtomicInteger SIMULATION_COUNTER = new AtomicInteger(0);
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


    public Simulation(SimulationConfig cfg, long seed) {
        double arrivalRate = cfg.getArrivalRate();
        Map<String, Map<String, Double>> serviceRates = cfg.getServiceRates();
        this.routingMatrix = cfg.getRoutingMatrix();
        this.maxEvents = cfg.getMaxEvents();
        this.network = new Network(serviceRates);
        this.rng = cfg.getRngs();
        this.rng.putSeed(seed);
        this.arrivalGenerator = new ArrivalGenerator(scheduler, arrivalRate, "A", 1, rng);
        facade = new EstimatorFacade(network, scheduler, routingMatrix);
        scheduler.subscribe(Event.Type.ARRIVAL, this::onArrival);
        scheduler.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    private void onArrival(Event e, NextEventScheduler s) {
        Node node = network.getNode(e.getServer());
        if (e.getJobId() == -1) {
            int cls = e.getJobClass();
            double meanService = node.getServiceMeans().get(cls);
            double svc = RVMS.idfExponential(meanService, rng.random());
            Job job = new Job(cls, s.getCurrentTime(), svc);
            s.getJobTable().put(job.getId(), job);
            totalExternalArrivals++;
            node.arrival(job, s);
        } else {
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
            return;
        }

        String nextNode = tc.serverTarget();
        int nextClass = Integer.parseInt(tc.eventClass());
        job.setJobClass(nextClass);
        double meanService = network.getNode(nextNode).getServiceMeans().get(nextClass);
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
        long start = System.nanoTime();
        while (scheduler.hasNext()) {
            scheduler.next();
            cnt++;
            if (!arrivalsStopped && cnt >= maxEvents) {
                arrivalGenerator.setActive(false);
                arrivalsStopped = true;
            }
        }
        this.facade.calculateStats(this.scheduler, this.network);
        long end = System.nanoTime();
        long elapsed = end - start;
        String msg = String.format("Simulation %d Completed: external=%d, done=%d, took=%f s",
                SIMULATION_COUNTER.incrementAndGet(),
                totalExternalArrivals,
                totalCompletedJobs,
                elapsed / 1e9);
        SysLogger.getInstance().getLogger().info(msg);

        Event.reset();
        Job.reset();
    }
}