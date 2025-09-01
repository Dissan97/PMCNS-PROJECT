package com.gforyas.webappsim.simulator.router;

import com.gforyas.webappsim.simulator.TargetClass;

import java.util.Map;

/**
 * Router deterministico: effettua semplice lookup nella mappa legacy
 * (node -> class -> TargetClass).
 */
public class DeterministicRouter implements Router {

    private final Map<String, Map<String, TargetClass>> routingMatrix;

    public DeterministicRouter(Map<String, Map<String, TargetClass>> routingMatrix) {
        this.routingMatrix = routingMatrix;
    }

    @Override
    public TargetClass next(String currentServer, int currentClass, com.gforyas.webappsim.lemer.Rngs rng) {
        if (routingMatrix == null) return null;
        Map<String, TargetClass> perClass = routingMatrix.get(currentServer);
        if (perClass == null) return null;
        return perClass.get(Integer.toString(currentClass));
    }

    @Override
    public boolean isProbabilistic() {
        return false;
    }
}
