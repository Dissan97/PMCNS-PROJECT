package it.torvergata.ahmed.rand;


import it.torvergata.ahmed.rand.dist.Distribution;
import it.torvergata.ahmed.rand.dist.Exponential;
import lombok.Getter;
import lombok.Setter;

import static it.torvergata.ahmed.rand.RandParameters.*;

/**
 * class for Lehmer random number generator implementations.
 * Provides common functionality and structure for generating pseudo-random numbers
 * using the Lehmer algorithm.
 */
public class Random {

    @Getter
    long seed;
    long state = 0L;
    @Getter
    @Setter
    private Distribution distribution;

    /**
     * Constructs a random number generator with the specified seed.
     * @param seed that starts the wheel of pseudo-random generator if
     *             seed less than 0, then seed is set by current time
     * @param distribution the distribution to use for generating random numbers
     */

    public Random(long seed, Distribution distribution) {
        this.seed = seed;
        if (this.seed <= 0) {
            this.seed = System.nanoTime();
        }
        this.distribution = distribution;
        this.resetSeed(M);
    }
    /**
     * Constructs a random number generator with the specified seed.
     * The distribution is set to the Exponential distribution.
     * @param seed that starts the wheel of pseudo-random generator if
     *             seed less than 0, then seed is set by current time
     *
     */
    public Random(long seed) {
        this(seed, new Exponential(1.0));
    }

    /**
     * Constructs a random number generator with the specified distribution.
     * The seed is set by the current time.
     * @param distribution the distribution to use for generating random numbers
     */
    public Random(Distribution distribution) {
        this(System.nanoTime(), distribution);
    }
    /**
     * Default constructor that uses the Exponential distribution.
     * and the seed is set by the current time.
     */
    public Random() {
        this(System.nanoTime(), new Exponential(1.0));
    }

    /**
     * Generates the next random number using Lehmer's algorithm.
     * Implements the formula: X(n+1) = (A * X(n)) mod M
     * using optimization to avoid overflow in multiplication.
     *
     * @return the next random number in the sequence
     */

    public long next() {
        long xDivQ = state / Q;
        long xModQ = state % Q;
        long t = A * xModQ - R * xDivQ;
        if (t > 0) {
            state = t;
        } else {
            state = t + M;
        }
        return state;
    }

    /**
     * Generates the next random number as a double in the range [0,1).
     * Normalizes the integer output from next() by dividing by the modulus.
     *
     * @return a random double between 0.0 (inclusive) and 1.0 (exclusive)
     */
    public double nextDouble() {
        return (double) next() / M;
    }

    /**
     * Generates the next random value based on the specified statistical distribution.
     * The method utilizes the current distribution to produce a random double value.
     *
     * @return the next random double value generated according to the associated distribution
     */
    public double nextDist(){
        return this.distribution.nextDouble(this);
    }

    /**
     * Sets a new seed for the random number generator.
     * Resets the internal state using the new seed value.
     *
     * @param newSeed the new seed value to initialize the generator
     */
    public void putSeed(long newSeed) {
        this.seed = newSeed;
        this.resetSeed(M);
    }
    /**
     * Resets the internal state using the current seed value and modulus.
     * If the resulting state is zero, it is set to 1 to avoid a degenerate sequence.
     *
     * @param m the modulus value used in the Lehmer algorithm
     */
    protected void resetSeed(long m) {
        this.state = (this.seed % m);
        if (this.state == 0){
            this.state = 1;
        }
    }

}
