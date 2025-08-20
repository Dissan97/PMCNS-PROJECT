package com.gforyas.webappsim.lb;

import com.gforyas.webappsim.simulator.Network;
import com.gforyas.webappsim.simulator.NextEventScheduler;
import com.gforyas.webappsim.simulator.Node;
import com.gforyas.webappsim.simulator.TargetClass;

import java.util.Comparator;
import java.util.List;

/**
 * Least-busy load balancer: picks the target whose node currently has the fewest jobs in service.
 * Ties are broken by the natural order of server name (stable).
 */
public class LeastBusyLoadBalance extends LoadBalance {

    @Override
    public TargetClass choose(String currentNode, int jobClass, List<TargetClass> candidates,
                              Network network, NextEventScheduler scheduler) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("Candidates cannot be null or empty");
        }
        return candidates.stream()
                .min(Comparator.<TargetClass>comparingInt(tc -> {
                            Node n = network.getNode(tc.serverTarget());
                            return n != null ? n.inService() : Integer.MAX_VALUE;
                        })
                        .thenComparing(TargetClass::serverTarget))
                .orElse(candidates.getFirst());
    }
}
