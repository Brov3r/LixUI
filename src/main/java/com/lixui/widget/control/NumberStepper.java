package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.text.HorizontalAlignment;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;

import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * A numeric field with decrement and increment buttons embedded inside its surface.
 * A click changes the value by one step, while holding a button starts repeating after a short
 * delay. Shift, Control and Alt clicks multiply the step by 10, 100 and 1000 respectively, and the
 * modifiers combine multiplicatively. Typed text updates the value immediately, and Enter
 * normalizes it to the configured range and decimal precision. The value is centered between the
 * embedded buttons.
 */
public class NumberStepper extends Input {
    private static final float BUTTON_WIDTH = 28;
    private static final float BUTTON_INSET = 2;
    private static final float REPEAT_INTERVAL = 0.08f;
    private static final int MAX_DECIMALS = 9;
    private static final int MAX_INPUT_LENGTH = 24;

    private final StepButton decrement = new StepButton(-1);
    private final StepButton increment = new StepButton(1);
    private double value;
    private double minimum;
    private double maximum = 100;
    private double step = 1;
    private int decimals;
    private boolean syncing;
    private int repeatDirection;
    private float repeatMultiplier;
    private float repeatAccumulator;
    private DoubleConsumer onValueChange;

    /**
     * Creates a stepper in the range {@code 0..100} with unit steps.
     */
    public NumberStepper() {
        setAllowedCharacters(Input.CharacterKind.DIGITS, Input.CharacterKind.SYMBOLS);
        setMaxLength(MAX_INPUT_LENGTH);
        setTextAlignment(HorizontalAlignment.CENTER);
        setOnChange(this::parseChange);
        setOnSubmit(this::commitInput);
        add(decrement).add(increment);
        syncInput();
    }

    /**
     * Returns the current value.
     *
     * @return the normalized value
     */
    public final double value() {
        return value;
    }

    /**
     * Replaces the value, clamping and rounding it into the configured range and precision.
     * The change callback runs when the normalized value actually changes.
     *
     * @param value new value
     * @throws IllegalArgumentException if the value is non-finite
     */
    public final void setValue(double value) {
        requireFinite(value, "Stepper value");
        double normalized = normalize(value);
        boolean changed = normalized != this.value;
        this.value = normalized;
        syncInput();
        if (changed) {
            notifyChange();
        }
    }

    /**
     * Returns the minimum value.
     *
     * @return the inclusive lower bound
     */
    public final double minimum() {
        return minimum;
    }

    /**
     * Returns the maximum value.
     *
     * @return the inclusive upper bound
     */
    public final double maximum() {
        return maximum;
    }

