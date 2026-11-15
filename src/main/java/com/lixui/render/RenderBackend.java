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
     * Creates a texture from CPU image data and transfers it to the GPU.
     * The backend owns the returned texture until {@link #destroyTexture(Texture)} or {@link #close()}.
     *
     * @param image RGBA8 image data with a top-left origin
     * @return the created texture
     * @throws NullPointerException if {@code image} is null
     */
    Texture createTexture(ImageData image);

    /**
     * Releases a texture created by this backend.
     * Wrapped host textures are managed by the host and are ignored by this method.
     *
     * @param texture texture to release
     * @throws NullPointerException if {@code texture} is null
     * @throws IllegalArgumentException if the texture belongs to another backend
     */
    void destroyTexture(Texture texture);

    /**
     * Releases GPU resources owned by this backend, including textures created through
     * {@link #createTexture(ImageData)}.
     * The graphics context that created the resources must be current when this method is called.
     */
    @Override
    void close();
}
