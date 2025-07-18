package it.torvergata.ahmed.rand.dist;

import it.torvergata.ahmed.rand.Random;
import it.torvergata.ahmed.rand.MultiRandomStream;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import static it.torvergata.ahmed.rand.RandParameters.EPSILON;

/**
 * General-purpose Hyper-exponential distribution with multiple phases.
 * The distribution is a mixture of exponential distributions where the phase
 * is chosen randomly according to specified probabilities.
 * Each phase has its own rate parameter lambda.
 * Sampling algorithm:
 * 1. Select phase i with probability p_i.
 * 2. Draw an exponential random variable with rate lambda_i.
 * This allows modeling distributions with high variance and heavy tails.
 */
@Getter
@Setter
public class HyperExponential implements Distribution {

    /**
     * Probabilities for each phase (p_i), must sum to 1.
     */
    private final double[] probabilities;

    /**
     * Rate parameters (lambda_i) for each exponential phase, all positive.
     */
    private final double[] lambdas;

    /**
     * Cumulative probabilities computed from {@link #probabilities} for efficient sampling.
     */
    private final double[] cumulativeProbabilities;

    /**
     * Constructs a HyperExponentialMulti distribution.
     *
     * @param probabilities Array of phase probabilities p_i, each in [0,1], summing to 1.
     * @param lambdas Array of positive rate parameters lambda_i for each phase.
     * @throws IllegalArgumentException if input arrays differ in length, are empty,
     *                                  contain invalid values, or probabilities do not sum to 1.
     */
    public HyperExponential(double @NotNull [] probabilities, double @NotNull [] lambdas) {
        if (probabilities.length != lambdas.length) {
            throw new IllegalArgumentException("Probabilities and lambdas arrays must have the same length");
        }
        if (probabilities.length == 0) {
            throw new IllegalArgumentException("Arrays must not be empty");
        }
        double sumP = 0.0;
        for (double p : probabilities) {
            if (p < 0.0 || p > 1.0) throw new IllegalArgumentException("Probabilities must be in [0,1]");
            sumP += p;
        }
        if (Math.abs(sumP - 1.0) > 1e-12) {
            throw new IllegalArgumentException("Probabilities must sum to 1");
        }
        for (double l : lambdas) {
            if (l <= 0.0) throw new IllegalArgumentException("Lambdas must be positive");
        }

        this.probabilities = probabilities.clone();
        this.lambdas = lambdas.clone();

        // Build cumulative distribution for efficient phase sampling
        this.cumulativeProbabilities = new double[probabilities.length];
        double cumulative = 0.0;
        for (int i = 0; i < probabilities.length; i++) {
            cumulative += probabilities[i];
            this.cumulativeProbabilities[i] = cumulative;
        }
    }

    /**
     * Convenience constructor for two-phase hyper exponential distribution.
     * with same lambda
     *
     * @param p probability of phase 1 (must be in [0,1])
     * @param lambda rate parameter for phase 1 (must be positive)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public HyperExponential(double p, double lambda) {
        this(
                new double[] {p, 1.0 - p},
                new double[] {lambda, lambda}
        );
    }
    
    /**
     * Samples the phase index given a uniform random number u in [0,1).
     *
     * @param u Uniform random number in [0,1).
     * @return Index of the sampled phase.
     */
    private int samplePhase(double u) {
        for (int i = 0; i < cumulativeProbabilities.length; i++) {
            if (u < cumulativeProbabilities[i]) {
                return i;
            }
        }
        // Fallback (should not happen if probabilities sum to 1)
        return cumulativeProbabilities.length - 1;
    }

    /**
     * Generates a hyper-exponential random variable using the provided Random stream.
     *
     * @param random Random number generator.
     * @return A sample from the hyper exponential distribution.
     */
    @Override
    public double nextDouble(@NotNull Random random) {
        double uPhase = random.nextDouble();
        double uExp = random.nextDouble();

        if (uExp >= 1.0 - EPSILON) uExp = 1.0 - EPSILON;

        int phase = samplePhase(uPhase);
        double lambda = lambdas[phase];

        return -Math.log(1.0 - uExp) / lambda;
    }

    /**
     * Generates a hyper exponential random variable using the provided MultiRandomStream.
     *
     * @param random MultiRandomStream number generator.
     * @param i Stream index.
     * @return A sample from the hyper exponential distribution.
     */
    @Override
    public double nextMultiDouble(@NotNull MultiRandomStream random, int i) {
        double uPhase = random.nextDouble(i);
        double uExp = random.nextDouble(i);

        if (uExp >= 1.0 - EPSILON) uExp = 1.0 - EPSILON;

        int phase = samplePhase(uPhase);
        double lambda = lambdas[phase];

        return -Math.log(1.0 - uExp) / lambda;
    }
}
