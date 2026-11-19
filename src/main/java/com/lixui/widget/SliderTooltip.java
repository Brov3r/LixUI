package com.lixui.widget;

/**
 * Fades a slider value tooltip in while it is active and keeps it visible for a short linger period.
 */
final class SliderTooltip {
    private static final double HOLD_SECONDS = 2.5;
    private static final float FADE_RATE = 16;

    private float alpha;
    private double visibleUntil;

    /**
     * Advances the tooltip fade.
     *
     * @param frame timing information for the current frame
     * @param active true while the tooltip should stay visible
     */
    void update(FrameInfo frame, boolean active) {
        double now = frame.elapsedSeconds();
        if (active) {
            visibleUntil = now + HOLD_SECONDS;
        }
        float target = active || now < visibleUntil ? 1 : 0;
        float delta = frame.deltaSeconds();
        if (delta <= 0) {
            return;
        }
        float factor = 1 - (float) Math.exp(-FADE_RATE * delta);
        alpha += (target - alpha) * factor;
        if (alpha < 0.001f) {
            alpha = 0;
        } else if (target == 1 && 1 - alpha < 0.001f) {
            alpha = 1;
        }
    }

    /**
     * Returns the current tooltip opacity.
     *
     * @return an opacity in {@code [0, 1]}
     */
    float alpha() {
        return alpha;
    }
}
