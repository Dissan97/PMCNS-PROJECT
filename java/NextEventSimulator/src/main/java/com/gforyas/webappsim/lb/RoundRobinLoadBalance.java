package com.gforyas.webappsim.lb;

import com.gforyas.webappsim.simulator.NextEventScheduler;
import com.gforyas.webappsim.simulator.Network;
import com.gforyas.webappsim.simulator.TargetClass;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin load balancer: cycles through candidates per (node,class) key.
 */
public class RoundRobinLoadBalance extends LoadBalance {

    private final Map<String, AtomicInteger> cursors = new ConcurrentHashMap<>();

    private static String key(String node, int cls) {
        return node + "#" + cls;
    }

    @Override
    public TargetClass choose(String currentNode, int jobClass, List<TargetClass> candidates,
                              Network network, NextEventScheduler scheduler) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("Candidates cannot be null or empty");
        }
        String k = key(currentNode, jobClass);
        AtomicInteger idx = cursors.computeIfAbsent(k, s -> new AtomicInteger(0));
        int pos = Math.floorMod(idx.getAndIncrement(), candidates.size());
        return candidates.get(pos);
    }
}