    /**
     * Sets the inclusive value range.
     * The current value is clamped and rounded into the new range.
     *
     * @param minimum inclusive lower bound
     * @param maximum inclusive upper bound
     * @throws IllegalArgumentException if a bound is non-finite or the minimum exceeds the maximum
     */
    public final void setRange(double minimum, double maximum) {
        requireFinite(minimum, "Stepper minimum");
        requireFinite(maximum, "Stepper maximum");
        if (minimum > maximum) {
            throw new IllegalArgumentException("Stepper minimum must not exceed the maximum.");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        setValue(value);
    }

    /**
     * Returns the increment used by the buttons and the repeat action.
     *
     * @return the positive step
     */
    public final double step() {
        return step;
    }

    /**
     * Sets the increment used by the buttons and the repeat action.
     *
     * @param step positive step
     * @throws IllegalArgumentException if the step is not positive or non-finite
     */
    public final void setStep(double step) {
        if (!Double.isFinite(step) || step <= 0) {
            throw new IllegalArgumentException("Stepper step must be finite and positive.");
        }
        this.step = step;
    }

    /**
     * Returns the number of decimal places used for display and rounding.
     *
     * @return the decimal place count
     */
    public final int decimals() {
        return decimals;
    }

    /**
     * Sets the number of decimal places used for display and rounding.
     *
     * @param decimals decimal place count from zero up to nine
     * @throws IllegalArgumentException if the count is out of range
     */
    public final void setDecimals(int decimals) {
        if (decimals < 0 || decimals > MAX_DECIMALS) {
            throw new IllegalArgumentException("Decimals must be between zero and nine.");
        }
        if (this.decimals == decimals) {
            return;
        }
        this.decimals = decimals;
        setValue(value);
    }

    /**
     * Returns the value change callback.
     *
     * @return the callback, or null when none is assigned
     */
    public final DoubleConsumer onValueChange() {
        return onValueChange;
    }

    /**
     * Assigns a callback invoked after the normalized value changes.
     *
     * @param onValueChange change callback, or null to clear it
     */
    public final void setOnValueChange(DoubleConsumer onValueChange) {
        this.onValueChange = onValueChange;
    }

    /**
     * Measures the field with the embedded button slots.
     *
     * @param constraints available and required dimensions
     * @return the natural stepper size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Size base = super.onMeasure(constraints);
        return new Size(base.width() + BUTTON_WIDTH * 2, base.height());
    }

    /**
     * Places the decrement and increment buttons inside the field surface.
     */
    @Override
    protected void onLayout() {
        super.onLayout();
        float height = bounds().height();
        float buttonHeight = Math.max(0, height - BUTTON_INSET * 2);
        float buttonWidth = Math.max(0, Math.min(BUTTON_WIDTH - BUTTON_INSET * 2,
                bounds().width() / 2 - BUTTON_INSET * 2));
        decrement.layout(new Rect(BUTTON_INSET, BUTTON_INSET, buttonWidth, buttonHeight));
        increment.layout(new Rect(bounds().width() - BUTTON_INSET - buttonWidth, BUTTON_INSET,
                buttonWidth, buttonHeight));
        decrement.setEnabled(isEnabled());
        increment.setEnabled(isEnabled());
    }

    /**
     * Reserves the embedded button slots on both sides of the text.
     *
     * @param widgetStyle resolved style used for the field
     * @return the content rectangle in widget-local coordinates
     */
    @Override
    protected Rect contentRect(WidgetStyle widgetStyle) {
        Rect base = super.contentRect(widgetStyle);
        if (base.isEmpty()) {
            return base;
        }
        float reserve = Math.min(BUTTON_WIDTH, base.width() / 2);
        return new Rect(base.x() + reserve, base.y(),
                Math.max(0, base.width() - reserve * 2), base.height());
    }

    /**
     * Applies flat button styles when the stepper attaches.
     */
    @Override
    protected void onAttached() {
        applyTheme();
    }

    /**
     * Reapplies the flat button styles after a context change.
     */
    @Override
    protected void onContextChanged() {
        applyTheme();
    }

    /**
     * Advances the long-press repeat while a button is held and keeps the caret blinking.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        super.onUpdate(frame);
        if (repeatDirection == 0) {
            return;
        }
        Button button = repeatDirection > 0 ? increment : decrement;
        if (!button.isPressed()) {
            repeatDirection = 0;
            return;
        }
        repeatAccumulator += frame.deltaSeconds();
        while (repeatAccumulator >= REPEAT_INTERVAL) {
            repeatAccumulator -= REPEAT_INTERVAL;
            adjust(repeatDirection, repeatMultiplier);
        }
    }

    /**
     * Keeps the text caret still when a press lands on one of the embedded buttons.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when the press belongs to an embedded button
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button == MouseButton.LEFT
                && (decrement.bounds().contains(x, y) || increment.bounds().contains(x, y))) {
            return true;
        }
        return super.onPointerPress(x, y, button, modifiers);
    }

    /**
     * Starts the repeat for a held increment or decrement button.
     * The manager suppresses the following click after a long press.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers modifiers captured at press time
     * @return true when a repeat started
     */
    @Override
    protected boolean onLongPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        if (increment.bounds().contains(x, y)) {
            startRepeat(1, modifiers);
            return true;
        }
        if (decrement.bounds().contains(x, y)) {
            startRepeat(-1, modifiers);
            return true;
        }
        return false;
    }

