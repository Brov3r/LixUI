package com.lixui.widget.image;

/**
 * A reference to an in-flight or cached image request.
 * Closing the handle releases the widget's reference and suppresses pending callbacks. Closing is
 * idempotent, and callers should close the handle when the image is detached or its source changes.
 */
public interface ImageHandle extends AutoCloseable {
    /**
     * Releases this reference and cancels pending delivery for this handle.
     */
    @Override
    void close();
}
