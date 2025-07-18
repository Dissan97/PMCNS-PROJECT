package it.torvergata.ahmed.rand.dist;

import it.torvergata.ahmed.rand.MultiRandomStream;
import it.torvergata.ahmed.rand.Random;

public class GeometricDistribution implements Distribution {
    private final double q;            // q = 1-p > 0
    public GeometricDistribution(double p) {
        if (p <= 0 || p >= 1) throw new IllegalArgumentException();
        this.q = 1.0 - p;
    }
    @Override
    public double nextDouble(Random r) {
        return Math.floor(Math.log(1.0 - r.nextDouble()) / Math.log(q));
    }
    @Override
    public double nextMultiDouble(MultiRandomStream ms, int i){
        return Math.floor(Math.log(1.0 - ms.nextDouble(i)) / Math.log(q));
    }
}