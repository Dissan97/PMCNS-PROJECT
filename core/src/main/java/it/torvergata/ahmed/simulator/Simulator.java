package it.torvergata.ahmed.simulator;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CountDownLatch;

/**
 * Abstract base class for simulation implementations.
 * Extends Thread to support concurrent execution of simulations.
 * Maintains simulation statistics and configuration parameters.
 */
@Getter
@Setter
public abstract class Simulator implements Runnable{


    /**
     * Current simulation step counter
     */
    protected long index = 0;
    /**
     * Initial time of the simulation
     */
    protected final double startSimulation;
    /**
     * Number of simulation steps to perform
     */
    protected final long lastSimulation;
    /**
     * Time of customer arrival
     */
    protected double arrival;
    /**
     * Delay time in the queue
     */
    protected double delay;
    /**
     * Service time for the customer
     */
    protected double service;
    /**
     * Total wait time (delay plus service)
     */
    protected double wait;
    /**
     * Time when a customer leaves a system
     */
    protected double departure;
    /**
     * Average mean arrival frequency
     */
    protected double meanArrivalFrequency = 0.0;
    /**
     * Average service time across all customers
     */
    protected double meanService = 0.0;
    /**
     * Average delay time across all customers
     */
    protected double meanDelay = 0.0;
    /**
     * Average wait time across all customers
     */
    protected double meanWait = 0.0;
    /**
     * Average time between customer arrivals
     */
    protected double meanInterArrival = 0.0;

    /**
     * Countdown latch used to coordinate synchronization between thread execution.
     * This latch is likely used to ensure all prerequisite tasks or conditions are completed
     * before proceeding with dependent operations within the simulation.
     * The latch is decremented as tasks are complete and used as a blocking mechanism
     * to pause threads until the latch count reaches zero.
     */
    @Getter
    @Setter
    protected CountDownLatch latch;

    /**
     * Constructs a simulator with the default configuration.
     */
    protected Simulator() {
        this(SimulatorConfig.getDefaultConfig());
    }

    /**
     * Constructs a simulator with the specified configuration.
     *
     * @param config The configuration parameters for the simulation
     */
    protected Simulator(@NotNull SimulatorConfig config) {
        this(config, null);
    }

    /**
     * Constructs a simulator with the specified configuration and countdown latch.
     * This constructor initializes the simulation parameters based on the given configuration
     * and associates a latch for managing concurrent simulation flows.
     *
     * @param config The configuration parameters for the simulation. Contains information
     *               such as start time, last time, arrival rate, delay rate, service rate,
     *               wait rate, and departure rate.
     * @param latch  The countdown latch to be used for synchronization during the simulation.
     *               May be null if not required.
     */
    protected Simulator(@NotNull SimulatorConfig config, CountDownLatch latch) {
        this.startSimulation = config.getStart();
        this.lastSimulation = config.getLast();
        this.arrival = config.getArrival();
        this.delay = config.getDelay();
        this.service = config.getService();
        this.wait = config.getWait();
        this.departure = config.getDeparture();
        this.latch = latch;
    }
    

    /**
     * Prints the mean values calculated during simulation.
     * @return return the collected metrics
     */
    public abstract String getMetricsToJson();

    /**
     * Retrieves the service time for the current simulation step.
     */
    protected abstract void getService();

    /**
     * Retrieves the arrival time for the current simulation step.
     */
    protected abstract void getArrival();


}
