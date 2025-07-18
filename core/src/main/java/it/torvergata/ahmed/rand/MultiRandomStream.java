package it.torvergata.ahmed.rand;

import it.torvergata.ahmed.rand.dist.Distribution;
import lombok.Getter;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import static it.torvergata.ahmed.rand.RandParameters.*;

/**
 * A multi-stream implementation of the Lehmer pseudo-random number generator.
 * This class manages multiple independent streams of random numbers using the Lehmer algorithm:
 * X(n+1) = A·X(n) mod M
 * Each stream is initialized with seeds that are guaranteed to be far apart in the
 * random number sequence to avoid overlap between streams.
 */
public class MultiRandomStream {

    @Getter
    private long seed;

    private final int streamCount;
    private final long[] states;
    private final Map<Integer, Distribution> distributionMap;

    /**
     * Constructs a new multi-stream random number generator with the specified seed.
     * with a stream of 256
     * If the provided seed is less than or equal to zero, the current system time
     * in nanoseconds will be used as the seed.
     *
     * @param seed the initial seed value for the random number generator
     */
    public MultiRandomStream(long seed) {
        this(seed, 256);
    }
    /**
     * Constructs a new multi-stream random number generator with the specified seed.
     * with a stream of 256
     * If the provided seed is less than or equal to zero, the current system time
     * in nanoseconds will be used as the seed.
     *
     * @param seed the initial seed value for the random number generator
     * @param streamCount the stream size
     */
    public MultiRandomStream(long seed, int streamCount) {
        this.seed = seed <= 0 ? System.nanoTime() : seed;
        if (streamCount < 1) {
            throw new IllegalArgumentException("Stream count must be greater than 0");
        }
        this.streamCount = streamCount;
        this.states = new long[this.streamCount];
        initializeStreams();
        this.distributionMap = new LinkedHashMap<>();
    }

    /**
     * Initializes all streams with distant seeds in the Lehmer cycle.
     * Ensures that streams do not overlap in their number sequence.
     */
    private void initializeStreams() {
        long baseSeed = this.seed % M;
        if (baseSeed <= 0) baseSeed = 1;

        long distance = (M - 1) / streamCount;

        BigInteger bigA = BigInteger.valueOf(A);
        BigInteger bigM = BigInteger.valueOf(M);
        BigInteger bigJump = bigA.modPow(BigInteger.valueOf(distance), bigM);
        BigInteger state = BigInteger.valueOf(baseSeed).multiply(bigA).mod(bigM);

        for (int i = 0; i < streamCount; i++) {
            states[i] = state.longValue();

            // Jump ahead using BigInteger to avoid overflow
            state = state.multiply(bigJump).mod(bigM);
        }
    }


    /**
     * Lehmer next step: X(n+1) = A·X(n) mod M
     */
    private long nextLehmer(long current) {
        long xDivQ = current / Q;
        long xModQ = current % Q;
        long t = A * xModQ - R * xDivQ;
        long next = (t > 0) ? t : t + M;

        // ❗ Enforce range [1, M-1]
        if (next == 0 || next == M) {
            next = 1; // or pick another fixed valid value, but 1 is safe
        }

        return next;
    }


    /**
     * Generates the next random number in the specified stream using Lehmer's algorithm.
     *
     * @param i the index of the stream to use
     * @return the next random number in the sequence for the specified stream
     * @throws IllegalArgumentException if the stream index is out of range
     */
    public long next(int i) {
        if (i < 0 || i >= streamCount) {
            throw new IllegalArgumentException("Stream index must be in range 0-" + (streamCount - 1));
        }
        long state = nextLehmer(states[i]);
        states[i] = state;
        return state;
    }

    /**
     * Generates the next random number as a double in the range [0,1) for the specified stream.
     *
     * @param i the index of the stream to use
     * @return a random double between 0.0 (inclusive) and 1.0 (exclusive)
     * @throws IllegalArgumentException if the stream index is out of range
     */
    public double nextDouble(int i) {
        return (double) next(i) / (double) M;
    }

    /**
     * Associates a probability distribution with a specific stream.
     *
     * @param index        the stream index to associate with the distribution
     * @param distribution the probability distribution to add
     * @throws IllegalArgumentException if the stream index is out of range
     */
    public void addDistribution(int index, Distribution distribution) {
        if (index < 0 || index >= this.streamCount) {
            throw new IllegalArgumentException("Distribution index must be in range 0-" + (streamCount - 1));
        }
        this.distributionMap.put(index, distribution);
    }

    /**
     * Removes the probability distribution associated with a specific stream.
     *
     * @param index the stream index whose distribution should be removed
     * @throws IllegalArgumentException if the stream index is out of range
     */
    public void removeDistribution(int index) {
        if (index < 0 || index >= streamCount) {
            throw new IllegalArgumentException("Distribution index must be in range 0-" + (streamCount - 1));
        }
        this.distributionMap.remove(index);
    }

    /**
     * Generates the next random number according to the distribution associated with the specified stream.
     *
     * @param i the index of the stream to use
     * @return the next random number generated according to the stream's distribution
     * @throws IllegalArgumentException if the stream index is out of range
     * @throws IllegalStateException    if no distribution is set for the specified stream
     */
    public double nextDist(int i) {
        if (i < 0 || i >= streamCount) {
            throw new IllegalArgumentException("Distribution index must be in range 0-" + (streamCount - 1));
        }
        Distribution dist = this.distributionMap.get(i);
        if (dist == null) {
            throw new IllegalStateException("No distribution set for stream " + i);
        }
        return dist.nextMultiDouble(this, i);
    }
}
