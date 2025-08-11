package com.g45.webappsim.simulator;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class Network {
    private final Map<String, Node> nodes = new HashMap<>();

    public Network(@NotNull Map<String, Map<String, Double>> serviceRates) {
        // Convert serviceRates to class->mean map per node
        for (var e : serviceRates.entrySet()) {
            String nodeName = e.getKey();
            Map<Integer, Double> means = new HashMap<>();
            for (var ec : e.getValue().entrySet()) {
                int cls = Integer.parseInt(ec.getKey());
                means.put(cls, ec.getValue());
            }
            nodes.put(nodeName, new Node(nodeName, means));
        }
    }

    public Node getNode(String name) {
        return nodes.get(name);
    }

    public Set<String> allNodes() {
        return nodes.keySet();
    }
}