    /**
     * Stops the repeat when the pointer button is released.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button released button
     * @param modifiers active modifiers
     * @return false so the event can still bubble
     */
    @Override
    protected boolean onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
        repeatDirection = 0;
        return super.onPointerRelease(x, y, button, modifiers);
    }

    private void applyTheme() {
        if (context() == null) {
            return;
        }
        Theme theme = context().theme();
        Color idle = Color.mix(theme.surface(), theme.text(), 0.08f);
        Color hover = Color.mix(theme.surfaceHover(), theme.primary(), 0.18f);
        Color pressed = Color.mix(theme.surfacePressed(), theme.primary(), 0.3f);
        WidgetStyle style = new WidgetStyle(idle, hover, pressed, theme.surface(),
                theme.text(), theme.textMuted().withAlpha(0.5f),
                theme.border().withAlpha(0.55f), theme.focusRing(), 6, 2, 1);
        decrement.setStyle(style);
        increment.setStyle(style);
    }

    private void startRepeat(int direction, Modifiers modifiers) {
        repeatDirection = direction;
        repeatMultiplier = modifierMultiplier(modifiers);
        repeatAccumulator = 0;
        adjust(direction, repeatMultiplier);
    }

    private void adjust(int direction, float multiplier) {
        setValue(value + direction * step * multiplier);
    }

    private void parseChange(String text) {
        if (syncing) {
            return;
        }
        Double parsed = parse(text);
        if (parsed == null) {
            return;
        }
        double normalized = normalize(parsed);
        if (normalized != value) {
            value = normalized;
            notifyChange();
        }
    }

    private void commitInput() {
        Double parsed = parse(text());
        if (parsed != null) {
            double normalized = normalize(parsed);
            if (normalized != value) {
                value = normalized;
                notifyChange();
            }
        }
        syncInput();
    }

    private void syncInput() {
        syncing = true;
        try {
            setText(format(value));
        } finally {
            syncing = false;
        }
    }

    private double normalize(double raw) {
        double clamped = Math.clamp(raw, minimum, maximum);
        double scale = Math.pow(10, decimals);
        double rounded = Math.round(clamped * scale) / scale;
        return rounded == -0.0 ? 0 : rounded;
    }

    private String format(double number) {
        return String.format(Locale.ROOT, "%." + decimals + "f", number);
    }

    private static Double parse(String text) {
        String trimmed = text.trim().replace(',', '.');
        if (trimmed.isEmpty() || trimmed.equals("-") || trimmed.equals(".") || trimmed.equals("-.")) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(trimmed);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void notifyChange() {
        if (onValueChange != null) {
            onValueChange.accept(value);
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite.");
        }
    }

    private static float modifierMultiplier(Modifiers modifiers) {
        float multiplier = 1;
        if (modifiers.shift()) {
            multiplier *= 10;
        }
        if (modifiers.control()) {
            multiplier *= 100;
        }
        if (modifiers.alt()) {
            multiplier *= 1000;
        }
        return multiplier;
    }

    private final class StepButton extends Button {
        private final int direction;

        private StepButton(int direction) {
            super(direction < 0 ? "-" : "+");
            this.direction = direction;
            setFocusable(false);
            setOnAction(() -> adjust(direction, 1));
        }

        /**
         * Applies the modifier multiplier on a completed pointer click.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button clicked button
         * @param modifiers active modifiers
         * @return true when the click is consumed
         */
        @Override
        protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button != MouseButton.LEFT) {
                return false;
            }
            playSound();
            adjust(direction, modifierMultiplier(modifiers));
            return true;
        }

        /**
         * Applies the modifier multiplier on the second click of a double click.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button clicked button
         * @param modifiers active modifiers
         * @return true when the double click is consumed
         */
        @Override
        protected boolean onDoubleClick(float x, float y, MouseButton button,
                                        Modifiers modifiers) {
            return onClick(x, y, button, modifiers);
        }
    }
}
