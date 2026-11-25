package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A shortcut field that captures the next key or mouse button as a binding.
 * Clicking the field starts capture mode. Escape cancels capture without changing the current
 * binding, and the trailing cross clears it. The displayed value uses names such as
 * {@code Ctrl+Shift+K} or {@code Ctrl+Mouse Left}. Modifier keys alone never complete a binding.
 */
public final class Keybind extends Panel {
    private static final float PADDING = 10;
    private static final float CLEAR_SIZE = 18;
    private static final float CLEAR_GAP = 8;
    private static final float MIN_WIDTH = 190;
    private static final float MIN_HEIGHT = 36;
    private static final String PLACEHOLDER = "Not set";
    private static final String CAPTURING = "Press a key or mouse button...";

    private Binding binding;
    private boolean capturing;
    private boolean suppressClick;
    private Consumer<Binding> onChange;
    private float pointerX = -1;
    private float pointerY = -1;
    private boolean pointerInside;

    /**
     * Creates an unbound shortcut field.
     */
    public Keybind() {
        setFocusable(true);
    }

    /**
     * Creates a shortcut field with an initial binding.
     *
     * @param key bound key
     * @param modifiers bound modifiers
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the key is a modifier key
     */
    public Keybind(Key key, Modifiers modifiers) {
        this();
        setBinding(new Binding(key, modifiers));
    }

    /**
     * Creates a shortcut field with an initial mouse binding.
     *
     * @param mouseButton bound mouse button
     * @param modifiers bound modifiers
     * @throws NullPointerException if an argument is null
     */
    public Keybind(MouseButton mouseButton, Modifiers modifiers) {
        this();
        setBinding(new Binding(mouseButton, modifiers));
    }

    /**
     * Returns the current binding.
     *
     * @return the binding, or null when the field is empty
     */
    public Binding binding() {
        return binding;
    }

    /**
     * Replaces the binding.
     *
     * @param binding new binding, or null to clear the field
     * @throws IllegalArgumentException if the binding key is a modifier key
     */
    public void setBinding(Binding binding) {
        if (Objects.equals(this.binding, binding)) {
            return;
        }
        this.binding = binding;
        notifyChanged();
    }

    /**
     * Clears the binding and notifies the change listener.
     */
    public void clearBinding() {
        setBinding(null);
    }

    /**
     * Reports whether the field currently waits for a key press.
     *
     * @return true in capture mode
     */
    public boolean isCapturing() {
        return capturing;
    }

    /**
     * Returns the callback invoked when the binding changes.
     *
     * @return the change listener, or null when none is assigned
     */
    public Consumer<Binding> onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked after a binding changes or is cleared.
     *
     * @param onChange listener receiving the new binding, or null when the field is cleared
     */
    public void setOnChange(Consumer<Binding> onChange) {
        this.onChange = onChange;
    }

    /**
     * Returns the formatted binding text.
     *
     * @return the binding text, or an empty string when the field is empty
     */
    public String text() {
        return binding == null ? "" : binding.text();
    }

