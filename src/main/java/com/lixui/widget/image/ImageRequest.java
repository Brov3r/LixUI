package com.lixui.widget.image;

import com.lixui.render.TextureOptions;

import java.net.URI;
import java.util.Objects;

/**
 * A decoded image request used as the cache key of an {@link ImageLoader}.
 * The target size is the maximum decoded image box in physical pixels. Loaders should decode at
 * that size without upscaling and preserve the source aspect ratio.
 *
 * @param uri absolute image URI
 * @param targetWidth maximum decoded width in physical pixels
 * @param targetHeight maximum decoded height in physical pixels
 * @param options sampling options applied when the decoded image is uploaded as a texture
 */
public record ImageRequest(URI uri, int targetWidth, int targetHeight, TextureOptions options) {
    /** The largest accepted target dimension in physical pixels. */
    public static final int MAX_DIMENSION = 8192;

    /**
     * Creates an image request with default texture sampling.
     *
     * @param uri absolute image URI
     * @param targetWidth maximum decoded width in physical pixels
     * @param targetHeight maximum decoded height in physical pixels
     * @throws NullPointerException if {@code uri} is null
     * @throws IllegalArgumentException if the URI is not absolute or a target dimension is invalid
     */
    public ImageRequest(URI uri, int targetWidth, int targetHeight) {
        this(uri, targetWidth, targetHeight, TextureOptions.DEFAULT);
    }

    /**
     * Creates an image request.
     *
     * @param uri absolute image URI
     * @param targetWidth maximum decoded width in physical pixels
     * @param targetHeight maximum decoded height in physical pixels
     * @param options sampling options applied to the uploaded texture
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the URI is not absolute or a target dimension is invalid
     */
    public ImageRequest {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(options, "options");
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("Image request URI must be absolute.");
        }
        validateDimension(targetWidth, "Target width");
        validateDimension(targetHeight, "Target height");
    }

    private static void validateDimension(int dimension, String name) {
        if (dimension <= 0 || dimension > MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    name + " must be between 1 and " + MAX_DIMENSION + " pixels.");
        }
    }
}
