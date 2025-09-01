package com.gforyas.webappsim;

import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.Simulation;
import com.gforyas.webappsim.simulator.SimulationConfig;
import com.gforyas.webappsim.simulator.SimulationCoxian;
import com.gforyas.webappsim.util.ConfigParser;
import com.gforyas.webappsim.util.SinkConvergenceToCsv;
import com.gforyas.webappsim.util.SinkObj5ToCsvCox;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Logger;

public class CoxianMain {

    private static void launch(String confPath, double[][] muList, double[][] pList) {
        App.setCfgPath(confPath);
        Logger logger = SysLogger.getInstance().getLogger();
        Rngs rngs = new Rngs();
        SimulationConfig config = ConfigParser.getConfig(confPath);
        config.setRngs(rngs);

        for (var seed : config.getSeeds()) {
            SinkObj5ToCsvCox sink = new SinkObj5ToCsvCox(seed);
            SinkConvergenceToCsv conv = new SinkConvergenceToCsv(seed);
            config.setSink(sink);
            config.setSinkConv(conv);

            for (int i = 0; i < muList.length; i++) {
                double[] mu = muList[i];
                double[] p = pList[i];

                for (int arrival = 0; arrival < config.getNumArrivals(); arrival++) {
                    String msg = String.format(
                            "launch seed=%d, arrival=%d, Cox(mu=%s, p=%s)",
                            seed, arrival, Arrays.toString(mu), Arrays.toString(p));
                    logger.info(msg);

                    Simulation sim = new SimulationCoxian(config, seed, mu, p);
                    sim.run();
                    Rngs.resetStreamId();
                }

                // Optional: add a column-like tag line end (like PROBABILITY in your sink)

                sink.addCoxParams(mu, p);

            }
            sink.sink();
            conv.sink();
        }
    }

    public static void main(String[] args) {
        String path = null;
        try {
            path = Objects.requireNonNull(SimulationConfig.class.getResource("obj1.json")).toURI().getPath();
        } catch (URISyntaxException e) {
            SysLogger.getInstance().getLogger().warning(e.getMessage());
        }

        // Example: three Coxian settings
        double[][] muList = {
                {3.0, 1.0, 0.5},
                {2.5, 2.5},         // Erlang(2) as special case (p=[1.0])
                {0.8, 5.0, 6.0}
        };
        double[][] pList = {
                {0.6, 0.5},         // p_k implicit 0
                {1.0},              // Erlang(2): always advance to last
                {0.7, 0.3}
        };

        launch(path, muList, pList);
    }
}
