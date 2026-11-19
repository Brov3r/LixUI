package com.lixui.widget;

/**
 * Timing and placement of tooltips.
 * The same value configures the manager defaults and per-widget overrides, so a tooltip owner can
 * change its delay, fade timing or offset without affecting other widgets.
 *
 * @param delaySeconds seconds the pointer must rest before a tooltip appears
 * @param appearSeconds fade-in duration in seconds; zero shows the tooltip immediately
 * @param disappearSeconds fade-out duration in seconds; zero hides the tooltip immediately
 * @param offsetX horizontal distance from the pointer in logical UI units
 * @param offsetY vertical distance from the pointer in logical UI units
 */
public record TooltipSettings(float delaySeconds, float appearSeconds, float disappearSeconds,
                              float offsetX, float offsetY) {
    private static final float DEFAULT_DELAY = 0.5f;
    private static final float DEFAULT_FADE = 0.12f;
    private static final float DEFAULT_OFFSET_X = 12;
    private static final float DEFAULT_OFFSET_Y = 18;

    /**
     * Creates tooltip settings.
     *
     * @param delaySeconds seconds the pointer must rest before a tooltip appears
     * @param appearSeconds fade-in duration in seconds
     * @param disappearSeconds fade-out duration in seconds
     * @param offsetX horizontal distance from the pointer in logical UI units
     * @param offsetY vertical distance from the pointer in logical UI units
     * @throws IllegalArgumentException if a duration is negative or non-finite, or an offset is non-finite
     */
    public TooltipSettings {
        if (!Float.isFinite(delaySeconds) || delaySeconds < 0) {
            throw new IllegalArgumentException("Tooltip delay must be finite and non-negative.");
        }
        if (!Float.isFinite(appearSeconds) || appearSeconds < 0) {
            throw new IllegalArgumentException("Tooltip appear duration must be finite and non-negative.");
        }
        if (!Float.isFinite(disappearSeconds) || disappearSeconds < 0) {
            throw new IllegalArgumentException("Tooltip disappear duration must be finite and non-negative.");
        }
        if (!Float.isFinite(offsetX) || !Float.isFinite(offsetY)) {
            throw new IllegalArgumentException("Tooltip offsets must be finite.");
        }
    }

    /**
     * Returns the built-in settings: a half-second delay, a short fade and a small pointer offset.
     *
     * @return the default tooltip settings
     */
    public static TooltipSettings defaults() {
        return new TooltipSettings(DEFAULT_DELAY, DEFAULT_FADE, DEFAULT_FADE,
                DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
    }

    /**
     * Returns a copy with a different delay.
     *
     * @param delaySeconds seconds the pointer must rest before a tooltip appears
     * @return the updated settings
     * @throws IllegalArgumentException if the delay is negative or non-finite
     */
    public TooltipSettings withDelay(float delaySeconds) {
        return new TooltipSettings(delaySeconds, appearSeconds, disappearSeconds, offsetX, offsetY);
    }

    /**
     * Returns a copy with different fade durations.
     *
     * @param appearSeconds fade-in duration in seconds
     * @param disappearSeconds fade-out duration in seconds
     * @return the updated settings
     * @throws IllegalArgumentException if a duration is negative or non-finite
     */
    public TooltipSettings withFade(float appearSeconds, float disappearSeconds) {
        return new TooltipSettings(delaySeconds, appearSeconds, disappearSeconds, offsetX, offsetY);
    }

    /**
     * Returns a copy with a different pointer offset.
     *
     * @param offsetX horizontal distance from the pointer in logical UI units
     * @param offsetY vertical distance from the pointer in logical UI units
     * @return the updated settings
     * @throws IllegalArgumentException if an offset is non-finite
     */
    public TooltipSettings withOffset(float offsetX, float offsetY) {
        return new TooltipSettings(delaySeconds, appearSeconds, disappearSeconds, offsetX, offsetY);
    }
}
