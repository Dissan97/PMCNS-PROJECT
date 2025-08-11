package com.g45.webappsim.estimators;

public class WelfordEstimator {
    private long n = 0L;
    private double mean = 0.0;
    private double M2 = 0.0;
    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;

    public void add(double x) {
        n += 1;
        double delta = x - mean;
        mean += delta / n;
        M2 += delta * (x - mean);
        if (x < min) min = x;
        if (x > max) max = x;
    }

    public long getCount() {
        return n;
    }

    public double getMean() {
        return n > 0 ? mean : 0.0;
    }

    public double getStddev() {
        return n > 1 ? Math.sqrt(M2 / (n - 1)) : 0.0;
    }

    public double getMin() {
        return n > 0 ? min : 0.0;
    }

    public double getMax() {
        return n > 0 ? max : 0.0;
    }
}
