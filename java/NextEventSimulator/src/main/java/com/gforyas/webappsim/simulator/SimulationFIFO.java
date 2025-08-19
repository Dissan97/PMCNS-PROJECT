package com.gforyas.webappsim.simulator;

import java.util.HashMap;
import java.util.Map;

public class SimulationFIFO extends Simulation {

    private final Map<String, NodeFIFO> nodes = new HashMap<>();

    public SimulationFIFO(SimulationConfig cfg, long seed) {
        super(cfg, seed);
        Map<String, Node> oldNodes = this.network.getNodes();

        oldNodes.forEach((id, node) -> nodes.put(id, new NodeFIFO(node.getName(), node.getServiceMeans())));
        oldNodes.clear();
        oldNodes.putAll(nodes);
    }


}
