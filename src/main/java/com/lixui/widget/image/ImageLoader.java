package com.lixui.widget.image;

import com.lixui.render.Texture;
import com.lixui.widget.UiContext;

import java.util.function.Consumer;

/**
 * Resolves remote image requests into uploaded textures.
 * Implementations must invoke callbacks on the render thread, for example through
 * {@link UiContext#post(Runnable)}, so textures are created on the thread that owns the backend.
 */
public interface ImageLoader extends AutoCloseable {
    /**
     * Requests an image and registers callbacks for its result.
     * A cached texture may be delivered on a later render frame.
     *
     * @param request image request used as the cache key
     * @param onReady callback receiving the uploaded texture
     * @param onError callback receiving a decoding, network or upload failure
     * @return a handle that releases this request when closed
     * @throws NullPointerException if an argument is null
     * @throws IllegalStateException if the loader is closed
     */
    ImageHandle load(ImageRequest request, Consumer<Texture> onReady, Consumer<Throwable> onError);

    /**
     * Releases loader resources and cached textures.
     */
    @Override
    default void close() {
    }
}
