package it.torvergata.ahmed.rand.dist;

import it.torvergata.ahmed.rand.MultiRandomStream;
import it.torvergata.ahmed.rand.Random;

public class BernoulliDistribution implements Distribution {
    private final double p;
    public BernoulliDistribution(double p) {
        if (p < 0 || p > 1) throw new IllegalArgumentException();
        this.p = p;
    }
    @Override
    public double nextDouble(Random r) { return r.nextDouble() < p ? 1.0 : 0.0; }
    @Override
    public double nextMultiDouble(MultiRandomStream ms, int i){return ms.nextDouble(i)<p?1.0:0.0;}
}