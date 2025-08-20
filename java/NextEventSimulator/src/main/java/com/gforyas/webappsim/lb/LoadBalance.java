package com.gforyas.webappsim.lb;

import com.gforyas.webappsim.simulator.NextEventScheduler;
import com.gforyas.webappsim.simulator.Network;
import com.gforyas.webappsim.simulator.TargetClass;

import java.util.List;

/**
 * Abstract load balancer that decides the next TargetClass among multiple candidates.
 * Strategies should be stateless or internally synchronized if shared.
 */
public abstract class LoadBalance {

    /**
     * Choose the next target among candidates for a given (currentNode, jobClass).
     *
     * @param currentNode current node name (e.g., "A", "B")
     * @param jobClass    logical class of the job at routing time
     * @param candidates  non-empty list of routing candidates
     * @param network     network view (can be used to inspect current loads)
     * @param scheduler   scheduler (can be used to read the simulation time if needed)
     * @return the chosen TargetClass
     * @throws IllegalArgumentException if candidates is null or empty
     */
    public abstract TargetClass choose(
            String currentNode,
            int jobClass,
            List<TargetClass> candidates,
            Network network,
            NextEventScheduler scheduler
    );
}
