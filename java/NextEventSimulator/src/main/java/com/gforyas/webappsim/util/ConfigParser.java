package com.gforyas.webappsim.util;

import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.Balancing;
import com.gforyas.webappsim.simulator.SimulationConfig;
import com.gforyas.webappsim.simulator.SimulationType;
import com.gforyas.webappsim.simulator.TargetClass;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

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

    private static final List<String> DEFAULT_CONFIGS = findConfigs();
    public static final String CLASS_EQUAL = " class=";

    /**
     * Returns the default config found in the resources
     *
     * @return List of configs
     */
    public static List<String> getDefaultConfigs() {
        return DEFAULT_CONFIGS;
    }

    private static List<String> findConfigs() {
        List<String> result = new ArrayList<>();

        try {
            // Look for the configs folder inside resources
            String basePath = "";
            URL url = SimulationConfig.class.getClassLoader().getResource(basePath);

            if (url == null) {
                SysLogger.getInstance().getLogger().warning("No configs directory found in resources");
                return Collections.emptyList();
            }

            if (url.getProtocol().equals("file")) {
                // Case: running from IDE or exploded build -> use filesystem path
                Path configDir = Paths.get(url.toURI());
                try (Stream<Path> stream = Files.list(configDir)) {
                    stream.filter(p -> p.toString().endsWith(".json"))
                            .map(p -> p.getFileName().toString())
                            .forEach(result::add);
                }
            } else if (url.getProtocol().equals("jar")) {
                // Case: running from packaged JAR -> scan entries inside the jar
                String jarPath = url.getPath().substring(5, url.getPath().indexOf("!"));
                try (JarFile jar = new JarFile(jarPath)) {
                    jar.stream()
                            .map(JarEntry::getName)
                            .filter(n -> n.startsWith(basePath) && n.endsWith(".json"))
                            .map(n -> n.substring(basePath.length()))
                            .forEach(result::add);
                }
            } else {
                // Any other protocol is unexpected
                SysLogger.getInstance().getLogger()
                        .warning("Unsupported protocol for configs: " + url.getProtocol());
            }
        } catch (Exception e) {
            SysLogger.getInstance().getLogger().severe("Error loading configs: " + e.getMessage());
            return Collections.emptyList();
        }

        return result;
    }

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
        JSONObject serviceJson = jsonObject.getJSONObject(SERVICE_RATES.name().toLowerCase());
        JSONObject routingJson = jsonObject.getJSONObject(ROUTING_MATRIX.name().toLowerCase());

        SimulationConfig config = new SimulationConfig();
        getArrivalRate(jsonObject, config);
        config.setMaxEvents(jsonObject.getInt(MAX_EVENTS.name().toLowerCase()));

        Map<String, Map<String, Double>> serviceMap = new HashMap<>();
        Map<String, Map<String, TargetClass>> routingMap = new HashMap<>();
        Map<String, Map<String, java.util.List<TargetClass>>> routingMapLB = new HashMap<>();

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
        parseMatrix(routingJson, routingMap); // legacy single-target
        // NEW: single entry point, fills BOTH maps (legacy + LB-aware)
        parseMatrix(routingJson, routingMap, routingMapLB);
        parseBatch(jsonObject, config);// new multi-target
        config.setServiceRates(serviceMap);
        config.setRoutingMatrix(routingMap);
        config.setRoutingMatrixLB(routingMapLB);
        checkSimulationType(jsonObject, config);
        return config;
    }

    private static void getArrivalRate(JSONObject jsonObject, SimulationConfig config) {
        List<Double> arrivalRate = new ArrayList<>();
        if (jsonObject.has(ARRIVAL_RATE.name().toLowerCase())) {
            JSONArray arrivalArray = jsonObject.getJSONArray(ARRIVAL_RATE.name().toLowerCase());
            for (int i = 0; i < arrivalArray.length(); i++) {
                arrivalRate.add(arrivalArray.getDouble(i));
            }
            arrivalRate = arrivalRate.stream().sorted(Double::compareTo).toList();
            config.setArrivalRate(arrivalRate);
        }
    }

    private static void parseBatch(@NotNull JSONObject jsonObject, SimulationConfig config) {
        if (jsonObject.has(BATCH_LENGTH.name().toLowerCase())) {
            config.setBatchLength(jsonObject.getInt(BATCH_LENGTH.name().toLowerCase()));
        }
        if (jsonObject.has(MAX_BATCHES.name().toLowerCase())) {
            config.setMaxBatches(jsonObject.getInt(MAX_BATCHES.name().toLowerCase()));
        }
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

            checkForRoutingMatrix(routingMap, node, perClass);
        }

    }

    /**
     * Parse routing_matrix supporting String, JSONObject and JSONArray rules,
     * and fill BOTH maps:
     * - legacyMap: single TargetClass per (node,class) = first candidate
     * - lbMap: full list of candidates per (node,class)
     */
    private static void parseMatrix(
            @NotNull JSONObject routingJson,
            Map<String, Map<String, TargetClass>> legacyMap,
            Map<String, Map<String, java.util.List<TargetClass>>> lbMap) {
        for (String node : routingJson.keySet()) {
            JSONObject perClass = routingJson.getJSONObject(node);

            legacyMap.putIfAbsent(node, new HashMap<>());
            lbMap.putIfAbsent(node, new HashMap<>());

            for (String clsKey : perClass.keySet()) {
                Object rule = perClass.get(clsKey);

                // Normalize any kind of rule to a list of TargetClass
                java.util.List<TargetClass> candidates = parseRuleToList(node, clsKey, rule);

                // Store LB-aware list (immutable copy)
                lbMap.get(node).put(clsKey, java.util.List.copyOf(candidates));

                // Derive legacy single-target by taking the first candidate, if present
                if (!candidates.isEmpty()) {
                    legacyMap.get(node).put(clsKey, candidates.getFirst());
                } else {
                    String warning = "routing_matrix: empty candidate list for node=" + node + CLASS_EQUAL + clsKey;
                    SysLogger.getInstance().getLogger().warning(warning);
                }
            }
        }
    }

    private static void checkForRoutingMatrix(Map<String, Map<String, TargetClass>> routingMap, String node,
                                              JSONObject perClass) {
        try {
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
        } catch (JSONException ignored) {
            // ignored when there is load balance
        }
    }

    /**
     * Normalize a routing rule (String | JSONObject | JSONArray) into a list of
     * TargetClass.
     * - String: "EXIT" (target defaults to current node but ignored on EXIT)
     * - JSONObject: {"target":"B","class":"1"}
     * - JSONArray: mixed list of String/JSONObject, in order
     */
    private static java.util.List<TargetClass> parseRuleToList(
            String node, String clsKey, Object rule) {
        java.util.ArrayList<TargetClass> out = new java.util.ArrayList<>();

        if (rule instanceof String s) {
            // e.g., "EXIT"
            out.add(new TargetClass(node, s));
            return out;
        }

        if (rule instanceof JSONObject obj) {
            String target = obj.getString(JSONKeys.TARGET.name().toLowerCase());
            String clazz = obj.get(JSONKeys.CLASS.name().toLowerCase()).toString();
            out.add(new TargetClass(target, clazz));
            return out;
        }

        if (rule instanceof JSONArray arr) {
            if (arr.isEmpty()) {
                String warning = "routing_matrix: empty array for node=" + node + CLASS_EQUAL + clsKey;
                SysLogger.getInstance().getLogger().warning(warning);

                return out;
            }
            for (int i = 0; i < arr.length(); i++) {
                Object it = arr.get(i);
                switch (it) {
                    case String s2 -> out.add(new TargetClass(node, s2));
                    case JSONObject o -> {
                        String target = o.getString(JSONKeys.TARGET.name().toLowerCase());
                        String clazz = o.get(JSONKeys.CLASS.name().toLowerCase()).toString();
                        out.add(new TargetClass(target, clazz));
                    }
                    default -> {
                        String warning = "routing_matrix: unsupported element type in array for node=" + node +
                                CLASS_EQUAL + clsKey + " → " + it;
                        SysLogger.getInstance().getLogger().warning(warning);
                    }
                }
            }
            return out;
        }
        String severe = "routing_matrix: unsupported rule type for node=" + node + CLASS_EQUAL + clsKey +
                " → " + rule;
        SysLogger.getInstance().getLogger().severe(severe);

        return out;
    }

    private static void checkSimulationType(JSONObject jsonObject, SimulationConfig config) {
        String stKey = JSONKeys.SIMULATION_TYPE.name().toLowerCase();
        if (jsonObject.has(stKey)) {
            String st = jsonObject.getString(stKey);
            try {
                config.setSimulationType(SimulationType.valueOf(st.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                SysLogger.getInstance().getLogger()
                        .warning("Unknown simulation_type='" + st + "', defaulting to NORMAL");
                config.setSimulationType(SimulationType.NORMAL);
            }
        } else {
            config.setSimulationType(SimulationType.NORMAL);
        }

        // --- Load balance policy of LOAD_BALANCE selected ---
        if (config.getSimulationType() == SimulationType.LOAD_BALANCE) {
            String lbKey = JSONKeys.LOAD_BALANCE.name().toLowerCase();
            if (jsonObject.has(lbKey)) {
                JSONObject lb = jsonObject.getJSONObject(lbKey);
                String polKey = JSONKeys.BALANCING.name().toLowerCase();
                String pol = lb.optString(polKey, "RR");
                config.setBalancing(Balancing.parse(pol));
            } else {
                config.setBalancing(Balancing.RR);
            }
        }
    }

    /**
     * Enumeration of JSON key names used in configuration files.
     */
    public enum JSONKeys {
        /**
         * JSON key for the global arrival rate of jobs.
         */
        ARRIVAL_RATE,
        /**
         * JSON key for the service rates of each server and job class.
         */
        SERVICE_RATES,
        /**
         * JSON key for the routing matrix between servers and job classes.
         */
        ROUTING_MATRIX,
        /**
         * JSON key for the maximum number of events to simulate.
         */
        MAX_EVENTS,
        /**
         * JSON key for the target node in routing rules.
         */
        TARGET,
        /**
         * JSON key for the job class in routing rules.
         */
        CLASS,
        INITIAL_ARRIVAL,
        SEEDS,
        WARMUP_COMPLETIONS,
        BATCH_LENGTH,
        MAX_BATCHES,
        SIMULATION_TYPE,
        LOAD_BALANCE,
        BALANCING
    }

}
