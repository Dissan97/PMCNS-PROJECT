package it.torvergata.ahmed.rand.dist;

import it.torvergata.ahmed.rand.Random;
import it.torvergata.ahmed.rand.MultiRandomStream;
import lombok.Getter;
import lombok.Setter;

/**
 * Generates random numbers following a Gaussian (normal) distribution.
 * Uses the Box-Muller transform algorithm to generate normally distributed random numbers
 * with specified mean and standard deviation.
 */
public class Gaussian implements Distribution {
    private boolean hasSpare = false;
    private double spare = 0.0;

    @Getter
    @Setter
    private double mean;
    @Getter
    @Setter
    private double std;

    /**
     * Constructs a Gaussian random number generator.
     *
     * @param mu    The mean (average) of the distribution
     * @param sigma The standard deviation of the distribution
     */
    public Gaussian(double mu, double sigma) {

        this.mean = mu;
        if (sigma < 0.0) {
            throw new IllegalArgumentException("sigma must be non-negative");
        }
        this.std = sigma;
    }

    /**
     * Generates the next Gaussian distributed random number.
     * Uses the Box-Muller transform to convert uniform random numbers
     * to normally distributed random numbers.
     * @param random The random number Generator
     * @return The next Gaussian distributed a random number
     * @see Random
     */
    @Override
    public double nextDouble(Random random) {
        if (hasSpare) {
            hasSpare = false;
            return this.mean + this.std * this.spare;
        }

        hasSpare = true;
        double u;
        double v;
        double s;

        do {
            u = random.nextDouble() * 2.0 - 1.0;
            v = random.nextDouble() * 2.0 - 1.0;
            s = u * u + v * v;
        } while (s >= 1.0 || s == 0.0);

        s = Math.sqrt(-2.0 * Math.log(s) / s);
        spare = v * s;
        return this.mean + this.std * (u * s);
    }
    /**
     * Generates the next Gaussian distributed random number.
     * Uses the Box-Muller transform to convert uniform random numbers
     * to normally distributed random numbers.
     * @param random The RandomMulti number Generator
     * @return The next Gaussian distributed a random number
     * @see Random
     */
    @Override
    public double nextMultiDouble(MultiRandomStream random, int i) {
        if (hasSpare) {
            hasSpare = false;
            return this.mean + this.std * this.spare;
        }

        hasSpare = true;
        double u;
        double v;
        double s;

        do {
            u = random.nextDouble(i) * 2.0 - 1.0;
            v = random.nextDouble(i) * 2.0 - 1.0;
            s = u * u + v * v;
        } while (s >= 1.0 || s == 0.0);

        s = Math.sqrt(-2.0 * Math.log(s) / s);
        spare = v * s;
        return this.mean + this.std * (u * s);
    }


}
