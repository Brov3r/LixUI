package com.lixui.render;

import java.util.Objects;

/**
 * An immutable RGBA8 image in row-major order, starting at the top-left pixel.
 * Pixel data is straight-alpha and not premultiplied.
 *
 * @param width image width in pixels
 * @param height image height in pixels
 * @param pixels RGBA bytes, four per pixel, from the top row to the bottom row
 */
public record ImageData(int width, int height, byte[] pixels) {
    /**
     * Creates image data, validating its dimensions and payload size.
     *
     * @param width image width in pixels
     * @param height image height in pixels
     * @param pixels RGBA bytes, four per pixel
     * @throws NullPointerException if {@code pixels} is null
     * @throws IllegalArgumentException if a dimension is not positive or the payload size is wrong
     */
    public ImageData {
        Objects.requireNonNull(pixels, "pixels");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive.");
        }
        long expected = (long) width * height * 4;
        if (pixels.length != expected) {
            throw new IllegalArgumentException(
                    "RGBA8 image requires " + expected + " bytes, got " + pixels.length + ".");
        }
    }
}
