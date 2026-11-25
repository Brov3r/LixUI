package com.lixui.demo;

/**
 * End-to-end frame metrics collected while the stress scene is visible.
 *
 * @param frames recorded frames
 * @param frameLimit configured frame limit
 * @param frameNanos frame interval statistics in nanoseconds
 * @param drawCalls draw call statistics from the render backend
 */
record BenchmarkLiveMetrics(int frames, int frameLimit, BenchmarkStats frameNanos,
                            BenchmarkStats drawCalls) {
    /**
     * Returns the average frame rate derived from the mean frame interval.
     *
     * @return average frames per second, or zero for an empty capture
     */
    double averageFps() {
        return frameNanos.mean() <= 0 ? 0 : 1_000_000_000.0 / frameNanos.mean();
    }
}
