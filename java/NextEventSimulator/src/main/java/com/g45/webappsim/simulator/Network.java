package com.g45.webappsim.simulator;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Represents the network of service nodes in the simulation.
 * <p>
 * Each node corresponds to a service station (e.g., "A", "B", "P")
 * and holds the mean service times for different job classes.
 * </p>
 * {@link Node}
 *
 */
public final class Network {

    /**
     * Mapping of node name to {@link Node} instances.
     */
    private final Map<String, Node> nodes = new HashMap<>();

    /**
     * Constructs a network from a service rate configuration.
     * <p>
     * The service rates map has the structure:
     * <pre>
     * {
     *     "A": { "1": 0.2, "2": 0.4, "3": 0.1 },
     *     "B": { "1": 0.8 },
     *     "P": { "2": 0.4 }
     * }
     * </pre>
     * where the outer key is the node name and the inner map
     * contains job-class-to-mean-service-time pairs.
     * </p>
     *
     * @param serviceRates map of node names to their class-specific mean service times
     */
    public Network(@NotNull Map<String, Map<String, Double>> serviceRates) {
        // Convert serviceRates to Integer class → mean time map for each node
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

    /**
     * Retrieves a {@link Node} instance by its name.
     *
     * @param name the name of the node (e.g., "A", "B", "P")
     * @return the corresponding {@link Node} instance, or {@code null} if not found
     */
    public Node getNode(String name) {
        return nodes.get(name);
    }

    /**
     * Returns the set of all node names in the network.
     *
     * @return set of node names
     */
    public Set<String> allNodes() {
        return nodes.keySet();
    }
}
