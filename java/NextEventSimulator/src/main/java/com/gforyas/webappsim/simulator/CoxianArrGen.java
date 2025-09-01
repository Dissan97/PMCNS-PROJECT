package com.gforyas.webappsim.simulator;

import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.simulator.arrivals.ArrivalGenerator;

public class CoxianArrGen extends ArrivalGenerator {

    // Separate stream to draw the "phase-advance" Bernoulli decisions
    private final int phaseDecisionStream;

    // Coxian parameters
    private final double[] mu;   // rates μ1..μk
    private final double[] p;    // continuation probs p1..p_{k-1}; implicitly p_k = 0

    /**
     * Constructs a Coxian arrival generator.
     *
     * @param scheduler    Event scheduler
     * @param rate         Kept for compatibility, not used directly by Coxian (mean is implied by mu/p)
     * @param targetNode   Target node name
     * @param jobClass     Class id
     * @param rng          RNGs manager
     * @param mu           Array of exponential rates per phase (length k, all > 0)
     * @param p            Array of phase-advance probabilities (length k-1), last implicit 0
     */
    public CoxianArrGen(NextEventScheduler scheduler, double rate, String targetNode,
                        int jobClass, Rngs rng, double[] mu, double[] p) {
        super(scheduler, rate, targetNode, jobClass, rng);
        this.phaseDecisionStream = Rngs.getStreamId();
        this.mu = mu.clone();
        this.p  = p.clone();
        // Basic validation to fail-fast at config time
        if (mu.length == 0) throw new IllegalArgumentException("Coxian: mu cannot be empty");
        if (p.length != mu.length - 1) throw new IllegalArgumentException("Coxian: p must have length k-1");
        for (double r : mu) if (r <= 0.0) throw new IllegalArgumentException("Coxian: all rates must be > 0");
        for (double q : p)  if (q < 0.0 || q > 1.0) throw new IllegalArgumentException("Coxian: p in [0,1]");
    }

    @Override
    protected void onArrival(Event ev, NextEventScheduler scheduler) {
        if (!active || ev instanceof BootstrapEvent) return;
        if (ev.getJobId() == -1 && targetNode.equals(ev.getServer())) {
            // Draw next inter-arrival from Coxian
            double ia = RVMS.idfCoxian(mu, p, rng, phaseDecisionStream, this.streamId);
            Event nextExternal = new Event(0.0, Event.Type.ARRIVAL, targetNode, -1, jobClass);
            scheduler.scheduleAt(nextExternal, scheduler.getCurrentTime() + ia);
        }
    }
}
