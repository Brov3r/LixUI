package com.lixui.platform;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Objects;

/**
 * Host integrations used by the UI for clipboard access, cursor changes and URI opening.
 * Implementations are usually window adapters and remain owned by the host application.
 */
public interface UiPlatform {
    /** A platform that has no clipboard, cursor or URI support. */
    UiPlatform NONE = new UiPlatform() {
        /**
         * Returns an empty clipboard text.
         *
         * @return an empty string
         */
        @Override
        public String clipboardText() {
            return "";
        }

        /**
         * Ignores clipboard updates.
         *
         * @param text text to copy
         */
        @Override
        public void setClipboardText(String text) {
        }

        /**
         * Ignores cursor changes.
         *
         * @param cursor requested cursor
         */
        @Override
        public void setCursor(Cursor cursor) {
        }

        /**
         * Ignores URI opening requests.
         *
         * @param uri requested URI
         */
        @Override
        public void openUri(String uri) {
        }
    };

    /**
     * Returns the current clipboard text.
     *
     * @return the clipboard text, or an empty string when the clipboard is empty
     */
    String clipboardText();

    /**
     * Replaces the clipboard text.
     *
     * @param text text to copy
     * @throws NullPointerException if {@code text} is null
     */
    void setClipboardText(String text);

    /**
     * Changes the pointer cursor for the host window.
     *
     * @param cursor requested cursor
     * @throws NullPointerException if {@code cursor} is null
     */
    void setCursor(Cursor cursor);

    /**
     * Opens a URI in the platform's default handler, usually a web browser.
     * The default implementation uses the JVM desktop integration. Hosts can override it to route
     * links through their own launcher.
     *
     * @param uri absolute URI to open
     * @throws NullPointerException if {@code uri} is null
     * @throws IllegalArgumentException if {@code uri} is not a valid URI
     * @throws UnsupportedOperationException if the platform cannot open URIs
     * @throws UncheckedIOException if the operating system fails to launch the handler
     */
    default void openUri(String uri) {
        Objects.requireNonNull(uri, "uri");
        URI target = URI.create(uri);
        if (!Desktop.isDesktopSupported()) {
            throw new UnsupportedOperationException("Opening URIs is not supported on this platform.");
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            throw new UnsupportedOperationException("Browsing is not supported on this platform.");
        }
        try {
            desktop.browse(target);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to open " + uri + ".", exception);
        }
    }
}
