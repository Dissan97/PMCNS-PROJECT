package com.g45.webappsim.simulator;

/**
 * Represents the routing target for a job after service completion.
 * <p>
 * A {@code TargetClass} defines:
 * <ul>
 *   <li>The next server/node to which the job should be sent.</li>
 *   <li>The class ID (or {@code EXIT}) the job will have at the next node.</li>
 * </ul>
 * This is used in the routing matrix to determine job transitions between nodes.
 * </p>
 *
 * @param serverTarget the name of the target server/node
 * @param eventClass   the class ID as a string, or the special value {@code SimulationConfig.EXIT} to indicate job completion
 */
public record TargetClass(String serverTarget, String eventClass) {
}
