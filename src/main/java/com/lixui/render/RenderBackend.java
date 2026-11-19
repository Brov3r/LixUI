package com.lixui.render;

import com.lixui.render.command.ShapeCommand;

import java.util.Objects;

/**
 * A graphics backend that consumes backend-neutral draw lists.
 * Implementations own their GPU state and may change graphics state while drawing.
 */
public interface RenderBackend extends AutoCloseable {
    /**
     * Reports whether this backend evaluates analytic shapes and gradients on the GPU.
     * The default preserves tessellated commands for existing backend implementations.
     *
     * @return true when {@link ShapeCommand} is supported
     */
    default boolean supportsAnalyticShapes() {
        return false;
    }

    /**
     * Sets a pixel-for-pixel viewport where logical and framebuffer dimensions are equal.
     *
     * @param framebufferWidth target width in pixels
     * @param framebufferHeight target height in pixels
     * @throws IllegalArgumentException if a dimension is not positive
     */
    default void resize(int framebufferWidth, int framebufferHeight) {
        resize(new Viewport(framebufferWidth, framebufferHeight, framebufferWidth, framebufferHeight));
    }

    /**
     * Sets the physical framebuffer and logical UI viewport.
     * Backends map the logical viewport onto the full framebuffer.
     *
     * @param viewport target viewport
     * @throws NullPointerException if {@code viewport} is null
     */
    void resize(Viewport viewport);

    /**
     * Draws the supplied commands into the currently bound render target.
     * The target viewport must have been reported with {@link #resize(Viewport)}.
     *
     * @param drawList commands to render
     * @throws NullPointerException if {@code drawList} is null
     * @throws IllegalStateException if the target viewport is unknown
     */
    void draw(DrawList drawList);

    /**
     * Returns the number of draw calls submitted by the most recent {@link #draw(DrawList)}.
     * Backends that do not track draw calls return zero.
     *
     * @return the draw call count of the last frame
     */
    default int drawCallCount() {
        return 0;
    }

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
     * Creates a texture with explicit sampling options.
     * The default implementation delegates to {@link #createTexture(ImageData)} for default options
     * and rejects custom options, so existing backends keep working unchanged.
     *
     * @param image RGBA8 image data with a top-left origin
     * @param options filtering, wrapping and mipmap settings applied to the texture
     * @return the created texture
     * @throws NullPointerException if an argument is null
     * @throws UnsupportedOperationException if this backend does not support custom sampling options
     */
    default Texture createTexture(ImageData image, TextureOptions options) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(options, "options");
        if (!TextureOptions.DEFAULT.equals(options)) {
            throw new UnsupportedOperationException("Custom texture options are not supported.");
        }
        return createTexture(image);
    }

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
