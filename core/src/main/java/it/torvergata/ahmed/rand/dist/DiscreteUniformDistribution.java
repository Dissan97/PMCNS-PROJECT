package it.torvergata.ahmed.rand.dist;

import it.torvergata.ahmed.rand.MultiRandomStream;
import it.torvergata.ahmed.rand.RandParameters;
import it.torvergata.ahmed.rand.Random;

/* DiscreteUniformDistribution.java */
public class DiscreteUniformDistribution implements Distribution {
    private final int a;
    private final long span;                // (b - a + 1)

    public DiscreteUniformDistribution(int a, int b) {
        if (b < a) throw new IllegalArgumentException();
        this.a = a; // inclusive bounds
        this.span = (long)b - a + 1;
    }
    @Override
    public double nextDouble(Random rng) {
        // scale 64-bit Lehmer state without bias
        long x;
        long lim = (RandParameters.M / span) * span;
        do { x = rng.next(); } while (x >= lim);
        return a + (double)(x % span);              // returned as double
    }
    @Override
    public double nextMultiDouble(MultiRandomStream ms, int i) {
        long x;
        long lim = (RandParameters.M / span) * span;
        do { x = ms.next(i); } while (x >= lim);
        return a +(double)(x % span);
    }
}
