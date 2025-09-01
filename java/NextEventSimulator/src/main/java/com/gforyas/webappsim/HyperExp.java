package com.gforyas.webappsim;

import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.Simulation;
import com.gforyas.webappsim.simulator.SimulationConfig;
import com.gforyas.webappsim.simulator.SimulationHyperExp;
import com.gforyas.webappsim.util.ConfigParser;
import com.gforyas.webappsim.util.SinkConvergenceToCsv;
import com.gforyas.webappsim.util.SinkObj5ToCsv;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Logger;

public class HyperExp {

    private static int numPaths = 0;

    private static void launch(String confPath){
        Logger logger = SysLogger.getInstance().getLogger();
        String msg;
        Rngs rngs = new Rngs();
        // Load config from a path
        SimulationConfig config = ConfigParser.getConfig(confPath);
        config.setRngs(rngs);
        int tot = config.getNumArrivals() * 10 * config.getSeeds().size() * (numPaths > 0 ? numPaths : 1);
        int counter = 1;
        for (var seed : config.getSeeds()) {
            SinkObj5ToCsv sink = new SinkObj5ToCsv(seed);
            SinkConvergenceToCsv convergenceToCsv = new SinkConvergenceToCsv(seed);
            config.setSink(sink);
            config.setSinkConv(convergenceToCsv);

            for (var prob = 1; prob < 10; prob++) {
                for (var arrival = 0; arrival < config.getNumArrivals(); arrival++) {
                    msg = String.format("launch for seed=%d, arrival=%d, probability=%f, run %d / %d", seed, arrival,
                            prob / 10.0, counter++, tot);
                    logger.info(msg);
                    Simulation simulation = new SimulationHyperExp(config, seed, prob / 10.0);
                    simulation.run();
                    Rngs.resetStreamId();
                }
                sink.addProb(prob / 10.0);
            }
            sink.sink();
            convergenceToCsv.sink();
        }
    }




    private static void launchMultiple(String ... paths){
        for (var path : paths) {
            if (path == null){
                continue;
            }
            App.setCfgPath(path);
            launch(path);
        }
    }

    public static void main(String[] args) {
        String[] paths = new String[4];
        try {
            paths[0] = Objects.requireNonNull(SimulationConfig.class.getResource("obj1.json")).toURI().getPath();
            paths[1] =Objects.requireNonNull(SimulationConfig.class.getResource("obj2.json")).toURI().getPath();
            paths[2] =Objects.requireNonNull(SimulationConfig.class.getResource("obj3.json")).toURI().getPath();
            paths[3] =Objects.requireNonNull(SimulationConfig.class.getResource("obj3_enhance.json")).toURI().getPath();
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
