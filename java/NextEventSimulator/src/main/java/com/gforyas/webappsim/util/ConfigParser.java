package com.gforyas.webappsim.util;

import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.Balancing;
import com.gforyas.webappsim.simulator.ProbArc; // NEW
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

public class ConfigParser {

    private static final List<String> DEFAULT_CONFIGS = findConfigs();
    public static final String CLASS_EQUAL = " class=";

    public static List<String> getDefaultConfigs() {
        return DEFAULT_CONFIGS;
    }

    private static List<String> findConfigs() {
        List<String> result = new ArrayList<>();
        try {
            String basePath = "";
            URL url = SimulationConfig.class.getResource(basePath);

            if (url == null) {
                SysLogger.getInstance().getLogger().warning("No configs directory found in resources");
                return Collections.emptyList();
            }

            if (url.getProtocol().equals("file")) {
                Path configDir = Paths.get(url.toURI());
                try (Stream<Path> stream = Files.list(configDir)) {
                    stream.filter(p -> p.toString().endsWith(".json"))
                            .map(p -> p.getFileName().toString())
                            .forEach(result::add);
                }
            } else if (url.getProtocol().equals("jar")) {
                String jarPath = url.getPath().substring(5, url.getPath().indexOf("!"));
                try (JarFile jar = new JarFile(jarPath)) {
                    jar.stream()
                            .map(JarEntry::getName)
                            .filter(n -> n.endsWith(".json"))
                            .forEach(result::add);
                }
            } else {
                SysLogger.getInstance().getLogger()
                        .warning("Unsupported protocol for configs: " + url.getProtocol());
            }
        } catch (Exception e) {
            SysLogger.getInstance().getLogger().severe("Error loading configs: " + e.getMessage());
            return Collections.emptyList();
        }
        return result;
    }

    private ConfigParser() {
        throw new UnsupportedOperationException("cannot be instantiated");
    }

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

        // Parse servizi (necessario per validare la tabella probabilistica)
        parseService(serviceJson, serviceMap);

// NEW: routing_mode opzionale
        if (jsonObject.has(ROUTING_MODE.name().toLowerCase())) {
            config.setRoutingMode(jsonObject.getString(ROUTING_MODE.name().toLowerCase()));
        }

// NEW: safety opzionale
        if (jsonObject.has(SAFETY.name().toLowerCase())) {
            JSONObject safety = jsonObject.getJSONObject(SAFETY.name().toLowerCase());
            if (safety.has(MAX_HOPS.name().toLowerCase())) {
                config.setSafetyMaxHops(safety.getInt(MAX_HOPS.name().toLowerCase()));
            }
        }

// NEW: costruisci la tabella probabilistica (auto-detect "p")
        parseProbRouting(routingJson, serviceMap, config);

// ✅ SOLO se NON è probabilistico, popola la mappa legacy single-target
        if (!config.isProbabilistic()) {
            parseMatrix(routingJson, routingMap); // legacy single-target
        }

// LB-aware (lasciare): qui faremo anche un piccolo filtro per gli oggetti con "p"
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

    private static void parseService(@NotNull JSONObject serviceJson, Map<String, Map<String, Double>> serviceMap) {
        for (String k : serviceJson.keySet()) {
            serviceMap.putIfAbsent(k, new HashMap<>());
            JSONObject services = serviceJson.getJSONObject(k);
            for (String k2 : services.keySet()) {
                serviceMap.get(k).put(k2, services.optDouble(k2));
            }
        }
    }

    private static void parseMatrix(@NotNull JSONObject routingJson,
                                    Map<String, Map<String, TargetClass>> routingMap) {
        for (String node : routingJson.keySet()) {
            routingMap.putIfAbsent(node, new HashMap<>());
            JSONObject perClass = routingJson.getJSONObject(node);
            checkForRoutingMatrix(routingMap, node, perClass);
        }
    }

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

                java.util.List<TargetClass> candidates = parseRuleToList(node, clsKey, rule);

                lbMap.get(node).put(clsKey, java.util.List.copyOf(candidates));

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

