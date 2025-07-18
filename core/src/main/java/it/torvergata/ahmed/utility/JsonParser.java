package it.torvergata.ahmed.utility;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class JsonParser {

    /**
     * Print the means collected from simulations to the desired stream
     *
     * @param out the stream where should be pushed the simulator means
     * @param results are the results that should be reported
     */

    public static void printMeans(OutputStream out, String @NotNull [] results) throws IOException {
        double meanArrival = 0;
        double meanService = 0;
        double meanDelay = 0;
        double meanWait = 0;
        double meanInterArrival = 0;
        double utilization = 0;

        for (String json : results) {
            JSONObject root = new JSONObject(json);

            meanArrival += root.getJSONObject("Mean Arrival").getDouble("Value");
            meanService += root.getJSONObject("Mean Service").getDouble("Value");
            meanDelay += root.getJSONObject("Mean Delay").getDouble("Value");
            meanWait += root.getJSONObject("Mean Wait").getDouble("Value");
            meanInterArrival += root.getJSONObject("Mean InterArrival").getDouble("Value");
            utilization += root.getJSONObject("Utilization").getDouble("Value");

        }
        out.write(String.format("Mean Arrival: %.6f%n", meanArrival / results.length)
                .getBytes(StandardCharsets.UTF_8));


        out.write(String.format("Mean Service: %.6f%n", meanService / results.length)
                .getBytes(StandardCharsets.UTF_8));
        out.write(String.format("Mean Delay: %.6f%n", meanDelay / results.length)
                .getBytes(StandardCharsets.UTF_8));
        out.write(String.format("Mean Wait: %.6f%n", meanWait / results.length)
                .getBytes(StandardCharsets.UTF_8));
        out.write(String.format("Mean InterArrival: %.6f%n", meanInterArrival / results.length)
                .getBytes(StandardCharsets.UTF_8));
        out.write(String.format("Utilization: %.6f%n" , utilization / results.length)
                .getBytes(StandardCharsets.UTF_8));

    }
}
