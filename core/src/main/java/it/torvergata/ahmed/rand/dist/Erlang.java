package it.torvergata.ahmed.rand.dist;

import it.torvergata.ahmed.rand.Random;
import it.torvergata.ahmed.rand.MultiRandomStream;
import org.jetbrains.annotations.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Generates random numbers following an Erlang distribution.
 * The Erlang distribution is a special case of the Gamma distribution
 * with integer shape parameter k and rate λ.
 */
@Getter
@Setter
public class Erlang implements Distribution {

    /**
     * Number of states of exponential
     */
    private int k;          // Shape parameter (must be ≥ 1)
    private final Exponential exponential;

    public Erlang(int k, double lambda) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }
        if (lambda <= 0.0) {
            throw new IllegalArgumentException("lambda must be positive");
        }
        this.k = k;
        this.exponential = new Exponential(lambda);
    }

    @Override
    public double nextDouble(@NotNull Random random) {
        double sum = 0.0;
        for (int i = 0; i < k; i++) {
            sum += exponential.nextDouble(random);
        }
        return sum;
    }

    @Override
    public double nextMultiDouble(@NotNull MultiRandomStream random, int i) {
        double sum = 0.0;
        for (int j = 0; j < k; j++) {
            sum += exponential.nextMultiDouble(random, i);
        }
        return sum;
    }
}