    private static java.util.List<TargetClass> parseRuleToList(
            String node, String clsKey, Object rule) {
        java.util.ArrayList<TargetClass> out = new java.util.ArrayList<>();

        if (rule instanceof String s) {
            // e.g., "EXIT"
            out.add(new TargetClass(node, s));
            return out;
        }

        if (rule instanceof JSONObject obj) {
            // ✅ NOVITÀ: se è un oggetto probabilistico (ha "p"), lo ignoriamo qui.
            if (obj.has("p")) {
                return out; // vuoto → niente candidato legacy/LB da questo elemento
            }
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
                        // ✅ NOVITÀ: se l'elemento ha "p", è probabilistico → lo ignoriamo qui
                        if (o.has("p")) break;
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


    // ============================================================
    // NEW: parsing della tabella probabilistica (auto-detect + validazione)
    // ============================================================
    private static void parseProbRouting(
            @NotNull JSONObject routingJson,
            Map<String, Map<String, Double>> serviceMap,
            SimulationConfig config
    ) {
        final double TOL = 1e-9;
        boolean anyProbabilistic = false;
        Map<String, Map<Integer, List<ProbArc>>> probTable = new HashMap<>();

        // Se l'utente ha già specificato "routing_mode":"probabilistic", lo memorizziamo,
        // ma comunque facciamo auto-detect verificando la presenza di "p" nelle regole.
        boolean modeFlag = "probabilistic".equalsIgnoreCase(config.getRoutingMode());

        for (String node : routingJson.keySet()) {
            JSONObject perClass = routingJson.getJSONObject(node);

            for (String clsKey : perClass.keySet()) {
                Object rule = perClass.get(clsKey);

                // Caso 1: JSONArray di archi con "p"
                if (rule instanceof JSONArray arr && containsProbEntries(arr)) {
                    List<ProbArc> arcs = parseProbArcArray(node, clsKey, arr, serviceMap);
                    validateSum(node, clsKey, arcs, TOL);
                    probTable.computeIfAbsent(node, k -> new HashMap<>())
                            .put(parseClassToInt(clsKey), arcs);
                    anyProbabilistic = true;
                    continue;
                }

                // Caso 2: JSONObject singolo con "p"
                if (rule instanceof JSONObject obj && obj.has("p")) {
                    ProbArc arc = parseProbArcObject(node, clsKey, obj, serviceMap);
                    List<ProbArc> arcs = new ArrayList<>();
                    arcs.add(arc);
                    validateSum(node, clsKey, arcs, TOL);
                    probTable.computeIfAbsent(node, k -> new HashMap<>())
                            .put(parseClassToInt(clsKey), arcs);
                    anyProbabilistic = true;
                    continue;
                }

                // Altrimenti: non probabilistico per (node,clsKey) → gestito dal legacy.
            }
        }

        if (modeFlag && !anyProbabilistic) {
            String severe = "routing_mode=probabilistic ma nessuna regola con 'p' trovata in routing_matrix";
            SysLogger.getInstance().getLogger().severe(severe);
            System.exit(-1);
        }

        if (anyProbabilistic) {
            // Se l'utente non ha esplicitato la modalità, la impostiamo in auto-detect
            if (config.getRoutingMode() == null) {
                config.setRoutingMode("probabilistic");
            }
            System.out.println(probTable);
            config.setProbRoutingTable(probTable);
        }
    }

    /** Ritorna true se l'array contiene almeno un oggetto con campo "p". */
    private static boolean containsProbEntries(JSONArray arr) {
        for (int i = 0; i < arr.length(); i++) {
            Object it = arr.get(i);
            if (it instanceof JSONObject o && o.has("p")) return true;
        }
        return false;
    }

    /** Parsifica un JSONArray di oggetti con "p" in una lista di ProbArc. */
    private static List<ProbArc> parseProbArcArray(
            String node, String clsKey, JSONArray arr,
            Map<String, Map<String, Double>> serviceMap
    ) {
        List<ProbArc> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            Object it = arr.get(i);
            if (it instanceof JSONObject o && o.has("p")) {
                out.add(parseProbArcObject(node, clsKey, o, serviceMap));
            } else {
                // elementi non probabilistici sono ignorati in questo ramo
                // (restano gestiti dal legacy parseMatrix)
            }
        }
        if (out.isEmpty()) {
            String severe = "routing_matrix probabilistica: array vuoto per node=" + node + CLASS_EQUAL + clsKey;
            SysLogger.getInstance().getLogger().severe(severe);
            System.exit(-1);
        }
        return out;
    }

    /** Parsifica un singolo JSONObject con "p" in ProbArc, con validazioni. */
    private static ProbArc parseProbArcObject(
            String node, String clsKey, JSONObject o,
            Map<String, Map<String, Double>> serviceMap
    ) {
        // target obbligatorio
        if (!o.has(TARGET.name().toLowerCase())) {
            String severe = "routing_matrix probabilistica: manca 'target' per node=" + node + CLASS_EQUAL + clsKey;
            SysLogger.getInstance().getLogger().severe(severe);
            System.exit(-1);
        }
        String target = o.getString(TARGET.name().toLowerCase());

        // p obbligatorio
        double p = o.getDouble("p");
        if (!(p > 0.0)) {
            String severe = "routing_matrix probabilistica: p deve essere > 0 per node=" + node + CLASS_EQUAL + clsKey;
            SysLogger.getInstance().getLogger().severe(severe);
            System.exit(-1);
        }

        // EXIT: class NON deve esserci
        if (SimulationConfig.EXIT.equalsIgnoreCase(target)) {
            if (o.has(CLASS.name().toLowerCase())) {
                String warning = "routing_matrix probabilistica: 'class' ignorata su EXIT per node=" +
                        node + CLASS_EQUAL + clsKey;
                SysLogger.getInstance().getLogger().warning(warning);
            }
            return new ProbArc(target, null, p);
        }

        // target ≠ EXIT → class obbligatoria
        if (!o.has(CLASS.name().toLowerCase())) {
            String severe = "routing_matrix probabilistica: manca 'class' per target=" + target +
                    " node=" + node + CLASS_EQUAL + clsKey;
            SysLogger.getInstance().getLogger().severe(severe);
            System.exit(-1);
        }

        Integer nextClass = parseClassToInt(o.get(CLASS.name().toLowerCase()));
        // Validazione: il nodo e la classe esistono nei service rates
        if (!serviceMap.containsKey(target)) {
            String severe = "routing_matrix probabilistica: target inesistente nei service_rates: " + target;
            SysLogger.getInstance().getLogger().severe(severe);
            System.exit(-1);
        }
        Map<String, Double> classes = serviceMap.get(target);
        if (!classes.containsKey(nextClass.toString())) {
            String severe = "routing_matrix probabilistica: class '" + nextClass + "' assente nei service_rates del nodo " + target;
            SysLogger.getInstance().getLogger().severe(severe);
            System.exit(-1);
        }

        return new ProbArc(target, nextClass, p);
    }

    /** Verifica che la somma delle p sia 1 entro tolleranza; in caso contrario termina. */
    private static void validateSum(String node, String clsKey, List<ProbArc> arcs, double tol) {
        double sum = 0.0;
        for (ProbArc a : arcs) sum += a.getP();
        if (Math.abs(sum - 1.0) > tol) {
            String severe = String.format(Locale.ROOT,
                    "routing_matrix probabilistica: Σp=%.12f ≠ 1 per node=%s class=%s",
                    sum, node, clsKey);
            SysLogger.getInstance().getLogger().severe(severe);
            System.exit(-1);
        }
    }

    /** Converte una chiave di classe (String o Number) a Integer. */
    private static Integer parseClassToInt(Object clsVal) {
        if (clsVal instanceof Number n) return n.intValue();
        return Integer.parseInt(clsVal.toString());
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

    /** Chiavi JSON supportate. */
    public enum JSONKeys {
        ARRIVAL_RATE,
        SERVICE_RATES,
        ROUTING_MATRIX,
        MAX_EVENTS,
        TARGET,
        CLASS,
        INITIAL_ARRIVAL,
        SEEDS,
        WARMUP_COMPLETIONS,
        BATCH_LENGTH,
        MAX_BATCHES,
        SIMULATION_TYPE,
        LOAD_BALANCE,
        BALANCING,
        // NEW:
        ROUTING_MODE,
        SAFETY,
        MAX_HOPS
    }
}
