package it.torvergata.ahmed.rand.dist;

import it.torvergata.ahmed.rand.MultiRandomStream;
import it.torvergata.ahmed.rand.Random;

/* PoissonDistribution.java  – hybrid Knuth / PTRS */
public class PoissonDistribution implements Distribution {
    private final double lambda;
    private final double sqrtLambda;
    private final double logLambda;
    private final double g;
    public PoissonDistribution(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException();
        this.lambda = lambda;
        this.sqrtLambda = Math.sqrt(lambda);
        this.logLambda  = Math.log(lambda);
        this.g = lambda * Math.exp(-lambda);   // for small-λ inversion
    }

    /* ---------- small λ (<30): Knuth ---------- */
    private long knuth(Random r) {
        long k = 0L;
        double localLambda = Math.exp(-lambda);
        double p = 1.0;
        do { ++k; p *= r.nextDouble(); } while (p > localLambda);
        return k - 1L;
    }

    /* ---------- large λ (>=30): PTRS ---------- */
    private long ptrs(Random r) {
        double c = 0.767 - 3.36 / lambda;
        double beta = Math.PI / Math.sqrt(3.0 * lambda);
        double alpha = beta * lambda;
        double k = Math.log(c) - lambda - Math.log(beta);

        while (true) {
            double u = r.nextDouble();
            double x = (alpha - Math.log((1.0 - u) / u)) / beta;
            int n = (int)Math.floor(x + 0.5);
            if (n < 0) continue;

            double v = r.nextDouble();
            double y = alpha - beta * x;
            double lhs = y + Math.log(v / ((1.0 + Math.exp(y)) * (1.0 + Math.exp(y))));
            double rhs = k + n * Math.log(lambda) - logFactorial(n);
            if (lhs <= rhs) return n;
        }
    }

    private static double logFactorial(int n) {
        return n < 20
                ? LOG_FACT_SMALL[n]
                : (n + 0.5) * Math.log(n) - n + 0.5 * Math.log(2 * Math.PI)
                + 1.0 / (12.0 * n) - 1.0 / (360.0 * n * n * n);
    }
    private static final double[] LOG_FACT_SMALL = {
            0.0,0.0,0.693147,1.791759,3.178054,4.787492,6.579251,8.525161,10.604603,
            12.801827,15.104413,17.502308,19.987214,22.552164,25.191221,27.899272,
            30.671861,33.505073,36.395445,39.339884,42.335616};

    @Override
    public double nextDouble(Random r) {
        return lambda < 30 ? knuth(r) : ptrs(r);
    }
    @Override
    public double nextMultiDouble(MultiRandomStream ms, int i){
        return lambda < 30 ? knuth(new RandomWrapper(ms,i)) : ptrs(new RandomWrapper(ms,i));
    }

    /* Wrap MultiRandomStream so we can reuse the same code path */
    static final class RandomWrapper extends Random {
        private final MultiRandomStream ms; private final int idx;
        RandomWrapper(MultiRandomStream ms,int idx){this.ms=ms;this.idx=idx;}
        @Override
        public long next(){ return (int)(ms.next(idx)); }
        @Override
        public double nextDouble(){ return ms.nextDouble(idx); }
    }

}

