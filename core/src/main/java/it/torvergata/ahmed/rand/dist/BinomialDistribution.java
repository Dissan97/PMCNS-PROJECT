package it.torvergata.ahmed.rand.dist;

import it.torvergata.ahmed.rand.MultiRandomStream;
import it.torvergata.ahmed.rand.Random;

/* BinomialDistribution.java  – BTPE */
public class BinomialDistribution implements Distribution {
    private final int n; private final double p, q; private final double npq, fm, p1, c;
    public BinomialDistribution(int n, double p) {
        if (n <= 0 || p < 0 || p > 1) throw new IllegalArgumentException();
        this.n=n; this.p=p; this.q=1-p; this.npq=n*p*q;
        this.fm = n*p + p;                    // mean+1
        double s = Math.sqrt(npq);
        this.p1 = 2.195 * s - 4.6 * q + 0.5;
        this.c  = 0.134 + 20.5 / (15.3 + n);
    }
    @Override
    public double nextDouble(Random r) {
        if (n < 50) {               // fallback: sum of Bernoulli
            int x = 0;
            for (int i=0;i<n;i++) if (r.nextDouble()<p) ++x;
            return x;
        }
        // BTPE main loop
        while (true) {
            double u = r.nextDouble() - 0.5;
            double v = r.nextDouble();
            double us = 0.5 - Math.abs(u);
            int k = (int)Math.floor((2.0 * p1 / us + fm) * u + fm);
            if (k < 0 || k > n) continue;
            double alpha = (npq / (k*(n-k)));
            double rhs = Math.exp(Math.log(v) + Math.log(c) + k*Math.log(p/q) + (n-k)*Math.log(q/p));
            if (us >= 0.07 && v <= alpha) return k;
            /* slower squeeze / acceptance–rejection tests omitted for brevity,
               see Kachitvichyanukul & Schmeiser (1988) */
        }
    }
    @Override
    public double nextMultiDouble(MultiRandomStream ms, int i){
        return nextDouble(new PoissonDistribution.RandomWrapper(ms,i));
    }
}

