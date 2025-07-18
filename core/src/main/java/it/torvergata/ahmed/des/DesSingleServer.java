package it.torvergata.ahmed.des;


import it.torvergata.ahmed.rand.MultiRandomStream;
import it.torvergata.ahmed.simulator.Simulator;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

/**
 * A Discrete Event Simulator implementation that extends the base Simulator class.
 * This class handles the simulation of discrete events occurring at specific points in time,
 * managing the scheduling and processing of events in chronological order.
 */
public class DesSingleServer extends Simulator {

    static final String VALUE = "Value";
    static final String METRIC = "Metric";

    /** Multi random stream generator*/
    final MultiRandomStream randomStream;
    /** Last registered arrival */
    double lastArrival = 0.0;
    final int arrivalIndex;
    final int serviceIndex;


    /**
     * Constructs a new DiscreteEventSimulator with the specified configuration.
     * With seed equals to system time and streams of 256 and arrival 1.0 Exponential
     * and service 0.5 Exponential
     */

    public DesSingleServer(){
        this(DesSingleServerConfig.getDefaultConfig());
    }

    /**
     * Constructs a new DiscreteEventSimulator with the specified configuration and latch.
     * This constructor is designed for use in multithreaded environments where synchronization
     * between multiple simulator instances is required.
     *
     * @param config The configuration settings for the simulator must not be null
     */
    public DesSingleServer(@NotNull DesSingleServerConfig config) {
        super(config, config.getLatch());
        this.arrivalIndex = config.getArrivalIndex();
        this.serviceIndex = config.getServiceIndex();
        this.randomStream = config.getMultiRandomStream();
        this.randomStream.addDistribution(this.arrivalIndex, config.getArrivalDistribution());
        this.randomStream.addDistribution(this.serviceIndex, config.getServiceDistribution());
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
            double utilization = Math.min(this.meanArrivalFrequency * this.meanService, 1.0);
            metrics.put("Utilization", new JSONObject()
                    .put(VALUE, utilization)
                    .put(METRIC, "ratio"));


            return metrics.toString(2);

    }


    /**
     * Generates the next service time for an event in the simulation.
     * This method determines how long a service will take when processed.
     */
    @Override
    protected void getService() {
        this.service = this.randomStream.nextDist(serviceIndex);
        this.wait = this.delay + this.service;
        this.departure = this.arrival + this.wait;
    }

    /**
     * Generates the next arrival time for an event in the simulation.
     * This method determines when the next event will arrive in the system.
     */
    @Override
    protected void getArrival() {
        double localArrival = this.randomStream.nextDist(arrivalIndex);
        this.arrival = this.lastArrival + localArrival;
        if (this.arrival < this.departure){
            this.delay = departure - this.arrival;
        }else {
            this.delay = 0.0;
        }
        this.lastArrival = this.arrival;
    }

    /**
     * Executes the discrete event simulation.
     * This method implements the main simulation loop, processing events in chronological order.
     * It can be used in multi-thread environments to run the simulation concurrently.
     */
    @Override
    public void run() {
        var localLast = this.lastSimulation;
        var sum = new Sum();
        while (this.index < localLast){
            this.index++;
            getArrival();
            getService();
            sum.addArrival(this.arrival);
            sum.addDelay(this.delay);
            sum.addWait(this.wait);
            sum.addService(this.service);
        }
        sum.interArrival = (this.arrival - this.startSimulation);

        this.meanService = (sum.service / localLast);
        this.meanDelay = (sum.delay / localLast);
        this.meanInterArrival = (sum.interArrival / localLast);
        this.meanWait = (sum.wait / localLast);
        this.meanArrivalFrequency = (1.0 / this.meanInterArrival);

        if (latch != null){
            latch.countDown();
        }
    }

    /**
     * Container class used by father class for mean calculation goal
     */
    private static class Sum {
        double arrival = 0.0;
        double delay = 0.0;
        double wait = 0.0;
        double service = 0.0;
        double interArrival = 0.0;

        /**
         * Add arrival in the container
         * @param arrival the new arrival time
         */
        void addArrival(double arrival) {
            this.arrival += arrival;
        }

        /**
         * Add delay in the container
         * @param delay the new delay time
         */
        void addDelay(double delay) {
            this.delay += delay;
        }

        /**
         * Add wait in the container
         * @param wait the new wait time
         */
        void addWait(double wait) {
            this.wait += wait;
        }

        /**
         * Add service in the container
         * @param service the new service time
         */
        void addService(double service) {
            this.service += service;
        }

    }

}
