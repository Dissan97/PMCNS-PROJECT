package it.torvergata.ahmed.simulator;

import it.torvergata.ahmed.rand.MultiRandomStream;
import it.torvergata.ahmed.rand.Random;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CountDownLatch;

/**
 * Configuration class for simulator parameters.
 * Holds all necessary parameters and random number generators for simulation execution.
 */
@Setter
@Getter
public class SimulatorConfig {
    protected double start;
    protected long last;
    protected double arrival;
    protected double delay;
    protected double service;
    protected double wait;
    protected double departure;
    protected Random random;
    protected MultiRandomStream multiRandom;
    protected CountDownLatch latch;

    /**
     * Constructs configuration with a single random number generator.
     */
    public SimulatorConfig() {
        // needed for javadoc
    }

    /**
     * Creates default configuration with specified seed.
     *
     * @return A new SimulatorConfig instance with default values
     */
    public static @NotNull SimulatorConfig getDefaultConfig() {

        SimulatorConfig config = new SimulatorConfig();
        setDefaultValues(config);
        return config;
    }
    protected static void setDefaultValues(@NotNull SimulatorConfig config){
        config.setStart(0.0);
        config.setLast(1000L);
        config.setArrival(0.0);
        config.setDelay(0.0);
        config.setService(0.0);
        config.setWait(0.0);
        config.setDeparture(1.0);
    }

}