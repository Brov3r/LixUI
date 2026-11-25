package com.lixui.demo;

/**
 * Metrics collected by one synchronous stress run.
 *
 * @param scenario scenario that produced the widgets
 * @param count requested stress element count
 * @param widgetCount widget instances in the built subtree, including containers
 * @param warmupIterations discarded iterations executed before measuring
 * @param measuredIterations recorded iterations
 * @param updateFrames update frames per iteration
 * @param buildNanos build and attach durations per iteration
 * @param layoutNanos measure and layout durations per iteration
 * @param updateNanos per-frame update durations across all measured frames
 */
record BenchmarkRunResult(BenchmarkScenario scenario, int count, int widgetCount,
                          int warmupIterations, int measuredIterations, int updateFrames,
                          BenchmarkStats buildNanos, BenchmarkStats layoutNanos,
                          BenchmarkStats updateNanos) {
    /**
     * Returns the median update duration of a single frame.
     *
     * @return median update time in nanoseconds per frame
     */
    double medianUpdateFrameNanos() {
        return updateNanos.median();
    }
}
