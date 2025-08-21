package com.gforyas.webappsim.lb;

import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.simulator.NextEventScheduler;
import com.gforyas.webappsim.simulator.Network;
import com.gforyas.webappsim.simulator.TargetClass;

import java.util.List;

/**
 * Random load balancer: picks a target uniformly at random.
 */
public class RandomLoadBalance extends LoadBalance {

    private final Rngs rng;
    private final int streamId;

    public RandomLoadBalance(Rngs rng) {
        this.rng = rng;
        this.streamId = Rngs.getStreamId();
    }

    @Override
    public TargetClass choose(String currentNode, int jobClass, List<TargetClass> candidates,
                              Network network, NextEventScheduler scheduler) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("Candidates cannot be null or empty");
        }
        // Uniform in [0,1)
        double u = rng.random(streamId);
        int idx = (int) Math.floor(u * candidates.size());
        if (idx >= candidates.size()) idx = candidates.size() - 1;
        return candidates.get(idx);
    }
}
