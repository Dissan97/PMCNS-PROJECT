package com.g45.webappsim;

import com.g45.webappsim.lemer.Rngs;
import com.g45.webappsim.logging.SysLogger;
import com.g45.webappsim.simulator.ConfigParser;
import com.g45.webappsim.simulator.Simulation;
import com.g45.webappsim.simulator.SimulationConfig;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * Entry point for the Web Application Simulator.
 * <p>
 * This class handles:
 * <ul>
 *     <li>Reading the simulation configuration file from command-line arguments or user input.</li>
 *     <li>Validating the provided configuration file path.</li>
 *     <li>Initializing the random number generator seeds.</li>
 *     <li>Running multiple simulation instances with different seeds.</li>
 * </ul>
 * The simulation is executed using the configuration loaded from the selected or provided JSON file.
 */
public class App {

    /**
     * Predefined seeds used to initialize the random number generator
     * for multiple independent simulation runs.
     */
    private static final int[] SEEDS = new int[]{314159265, 271828183, 141421357, 1732584193, 123456789};

    /**
     * Application logger instance for logging messages to console or file.
     */
    private static final Logger LOGGER = SysLogger.getInstance().getLogger();

    /**
     * Main method that starts the application.
     *
     * <p>Behavior:</p>
     * <ol>
     *     <li>If a command-line argument is provided, it is treated as the configuration file path.</li>
     *     <li>If no arguments are given, the user is prompted to choose from default configs or provide a custom path.</li>
     *     <li>Validates that the configuration file exists before running simulations.</li>
     *     <li>For each predefined seed, a {@link Simulation} is created and executed.</li>
     * </ol>
     *
     * @param args Command-line arguments. The first argument may be a path to the configuration JSON file.
     */
    public static void main(String[] args) {
        String configPath;

        // If argument is provided, use it as config path
        if (args.length > 0) {
            configPath = args[0];
            if (!Files.exists(Paths.get(configPath))) {
                LOGGER.severe("❌ Config file not found: " + configPath);
                return;
            }
        } else {
            // Ask user for config choice
            Scanner scanner = new Scanner(System.in);
            LOGGER.info("Select configuration:" +
                    "\n1) " + ConfigParser.DEFAULT_CONFIG_1 +
                    "\n2) " + ConfigParser.DEFAULT_CONFIG_2 +
                    "\n3) " + ConfigParser.DEFAULT_CONFIG_3 +
                    "\n4) Custom path" +
                    "\nEnter choice [1-4]: ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> configPath = ConfigParser.DEFAULT_CONFIG_1;
                case "2" -> configPath = ConfigParser.DEFAULT_CONFIG_2;
                case "3" -> configPath = ConfigParser.DEFAULT_CONFIG_3;
                case "4" -> {
                    LOGGER.info("Enter config file path");
                    configPath = scanner.nextLine().trim();
                    if (!Files.exists(Paths.get(configPath))) {
                        LOGGER.severe("❌ Config file not found: " + configPath);
                        return;
                    }
                }
                default -> {
                    LOGGER.severe("❌ Invalid choice");
                    return;
                }
            }
        }

        // Initialize RNG
        Rngs rngs = new Rngs();

        // Load config from path
        SimulationConfig config = ConfigParser.getConfig(configPath);
        config.setRngs(rngs);

        // Run simulation for each seed
        for (int seed : SEEDS) {
            Simulation simulation = new Simulation(config, seed);
            simulation.run();
        }
    }
}
