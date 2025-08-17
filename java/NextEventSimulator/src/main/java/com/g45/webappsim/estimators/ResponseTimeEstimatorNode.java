package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.util.HashMap;
import java.util.Map;

/**
 * Node-scoped response time estimator (per visit) that ALSO accumulates
 * per-job total time spent at this physical node.
 *
 * - On ARRIVAL at this node: store start time for this jobId.
 * - On DEPARTURE from this node: compute dt, add to Welford, and accumulate
 *   sumByJob[jobId] += dt. Multiple visits naturally sum (e.g., A visited 3 times).
 *
 * Use takeAndClear(jobId) at EXIT to retrieve the per-job total at this node.
 */
public class ResponseTimeEstimatorNode extends ResponseTimeEstimator {

    private final String node;
    private final Map<Integer, Double> sumByJob = new HashMap<>();
     private boolean collecting = false;

    public ResponseTimeEstimatorNode(NextEventScheduler sched,
                                             String node,
                                             Map<String, Map<String, TargetClass>> routing) {
        super(sched, routing);
        this.node = node;
    }

    @Override
    protected void onArrival(Event e, NextEventScheduler s) {
        if (!collecting) return; 
        if (e.getJobId() >= 0 && node.equals(e.getServer())) {
            // start per-visit
            arr.put(e.getJobId(), e.getTime());
        }
    }

    public void startCollecting() {
        collecting = true;
        arr.clear();
        sumByJob.clear();
        w.reset();
    }
    @Override
    protected void onDeparture(Event e, NextEventScheduler s) {
        if (!collecting) return; 
        if (!node.equals(e.getServer())) return;

        int id = e.getJobId();
        Double t0 = arr.remove(id);
        if (t0 != null) {
            double dt = e.getTime() - t0;
            w.add(dt);                         // keep per-node mean/std as before
            sumByJob.merge(id, dt, Double::sum); // accumulate per job
        }
        // Do NOT call super.onDeparture(e,s) because that filters by EXIT (global logic).
    }

    /** Return and clear the accumulated total time for this job at this node. */
    public double takeAndClear(int jobId) {
        Double v = sumByJob.remove(jobId);
        return (v != null) ? v : 0.0;
    }
}
