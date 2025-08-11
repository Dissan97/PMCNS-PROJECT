package com.g45.webappsim.simulator;

import com.g45.webappsim.logging.SysLogger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.g45.webappsim.simulator.ConfigParser.JSONKeys.*;

/**
 * Utility class for parsing JSON configuration files into {@link SimulationConfig} instances.
 * <p>
 * This parser reads configuration files containing arrival rates, service rates, routing matrices,
 * and maximum event limits. It supports both default configuration files packaged with the application
 * and custom file paths provided by the user.
 * </p>
 */
public class ConfigParser {

    /**
     * Default configuration file name for the first simulation scenario.
     * <p>File is expected to be available on the classpath as a resource.</p>
     */
    public static final String DEFAULT_CONFIG_1 = "obj1.json";

    /**
     * Default configuration file name for the second simulation scenario.
     * <p>File is expected to be available on the classpath as a resource.</p>
     */
    public static final String DEFAULT_CONFIG_2 = "obj2.json";

    /**
     * Default configuration file name for the third simulation scenario.
     * <p>File is expected to be available on the classpath as a resource.</p>
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
     * Reads and parses a simulation configuration JSON file into a {@link SimulationConfig} object.
     *
     * @param path The resource path to the JSON configuration file.
     * @return A populated {@link SimulationConfig} instance.
     * @throws NullPointerException if the resource is not found.
     */
    public static SimulationConfig getConfig(String path) {
        JSONTokener jsonTokener = null;
        try {
            jsonTokener = new JSONTokener(Objects.requireNonNull(ConfigParser.class.getResourceAsStream(path)));
        } catch (NullPointerException e) {
            SysLogger.getInstance().getLogger().severe("Path missing: " + path + " Error: " + e.getMessage());
            System.exit(-1);
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

        parseService(serviceJson, serviceMap);
        parseMatrix(routingJson, routingMap);

        config.setServiceRates(serviceMap);
        config.setRoutingMatrix(routingMap);

        return config;
    }

    /**
     * Parses the {@code service_rates} section of the JSON configuration.
     *
     * @param serviceJson The {@link JSONObject} containing service rate definitions.
     * @param serviceMap  The map to populate, where each key is a node name, and each value is a map
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
     * @param routingJson The {@link JSONObject} containing routing rules per node and job class.
     * @param routingMap  The map to populate, where each key is a node name, and each value is a map
     *                    of job class IDs to their {@link TargetClass} routing definition.
     */
    private static void parseMatrix(@NotNull JSONObject routingJson,
                                    Map<String, Map<String, TargetClass>> routingMap) {
        for (String node : routingJson.keySet()) {
            routingMap.putIfAbsent(node, new HashMap<>());
            JSONObject perClass = routingJson.getJSONObject(node);

            for (String clsKey : perClass.keySet()) {
                Object rule = perClass.get(clsKey);

                String clazz;    // Job class ID or "EXIT"
                String target;   // Target node name (ignored for EXIT)

                if (rule instanceof String) {
                    clazz = (String) rule;
                    target = node;
                } else {
                    JSONObject robj = perClass.getJSONObject(clsKey);
                    target = robj.getString(TARGET.name().toLowerCase());
                    Object cobj = robj.get(CLASS.name().toLowerCase());
                    if (cobj instanceof Number) {
                        clazz = Integer.toString(((Number) cobj).intValue());
                    } else {
                        clazz = robj.getString(CLASS.name().toLowerCase());
                    }
                }

                TargetClass tc = new TargetClass(target, clazz);
                routingMap.get(node).put(clsKey, tc);
            }
        }
        SysLogger.getInstance().getLogger().info(routingMap.toString());
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
        CLASS
    }

}
