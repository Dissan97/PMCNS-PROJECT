package com.gforyas.webappsim.estimators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utilities to compute batch-means statistics from per-batch values.
 *
 * <p>Two usage styles are supported:</p>
 * <ul>
 *     <li><strong>Single-metric mode</strong>: use the instance API ({@link #add(double)}, {@link #summarize()})
 *         to compute mean-of-means, sample std across batch means, and standard error.</li>
 *     <li><strong>Multi-metric helpers</strong>: use the static helpers that take the full list of
 *         {@link BatchMeansWindow.Result} and produce summaries for the main performance metrics
 *         (response time, population, throughput, utilization). For response time they also provide
 *         a weighted mean (by completions) and a Little's-law-based estimate per batch.</li>
 * </ul>
 *
 * <p><strong>Important</strong>: Classic batch-means assumes equal-length batches. Ensure you
 * rotate batches at fixed time boundaries and drop any partial tail, otherwise the theory is
 * weakened (especially for time-weighted metrics such as population and utilization).</p>
 */
public class BatchMeansSummary {

    /** Container for aggregate statistics (unweighted). */
    public static final class Stats {
        /** Number of batches. */
        public final int batches;
        /** Mean of batch means. */
        public final double meanOfMeans;
        /** Sample standard deviation across batch means (ddof=1). */
        public final double sampleStd;
        /** Standard error of the mean (sampleStd / sqrt(n)). */
        public final double stdError;

        public Stats(int batches, double meanOfMeans, double sampleStd, double stdError) {
            this.batches = batches;
            this.meanOfMeans = meanOfMeans;
            this.sampleStd = sampleStd;
            this.stdError = stdError;
        }
    }

    /**
     * Container for aggregate statistics with frequency weights (e.g., completions).
     *
     * @param batches      Number of batches.
     * @param weightedMean Weighted mean.
     * @param stdError     Approximate standard error. Computed from a weighted sample variance using
     *                     frequency weights and (n-1) degrees of freedom.
     */
        public record WeightedStats(int batches, double weightedMean, double stdError) {
    }

    /**
     * Compact summary for all batch metrics of interest.
     *
     * @param meanResponseTime unweighted mean of batch-RT means
     * @param meanRtWeighted   RT weighted by completions per batch
     * @param meanRtLittle     RT via Little per batch: meanN / X
     * @param totalHours       Sum of batch durations in hours, useful for reporting "simulation_time(H)".
     */
        public record MultiStats(Stats meanResponseTime, WeightedStats meanRtWeighted, Stats meanRtLittle,
                                 Stats meanPopulation, Stats throughput, Stats utilization, double totalHours,
                                 double throughputOverall) {
    }

    // ----------------- Instance API (single metric) -----------------

    private final List<Double> values = new ArrayList<>();

    /** Adds a single per-batch value. */
    public void add(double v) { values.add(v); }

    /** Bulk-add convenience. */
    public void addAll(List<Double> vs) { values.addAll(vs); }

    /** Returns an immutable view of all values. */
    public List<Double> values() { return Collections.unmodifiableList(values); }

    /**
     * Computes aggregate statistics across the collected batch values.
     * If fewer than 2 values are present, the standard deviation and
     * standard error are reported as 0.
     */
    public Stats summarize() {
        return summarizeList(values);
    }

    // ----------------- Static helpers (multi-metric) -----------------

    /** Unweighted summary for a list. */
    public static Stats summarizeList(List<Double> vals) {
        int n = vals.size();
        if (n == 0) return new Stats(0, 0.0, 0.0, 0.0);

        double mean = 0.0;
        for (double v : vals) mean += v;
        mean /= n;

        if (n == 1) return new Stats(1, mean, 0.0, 0.0);

        double s2 = 0.0;
        for (double v : vals) {
            double d = v - mean;
            s2 += d * d;
        }
        s2 /= (n - 1); // unbiased sample variance across batch means
        double std = Math.sqrt(s2);
        double se  = std / Math.sqrt(n);
        return new Stats(n, mean, std, se);
    }

    /**
     * Weighted summary for a list using frequency weights (e.g., number of completions per batch).
     * The returned standard error is an approximation based on a weighted sample variance.
     */
    public static WeightedStats summarizeWeighted(List<Double> vals, List<Double> weights) {
        int n = vals.size();
        if (n == 0) return new WeightedStats(0, 0.0, 0.0);

        double wSum = 0.0;
        double wMeanAcc = 0.0;
        for (int i = 0; i < n; i++) {
            double w = Math.max(0.0, i < weights.size() ? weights.get(i) : 0.0);
            wSum += w;
            wMeanAcc += w * vals.get(i);
        }
        if (wSum <= 0.0) return new WeightedStats(n, 0.0, 0.0);

        double mean = wMeanAcc / wSum;

        // Weighted sample variance with frequency weights; approx ddof=(n-1)
        double s2wNum = 0.0;
        for (int i = 0; i < n; i++) {
            double w = Math.max(0.0, i < weights.size() ? weights.get(i) : 0.0);
            double d = vals.get(i) - mean;
            s2wNum += w * d * d;
        }
        double var = (n > 1) ? (s2wNum / (wSum * (n - 1))) : 0.0;
        double se  = Math.sqrt(Math.max(0.0, var));
        return new WeightedStats(n, mean, se);
    }

    /**
     * Produces a consolidated summary for the standard batch metrics using the list of
     * per-batch results produced by {@link BatchMeansWindow}.
     *
     * <ul>
     *   <li><strong>mean_response_time</strong>: unweighted batch-means over the batch RT means.</li>
     *   <li><strong>mean_response_time_weighted</strong>: weighted by estimated completions per batch.</li>
     *   <li><strong>RT via Little</strong>: for each batch, T_b = meanN_b / X_b (if X_b&gt;0), then batch-means.</li>
     *   <li><strong>mean_population</strong>, <strong>throughput</strong>, <strong>utilization</strong>: unweighted batch-means.</li>
     *   <li><strong>totalHours</strong>: sum of batch durations divided by 3600.</li>
     * </ul>
     */
    public static MultiStats summarizeAll(List<BatchMeansWindow.Result> results) {
        List<Double> rt = new ArrayList<>();
        List<Double> rtLittle = new ArrayList<>();
        List<Double> meanN = new ArrayList<>();
        List<Double> thr = new ArrayList<>();
        List<Double> util = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double totalSeconds = 0.0;
        double totalComps   = 0.0;

        for (var r : results) {
            double len = Math.max(0.0, r.tEnd - r.tStart);
            totalSeconds += len;
            totalComps   += r.completions;
            rt.add(r.meanRt);
            meanN.add(r.meanN);
            thr.add(r.throughput);
            util.add(r.utilization);

            // Weight ≈ completions in the batch (throughput × length)
            double comps = r.throughput > 0.0 ? r.throughput * len : 0.0;
            weights.add(comps);

            // Little per batch (only if throughput > 0)
            if (r.throughput > 0.0) {
                rtLittle.add(r.meanN / r.throughput);
            }
        }

        Stats rtStats        = summarizeList(rt);
        WeightedStats rtW    = summarizeWeighted(rt, weights);
        Stats rtLittleStats  = summarizeList(rtLittle);
        Stats nStats         = summarizeList(meanN);
        Stats xStats         = summarizeList(thr);
        Stats uStats         = summarizeList(util);
        double hours         = totalSeconds / 3600.0;
        double throughputOverall = (totalSeconds > 0.0) ? (totalComps / totalSeconds) : 0.0;
        return new MultiStats(rtStats, rtW, rtLittleStats, nStats, xStats, uStats, hours, throughputOverall);
    }
}
