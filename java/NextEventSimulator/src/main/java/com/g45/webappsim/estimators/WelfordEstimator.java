package com.g45.webappsim.estimators;

/**
 * Online statistics estimator using Welford's algorithm.
 * <p>
 * This class computes the mean, standard deviation, minimum, and maximum
 * incrementally as new samples are added, without storing all data points.
 * </p>
 *
 * <p>
 * The algorithm is numerically stable and efficient for large datasets.
 * </p>
 */
public class WelfordEstimator {

    /** Number of samples added so far. */
    private long n = 0L;

    /** Current running mean of the samples. */
    private double mean = 0.0;

    /** Sum of squares of differences from the mean (used for variance). */
    private double M2 = 0.0;

    /** Minimum observed value. */
    private double min = Double.POSITIVE_INFINITY;

    /** Maximum observed value. */
    private double max = Double.NEGATIVE_INFINITY;

    /**
     * Adds a new sample to the estimator.
     *
     * @param x the new sample value
     */
    public void add(double x) {
        n += 1;
        double delta = x - mean;
        mean += delta / n;
        M2 += delta * (x - mean);
        if (x < min) min = x;
        if (x > max) max = x;
    }

    /**
     * @return the number of samples processed
     */
    public long getCount() {
        return n;
    }

    /**
     * @return the current mean, or 0.0 if no samples
     */
    public double getMean() {
        return n > 0 ? mean : 0.0;
    }

    /**
     * @return the sample standard deviation, or 0.0 if fewer than 2 samples
     */
    public double getStddev() {
        return n > 1 ? Math.sqrt(M2 / (n - 1)) : 0.0;
    }

    /**
     * @return the minimum observed value, or 0.0 if no samples
     */
    public double getMin() {
        return n > 0 ? min : 0.0;
    }

    /**
     * @return the maximum observed value, or 0.0 if no samples
     */
    public double getMax() {
        return n > 0 ? max : 0.0;
    }

    public void reset() {
        n = 0L;
        mean = 0.0;
        M2 = 0.0;
        min = Double.POSITIVE_INFINITY;
        max = Double.NEGATIVE_INFINITY;
    }
}
