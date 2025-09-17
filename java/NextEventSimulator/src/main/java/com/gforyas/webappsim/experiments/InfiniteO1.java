package com.gforyas.webappsim.experiments;

import com.gforyas.webappsim.App;
import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.Simulation;
import com.gforyas.webappsim.simulator.SimulationConfig;
import com.gforyas.webappsim.simulator.SimulationHyperExp;
import com.gforyas.webappsim.util.*;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Logger;

public class InfiniteO1 {


    private static int numPaths = 0;

    private static void launch(String confPath) {
        Logger logger = SysLogger.getInstance().getLogger();
        Rngs rngs = new Rngs();
        // Load config from a path
        SimulationConfig config = ConfigParser.getConfig(confPath);
        SinkToCsv sink = new SinkBatchToCsv(confPath.replace(".json", ".csv"));
        config.setSink(sink);
        config.setRngs(rngs);
        Simulation simulation = new Simulation(config);
        simulation.run();
        //sink.sink();
        Rngs.resetStreamId();
    }


    private static void launchMultiple(String... paths) {
        for (var path : paths) {
            if (path == null) {
                continue;
            }
            App.setCfgPath(path);
            launch(path);
        }
    }

    public static void main(String[] args) {
        String[] paths = new String[4];
        try {
            paths[0] = Objects.requireNonNull(SimulationConfig.class.getResource("obj1_batch.json")).toURI().getPath();
        } catch (URISyntaxException e) {
            SysLogger.getInstance().getLogger().warning(e.getMessage());
        }
        Arrays.stream(paths).forEach(s -> {
            if (s != null)
                numPaths += 1;
        });
        launchMultiple(paths);

    }

}



