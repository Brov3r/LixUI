package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;

/**
 * A frame-stepped stress run that measures build, layout and update phases with
 * {@link System#nanoTime()}.
 * One phase runs per {@link #step()} call, so building ten thousand widgets does not block a single
 * frame. Text, layout and update measurements include real widget operations only; GPU timing is
 * never inferred.
 */
final class BenchmarkRunJob {
    private static final float FRAME_SECONDS = 1f / 60f;
    private static final float MEASURE_HEIGHT = 1_000_000f;
    private static final float FALLBACK_WIDTH = 480;

    private enum Phase {
        /** Creates the stress subtree and attaches it to the host. */
        BUILD,
        /** Measures and lays out the stress subtree. */
        LAYOUT,
        /** Advances the requested number of update frames. */
        UPDATE,
        /** The run has produced its result. */
        FINISHED
    }

    private final Widget host;
    private final BenchmarkScenario scenario;
    private final int count;
    private final int warmupIterations;
    private final int measuredIterations;
    private final int updateFrames;
    private final double[] buildSamples;
    private final double[] layoutSamples;
    private final double[] updateSamples;
    private Phase phase = Phase.BUILD;
    private Widget content;
    private int iteration;
    private BenchmarkRunResult result;

    /**
     * Creates a pending run.
     *
     * @param host attached widget that receives the stress subtree
     * @param scenario scenario to build
     * @param count requested stress element count
     * @param warmupIterations discarded iterations before measurement, at least zero
     * @param measuredIterations recorded iterations, at least one
     * @param updateFrames update frames per iteration, at least one
     * @throws IllegalArgumentException if a count is negative or an iteration count is invalid
     */
    BenchmarkRunJob(Widget host, BenchmarkScenario scenario, int count, int warmupIterations,
                    int measuredIterations, int updateFrames) {
        if (count < 0 || count > scenario.maximumCount()) {
            throw new IllegalArgumentException("Stress count is outside the scenario range.");
        }
        if (warmupIterations < 0) {
            throw new IllegalArgumentException("Warmup iterations must be non-negative.");
        }
        if (measuredIterations <= 0) {
            throw new IllegalArgumentException("At least one measured iteration is required.");
        }
        if (updateFrames <= 0) {
            throw new IllegalArgumentException("At least one update frame is required.");
        }
        this.host = host;
        this.scenario = scenario;
        this.count = count;
        this.warmupIterations = warmupIterations;
        this.measuredIterations = measuredIterations;
        this.updateFrames = updateFrames;
        buildSamples = new double[measuredIterations];
        layoutSamples = new double[measuredIterations];
        updateSamples = new double[measuredIterations * updateFrames];
    }

    /**
     * Advances one measurement phase.
     *
     * @return true when the run has finished
     */
    boolean step() {
        if (phase == Phase.FINISHED) {
            return true;
        }
        switch (phase) {
            case BUILD -> build();
            case LAYOUT -> layout();
            case UPDATE -> update();
            case FINISHED -> {
                return true;
            }
            default -> throw new IllegalStateException("Unknown benchmark phase.");
        }
        return phase == Phase.FINISHED;
    }

    /**
     * Reports whether the run has finished and its result is available.
     *
     * @return true after the final update phase
     */
    boolean isFinished() {
        return phase == Phase.FINISHED;
    }

    /**
     * Returns the completed result.
     *
     * @return the measured result, or null while the run is still active
     */
    BenchmarkRunResult result() {
        return result;
    }

    /**
     * Describes the current progress for the status line.
     *
     * @return a short progress message
     */
    String statusText() {
        if (phase == Phase.FINISHED) {
            return "Run complete.";
        }
        int total = warmupIterations + measuredIterations;
        int current = Math.min(total, iteration + 1);
        String step = switch (phase) {
            case BUILD -> "build";
            case LAYOUT -> "measure + layout";
            case UPDATE -> "update " + updateFrames + " frames";
            case FINISHED -> "done";
        };
        return "Running " + scenario.title() + " " + current + "/" + total + " · " + step;
    }

    private void build() {
        host.removeAll();
        long started = System.nanoTime();
        content = scenario.create(count);
        host.add(content);
        long elapsed = System.nanoTime() - started;
        if (isMeasuredIteration()) {
            buildSamples[measuredIndex()] = elapsed;
        }
        phase = Phase.LAYOUT;
    }

    private void layout() {
        float width = host.bounds().width() > 0 ? host.bounds().width() : FALLBACK_WIDTH;
        long started = System.nanoTime();
        Size natural = content.measure(LayoutConstraints.loose(width, MEASURE_HEIGHT));
        content.layout(new Rect(0, 0, width, natural.height()));
        long elapsed = System.nanoTime() - started;
        if (isMeasuredIteration()) {
            layoutSamples[measuredIndex()] = elapsed;
        }
        phase = Phase.UPDATE;
    }

    private void update() {
        UiContext context = host.context();
        for (int frameIndex = 0; frameIndex < updateFrames; frameIndex++) {
            long started = System.nanoTime();
            float elapsedSeconds = (frameIndex + 1) * FRAME_SECONDS;
            content.update(new FrameInfo(FRAME_SECONDS, elapsedSeconds, frameIndex));
            if (context != null) {
                context.animator().update(FRAME_SECONDS);
            }
            long elapsed = System.nanoTime() - started;
            if (isMeasuredIteration()) {
                updateSamples[measuredIndex() * updateFrames + frameIndex] = elapsed;
            }
        }
        iteration++;
        if (iteration >= warmupIterations + measuredIterations) {
            finish();
        } else {
            phase = Phase.BUILD;
        }
    }

    private void finish() {
        result = new BenchmarkRunResult(scenario, count, countWidgets(content),
                warmupIterations, measuredIterations, updateFrames,
                BenchmarkStats.of(buildSamples), BenchmarkStats.of(layoutSamples),
                BenchmarkStats.of(updateSamples));
        phase = Phase.FINISHED;
    }

    private boolean isMeasuredIteration() {
        return iteration >= warmupIterations;
    }

    private int measuredIndex() {
        return iteration - warmupIterations;
    }

    private static int countWidgets(Widget root) {
        int count = 1;
        for (Widget child : root.children()) {
            count += countWidgets(child);
        }
        return count;
    }
}
