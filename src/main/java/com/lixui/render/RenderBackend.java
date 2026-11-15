package com.lixui.render;

/**
 * A graphics backend that consumes backend-neutral draw lists.
 * Implementations own their GPU state and may change graphics state while drawing.
 */
public interface RenderBackend extends AutoCloseable {
    /**
     * Sets the size of the render target in pixels.
     * Backends use it to map logical UI coordinates onto the target.
     *
     * @param framebufferWidth target width in pixels
     * @param framebufferHeight target height in pixels
     * @throws IllegalArgumentException if a dimension is not positive
     */
    void resize(int framebufferWidth, int framebufferHeight);

    /**
     * Draws the supplied commands into the currently bound render target.
     * The target size must have been reported with {@link #resize(int, int)}.
     *
     * @param drawList commands to render
     * @throws NullPointerException if {@code drawList} is null
     * @throws IllegalStateException if the target size is unknown
     */
    void draw(DrawList drawList);

    /**
     * Releases GPU resources owned by this backend.
     * The graphics context that created the resources must be current when this method is called.
     */
    @Override
    void close();
}
