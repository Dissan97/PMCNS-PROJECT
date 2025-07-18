package it.torvergata.ahmed;



import it.torvergata.ahmed.des.DesMultiServer;
import it.torvergata.ahmed.des.DesSingleServer;
import it.torvergata.ahmed.des.DesSingleServerConfig;
import it.torvergata.ahmed.rand.dist.Exponential;
import it.torvergata.ahmed.simulator.Simulator;
import it.torvergata.ahmed.utility.JsonParser;
import it.torvergata.ahmed.utility.SimStream;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Main class. Run the app
 */
public class DesTrial {
    /**
     * The main class Constructor. Cannot have an instance.
     */
    private DesTrial() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Main method.
     *
     * @param args the default arguments can be void
     */
    public static void main(String[] args) throws InterruptedException, IOException {

        DesSingleServerConfig simulatorConfig = DesSingleServerConfig.getDefaultConfig(1234, 256);
        simulatorConfig.setLast(100000);

        int threads = 16;
        CountDownLatch latch = new CountDownLatch(threads);
        simulatorConfig.setLatch(latch);
        simulatorConfig.setLast(1000000L);
        int[] services = new int[threads];
        int[] arrivals = new int[threads];
        Simulator[] simulators = new Simulator[threads];
        String[] results = new String[threads];
        for (int i = 0; i < threads; i++) {
            arrivals[i] = i * 2;
            services[i] = (i * 2) + 1;
        }

        for (int i = 0; i < threads; i++) {
            simulatorConfig.setArrivalIndex(arrivals[i]);
            simulatorConfig.setServiceIndex(services[i]);
            simulators[i] = new DesSingleServer(simulatorConfig);
        }
        for (int i = 0; i < threads; i++) {
            new Thread(simulators[i], "Thread#" + i).start();
        }

        latch.await();
        for (int i = 0; i < threads; i++) {
            results[i] = simulators[i].getMetricsToJson();
        }
        SimStream.OUT.println("Single server results:");
        JsonParser.printMeans(SimStream.OUT, results);

        DesSingleServerConfig config = DesSingleServerConfig.getDefaultConfig(1234, 2);
        config.setLast(1000000);
        config.setServiceDistribution(new Exponential(0.5));
        DesMultiServer multiServer = new DesMultiServer(config, 4);
        multiServer.run();
        SimStream.OUT.println("Multi server results:");
        JsonParser.printMeans(SimStream.OUT, new String[] { multiServer.getMetricsToJson() });
        // Print JSON metrics

    }
}
