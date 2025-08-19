package com.gforyas.webappsim;

import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.SimulationFIFO;
import com.gforyas.webappsim.util.ConfigParser;
import com.gforyas.webappsim.simulator.Simulation;
import com.gforyas.webappsim.simulator.SimulationConfig;
import com.gforyas.webappsim.util.Printer;
import com.gforyas.webappsim.util.UserUi;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
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
     * Application logger instance for logging messages to console or file.
     */
    private static final Logger LOGGER = SysLogger.getInstance().getLogger();

    private static String cfgPath;

    /**
     * Main method that starts the application.
     *2
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
        boolean auto = false;
        // If an argument is provided, use it as a config path
        if (args.length > 0) {
            App.cfgPath = args[0];
            if (!Files.exists(Paths.get(App.cfgPath))) {
                String warning = App.class.getSimpleName() + ": config file does not exist: " + App.cfgPath;
                LOGGER.warning(warning);
                if (userChoose()) return;
            }else {
                auto = true;
            }
        } else {
            if (userChoose()) return;
        }

        launch(auto);
    }

    private static void launch(boolean auto) {
        // Initialize RNG
        Rngs rngs = new Rngs();

        // Load config from a path
        SimulationConfig config = ConfigParser.getConfig(App.cfgPath);
        if (!auto) {
            askUserModification(config);
        } else {
            // Even in auto mode, show a concise start message through our printer
            Printer.out().info("Auto mode: starting simulation with provided configuration.");
        }
        config.setRngs(rngs);

        // Run simulation for each seed
        String info = App.class.getSimpleName() + ": starting simulation with provided configuration\n" +
                config;
        LOGGER.info(info);
        for (var seed : config.getSeeds()) {
            for (var i = 0; i < config.getNumArrivals(); i++){
                Simulation simulation = new SimulationFIFO(config, seed);
                simulation.run();
                Rngs.resetStreamId();
            }
        }
    }

    private static void askUserModification(SimulationConfig config) {
        // Use Scanner(System.in) for input, but only our PersonalPrintStream for output
        try (Scanner scanner = new Scanner(System.in)) {
            UserUi ui = new UserUi(scanner);
            boolean proceed = ui.runInteractiveConfig(config);
            if (!proceed) {
                // If the user aborted, exit the app gracefully
                LOGGER.info("Simulation aborted by user.");
                System.exit(0);
            }
        }
    }

    private static boolean userChoose() {
        // Ask user for config choice
        Scanner scanner = new Scanner(System.in);
        List<String> configs = ConfigParser.getDefaultConfigs();
        StringBuilder builder = new StringBuilder("Select configuration:\n");
        for (int i = 0; i < configs.size(); i++) {
            builder.append(i+1).append(") ").append(configs.get(i)).append("\n");
        }
        int otherSize = configs.size()+1;
        builder.append(otherSize).append(") Custom path" +
                "\nEnter choice [1-").append(otherSize).append("]: ");
        String info = builder.toString();
        LOGGER.info(info);
        try {
            int choice = Integer.parseInt(scanner.nextLine().trim());
            if (choice > 0 && choice <= configs.size()){
                cfgPath = configs.get(choice - 1);
            } else if (choice == configs.size() + 1) {
                LOGGER.info("Enter config file path");
                cfgPath = scanner.nextLine().trim();
                if (!Files.exists(Paths.get(cfgPath))) {
                    String severe = App.class.getSimpleName() + ": config file does not exist: " + cfgPath;
                    LOGGER.severe(severe);
                    return true;
                }
            } else {
                LOGGER.severe("Invalid choice");
                return true;
            }

        }catch (NumberFormatException nfe){
            String severe = "Parse number exception: " + nfe.getMessage();
            LOGGER.severe(severe);
            return true;
        }
        return false;

    }

    public static String getCfgPath() {
        return cfgPath;
    }
}
