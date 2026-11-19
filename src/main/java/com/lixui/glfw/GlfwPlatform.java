package com.lixui.glfw;

import com.lixui.platform.Cursor;
import com.lixui.platform.UiPlatform;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * A GLFW implementation of clipboard access, cursor changes and URI opening.
 * Standard cursors are created lazily and released by {@link #close()}.
 * The adapter must be closed before its window is destroyed.
 */
public final class GlfwPlatform implements UiPlatform, AutoCloseable {
    private final long window;
    private final Map<Cursor, Long> cursors = new EnumMap<>(Cursor.class);
    private boolean closed;

    private GlfwPlatform(long window) {
        this.window = window;
    }

    /**
     * Creates an adapter for a GLFW window.
     *
     * @param window GLFW window handle
     * @return the adapter, which must be closed before the window is destroyed
     */
    public static GlfwPlatform install(long window) {
        return new GlfwPlatform(window);
    }

    /**
     * Returns the current clipboard text.
     *
     * @return the clipboard text, or an empty string when the clipboard is empty
     * @throws IllegalStateException if this adapter is closed
     */
    @Override
    public String clipboardText() {
        ensureOpen();
        String text = GLFW.glfwGetClipboardString(window);
        return text == null ? "" : text;
    }

    /**
     * Replaces the clipboard text.
     *
     * @param text text to copy
     * @throws NullPointerException if {@code text} is null
     * @throws IllegalStateException if this adapter is closed
     */
    @Override
    public void setClipboardText(String text) {
        ensureOpen();
        GLFW.glfwSetClipboardString(window, Objects.requireNonNull(text, "text"));
    }

    /**
     * Changes the pointer cursor for the window.
     *
     * @param cursor requested cursor
     * @throws NullPointerException if {@code cursor} is null
     * @throws IllegalStateException if this adapter is closed
     */
    @Override
    public void setCursor(Cursor cursor) {
        ensureOpen();
        GLFW.glfwSetCursor(window, handle(Objects.requireNonNull(cursor, "cursor")));
    }

    /**
     * Restores the default cursor and releases every standard cursor created by this adapter.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        GLFW.glfwSetCursor(window, MemoryUtil.NULL);
        for (long cursor : cursors.values()) {
            if (cursor != MemoryUtil.NULL) {
                GLFW.glfwDestroyCursor(cursor);
            }
        }
        cursors.clear();
    }

    private long handle(Cursor cursor) {
        Long existing = cursors.get(cursor);
        if (existing != null) {
            return existing;
        }
        long created = GLFW.glfwCreateStandardCursor(shape(cursor));
        cursors.put(cursor, created);
        return created;
    }

    private static int shape(Cursor cursor) {
        return switch (cursor) {
            case DEFAULT -> GLFW.GLFW_ARROW_CURSOR;
            case POINTER -> GLFW.GLFW_POINTING_HAND_CURSOR;
            case TEXT -> GLFW.GLFW_IBEAM_CURSOR;
            case CROSSHAIR -> GLFW.GLFW_CROSSHAIR_CURSOR;
            case MOVE -> GLFW.GLFW_RESIZE_ALL_CURSOR;
            case RESIZE_HORIZONTAL -> GLFW.GLFW_RESIZE_EW_CURSOR;
            case RESIZE_VERTICAL -> GLFW.GLFW_RESIZE_NS_CURSOR;
            case RESIZE_DIAGONAL -> GLFW.GLFW_RESIZE_NWSE_CURSOR;
            case RESIZE_DIAGONAL_INVERSE -> GLFW.GLFW_RESIZE_NESW_CURSOR;
            case NOT_ALLOWED -> GLFW.GLFW_NOT_ALLOWED_CURSOR;
        };
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("GLFW platform adapter is closed.");
        }
    }
}
