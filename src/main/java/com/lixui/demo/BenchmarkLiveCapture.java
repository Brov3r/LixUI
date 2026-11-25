package com.lixui.demo;

/**
 * Collects end-to-end frame intervals and draw call counts for a bounded live capture.
 * Frames are accepted only while the owner reports a visible scene. Hiding the scene stops the
 * capture so background tabs cannot distort the result.
 */
final class BenchmarkLiveCapture {
    private final int frameLimit;
    private final double[] frameNanos;
    private final int[] drawCalls;
    private int frames;
    private boolean running;
    private boolean stoppedByHiddenScene;

    /**
     * Creates a capture with a fixed frame limit.
     *
     * @param frameLimit positive maximum number of recorded frames
     * @throws IllegalArgumentException if the limit is not positive
     */
    BenchmarkLiveCapture(int frameLimit) {
        if (frameLimit <= 0) {
            throw new IllegalArgumentException("Frame limit must be positive.");
        }
        this.frameLimit = frameLimit;
        frameNanos = new double[frameLimit];
        drawCalls = new int[frameLimit];
    }

    /**
     * Starts a new capture.
     *
     * @param sceneVisible whether the benchmark scene is currently visible
     * @return true when the capture started, false when the scene is hidden
     */
    boolean start(boolean sceneVisible) {
        if (!sceneVisible) {
            return false;
        }
        frames = 0;
        running = true;
        stoppedByHiddenScene = false;
        return true;
    }

    /**
     * Records one rendered frame when the capture is running and the scene is visible.
     *
     * @param sceneVisible whether the benchmark scene is visible for this frame
     * @param deltaSeconds frame interval in seconds
     * @param drawCallCount draw calls issued by the backend for the frame
     * @return true when a frame was recorded
     */
    boolean addFrame(boolean sceneVisible, float deltaSeconds, int drawCallCount) {
        if (!running) {
            return false;
        }
        if (!sceneVisible) {
            running = false;
            stoppedByHiddenScene = true;
            return false;
        }
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0) {
            return false;
        }
        frameNanos[frames] = deltaSeconds * 1_000_000_000.0;
        drawCalls[frames] = Math.max(0, drawCallCount);
        frames++;
        if (frames >= frameLimit) {
            running = false;
        }
        return true;
    }

    /**
     * Stops the capture without discarding recorded frames.
     *
     * @return metrics for the recorded frames, or null when nothing was recorded
     */
    BenchmarkLiveMetrics stop() {
        running = false;
        return metrics();
    }

    /**
     * Builds metrics from the frames recorded so far.
     *
     * @return metrics, or null when no frame was recorded
     */
    BenchmarkLiveMetrics metrics() {
        if (frames == 0) {
            return null;
        }
        double[] frameSamples = new double[frames];
        int[] drawCallSamples = new int[frames];
        System.arraycopy(frameNanos, 0, frameSamples, 0, frames);
        System.arraycopy(drawCalls, 0, drawCallSamples, 0, frames);
        return new BenchmarkLiveMetrics(frames, frameLimit,
                BenchmarkStats.of(frameSamples), BenchmarkStats.of(drawCallSamples));
    }

    /**
     * Reports whether the capture is still accepting frames.
     *
     * @return true while the capture is running
     */
    boolean isRunning() {
        return running;
    }

    /**
     * Reports whether the last capture stopped because the scene became hidden.
     *
     * @return true when the hidden-scene guard stopped the capture
     */
    boolean wasStoppedByHiddenScene() {
        return stoppedByHiddenScene;
    }

    /**
     * Returns the number of recorded frames.
     *
     * @return recorded frame count
     */
    int frameCount() {
        return frames;
    }

    /**
     * Returns the configured frame limit.
     *
     * @return maximum number of recorded frames
     */
    int frameLimit() {
        return frameLimit;
    }
}
