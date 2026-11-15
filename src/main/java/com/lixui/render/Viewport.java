package com.lixui.render;

/**
 * A render target description that separates physical framebuffer pixels from logical UI units.
 * Backends map logical coordinates onto the full framebuffer; clipping rectangles are scaled by
 * {@link #scaleX()} and {@link #scaleY()} before being converted to a scissor rectangle.
 *
 * @param framebufferWidth physical framebuffer width in pixels
 * @param framebufferHeight physical framebuffer height in pixels
 * @param logicalWidth logical viewport width in UI units
 * @param logicalHeight logical viewport height in UI units
 */
public record Viewport(int framebufferWidth, int framebufferHeight, int logicalWidth, int logicalHeight) {
    /**
     * Creates a viewport, validating all dimensions.
     *
     * @param framebufferWidth physical framebuffer width in pixels
     * @param framebufferHeight physical framebuffer height in pixels
     * @param logicalWidth logical viewport width in UI units
     * @param logicalHeight logical viewport height in UI units
     * @throws IllegalArgumentException if a dimension is not positive
     */
    public Viewport {
        if (framebufferWidth <= 0 || framebufferHeight <= 0 || logicalWidth <= 0 || logicalHeight <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive.");
        }
    }

    /**
     * Returns the horizontal physical-to-logical scale.
     *
     * @return the positive X scale factor
     */
    public float scaleX() {
        return (float) framebufferWidth / logicalWidth;
    }

    /**
     * Returns the vertical physical-to-logical scale.
     *
     * @return the positive Y scale factor
     */
    public float scaleY() {
        return (float) framebufferHeight / logicalHeight;
    }
}
