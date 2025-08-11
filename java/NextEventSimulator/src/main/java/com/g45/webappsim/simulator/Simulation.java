package com.g45.webappsim.simulator;

import com.g45.webappsim.estimators.EstimatorFacade;
import com.g45.webappsim.lemer.Rngs;
import com.g45.webappsim.lemer.Rvms;
import com.g45.webappsim.logging.SysLogger;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a single Next-Event-Simulation run for the network model.
 * <p>
 * A {@code Simulation} sets up the network topology, arrival process, service rates,
 * routing logic, and runs events until the configured limit is reached.
 * </p>
 * <p>
 * Events are processed using a {@link NextEventScheduler} and are handled
 * by arrival and departure callbacks. Statistics are collected through
 * the {@link EstimatorFacade}.
 * </p>
 */
public class Simulation {

    /**
     * Counter to track how many simulations have been executed.
     */
    private static final AtomicInteger SIMULATION_COUNTER = new AtomicInteger(0);

    /**
     * Shared instance of random variate generators.
     */
    private static final Rvms RVMS = Rvms.getInstance();

    /**
     * Routing matrix mapping from node → (class → target node/class).
     */
    private final Map<String, Map<String, TargetClass>> routingMatrix;

    /**
     * Maximum number of events before stopping arrivals.
     */
    private final int maxEvents;

    /**
     * Event scheduler for managing future events.
     */
    private final NextEventScheduler scheduler = new NextEventScheduler();

    /**
     * Network of service nodes.
     */
    private final Network network;

    /**
     * Random number generator for the simulation.
     */
    private final Rngs rng;

    /**
     * Arrival generator for external Poisson arrivals.
     */
    private final ArrivalGenerator arrivalGenerator;

    /**
     * Total number of external arrivals observed in the simulation.
     */
    private int totalExternalArrivals = 0;

    /**
     * Total number of jobs completed (reached EXIT).
     */
    private int totalCompletedJobs = 0;

    /**
     * Flag to stop new arrivals once event cap is reached.
     */
    private boolean arrivalsStopped = false;

    /**
     * Facade for collecting and computing performance metrics.
     */
    private final EstimatorFacade facade;

    /**
     * Creates a simulation instance with the given configuration and seed.
     *
     * @param cfg  the simulation configuration
     * @param seed the RNG seed
     */
    public Simulation(SimulationConfig cfg, long seed) {
        double arrivalRate = cfg.getArrivalRate();
        Map<String, Map<String, Double>> serviceRates = cfg.getServiceRates();
        this.routingMatrix = cfg.getRoutingMatrix();
        this.maxEvents = cfg.getMaxEvents();
        this.network = new Network(serviceRates);
        this.rng = cfg.getRngs();
        this.rng.putSeed(seed);
        this.arrivalGenerator = new ArrivalGenerator(scheduler, arrivalRate, "A", 1, rng);
        this.facade = new EstimatorFacade(network, scheduler, routingMatrix);
        scheduler.subscribe(Event.Type.ARRIVAL, this::onArrival);
        scheduler.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    /**
     * Handles an arrival event at a node.
     * Creates a new {@link Job} for external arrivals or resumes an existing job.
     *
     * @param e the arrival event
     * @param s the scheduler
     */
    private void onArrival(Event e, NextEventScheduler s) {
        Node node = network.getNode(e.getServer());
        if (e.getJobId() == -1) {
            // External arrival
            int cls = e.getJobClass();
            double meanService = node.getServiceMeans().get(cls);
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

    /**
     * Handles a departure event from a node.
     * Routes the job to the next node or exits the system.
     *
     * @param e the departure event
     * @param s the scheduler
     */
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

    /**
     * Looks up the routing rule for a given node and class.
     *
     * @param node the node name
     * @param cls  the job class
     * @return the target routing information, or {@code null} if none found
     */
    private TargetClass lookupRouting(String node, int cls) {
        Map<String, TargetClass> m = routingMatrix.get(node);
        if (m == null) return null;
        return m.get(Integer.toString(cls));
    }

    /**
     * Runs the simulation until there are no more events to process.
     * Stops new arrivals once {@link #maxEvents} is reached.
     * Collects and logs performance statistics at the end.
     */
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
