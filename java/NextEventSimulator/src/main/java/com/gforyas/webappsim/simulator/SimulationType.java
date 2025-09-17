package com.gforyas.webappsim.simulator;

import com.gforyas.webappsim.lb.LeastBusyLoadBalance;
import com.gforyas.webappsim.lb.LoadBalance;
import com.gforyas.webappsim.lb.RandomLoadBalance;
import com.gforyas.webappsim.lb.RoundRobinLoadBalance;

/** Kind of simulation to run. */
public enum SimulationType {
    NORMAL,
    LOAD_BALANCE,
    FIFO;


    /**
     * Class builder for Simulation Object <br>
     * <ul>
     * <li>Simulation with fifo queue policy {@link SimulationType#FIFO}</li>
     * <li>Simulation with load balance policy {@link SimulationType#LOAD_BALANCE} {@link LoadBalance}</li>
     * <li>Simulation with ps queue policy {@link SimulationType#NORMAL} </li>
     * </ul>
     */
    public static class Builder {
        private Builder(){
            throw new UnsupportedOperationException("cannot be instantiated");
        }

        /**
         * Build a Simulation (NORMAL or LOAD_BALANCE) according to SimulationConfig.
         * @param config parsed configuration
         * @param seed   RNG seed to plant
         * @return a ready-to-run Simulation instance
         */
        public static Simulation build(SimulationConfig config, long seed) {
            switch (config.getSimulationType()) {
                case LOAD_BALANCE -> {
                    // LOAD_BALANCE case:
                    LoadBalance policy = switch (config.getBalancing()) {
                        case RR -> new RoundRobinLoadBalance();
                        case RND -> new RandomLoadBalance(config.getRngs()); // pass RNG for uniform choice
                        case LEAST -> new LeastBusyLoadBalance();
                    };
                    return new SimulationLB(config, seed, policy);
                }
                case FIFO -> {
                    return new SimulationFIFO(config);
                }
                default -> {
                    return new Simulation(config);
                }
            }

        }
    }

}
