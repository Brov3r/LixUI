package com.lixui.platform;

/**
 * Host integrations used by the UI for clipboard access and cursor changes.
 * Implementations are usually window adapters and remain owned by the host application.
 */
public interface UiPlatform {
    /** A platform that has no clipboard or cursor support. */
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
}
