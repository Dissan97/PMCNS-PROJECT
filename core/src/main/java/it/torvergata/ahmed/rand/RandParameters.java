package it.torvergata.ahmed.rand;

/**
 * Class containing parameters for the random number generators.
 */
public class RandParameters {
    /**
     * Constructor. Cannot have an instance.
     */
    private RandParameters(){
        throw new IllegalStateException("Utility class");
    }
    /**
     * Modulus for the Lehmer generator (2^63-1)
     */
    public static final long M = Long.MAX_VALUE;
    /**
     * Multiplier constant for the Lehmer generator
     */
    public static final long A = 3935559000370003845L;
    /**
     * Quotient of M/A for optimization
     */
    public static final long Q = M / A;
    /**
     * Remainder of M/A for optimization
     */
    public static final long R = M % A;
    /**
     * Small number to avoid overflow in multiplication
     */
    public static final double EPSILON = 1e-12;

}