    /**
     * Formats a key and its modifiers as a shortcut label.
     *
     * @param key key to format
     * @param modifiers active modifiers
     * @return a label such as {@code Ctrl+Shift+K}
     * @throws NullPointerException if an argument is null
     */
    public static String format(Key key, Modifiers modifiers) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(modifiers, "modifiers");
        return modifierPrefix(modifiers) + keyName(key);
    }

    /**
     * Formats a mouse button and its modifiers as a shortcut label.
     *
     * @param button mouse button to format
     * @param modifiers active modifiers
     * @return a label such as {@code Ctrl+Mouse Left}
     * @throws NullPointerException if an argument is null
     */
    public static String format(MouseButton button, Modifiers modifiers) {
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(modifiers, "modifiers");
        return modifierPrefix(modifiers) + mouseButtonName(button);
    }

    /**
     * Returns a readable name for a mouse button.
     *
     * @param button mouse button to name
     * @return a name such as {@code Mouse Left} or {@code Mouse 4}
     * @throws NullPointerException if {@code button} is null
     */
    public static String mouseButtonName(MouseButton button) {
        Objects.requireNonNull(button, "button");
        return switch (button) {
            case LEFT -> "Mouse Left";
            case RIGHT -> "Mouse Right";
            case MIDDLE -> "Mouse Middle";
            case BUTTON_4 -> "Mouse 4";
            case BUTTON_5 -> "Mouse 5";
            case BUTTON_6 -> "Mouse 6";
            case BUTTON_7 -> "Mouse 7";
            case BUTTON_8 -> "Mouse 8";
        };
    }

    /**
     * Returns a readable name for a key.
     *
     * @param key key to name
     * @return the display name
     * @throws NullPointerException if {@code key} is null
     */
    public static String keyName(Key key) {
        Objects.requireNonNull(key, "key");
        String name = key.name();
        return switch (key) {
            case UNKNOWN -> "Unknown";
            case DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4,
                    DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9 -> name.substring(6);
            case ESCAPE -> "Esc";
            case ENTER -> "Enter";
            case TAB -> "Tab";
            case SPACE -> "Space";
            case BACKSPACE -> "Backspace";
            case DELETE -> "Delete";
            case INSERT -> "Insert";
            case LEFT -> "Left";
            case RIGHT -> "Right";
            case UP -> "Up";
            case DOWN -> "Down";
            case HOME -> "Home";
            case END -> "End";
            case PAGE_UP -> "Page Up";
            case PAGE_DOWN -> "Page Down";
            case SHIFT_LEFT, SHIFT_RIGHT -> "Shift";
            case CONTROL_LEFT, CONTROL_RIGHT -> "Ctrl";
            case ALT_LEFT, ALT_RIGHT -> "Alt";
            case SUPER_LEFT, SUPER_RIGHT -> "Super";
            case CAPS_LOCK -> "Caps Lock";
            case SCROLL_LOCK -> "Scroll Lock";
            case NUM_LOCK -> "Num Lock";
            case PRINT_SCREEN -> "Print Screen";
            case PAUSE -> "Pause";
            case MENU -> "Menu";
            case MINUS -> "-";
            case EQUAL -> "=";
            case LEFT_BRACKET -> "[";
            case RIGHT_BRACKET -> "]";
            case BACKSLASH -> "\\";
            case SEMICOLON -> ";";
            case APOSTROPHE -> "'";
            case GRAVE -> "`";
            case COMMA -> ",";
            case PERIOD -> ".";
            case SLASH -> "/";
            case KEYPAD_0, KEYPAD_1, KEYPAD_2, KEYPAD_3, KEYPAD_4,
                    KEYPAD_5, KEYPAD_6, KEYPAD_7, KEYPAD_8, KEYPAD_9 ->
                    "Num " + name.substring(7);
            case KEYPAD_DECIMAL -> "Num .";
            case KEYPAD_DIVIDE -> "Num /";
            case KEYPAD_MULTIPLY -> "Num *";
            case KEYPAD_SUBTRACT -> "Num -";
            case KEYPAD_ADD -> "Num +";
            case KEYPAD_ENTER -> "Num Enter";
            case KEYPAD_EQUAL -> "Num =";
            case WORLD_1 -> "World 1";
            case WORLD_2 -> "World 2";
            default -> name;
        };
    }

    /**
     * Returns the control style from the current UI context.
     *
     * @return the context control style, or null when the field is not attached
     */
    @Override
    protected WidgetStyle defaultStyle() {
        return context() == null ? null : context().theme().controlStyle();
    }

    /**
     * Uses the local style or the context control style instead of an inherited panel style.
     *
     * @return the resolved field surface style, or null when the field is not attached
     */
    @Override
    protected WidgetStyle surfaceStyle() {
        return hasLocalStyle() ? style() : defaultStyle();
    }

    /**
     * Measures the field from its current text and the clear affordance.
     *
     * @param constraints available and required dimensions
     * @return the natural field size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Font font = font();
        Size text = font == null ? new Size(0, 0) : font.measure(displayText());
        float width = Math.max(MIN_WIDTH,
                text.width() + PADDING * 2 + CLEAR_SIZE + CLEAR_GAP);
        float height = Math.max(MIN_HEIGHT, text.height() + PADDING * 2);
        return new Size(width, height);
    }

    /**
     * Paints the field, its shortcut text and the clear cross.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        super.onDraw(renderer, frame);
        WidgetStyle style = surfaceStyle();
        Font font = font();
        if (style == null || font == null || localBounds().isEmpty()) {
            return;
        }
        WidgetState state = state();
        String text = displayText();
        boolean empty = binding == null && !capturing;
        Color textColor = empty ? context().theme().textMuted() : style.text(state);
        float textWidth = Math.max(0, localBounds().width() - PADDING * 2
                - CLEAR_SIZE - CLEAR_GAP);
        renderer.text(font, text, PADDING, localBounds().height() / 2, textColor,
                new TextStyle(textWidth, 0, 1, TextOverflow.ELLIPSIS,
                        HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
        if (binding != null && !capturing) {
            drawClear(renderer);
        }
    }

    /**
     * Captures the next pointer press as a mouse binding while capture mode is active.
     * The click that entered capture mode completes on release, so it never becomes a binding.
     *
     * @param x horizontal pointer position relative to the field
     * @param y vertical pointer position relative to the field
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when capture mode consumed the press
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (!capturing) {
            suppressClick = false;
            return false;
        }
        capturing = false;
        suppressClick = true;
        setBinding(new Binding(button, modifiers));
        return true;
    }

    /**
     * Starts capture on a click and clears the binding from the cross.
     *
     * @param x horizontal pointer position relative to the field
     * @param y vertical pointer position relative to the field
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the click was consumed
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (suppressClick) {
            suppressClick = false;
            return true;
        }
        if (button != MouseButton.LEFT) {
            return false;
        }
        if (binding != null && !capturing && clearBounds().contains(x, y)) {
            clearBinding();
            return true;
        }
        startCapture();
        return true;
    }

    /**
     * Treats a double click like a regular click so repeated activation still starts capture.
     *
     * @param x horizontal pointer position relative to the field
     * @param y vertical pointer position relative to the field
     * @param button double-clicked button
     * @param modifiers active modifiers
     * @return true when the double click was consumed
     */
    @Override
    protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
        return onClick(x, y, button, modifiers);
    }

    /**
     * Tracks the pointer for the clear hover highlight.
     *
     * @param x horizontal pointer position relative to the field
     * @param y vertical pointer position relative to the field
     * @return false so pointer movement can bubble
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        pointerX = x;
        pointerY = y;
        pointerInside = true;
        return false;
    }

    /**
     * Clears the pointer hover used by the clear affordance.
     */
    @Override
    protected void onPointerExit() {
        pointerInside = false;
    }

    /**
     * Requests the pointing hand cursor for the whole field.
     *
     * @return the pointer cursor
     */
    @Override
    public Cursor cursor() {
        return Cursor.POINTER;
    }

    /**
     * Cancels capture when the field loses focus without changing the binding.
     *
     * @param focused true when the field gained focus
     */
    @Override
    protected void onFocusChanged(boolean focused) {
        if (!focused) {
            capturing = false;
        }
    }

    /**
     * Captures the next non-modifier key, or cancels capture on Escape.
     * Auto-repeated key presses are consumed without completing the binding, so a key that was
     * already held before capture started cannot be assigned by its repeat stream.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat true for a repeated key press
     * @return true when capture mode consumed the key
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        if (!capturing) {
            return false;
        }
        if (repeat) {
            return true;
        }
        if (key == Key.ESCAPE) {
            capturing = false;
            return true;
        }
        if (isModifierKey(key)) {
            return true;
        }
        capturing = false;
        setBinding(new Binding(key, modifiers));
        return true;
    }

    /**
     * Consumes key releases while capture mode is active.
     *
     * @param key released key
     * @param modifiers active modifiers
     * @return true while capturing
     */
    @Override
    protected boolean onKeyReleased(Key key, Modifiers modifiers) {
        return capturing;
    }

    /**
     * Consumes typed characters while capture mode is active.
     *
     * @param codepoint typed codepoint
     * @return true while capturing
     */
    @Override
    protected boolean onCharTyped(int codepoint) {
        return capturing;
    }

    private void startCapture() {
        if (capturing) {
            return;
        }
        capturing = true;
        if (context() != null) {
            context().requestFocus(this);
        }
    }

    private String displayText() {
        if (capturing) {
            return CAPTURING;
        }
        return binding == null ? PLACEHOLDER : binding.text();
    }

    private Rect clearBounds() {
        return new Rect(localBounds().width() - PADDING - CLEAR_SIZE,
                (localBounds().height() - CLEAR_SIZE) / 2, CLEAR_SIZE, CLEAR_SIZE);
    }

    private void drawClear(RenderManager renderer) {
        Rect bounds = clearBounds();
        float centerX = bounds.x() + bounds.width() / 2;
        float centerY = bounds.y() + bounds.height() / 2;
        boolean hovered = pointerInside && bounds.contains(pointerX, pointerY);
        if (hovered) {
            renderer.roundedRect(bounds, 4, context().theme().primary().withAlpha(0.14f));
        }
        Color color = hovered ? context().theme().primary()
                : context().theme().textMuted();
        float radius = 4;
        renderer.strokeLine(new Point(centerX - radius, centerY - radius),
                new Point(centerX + radius, centerY + radius), 1.5f, Paint.solid(color));
        renderer.strokeLine(new Point(centerX + radius, centerY - radius),
                new Point(centerX - radius, centerY + radius), 1.5f, Paint.solid(color));
    }

    private void notifyChanged() {
        if (onChange != null) {
            onChange.accept(binding);
        }
    }

    private static String modifierPrefix(Modifiers modifiers) {
        StringBuilder prefix = new StringBuilder();
        if (modifiers.control()) {
            prefix.append("Ctrl+");
        }
        if (modifiers.shift()) {
            prefix.append("Shift+");
        }
        if (modifiers.alt()) {
            prefix.append("Alt+");
        }
        if (modifiers.superKey()) {
            prefix.append("Super+");
        }
        return prefix.toString();
    }

    private static boolean isModifierKey(Key key) {
        return switch (key) {
            case SHIFT_LEFT, SHIFT_RIGHT, CONTROL_LEFT, CONTROL_RIGHT,
                    ALT_LEFT, ALT_RIGHT, SUPER_LEFT, SUPER_RIGHT -> true;
            default -> false;
        };
    }

    /**
     * A key or mouse button plus the modifiers required to trigger it.
     */
    public static final class Binding {
        private final Key key;
        private final MouseButton mouseButton;
        private final Modifiers modifiers;

        /**
         * Creates a key binding.
         *
         * @param key non-modifier key
         * @param modifiers required modifiers
         * @throws NullPointerException if an argument is null
         * @throws IllegalArgumentException if {@code key} is a modifier key
         */
        public Binding(Key key, Modifiers modifiers) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(modifiers, "modifiers");
            if (isModifierKey(key)) {
                throw new IllegalArgumentException("A binding requires a non-modifier key.");
            }
            this.key = key;
            this.mouseButton = null;
            this.modifiers = modifiers;
        }

        /**
         * Creates a mouse binding.
         *
         * @param mouseButton bound mouse button
         * @param modifiers required modifiers
         * @throws NullPointerException if an argument is null
         */
        public Binding(MouseButton mouseButton, Modifiers modifiers) {
            this.key = null;
            this.mouseButton = Objects.requireNonNull(mouseButton, "mouseButton");
            this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
        }

        /**
         * Returns the bound key.
         *
         * @return the key, or null when this is a mouse binding
         */
        public Key key() {
            return key;
        }

        /**
         * Returns the bound mouse button.
         *
         * @return the mouse button, or null when this is a key binding
         */
        public MouseButton mouseButton() {
            return mouseButton;
        }

        /**
         * Reports whether this binding uses a mouse button.
         *
         * @return true for a mouse binding
         */
        public boolean isMouse() {
            return mouseButton != null;
        }

        /**
         * Returns the required modifiers.
         *
         * @return the modifiers
         */
        public Modifiers modifiers() {
            return modifiers;
        }

        /**
         * Returns the shortcut label.
         *
         * @return a label such as {@code Ctrl+Shift+K} or {@code Ctrl+Mouse Left}
         */
        public String text() {
            return isMouse() ? format(mouseButton, modifiers) : format(key, modifiers);
        }

        /**
         * Compares bindings by their key or mouse button and modifiers.
         *
         * @param other object to compare with
         * @return true when both bindings are equal
         */
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Binding binding)) {
                return false;
            }
            return key == binding.key && mouseButton == binding.mouseButton
                    && modifiers.equals(binding.modifiers);
        }

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(key, mouseButton, modifiers);
        }

        /**
         * Returns a diagnostic representation of the binding.
         *
         * @return the binding components
         */
        @Override
        public String toString() {
            return "Binding[key=" + key + ", mouseButton=" + mouseButton
                    + ", modifiers=" + modifiers + "]";
        }
    }
}
