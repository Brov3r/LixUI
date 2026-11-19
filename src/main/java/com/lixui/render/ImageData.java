package com.lixui.render;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * An RGBA8 image in row-major order, starting at the top-left pixel.
 * Pixel data is straight-alpha and not premultiplied.
 * The pixel array is shared with the caller and must not be modified during texture upload.
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
        int expected = payloadSize(width, height);
        if (pixels.length != expected) {
            throw new IllegalArgumentException(
                    "RGBA8 image requires " + expected + " bytes, got " + pixels.length + ".");
        }
    }

    /**
     * Reads a PNG or other ImageIO-supported image from the classpath.
     * The returned pixels keep the source dimensions and straight alpha.
     *
     * @param resource classpath resource path, with or without a leading slash
     * @return the decoded image data
     * @throws NullPointerException if {@code resource} is null
     * @throws IllegalArgumentException if the resource is missing or its format is unsupported
     * @throws UncheckedIOException if the resource cannot be read
     */
    public static ImageData fromResource(String resource) {
        Objects.requireNonNull(resource, "resource");
        return toImageData(read(resource));
    }

    /**
     * Reads and downsamples or upsamples a classpath image to the requested size.
     * Scaling uses high-quality bicubic interpolation.
     *
     * @param resource classpath resource path, with or without a leading slash
     * @param width target width in pixels
     * @param height target height in pixels
     * @return the decoded and resized image data
     * @throws NullPointerException if {@code resource} is null
     * @throws IllegalArgumentException if a target dimension is not positive, the resource is
     *                                  missing, or its format is unsupported
     * @throws UncheckedIOException if the resource cannot be read
     */
    public static ImageData fromResource(String resource, int width, int height) {
        Objects.requireNonNull(resource, "resource");
        payloadSize(width, height);
        BufferedImage image = read(resource);
        if (image.getWidth() == width && image.getHeight() == height) {
            return toImageData(image);
        }
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return toImageData(scaled);
    }

    private static BufferedImage read(String resource) {
        String path = resource.startsWith("/") ? resource : "/" + resource;
        try (InputStream input = ImageData.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("Image resource not found: " + resource);
            }
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IllegalArgumentException("Unsupported image resource: " + resource);
            }
            return image;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read image resource: " + resource, exception);
        }
    }

    private static ImageData toImageData(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] pixels = new byte[payloadSize(width, height)];
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
                int argb = row[x];
                int offset = (y * width + x) * 4;
                pixels[offset] = (byte) (argb >> 16);
                pixels[offset + 1] = (byte) (argb >> 8);
                pixels[offset + 2] = (byte) argb;
                pixels[offset + 3] = (byte) (argb >>> 24);
            }
        }
        return new ImageData(width, height, pixels);
    }

    private static int payloadSize(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > Integer.MAX_VALUE / 4) {
            throw new IllegalArgumentException("Image dimensions must fit an RGBA8 byte array.");
        }
        return width * height * 4;
    }
}
