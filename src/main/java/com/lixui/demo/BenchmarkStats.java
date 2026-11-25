package com.lixui.demo;

import java.util.Arrays;

/**
 * Summary statistics for one measured phase.
 * Values keep the unit of the samples, so timing phases can stay in nanoseconds while draw calls
 * remain counts.
 *
 * @param min smallest sample
 * @param median middle sample, or the average of the two middle samples
 * @param p95 ninety-fifth percentile using the nearest-rank method
 * @param mean arithmetic mean
 * @param max largest sample
 * @param count number of samples
 */
record BenchmarkStats(double min, double median, double p95, double mean, double max, int count) {
    /**
     * Computes summary statistics for the supplied samples.
     *
     * @param samples measured values, at least one
     * @return statistics for a copy of the samples
     * @throws IllegalArgumentException if no samples are supplied
     */
    static BenchmarkStats of(double[] samples) {
        if (samples.length == 0) {
            throw new IllegalArgumentException("At least one sample is required.");
        }
        double[] sorted = Arrays.copyOf(samples, samples.length);
        Arrays.sort(sorted);
        double sum = 0;
        for (double sample : sorted) {
            sum += sample;
        }
        int middle = sorted.length / 2;
        double median = sorted.length % 2 == 0
                ? (sorted[middle - 1] + sorted[middle]) / 2
                : sorted[middle];
        int percentileIndex = Math.max(0,
                (int) Math.ceil(sorted.length * 0.95) - 1);
        return new BenchmarkStats(sorted[0], median, sorted[percentileIndex],
                sum / sorted.length, sorted[sorted.length - 1], sorted.length);
    }

    /**
     * Computes summary statistics for integer samples.
     *
     * @param samples measured integer values, at least one
     * @return statistics for the supplied samples
     * @throws IllegalArgumentException if no samples are supplied
     */
    static BenchmarkStats of(int[] samples) {
        double[] values = new double[samples.length];
        for (int index = 0; index < samples.length; index++) {
            values[index] = samples[index];
        }
        return of(values);
    }
}
