package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.render.RenderManager;
import com.lixui.theme.Theme;

/**
 * A check box that draws a circular radio indicator.
 * A radio button never clears itself through user input; combine several of them with an exclusive
 * {@link ToggleGroup} to get the usual single-choice behavior. All settings, callbacks and tooltip
 * support are inherited from {@link CheckBox}.
 */
public class RadioBox extends CheckBox {
    private static final float DEFAULT_RADIO_SIZE = 16;
    private static final float DOT_RATIO = 0.45f;

    private float radioSize = DEFAULT_RADIO_SIZE;

    /**
     * Creates a radio button without a label.
     */
    public RadioBox() {
        this("");
    }

    /**
     * Creates a radio button with a label.
     *
     * @param text radio button label
     * @throws NullPointerException if {@code text} is null
     */
    public RadioBox(String text) {
        super(text);
    }

    /**
     * Returns the circular indicator diameter.
     *
     * @return the indicator size in logical UI units
     */
    public final float radioSize() {
        return radioSize;
    }

    /**
     * Sets the circular indicator diameter.
     *
     * @param radioSize positive size in logical UI units
     * @throws IllegalArgumentException if the size is not positive or non-finite
     */
    public final void setRadioSize(float radioSize) {
        if (!Float.isFinite(radioSize) || radioSize <= 0) {
            throw new IllegalArgumentException("Radio size must be finite and positive.");
        }
        this.radioSize = radioSize;
    }

    /**
     * Returns the circular indicator width.
     *
     * @return the indicator width in logical UI units
     */
    @Override
    protected float indicatorWidth() {
        return radioSize;
    }

    /**
     * Returns the circular indicator height.
     *
     * @return the indicator height in logical UI units
     */
    @Override
    protected float indicatorHeight() {
        return radioSize;
    }

    /**
     * Paints the circular indicator with a center dot when checked.
     *
     * @param renderer drawing manager for the current frame
     * @param theme current theme
     * @param bounds indicator rectangle in widget-local coordinates
     */
    @Override
    protected void drawIndicator(RenderManager renderer, Theme theme, Rect bounds) {
        Color backdrop = backdrop();
        Color accent = indicatorAccent(theme);
        float amount = checkedAmount();
        float radius = Math.min(bounds.width(), bounds.height()) / 2;
        drawRing(renderer, bounds, radius, blend(indicatorBorder(theme), accent, amount),
                backdrop);
        float dot = Math.min(bounds.width(), bounds.height()) * DOT_RATIO * amount;
        if (dot <= 0.5f) {
            return;
        }
        float centerX = bounds.x() + bounds.width() / 2;
        float centerY = bounds.y() + bounds.height() / 2;
        renderer.roundedRect(new Rect(centerX - dot / 2, centerY - dot / 2, dot, dot),
                dot / 2, accent);
    }

    /**
     * Keeps the radio button selected when the user activates an already checked button.
     */
    @Override
    protected void requestToggle() {
        if (!isChecked()) {
            playSound();
            setChecked(true);
        }
    }
}
