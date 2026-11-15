package com.lixui.widget;

/**
 * Timing information for a single UI frame.
 * Widgets receive the same instance in {@code onUpdate} and {@code onDraw}, so animations can use
 * either the frame delta or the accumulated elapsed time.
 *
 * @param deltaSeconds elapsed time since the previous update in seconds
 * @param elapsedSeconds accumulated time since the manager started in seconds
 * @param frameIndex zero-based index of the current update
 */
public record FrameInfo(float deltaSeconds, double elapsedSeconds, long frameIndex) {
    /** A zero-length frame used before the first update. */
    public static final FrameInfo ZERO = new FrameInfo(0, 0, 0);

    /**
     * Creates frame timing information.
     *
     * @param deltaSeconds elapsed time since the previous update in seconds
     * @param elapsedSeconds accumulated time since the manager started in seconds
     * @param frameIndex zero-based index of the current update
     * @throws IllegalArgumentException if a time is negative or non-finite, or the frame index is negative
     */
    public FrameInfo {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0) {
            throw new IllegalArgumentException("Frame delta must be finite and non-negative.");
        }
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) {
            throw new IllegalArgumentException("Elapsed time must be finite and non-negative.");
        }
        if (frameIndex < 0) {
            throw new IllegalArgumentException("Frame index must be non-negative.");
        }
    }
}
