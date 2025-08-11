package com.g45.webappsim;

import java.util.logging.Logger;

import com.g45.webappsim.lemer.Rng;
import com.g45.webappsim.lemer.Rngs;
import com.g45.webappsim.logging.SysLogger;
import com.g45.webappsim.simulator.ConfigParser;
import com.g45.webappsim.simulator.Simulation;
import com.g45.webappsim.simulator.SimulationConfig;


public class App {

    static int[] seeds = new int[]{314159265, 271828183, 141421357, 1732584193, 123456789};
    public static final Logger LOGGER = SysLogger.getInstance().getLogger();

    public static void main(String[] args) {
        Rngs rngs = new Rngs();
        SimulationConfig config = ConfigParser.getConfig(ConfigParser.DEFAULT_CONFIG_1);
        config.setRngs(rngs);

        for (int seed : seeds) {
            Simulation simulation = new Simulation(config, seed);
            simulation.run();
        }


    }
}
