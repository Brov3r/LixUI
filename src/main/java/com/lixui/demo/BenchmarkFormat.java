package com.lixui.demo;

import java.util.Locale;

/**
 * Formats benchmark values for the demo UI and tests.
 * All numbers use the root locale so decimal separators do not depend on the host locale.
 */
final class BenchmarkFormat {
    /**
     * Prevents instantiation of the formatting helper.
     *
     * @throws UnsupportedOperationException always
     */
    private BenchmarkFormat() {
        throw new UnsupportedOperationException("Utility class.");
    }

    /**
     * Formats a nanosecond duration as milliseconds with two decimals.
     *
     * @param nanos duration in nanoseconds
     * @return the duration in milliseconds without a unit suffix
     */
    static String millis(double nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0);
    }

    /**
     * Formats a count without a fractional part.
     *
     * @param value value to round
     * @return the rounded value as a decimal string
     */
    static String integer(double value) {
        return Long.toString(Math.round(value));
    }

    /**
     * Formats the relative change between two values as a percentage.
     *
     * @param current current value
     * @param previous previous value
     * @return signed percentage with one decimal, or an em dash when the previous value is zero
     */
    static String percentChange(double current, double previous) {
        if (previous == 0) {
            return "—";
        }
        return String.format(Locale.ROOT, "%+.1f%%", (current / previous - 1) * 100);
    }

    /**
     * Converts a mean frame duration into frames per second.
     *
     * @param meanFrameNanos mean frame interval in nanoseconds
     * @return frames per second with one decimal, or {@code 0.0} for a non-positive interval
     */
    static String fps(double meanFrameNanos) {
        if (meanFrameNanos <= 0) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.1f", 1_000_000_000.0 / meanFrameNanos);
    }
}
