package com.g45.webappsim.simulator;

import com.g45.webappsim.logging.SysLogger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.g45.webappsim.simulator.ConfigParser.JSONKeys.*;

public class ConfigParser {

    public static final String DEFAULT_CONFIG_1 = "obj1.json";
    public static final String DEFAULT_CONFIG_2 = "obj2.json";
    public static final String DEFAULT_CONFIG_3 = "obj3.json";

    private ConfigParser() {
        throw new UnsupportedOperationException("cannot be instantiated");
    }

    public static SimulationConfig getConfig(String path) {
        JSONTokener jsonTokener = new JSONTokener(Objects.requireNonNull(ConfigParser.class.getResourceAsStream(path)));
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

            for (String clsKey : perClass.keySet()) {
                Object rule = perClass.get(clsKey);

                String clazz;    // "1","2","3","EXIT"
                String target;   // node name or arbitrary for EXIT (not used)

                if (rule instanceof String) {
                    // e.g., "EXIT"
                    clazz = (String) rule;
                    target = node; // compatibilità con TargetClass, verrà gestito come EXIT
                } else {
                    // object: { "target": "...", "class": "2" } (oppure class numerica)
                    JSONObject robj = perClass.getJSONObject(clsKey);

                    // target as string
                    target = robj.getString(TARGET.name().toLowerCase());

                    // class can be string or number; normalize to string
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



    public enum JSONKeys {
        ARRIVAL_RATE,
        SERVICE_RATES,
        ROUTING_MATRIX,
        MAX_EVENTS,
        TARGET,
        CLASS
    }

}
