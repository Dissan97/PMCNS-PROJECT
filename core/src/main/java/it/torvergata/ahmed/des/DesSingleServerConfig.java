package it.torvergata.ahmed.des;


import it.torvergata.ahmed.rand.MultiRandomStream;
import it.torvergata.ahmed.rand.dist.Distribution;
import it.torvergata.ahmed.rand.dist.Exponential;
import it.torvergata.ahmed.simulator.SimulatorConfig;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;


/**
 * This configuration allow to use MachineShopSimulator by allowing the user define seed
 * arrival, service index with their related distributions
 */
@Setter
@Getter
public class DesSingleServerConfig extends SimulatorConfig {


    /**
     * The seed for PseudoRandom Generator
     */
    private long seed;
    /**
     * The arrival index of the stream related distribution value
     */
    private int arrivalIndex;
    /**
     * The service index of the stream related distribution value
     */
    private int serviceIndex;
    /**
     * The arrival distribution
     */
    private Distribution arrivalDistribution;
    /**
     * The service distribution
     */
    private Distribution serviceDistribution;
    /**
     * How many streams should have the MultiRandomStream
     */
    private int streams;
    /**
     * MultiRandomStream generator
     */
    private MultiRandomStream multiRandomStream;


    /**
     * Base constructor for MachineShopConfiguration
     */

    public DesSingleServerConfig() {
        // needed for java doc
    }

    /**
     * Default configuration for MachineShopSimulator
     * <ul>
     * <li>seed to the current time</li>
     * <li>arrival index to 0</li>
     * <li>service index to 1</li>
     * <li>arrival distribution to Exp with mean 1.0</li>
     * <li>service distribution to Exp with mean 0.</li>
     * </ul>
     * @return return the configuration

     */
    public static @NotNull DesSingleServerConfig getDefaultConfig() {
        return getDefaultConfig(System.nanoTime());
    }

    /**
     * Default configuration for MachineShopSimulator by indicating the seed
     * <ul>
     * <li>seed to the current time</li>
     * <li>arrival index to 0</li>
     * <li>service index to 1</li>
     * <li>arrival distribution to Exp with mean 1.0</li>
     * <li>service distribution to Exp with mean 0.</li>
     * </ul>
     * @param seed the seed for MultiRandomGenerator
     * @return return the configuration

     */
    public static @NotNull DesSingleServerConfig getDefaultConfig(long seed){
        return getDefaultConfig(seed,2);
    }
    /**
     * Default configuration for MachineShopSimulator by indicating the seed
     * <ul>
     * <li>seed to the current time</li>
     * <li>arrival index to 0</li>
     * <li>service index to 1</li>
     * <li>arrival distribution to Exp with mean 1.0</li>
     * <li>service distribution to Exp with mean 0.</li>
     * </ul>
     * @param seed the seed for MultiRandomGenerator
     * @param streams number of streams in multi-random generator
     * @return return the configuration

     */
    public static @NotNull DesSingleServerConfig getDefaultConfig(long seed, int streams) {
        DesSingleServerConfig config = new DesSingleServerConfig();
        setDefaultValues(config);
        config.seed = seed;
        config.arrivalIndex = 0;
        config.serviceIndex = 1;
        config.arrivalDistribution = new Exponential(1);
        config.serviceDistribution = new Exponential(2);
        config.streams = streams;
        config.multiRandomStream = new MultiRandomStream(config.seed, config.streams);
        config.latch = null;
        return config;
    }
}
