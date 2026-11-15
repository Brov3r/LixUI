package com.lixui.text;

import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable bytes of a TrueType or OpenType font face.
 * Instances can be created from raw bytes, files, streams or classpath resources and are reused for
 * any number of rasterized {@link Font} sizes. A direct buffer is allocated lazily and kept for the
 * lifetime of the instance because stb_truetype reads from it while glyphs are rasterized.
 */
public final class FontData {
    private final byte[] bytes;
    private ByteBuffer buffer;

    private FontData(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * Creates font data from a byte array and defensively copies it.
     *
     * @param bytes raw TrueType or OpenType data
     * @return immutable font data
     * @throws NullPointerException if {@code bytes} is null
     * @throws IllegalArgumentException if {@code bytes} is empty
     */
    public static FontData of(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Font data must not be empty.");
        }
        return new FontData(bytes.clone());
    }

    /**
     * Reads font data from a file.
     *
     * @param path file that contains TrueType or OpenType data
     * @return immutable font data
     * @throws NullPointerException if {@code path} is null
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if the file is empty
     */
    public static FontData from(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return of(Files.readAllBytes(path));
    }

    /**
     * Reads all remaining bytes from a stream without closing it.
     *
     * @param stream stream that provides TrueType or OpenType data
     * @return immutable font data
     * @throws NullPointerException if {@code stream} is null
     * @throws IOException if the stream cannot be read
     * @throws IllegalArgumentException if the stream provides no bytes
     */
    public static FontData from(InputStream stream) throws IOException {
        Objects.requireNonNull(stream, "stream");
        return of(stream.readAllBytes());
    }

    /**
     * Reads a classpath resource relative to this class.
     * Absolute names such as {@code "/fonts/regular.ttf"} are resolved from the classpath root.
     *
     * @param name resource name
     * @return immutable font data
     * @throws NullPointerException if {@code name} is null
     * @throws IOException if the resource is missing or cannot be read
     * @throws IllegalArgumentException if the resource provides no bytes
     */
    public static FontData fromResource(String name) throws IOException {
        Objects.requireNonNull(name, "name");
        try (InputStream stream = FontData.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("Missing classpath font resource: " + name);
            }
            return from(stream);
        }
    }

    synchronized ByteBuffer buffer() {
        if (buffer == null) {
            ByteBuffer direct = BufferUtils.createByteBuffer(bytes.length);
            direct.put(bytes).flip();
            buffer = direct;
        }
        return buffer;
    }
}
