package com.lixui.widget.control;

import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.theme.WidgetState;

/**
 * A keycap-like button that draws its label inside a rounded surface.
 * It inherits the focus, hover, pressed and click behavior of {@link Button}. A decorative keycap
 * can be made non-interactive so it neither takes keyboard focus nor reacts to pointer input, while
 * a clickable keycap can toggle its persistent down state to show a held or active key.
 */
public class Keycap extends Button {
    private boolean interactive = true;
    private boolean down;

    /**
     * Creates a keycap with an empty label.
     */
    public Keycap() {
        this("");
    }

    /**
     * Creates a keycap with a key name.
     *
     * @param text key name shown on the cap
     * @throws NullPointerException if {@code text} is null
     */
    public Keycap(String text) {
        super(text);
    }

    /**
     * Reports whether the keycap takes pointer and keyboard input.
     *
     * @return true when the keycap behaves like a button
     */
    public final boolean isInteractive() {
        return interactive;
    }

    /**
     * Enables or disables button behavior.
     * A non-interactive keycap keeps its normal look but is not focusable, shows the default cursor
     * and ignores pointer activations, which makes it suitable for shortcut hints.
     *
     * @param interactive true to behave like a button, false for a decorative keycap
     */
    public final void setInteractive(boolean interactive) {
        this.interactive = interactive;
        setFocusable(interactive);
    }

    /**
     * Reports whether the keycap is persistently drawn in its pressed state.
     *
     * @return true when the keycap is down
     */
    public final boolean isDown() {
        return down;
    }

    /**
     * Sets whether the keycap is persistently drawn in its pressed state.
     * This is independent from the transient pressed state while the pointer is held.
     *
     * @param down true to draw the keycap as held
     */
    public final void setDown(boolean down) {
        this.down = down;
    }

    /**
     * Uses the pressed surface while the keycap is down and otherwise follows the normal button
     * state resolution.
     *
     * @return the resolved interaction state
     */
    @Override
    protected WidgetState state() {
        if (!interactive) {
            return isEnabled() ? WidgetState.NORMAL : WidgetState.DISABLED;
        }
        if (down) {
            return WidgetState.PRESSED;
        }
        return super.state();
    }

    /**
     * Requests the pointer cursor only while the keycap is interactive.
     *
     * @return the pointer or default cursor
     */
    @Override
    public Cursor cursor() {
        return interactive ? Cursor.POINTER : Cursor.DEFAULT;
    }

    /**
     * Activates the keycap on a completed pointer click while it is interactive.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the click is consumed
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        return interactive && super.onClick(x, y, button, modifiers);
    }

    /**
     * Activates the keycap on a double click while it is interactive.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the double click is consumed
     */
    @Override
    protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
        return interactive && super.onDoubleClick(x, y, button, modifiers);
    }
}
