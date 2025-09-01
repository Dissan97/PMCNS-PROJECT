package com.gforyas.webappsim;

import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.SimulationType;
import com.gforyas.webappsim.util.*;
import com.gforyas.webappsim.simulator.Simulation;
import com.gforyas.webappsim.simulator.SimulationConfig;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
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
public class BVar {

    private static double min = 0.4;
    private static double max = 0.85;
    private static double step = 0.05;
    /**
     * Application logger instance for logging messages to console or file.
     */
    private static final Logger LOGGER = SysLogger.getInstance().getLogger();

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
    public static void main(String[] args) throws URISyntaxException {
        boolean auto = false;
        // If an argument is provided, use it as a config path
        launch(auto);
    }

    private static void launch(boolean auto) throws URISyntaxException {
        // Initialize RNG
        Rngs rngs = new Rngs();
        // Load config from a path
        URL url = SimulationConfig.class.getResource("obj3.json");
        Path p = Paths.get(url.toURI());
        App.setCfgPath(p.toString());
        SimulationConfig config = ConfigParser.getConfig(p.getFileName().toString());
        config.setRngs(rngs);

        // Run simulation for each seed
        String info = App.class.getSimpleName() + ": starting simulation with provided configuration\n" +
                config;
        LOGGER.info(info);
        for (var seed : config.getSeeds()) {
            final int MIN_CENT = (int) Math.round(min  * 100);
    final int MAX_CENT = (int) Math.round(max  * 100);
    final int STEP_CENT = (int) Math.round(step * 100);

    for (int bCent = MIN_CENT; bCent < MAX_CENT; bCent += STEP_CENT) {
        // ricava il double solo qui; es. 80 -> 0.80
        final double bVar = bCent / 100.0d;

        config.getServiceRates().get("B").put("1", bVar);
        SinkToCsv sink = new SinkToCsv(seed, bVar, 1);
        SinkConvergenceToCsv convergenceToCsv = new SinkConvergenceToCsv(seed, bVar, 1);
        config.setSink(sink);
        config.setSinkConv(convergenceToCsv);

        for (var i = 0; i < config.getNumArrivals(); i++) {
            Simulation simulation = SimulationType.Builder.build(config, seed);
            simulation.run();
            Rngs.resetStreamId();
        }
        sink.sink();
        // convergenceToCsv.sink();
    }
        }
    }
}


