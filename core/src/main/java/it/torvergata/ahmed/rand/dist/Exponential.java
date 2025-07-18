package it.torvergata.ahmed.rand.dist;

import it.torvergata.ahmed.rand.Random;
import it.torvergata.ahmed.rand.MultiRandomStream;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import static it.torvergata.ahmed.rand.RandParameters.EPSILON;

/**
 * Generates random numbers following an exponential distribution.
 * The exponential distribution is characterized by a rate parameter lambda,
 * which determines the shape of the distribution.
 */
@Setter
@Getter
public class Exponential implements Distribution {


    private double lambda;

    /**
     * Constructs an exponential random number generator.
     *
     * @param lambda The rate parameter of the exponential distribution (must be positive)
     */
    public Exponential(double lambda) {
        if (lambda <= 0.0){
            throw new IllegalArgumentException("lambda must be positive");
        }
        this.lambda = lambda;
    }


    /**
     * Generates the next exponentially distributed random number.
     * Uses the inverse transform sampling method.
     * @param random the  Random Number Generator
     * @return The next exponentially distributed random number
     */

    @Override
    public double nextDouble(@NotNull Random random) {
        double step = random.nextDouble();
        if (step >= 1.0 - EPSILON){
            step = 1.0 - EPSILON;
        }
        return -Math.log(1.0 - step) / this.lambda;

    }

    /**
     * Generates the next exponentially distributed random number.
     * Uses the inverse transform sampling method.
     * @param random the  RandomMultiStream Number Generator
     * @return The next exponentially distributed random number
     */
    @Override
    public double nextMultiDouble(@NotNull MultiRandomStream random, int i) {
        double step = random.nextDouble(i);
        if (step >= 1.0 - EPSILON){
            step = 1.0 - EPSILON;
        }
        return -Math.log(1.0 - step) / this.lambda;
    }
}
