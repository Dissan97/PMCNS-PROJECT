package com.gforyas.webappsim.util;

import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.SimulationConfig;
import com.gforyas.webappsim.simulator.TargetClass;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.gforyas.webappsim.util.ConfigParser.JSONKeys.*;

/**
 * Utility class for parsing JSON configuration files into
 * {@link SimulationConfig} instances.
 * <p>
 * This parser reads configuration files containing arrival rates, service
 * rates, routing matrices,
 * and maximum event limits. It supports both default configuration files
 * packaged with the application
 * and custom file paths provided by the user.
 * </p>
 */
public class ConfigParser {

    /**
     * Default configuration file name for the first simulation scenario.
     * <p>
     * The File is expected to be available on the classpath as a resource.
     * </p>
     */
    public static final String DEFAULT_CONFIG_1 = "obj1.json";

    /**
     * Default configuration file name for the second simulation scenario.
     * <p>
     * The File is expected to be available on the classpath as a resource.
     * </p>
     */
    public static final String DEFAULT_CONFIG_2 = "obj2.json";

    /**
     * Default configuration file name for the third simulation scenario.
     * <p>
     * The File is expected to be available on the classpath as a resource.
     * </p>
     */
    public static final String DEFAULT_CONFIG_3 = "obj3.json";

    /**
     * Private constructor to prevent instantiation.
     *
     * @throws UnsupportedOperationException always thrown when called
     */
    private ConfigParser() {
        throw new UnsupportedOperationException("cannot be instantiated");
    }

    /**
     * Reads and parses a simulation configuration JSON file into a
     * {@link SimulationConfig} object.
     *
     * @param path The resource path to the JSON configuration file.
     * @return A populated {@link SimulationConfig} instance.
     * @throws NullPointerException if the resource is not found.
     */
    public static SimulationConfig getConfig(String path) {
        JSONTokener jsonTokener = null;
        try {
            jsonTokener = new JSONTokener(Objects.requireNonNull(SimulationConfig.class.getResourceAsStream(path)));
        } catch (NullPointerException e) {
            try {
                jsonTokener = new JSONTokener(new FileReader(path));
            } catch (FileNotFoundException fex) {
                String severe = SimulationConfig.class.getSimpleName() + "Path missing: " + path + " Error: "
                        + fex.getMessage();
                SysLogger.getInstance().getLogger().severe(severe);
                System.exit(-1);
            }
        }
        JSONObject jsonObject = new JSONObject(jsonTokener);
        double arrivalRate = jsonObject.getDouble(ARRIVAL_RATE.name().toLowerCase());
        JSONObject serviceJson = jsonObject.getJSONObject(SERVICE_RATES.name().toLowerCase());
        JSONObject routingJson = jsonObject.getJSONObject(ROUTING_MATRIX.name().toLowerCase());

        SimulationConfig config = new SimulationConfig();

        config.setArrivalRate(arrivalRate);
        config.setMaxEvents(jsonObject.getInt(MAX_EVENTS.name().toLowerCase()));

        Map<String, Map<String, Double>> serviceMap = new HashMap<>();
        Map<String, Map<String, TargetClass>> routingMap = new HashMap<>();

        if (jsonObject.has(INITIAL_ARRIVAL.name().toLowerCase())) {
            config.setInitialArrival(jsonObject.getInt(INITIAL_ARRIVAL.name().toLowerCase()));
        }

        if (jsonObject.has(SEEDS.name().toLowerCase())) {
            String info = ConfigParser.class.getSimpleName() + " found seeds parsing the csv";
            SysLogger.getInstance().getLogger().info(info);
            ArrayList<Integer> seeds = new ArrayList<>();
            JSONArray seedsArray = jsonObject.getJSONArray(SEEDS.name().toLowerCase());
            for (int i = 0; i < seedsArray.length(); i++) {
                seeds.add(seedsArray.getInt(i));
            }
            config.setSeeds(seeds);
        }
        if (jsonObject.has(WARMUP_COMPLETIONS.name().toLowerCase())) {
            config.setWarmupCompletions(jsonObject.getInt(WARMUP_COMPLETIONS.name().toLowerCase()));
        }

        parseService(serviceJson, serviceMap);
        parseMatrix(routingJson, routingMap);

        config.setServiceRates(serviceMap);
        config.setRoutingMatrix(routingMap);

        return config;
    }

    /**
     * Parses the {@code service_rates} section of the JSON configuration.
     *
     * @param serviceJson The {@link JSONObject} containing service rate
     *                    definitions.
     * @param serviceMap  The map to populate, where each key is a node name, and
     *                    each value is a map
     *                    of job class IDs to their service rates.
     */
    private static void parseService(@NotNull JSONObject serviceJson, Map<String, Map<String, Double>> serviceMap) {
        for (String k : serviceJson.keySet()) {
            serviceMap.putIfAbsent(k, new HashMap<>());
            JSONObject services = serviceJson.getJSONObject(k);
            for (String k2 : services.keySet()) {
                serviceMap.get(k).put(k2, services.optDouble(k2));
            }
        }
    }

    /**
     * Parses the {@code routing_matrix} section of the JSON configuration.
     *
     * @param routingJson The {@link JSONObject} containing routing rules per node
     *                    and job class.
     * @param routingMap  The map to populate, where each key is a node name, and
     *                    each value is a map
     *                    of job class IDs to their {@link TargetClass} routing
     *                    definition.
     */
    private static void parseMatrix(@NotNull JSONObject routingJson,
            Map<String, Map<String, TargetClass>> routingMap) {
        for (String node : routingJson.keySet()) {
            routingMap.putIfAbsent(node, new HashMap<>());
            JSONObject perClass = routingJson.getJSONObject(node);

            for (String clsKey : perClass.keySet()) {
                Object rule = perClass.get(clsKey);

                String clazz;
                String target;

                if (rule instanceof String theRule) {
                    clazz = theRule;
                    target = node;
                } else {
                    JSONObject targetJson = perClass.getJSONObject(clsKey);
                    target = targetJson.getString(TARGET.name().toLowerCase());
                    Object className = targetJson.get(CLASS.name().toLowerCase());
                    if (className instanceof Number number) {
                        clazz = Integer.toString((number).intValue());
                    } else {
                        clazz = targetJson.getString(CLASS.name().toLowerCase());
                    }
                }

                TargetClass tc = new TargetClass(target, clazz);
                routingMap.get(node).put(clsKey, tc);
            }
        }

    }

    /**
     * Enumeration of JSON key names used in configuration files.
     */
    public enum JSONKeys {
        /** JSON key for the global arrival rate of jobs. */
        ARRIVAL_RATE,
        /** JSON key for the service rates of each server and job class. */
        SERVICE_RATES,
        /** JSON key for the routing matrix between servers and job classes. */
        ROUTING_MATRIX,
        /** JSON key for the maximum number of events to simulate. */
        MAX_EVENTS,
        /** JSON key for the target node in routing rules. */
        TARGET,
        /** JSON key for the job class in routing rules. */
        CLASS,
        INITIAL_ARRIVAL,
        SEEDS,
        WARMUP_COMPLETIONS
    }

}
