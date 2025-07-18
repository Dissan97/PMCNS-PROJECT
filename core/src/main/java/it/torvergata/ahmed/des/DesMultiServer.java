package it.torvergata.ahmed.des;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


/**
 * Multi-server discrete event simulator.
 */
public class DesMultiServer extends DesSingleServer {


    private final List<Double> departures;
    private int currentTarget = 0;
    private final int numServers;

    public DesMultiServer(@NotNull DesSingleServerConfig config, int numServers) {
        super(config);
        this.numServers = numServers;

        departures = new ArrayList<>();
        for (int i = 0; i < numServers; i++) {
            departures.add(0.0);
        }
    }


    @Override
    protected void getService() {
        this.service = this.randomStream.nextDist(serviceIndex);
        this.wait = this.delay + this.service;
        this.departure = this.arrival + this.wait;
        this.departures.set(currentTarget, this.departure);
    }

    @Override
    protected void getArrival() {
        double localArrival = this.randomStream.nextDist(arrivalIndex);
        this.arrival = this.lastArrival + localArrival;
        this.lastArrival = this.arrival;
        for (int i = 0; i < numServers; i++) {
            if (this.arrival >= departures.get(i)) {
                currentTarget = i;
                this.delay = 0.0;

                return;
            }
        }
        double min = Double.MAX_VALUE;
        for (int i = 0; i < numServers; i++) {
            if (departures.get(i) < min) {
                min = departures.get(i);
                currentTarget = i;
            }
        }
        this.delay = min - this.arrival;

    }

    /**
     * Prints the statistical means collected during the simulation.
     * This method is called to output simulation results.
     * @return String in JSON format of the metrics collected
     */
    @Override
    public String getMetricsToJson() {

        JSONObject metrics = new JSONObject();

        metrics.put("Mean Arrival", new JSONObject()
                .put(VALUE, this.meanArrivalFrequency)
                .put(METRIC, "jobs/s"));

        metrics.put("Mean Service", new JSONObject()
                .put(VALUE, this.meanService)
                .put(METRIC, "s"));

        metrics.put("Mean Delay", new JSONObject()
                .put(VALUE, this.meanDelay)
                .put(METRIC, "s"));

        metrics.put("Mean Wait", new JSONObject()
                .put(VALUE, this.meanWait)
                .put(METRIC, "s"));

        metrics.put("Mean InterArrival", new JSONObject()
                .put(VALUE, this.meanInterArrival)
                .put(METRIC, "s"));

        // Calcolo utilization con upper bound 1.0
        double utilization = Math.min(this.meanArrivalFrequency * (this.meanService / numServers ), 1.0);
        metrics.put("Utilization", new JSONObject()
                .put(VALUE, utilization)
                .put(METRIC, "ratio"));


        return metrics.toString(2);

    }

}
