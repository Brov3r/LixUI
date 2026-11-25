package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.TextLayout;
import com.lixui.text.TextStyle;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.UiContext;

import java.text.BreakIterator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A focusable single-line text field with caret, selection, horizontal scrolling and history.
 * Text can be edited with the keyboard, replaced through selection and copied or pasted through the
 * platform clipboard. The field supports the usual editing shortcuts: arrow and word navigation,
 * Home, End, Shift selection, Select All, Cut, Copy, Paste, Undo and Redo. Enter invokes the submit
 * callback and line breaks are normalized to spaces, because the field is single-line.
 * The field can hide its value for password entry, format it with a custom mask, and restrict the
 * accepted character categories. The caret and selection are only interactive while the field is
 * focused; focusing and pointer selection are handled by the widget manager like for other controls.
 */
public class Input extends Panel {
    /**
     * Categories used to restrict the characters accepted by the field.
     * Marks that belong to letters are classified as letters, while punctuation, symbols and
     * whitespace are classified as symbols.
     */
    public enum CharacterKind {
        /** Unicode digits. */
        DIGITS,
        /** Unicode letters and their combining marks. */
        LETTERS,
        /** Punctuation, symbols, whitespace and other remaining characters. */
        SYMBOLS
    }

    private static final float DEFAULT_CONTENT_WIDTH = 160;
    private static final int MAX_HISTORY = 200;
    private static final double CARET_BLINK_PERIOD = 1.06;
    private static final double CARET_BLINK_HALF_PERIOD = CARET_BLINK_PERIOD / 2;
    private static final double MULTI_CLICK_SECONDS = 0.4;
    private static final float MULTI_CLICK_SLOP = 4;
    private static final float SELECTION_OPACITY = 0.35f;

    private final Deque<EditState> undoHistory = new ArrayDeque<>();
    private final Deque<EditState> redoHistory = new ArrayDeque<>();
    private final Set<CharacterKind> allowedCharacters = EnumSet.allOf(CharacterKind.class);
    private String text;
    private String placeholder = "";
    private int maxLength;
    private boolean readOnly;
    private boolean secret;
    private char secretCharacter = '*';
    private String mask;
    private List<MaskSlot> maskSlots = List.of();
    private Consumer<String> onChange;
    private Runnable onSubmit;
    private Color selectionColor;
    private int caret;
    private int anchor;
    private float scrollX;
    private boolean dragging;
    private double blinkStart;
    private double lastElapsed;
    private double lastPressTime = Double.NEGATIVE_INFINITY;
    private float lastPressX;
    private float lastPressY;
    private int clickCount;

    /**
     * Creates an empty text field.
     */
    public Input() {
        this("");
    }

    /**
     * Creates a text field with initial text and the caret at the end.
     *
     * @param text initial text
     * @throws NullPointerException if {@code text} is null
     */
    public Input(String text) {
        this.text = normalizeSingleLine(Objects.requireNonNull(text, "text"));
        caret = this.text.length();
        anchor = caret;
        setFocusable(true);
    }

    /**
     * Returns the current value.
     * When a mask is configured, the value is formatted with the mask literals; use
     * {@link #rawText()} for the stored value without literals.
     *
     * @return the formatted value without line breaks
     */
    public final String text() {
        return formattedText();
    }

    /**
     * Returns the stored value without mask literals.
     *
     * @return the raw value without line breaks
     */
    public final String rawText() {
        return text;
    }

    /**
     * Replaces the value and moves the caret to its end.
     * Line breaks and tabs are normalized to spaces, mask literals and rejected characters are
     * ignored, the history is cleared, and the change callback is invoked when the resulting value
     * differs from the current one.
     *
     * @param text new value, either raw or already formatted with the active mask
     * @throws NullPointerException if {@code text} is null
     */
    public final void setText(String text) {
        String previous = this.text();
        String normalized = parseMaskedInput(
                sanitize(Objects.requireNonNull(text, "text")), valueLimit());
        this.text = normalized;
        caret = normalized.length();
        anchor = caret;
        scrollX = 0;
        undoHistory.clear();
        redoHistory.clear();
        resetBlink();
        if (!previous.equals(this.text())) {
            notifyChange();
        }
    }

    /**
     * Reports whether the field hides its value for password entry.
     *
     * @return true when displayed characters are replaced with the secret character
     */
    public final boolean isSecret() {
        return secret;
    }

    /**
     * Enables or disables password-style rendering.
     * Copy and cut are disabled while secret mode is active. The stored value and change callback
     * are not affected.
     *
     * @param secret true to render every value character as the secret character
     */
    public final void setSecret(boolean secret) {
        this.secret = secret;
        resetBlink();
    }

    /**
     * Returns the character rendered in place of every value character in secret mode.
     *
     * @return the secret character
     */
    public final char secretCharacter() {
        return secretCharacter;
    }

    /**
     * Replaces the character rendered in place of every value character in secret mode.
     *
     * @param secretCharacter non-control Unicode character
     * @throws IllegalArgumentException if the character is a control or surrogate character
     */
    public final void setSecretCharacter(char secretCharacter) {
        if (Character.isISOControl(secretCharacter) || Character.isSurrogate(secretCharacter)) {
            throw new IllegalArgumentException("Secret character must be printable.");
        }
        this.secretCharacter = secretCharacter;
    }

    /**
     * Returns the input mask pattern.
     *
     * @return the mask pattern, or null when no mask is configured
     */
    public final String mask() {
        return mask;
    }

    /**
     * Applies a custom input mask, replacing any previous mask.
     * Mask tokens are {@code x} for any accepted character, {@code 9} for a digit, {@code a} for a
     * letter and {@code *} for a letter or digit. Every other character is a literal that is inserted
     * automatically and can be escaped with a backslash. Character restrictions are applied together
     * with the mask tokens. An empty pattern removes the mask.
     *
     * @param mask mask pattern such as {@code "+7 (xxx) xxx-xx-xx"} or {@code "99.99.9999"}
     * @throws NullPointerException if {@code mask} is null
     */
    public final void setMask(String mask) {
        Objects.requireNonNull(mask, "mask");
        String previous = text();
        String nextMask = mask.isEmpty() ? null : mask;
        boolean changedMask = !Objects.equals(nextMask, this.mask);
        this.mask = nextMask;
        maskSlots = nextMask == null ? List.of() : parseMask(nextMask);
        String normalized = parseMaskedInput(this.text, valueLimit());
        this.text = normalized;
        caret = normalized.length();
        anchor = caret;
        scrollX = 0;
        undoHistory.clear();
        redoHistory.clear();
        resetBlink();
        if (changedMask || !previous.equals(text())) {
            notifyChange();
        }
    }

    /**
     * Removes the input mask while keeping the stored value.
     */
    public final void clearMask() {
        setMask("");
    }

    /**
     * Returns the accepted character categories.
     *
     * @return an immutable set of accepted categories
     */
    public final Set<CharacterKind> allowedCharacters() {
        return Set.copyOf(allowedCharacters);
    }

    /**
     * Restricts the field to the supplied character categories.
     * Passing no categories rejects every character. Existing text, pasted text and typed characters
     * are filtered, and mask tokens are applied on top of the restriction.
     *
     * @param kinds accepted character categories, or no arguments to reject all characters
     * @throws NullPointerException if the array or one of its entries is null
     */
    public final void setAllowedCharacters(CharacterKind... kinds) {
        Objects.requireNonNull(kinds, "kinds");
        allowedCharacters.clear();
        for (CharacterKind kind : kinds) {
            allowedCharacters.add(Objects.requireNonNull(kind, "kind"));
        }
        String normalized = parseMaskedInput(this.text, valueLimit());
        boolean changed = !normalized.equals(this.text);
        this.text = normalized;
        caret = clampIndex(normalized, caret);
        anchor = clampIndex(normalized, anchor);
        if (changed) {
            scrollX = 0;
            undoHistory.clear();
            redoHistory.clear();
            resetBlink();
            notifyChange();
        }
    }

    /**
     * Returns the placeholder shown while the text is empty.
     *
     * @return the placeholder text, or an empty string when none is set
     */
    public final String placeholder() {
        return placeholder;
    }

    /**
     * Assigns the placeholder shown while the text is empty.
     *
     * @param placeholder placeholder text, or an empty string to clear it
     * @throws NullPointerException if {@code placeholder} is null
     */
    public final void setPlaceholder(String placeholder) {
        this.placeholder = normalizeSingleLine(Objects.requireNonNull(placeholder, "placeholder"));
    }

    /**
     * Returns the maximum value length in Unicode code points.
     * Mask literals are not counted.
     *
     * @return the maximum length, or zero when the length is unlimited
     */
    public final int maxLength() {
        return maxLength;
    }

    /**
     * Limits the number of Unicode code points stored by the field.
     * Mask literals are not counted. A positive limit truncates the current value immediately.
     * Zero removes the limit.
     *
     * @param maxLength non-negative maximum length in code points, or zero for no limit
     * @throws IllegalArgumentException if the limit is negative
     */
    public final void setMaxLength(int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("Maximum length must be non-negative.");
        }
        this.maxLength = maxLength;
        String limited = parseMaskedInput(text, valueLimit());
        if (limited.equals(text)) {
            return;
        }
        text = limited;
        caret = clampIndex(limited, caret);
        anchor = clampIndex(limited, anchor);
        scrollX = 0;
        undoHistory.clear();
        redoHistory.clear();
        resetBlink();
        notifyChange();
    }

    /**
     * Reports whether editing is blocked.
     *
     * @return true when text changes are rejected
     */
    public final boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Enables or disables editing while keeping selection and clipboard copying available.
     *
     * @param readOnly true to reject text changes
     */
    public final void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    /**
     * Returns the callback invoked after the text changes.
     *
     * @return the change callback, or null when none is assigned
     */
    public final Consumer<String> onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked after the value changes.
     *
     * @param onChange callback receiving the formatted value, or null to clear it
     */
    public final void setOnChange(Consumer<String> onChange) {
        this.onChange = onChange;
    }

    /**
     * Returns the callback invoked when Enter is pressed.
     *
     * @return the submit callback, or null when none is assigned
     */
    public final Runnable onSubmit() {
        return onSubmit;
    }

    /**
     * Assigns a callback invoked when Enter or the keypad Enter is pressed.
     *
     * @param onSubmit submit callback, or null to clear it
     */
    public final void setOnSubmit(Runnable onSubmit) {
        this.onSubmit = onSubmit;
    }

    /**
     * Returns the explicit selection highlight color.
     *
     * @return the selection color, or null when the theme primary color is used
     */
    public final Color selectionColor() {
        return selectionColor;
    }

    /**
     * Assigns an explicit selection highlight color.
     *
     * @param selectionColor selection color, or null to use the theme primary color
     */
    public final void setSelectionColor(Color selectionColor) {
        this.selectionColor = selectionColor;
    }

    /**
     * Returns the caret index in the stored value.
     * Mask literals are not part of the index.
     *
     * @return a UTF-16 index between zero and the raw value length
     */
    public final int caretIndex() {
        return caret;
    }

    /**
     * Moves the caret to the supplied index and collapses the selection.
     * The index is clamped to the stored value and aligned to a code point boundary.
     *
     * @param index UTF-16 index in the raw value
     */
    public final void setCaretIndex(int index) {
        caret = clampIndex(index);
        anchor = caret;
        resetBlink();
    }

    /**
     * Returns the start of the current selection in the stored value.
     *
     * @return the smaller UTF-16 selection index without mask literals
     */
    public final int selectionStart() {
        return Math.min(anchor, caret);
    }

    /**
     * Returns the end of the current selection in the stored value.
     *
     * @return the larger UTF-16 selection index without mask literals
     */
    public final int selectionEnd() {
        return Math.max(anchor, caret);
    }

    /**
     * Reports whether a range of the stored value is selected.
     *
     * @return true when the selection start differs from the end
     */
    public final boolean hasSelection() {
        return anchor != caret;
    }

    /**
     * Selects the supplied range of the stored value.
     * Both indexes are clamped to the raw value and aligned to code point boundaries; either index
     * may be larger, so the anchor keeps the original direction for later Shift navigation. Mask
     * literals are not part of the indexes.
     *
     * @param start UTF-16 index where the selection starts
     * @param end UTF-16 index where the selection ends
     */
    public final void setSelection(int start, int end) {
        anchor = clampIndex(start);
        caret = clampIndex(end);
        resetBlink();
    }

    /**
     * Selects the whole text.
     */
    public final void selectAll() {
        setSelection(0, text.length());
    }

    /**
     * Collapses the selection at the caret.
     */
    public final void clearSelection() {
        anchor = caret;
        resetBlink();
    }

    /**
     * Returns the text cursor for an enabled field.
     *
     * @return the text cursor, or the default cursor when disabled
     */
    @Override
    public Cursor cursor() {
        return isEnabled() ? Cursor.TEXT : Cursor.DEFAULT;
    }

    /**
     * Resolves the field state, preferring the focused appearance over hover and press.
     * A focused field keeps its focus border while the pointer is over it or a selection drag is in
     * progress.
     *
     * @return the active widget state
     */
    @Override
    protected WidgetState state() {
        if (!isEnabled()) {
            return WidgetState.DISABLED;
        }
        if (isFocused()) {
            return WidgetState.FOCUSED;
        }
        if (isPressed()) {
            return WidgetState.PRESSED;
        }
        if (isHovered()) {
            return WidgetState.HOVERED;
        }
        return WidgetState.NORMAL;
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
     * Resolves the style used to paint and measure the field.
     * A locally assigned style wins; otherwise the current control style is used so that the field
     * keeps its own surface when it is placed inside a container with an inherited panel style.
     *
     * @return the field style, or null when the field is not attached and has no local style
     */
    protected final WidgetStyle resolvedStyle() {
        if (hasLocalStyle()) {
            return style();
        }
        UiContext uiContext = context();
        return uiContext == null ? style() : uiContext.theme().controlStyle();
    }

    /**
     * Measures the text and padding, reserving a preferred width for short content.
     *
     * @param constraints available and required dimensions
     * @return the natural field size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        WidgetStyle widgetStyle = resolvedStyle();
        if (widgetStyle == null) {
            return super.onMeasure(constraints);
        }
        Font font = font();
        float contentWidth = DEFAULT_CONTENT_WIDTH;
        float contentHeight = 0;
        if (font != null) {
            contentWidth = Math.max(contentWidth, font.measure(displayText()).width());
            if (text.isEmpty()) {
                contentWidth = Math.max(contentWidth, font.measure(placeholder).width());
            }
            contentHeight = font.lineHeight();
        }
        float padding = widgetStyle.padding();
        return new Size(contentWidth + padding * 2, contentHeight + padding * 2);
    }

    /**
     * Records the current frame time used for caret blinking.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        lastElapsed = frame.elapsedSeconds();
    }

    /**
     * Paints the field surface, selection, text, placeholder and blinking caret.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (localBounds().isEmpty()) {
            return;
        }
        WidgetStyle widgetStyle = resolvedStyle();
        if (widgetStyle == null) {
            return;
        }
        drawSurface(renderer, widgetStyle);
        Font font = font();
        if (font == null) {
            return;
        }
        Rect content = contentRect(widgetStyle);
        if (content.isEmpty()) {
            return;
        }
        String display = displayText();
        TextLayout layout = font.layoutBlockRelative(display, TextStyle.DEFAULT);
        float contentHeight = layout.size().height();
        float textTop = content.y() + (content.height() - contentHeight) / 2;
        float caretWidth = caretWidth(font);
        int displayCaret = displayIndexForRaw(caret);
        ensureCaretVisible(layout, content.width(), caretWidth, displayCaret);
        float textLeft = content.x() - scrollX;
        Point origin = absolutePosition();
        Rect contentClip = new Rect(origin.x() + content.x(), origin.y() + content.y(),
                content.width(), content.height());
        renderer.withClip(contentClip,
                () -> drawContent(renderer, frame, layout, font, widgetStyle, display,
                        displayCaret, textLeft, textTop, caretWidth));
    }

    /**
     * Starts a selection at the pointer press and focuses the field.
     * A plain press moves the caret and collapses the selection; Shift extends the existing
     * selection from its anchor. A third click within the multi-click interval selects the whole
     * text.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when the press is consumed
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        trackClick(x, y);
        if (clickCount >= 3) {
            selectAll();
            dragging = false;
            clickCount = 0;
            return true;
        }
        int index = indexAt(x, y);
        if (modifiers.shift()) {
            caret = index;
        } else {
            caret = index;
            anchor = index;
        }
        dragging = true;
        resetBlink();
        return true;
    }

    /**
     * Extends the selection while the pointer is dragged after a press.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @return true while a selection drag is active
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        if (!dragging) {
            return false;
        }
        if (Math.hypot(x - lastPressX, y - lastPressY) > MULTI_CLICK_SLOP) {
            clickCount = 0;
        }
        caret = indexAt(x, y);
        resetBlink();
        return true;
    }

    /**
     * Completes a selection drag.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button released button
     * @param modifiers active modifiers
     * @return true when the release is consumed
     */
    @Override
    protected boolean onPointerRelease(float x, float y, MouseButton button,
                                       Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        dragging = false;
        return true;
    }

    /**
     * Selects the word under the pointer on a double click.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the double click is consumed
     */
    @Override
    protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        selectWordAt(indexAt(x, y));
        dragging = false;
        return true;
    }

    /**
     * Applies navigation, editing, clipboard and history shortcuts.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat true for a repeated key press
     * @return true when the key is consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        if (!isEnabled()) {
            return false;
        }
        boolean command = modifiers.superKey() || (modifiers.control() && !modifiers.alt());
        boolean shift = modifiers.shift();
        if (key == Key.INSERT) {
            if (command && !shift) {
                return copySelection();
            }
            if (shift && !command) {
                return paste();
            }
            return false;
        }
        if (key == Key.DELETE && shift && !command) {
            return cutSelection();
        }
        if (command) {
            return switch (key) {
                case A -> {
                    selectAll();
                    yield true;
                }
                case C -> copySelection();
                case X -> cutSelection();
                case V -> paste();
                case Z -> {
                    yield shift ? redo() : undo();
                }
                case Y -> redo();
                case BACKSPACE -> deleteBackward(true);
                case DELETE -> deleteForward(true);
                case LEFT -> {
                    moveCaret(previousWord(caret), shift);
                    yield true;
                }
                case RIGHT -> {
                    moveCaret(nextWord(caret), shift);
                    yield true;
                }
                case HOME -> {
                    moveCaret(0, shift);
                    yield true;
                }
                case END -> {
                    moveCaret(text.length(), shift);
                    yield true;
                }
                default -> false;
            };
        }
        return switch (key) {
            case BACKSPACE -> deleteBackward(false);
            case DELETE -> deleteForward(false);
            case LEFT -> {
                if (!shift && hasSelection()) {
                    moveCaret(selectionStart(), false);
                } else {
                    moveCaret(previousIndex(caret), shift);
                }
                yield true;
            }
            case RIGHT -> {
                if (!shift && hasSelection()) {
                    moveCaret(selectionEnd(), false);
                } else {
                    moveCaret(nextIndex(caret), shift);
                }
                yield true;
            }
            case HOME -> {
                moveCaret(0, shift);
                yield true;
            }
            case END -> {
                moveCaret(text.length(), shift);
                yield true;
            }
            case ESCAPE -> {
                if (!hasSelection()) {
                    yield false;
                }
                clearSelection();
                yield true;
            }
            case ENTER, KEYPAD_ENTER -> {
                if (!repeat) {
                    submit();
                }
                yield true;
            }
            default -> false;
        };
    }

    /**
     * Inserts a typed Unicode code point at the caret, replacing the selection.
     * Control and surrogate characters are rejected because the field is single-line, and the
     * configured character restrictions and mask tokens are applied.
     *
     * @param codepoint typed code point
     * @return true when the event is consumed, including read-only and rejected characters
     */
    @Override
    protected boolean onCharTyped(int codepoint) {
        if (!isEnabled()) {
            return true;
        }
        if (readOnly) {
            return true;
        }
        if (codepoint < 0 || codepoint > Character.MAX_CODE_POINT
                || Character.isISOControl(codepoint)
                || (codepoint >= Character.MIN_SURROGATE && codepoint <= Character.MAX_SURROGATE)) {
            return false;
        }
        if (!allows(codepoint)) {
            return true;
        }
        insertText(new String(Character.toChars(codepoint)), false);
        return true;
    }

    /**
     * Resets the caret blink and cancels an unfinished selection drag when focus is lost.
     *
     * @param focused true when this widget gained focus
     */
    @Override
    protected void onFocusChanged(boolean focused) {
        if (!focused) {
            dragging = false;
            clickCount = 0;
        }
        resetBlink();
    }

    /**
     * Paints the state-aware field surface and border with analytic rounded rectangles.
     *
     * @param renderer drawing manager for the current frame
     * @param widgetStyle resolved style used for the field
     */
    protected final void drawSurface(RenderManager renderer, WidgetStyle widgetStyle) {
        WidgetState widgetState = state();
        Rect bounds = localBounds();
        float radius = widgetStyle.cornerRadius();
        float borderWidth = widgetStyle.borderWidth();
        if (borderWidth <= 0) {
            renderer.roundedRect(bounds, radius, widgetStyle.background(widgetState));
            return;
        }
        WidgetState borderState = widgetState == WidgetState.FOCUSED && !isFocusOutlineEnabled()
                ? WidgetState.NORMAL : widgetState;
        renderer.roundedRect(bounds, radius, widgetStyle.border(borderState));
        Rect inner = new Rect(borderWidth, borderWidth,
                Math.max(0, bounds.width() - borderWidth * 2),
                Math.max(0, bounds.height() - borderWidth * 2));
        if (!inner.isEmpty()) {
            renderer.roundedRect(inner, Math.max(0, radius - borderWidth),
                    widgetStyle.background(widgetState));
        }
    }

    private void drawContent(RenderManager renderer, FrameInfo frame, TextLayout layout,
                             Font font, WidgetStyle widgetStyle, String display,
                             int displayCaret, float textLeft, float textTop, float caretWidth) {
        Color textColor = widgetStyle.text(state());
        drawSelection(renderer, layout, widgetStyle,
                displayIndexForRaw(selectionStart()), displayIndexForRaw(selectionEnd()),
                textLeft, textTop);
        if (text.isEmpty()) {
            if (!placeholder.isEmpty()) {
                renderer.text(font, placeholder, textLeft, textTop, placeholderColor(widgetStyle),
                        TextStyle.DEFAULT);
            }
        } else {
            renderer.text(font, display, textLeft, textTop, textColor, TextStyle.DEFAULT);
        }
        if (!isEnabled() || !isFocused() || hasSelection()
                || !isCaretVisible(frame.elapsedSeconds())) {
            return;
        }
        float caretX = textLeft + layout.caretPosition(displayCaret).x();
        renderer.rect(new Rect(caretX, textTop, caretWidth, layout.lineHeight()), textColor);
    }

    /**
     * Paints selection rectangles for a display range.
     *
     * @param renderer drawing manager for the current frame
     * @param layout layout of the display text
     * @param widgetStyle resolved style used for the field
     * @param start selection start in display coordinates
     * @param end selection end in display coordinates
     * @param textLeft display text origin
     * @param textTop display text origin
     */
    protected final void drawSelection(RenderManager renderer, TextLayout layout,
                                       WidgetStyle widgetStyle, int start, int end,
                                       float textLeft, float textTop) {
        if (!isEnabled() || start == end) {
            return;
        }
        Color color = selectionColor;
        if (color == null) {
            UiContext context = context();
            color = context == null
                    ? widgetStyle.text(state()).withAlpha(SELECTION_OPACITY)
                    : context.theme().primary().withAlpha(SELECTION_OPACITY);
        }
        for (Rect rect : layout.selectionRects(start, end)) {
            renderer.rect(new Rect(textLeft + rect.x(), textTop + rect.y(),
                    rect.width(), rect.height()), color);
        }
    }

    /**
     * Resolves the placeholder color for the current interaction state.
     *
     * @param widgetStyle resolved style used for the field
     * @return the muted or disabled placeholder color
     */
    protected final Color placeholderColor(WidgetStyle widgetStyle) {
        if (!isEnabled()) {
            return widgetStyle.text(WidgetState.DISABLED);
        }
        UiContext context = context();
        return context == null ? widgetStyle.text(WidgetState.DISABLED) : context.theme().textMuted();
    }

    /**
     * Returns the content rectangle inside the field padding.
     * Subclasses can override this method to reserve space for embedded controls.
     *
     * @param widgetStyle resolved style used for the field
     * @return the content rectangle in widget-local coordinates, or an empty rectangle
     */
    protected Rect contentRect(WidgetStyle widgetStyle) {
        float padding = widgetStyle.padding();
        float width = localBounds().width() - padding * 2;
        float height = localBounds().height() - padding * 2;
        if (width <= 0 || height <= 0) {
            return Rect.EMPTY;
        }
        return new Rect(padding, padding, width, height);
    }

    private void ensureCaretVisible(TextLayout layout, float contentWidth, float caretWidth,
                                    int displayCaret) {
        if (contentWidth <= 0) {
            scrollX = 0;
            return;
        }
        float caretX = layout.caretPosition(displayCaret).x();
        float maxScroll = Math.max(0, layout.size().width() - contentWidth + caretWidth);
        if (maxScroll <= 0) {
            scrollX = 0;
            return;
        }
        if (caretX < scrollX) {
            scrollX = caretX;
        } else if (caretX + caretWidth - scrollX > contentWidth) {
            scrollX = caretX + caretWidth - contentWidth;
        }
        scrollX = Math.clamp(scrollX, 0, maxScroll);
    }

    /**
     * Maps a widget-local point to the closest caret index in the stored value.
     *
     * @param x horizontal coordinate relative to the widget
     * @param y vertical coordinate relative to the widget
     * @return the closest UTF-16 index in the raw value
     */
    protected int indexAt(float x, float y) {
        Font font = font();
        if (font == null) {
            return caret;
        }
        WidgetStyle widgetStyle = resolvedStyle();
        if (widgetStyle == null) {
            return caret;
        }
        Rect content = contentRect(widgetStyle);
        String display = displayText();
        TextLayout layout = font.layoutBlockRelative(display, TextStyle.DEFAULT);
        float textLeft = content.x() - scrollX;
        float textTop = content.y() + (content.height() - layout.size().height()) / 2;
        return clampIndex(rawIndexForDisplay(layout.caretIndexAt(x - textLeft, y - textTop)));
    }

    private void selectWordAt(int index) {
        if (text.isEmpty()) {
            anchor = 0;
            caret = 0;
            resetBlink();
            return;
        }
        int start;
        int end;
        if (index > 0 && isWordCodePoint(codePointBefore(index))) {
            start = wordStart(index);
            end = wordEnd(index);
        } else if (index < text.length() && isWordCodePoint(codePointAt(index))) {
            start = wordStart(index);
            end = wordEnd(index);
        } else {
            start = nonWordStart(index);
            end = nonWordEnd(index);
        }
        anchor = start;
        caret = end;
        resetBlink();
    }

    private int wordStart(int index) {
        int position = index;
        while (position > 0 && isWordCodePoint(codePointBefore(position))) {
            position = previousIndex(position);
        }
        return position;
    }

    private int wordEnd(int index) {
        int position = index;
        while (position < text.length() && isWordCodePoint(codePointAt(position))) {
            position = nextIndex(position);
        }
        return position;
    }

    private int nonWordStart(int index) {
        int position = index;
        while (position > 0 && !isWordCodePoint(codePointBefore(position))) {
            position = previousIndex(position);
        }
        return position;
    }

    private int nonWordEnd(int index) {
        int position = index;
        while (position < text.length() && !isWordCodePoint(codePointAt(position))) {
            position = nextIndex(position);
        }
        return position;
    }

    private int previousWord(int index) {
        int position = clampIndex(index);
        while (position > 0 && !isWordCodePoint(codePointBefore(position))) {
            position = previousIndex(position);
        }
        while (position > 0 && isWordCodePoint(codePointBefore(position))) {
            position = previousIndex(position);
        }
        return position;
    }

    private int nextWord(int index) {
        int position = clampIndex(index);
        if (position < text.length() && isWordCodePoint(codePointAt(position))) {
            while (position < text.length() && isWordCodePoint(codePointAt(position))) {
                position = nextIndex(position);
            }
        }
        while (position < text.length() && !isWordCodePoint(codePointAt(position))) {
            position = nextIndex(position);
        }
        return position;
    }

    /**
     * Moves the caret to the supplied raw index and optionally extends the selection.
     *
     * @param index UTF-16 index in the raw value
     * @param extend true to keep the selection anchor
     */
    protected final void moveCaret(int index, boolean extend) {
        caret = clampIndex(index);
        if (!extend) {
            anchor = caret;
        }
        resetBlink();
    }

    private boolean deleteBackward(boolean word) {
        if (!isEnabled() || readOnly) {
            return true;
        }
        if (hasSelection()) {
            deleteSelection();
            return true;
        }
        if (caret == 0) {
            return true;
        }
        int start = word ? previousWord(caret) : previousIndex(caret);
        applyEdit(text.substring(0, start) + text.substring(caret), start, start);
        return true;
    }

    private boolean deleteForward(boolean word) {
        if (!isEnabled() || readOnly) {
            return true;
        }
        if (hasSelection()) {
            deleteSelection();
            return true;
        }
        if (caret >= text.length()) {
            return true;
        }
        int end = word ? nextWord(caret) : nextIndex(caret);
        applyEdit(text.substring(0, caret) + text.substring(end), caret, caret);
        return true;
    }

    /**
     * Deletes the selected range from the stored value.
     */
    protected final void deleteSelection() {
        if (!isEnabled()) {
            return;
        }
        int start = selectionStart();
        int end = selectionEnd();
        applyEdit(text.substring(0, start) + text.substring(end), start, start);
    }

    /**
     * Inserts text at the caret, replacing the selection and honoring the mask and restrictions.
     * The history is updated when the value actually changes.
     *
     * @param value text to insert
     * @param pasted true when the text comes from the clipboard and may contain mask literals
     * @return true when the insertion is consumed
     */
    protected final boolean insertText(String value, boolean pasted) {
        if (!isEnabled() || readOnly) {
            return true;
        }
        int start = selectionStart();
        int end = selectionEnd();
        String sanitized = sanitize(value);
        if (sanitized.isEmpty() && start == end) {
            return false;
        }
        if (maskSlots.isEmpty()) {
            String insertion = truncateToCodePoints(filterAllowed(sanitized), allowance(start, end));
            if (insertion.isEmpty()) {
                return true;
            }
            String updated = text.substring(0, start) + insertion + text.substring(end);
            int position = start + insertion.length();
            applyEdit(updated, position, position);
            return true;
        }
        String before = text.substring(0, start);
        String after = text.substring(end);
        String extracted = pasted ? parseMaskedInput(sanitized, Integer.MAX_VALUE) : sanitized;
        String accepted = filterForMaskSlots(extracted, countCodePoints(before));
        accepted = truncateToCodePoints(accepted, allowance(start, end));
        if (accepted.isEmpty()) {
            return true;
        }
        String normalized = parseMaskedInput(before + accepted + after, valueLimit());
        int target = Math.min(countCodePoints(normalized),
                countCodePoints(before) + countCodePoints(accepted));
        int position = normalized.offsetByCodePoints(0, target);
        applyEdit(normalized, position, position);
        return true;
    }

    private int allowance(int start, int end) {
        if (maxLength == 0) {
            return Integer.MAX_VALUE;
        }
        int removed = countCodePoints(text.substring(start, end));
        return Math.max(0, maxLength - (countCodePoints(text) - removed));
    }

    private int valueLimit() {
        return maxLength == 0 ? Integer.MAX_VALUE : maxLength;
    }

    private String formattedText() {
        return formatText(false);
    }

    /**
     * Returns the visible text, applying the mask and secret mode.
     *
     * @return the display text
     */
    protected final String displayText() {
        return formatText(secret);
    }

    /**
     * Formats the stored value with the active mask and optional secret replacement.
     *
     * @param hideValues true to replace value characters with the secret character
     * @return the display text
     */
    protected final String formatText(boolean hideValues) {
        if (maskSlots.isEmpty()) {
            if (!hideValues) {
                return text;
            }
            StringBuilder hidden = new StringBuilder();
            for (int index = 0; index < text.length(); ) {
                int codepoint = text.codePointAt(index);
                index += Character.charCount(codepoint);
                if (isLineBreak(codepoint)) {
                    hidden.appendCodePoint(codepoint);
                } else {
                    hidden.append(secretCharacter);
                }
            }
            return hidden.toString();
        }
        if (text.isEmpty()) {
            return "";
        }
        StringBuilder formatted = new StringBuilder();
        int position = 0;
        for (MaskSlot slot : maskSlots) {
            if (slot.literal()) {
                formatted.append(slot.symbol());
                continue;
            }
            if (position >= text.length()) {
                break;
            }
            int codepoint = text.codePointAt(position);
            position += Character.charCount(codepoint);
            if (hideValues && !isLineBreak(codepoint)) {
                formatted.append(secretCharacter);
            } else {
                formatted.appendCodePoint(codepoint);
            }
        }
        return formatted.toString();
    }

    /**
     * Reports whether a code point must stay visible in secret mode.
     * The single-line field hides every character; multiline subclasses can preserve line breaks.
     *
     * @param codepoint code point to inspect
     * @return true when the code point is a structural line break
     */
    protected boolean isLineBreak(int codepoint) {
        return false;
    }

    private String parseMaskedInput(String input, int limit) {
        if (maskSlots.isEmpty()) {
            return truncateToCodePoints(filterAllowed(input), limit);
        }
        StringBuilder result = new StringBuilder();
        int inputIndex = 0;
        int written = 0;
        for (MaskSlot slot : maskSlots) {
            if (written >= limit) {
                break;
            }
            if (slot.literal()) {
                if (inputIndex < input.length()) {
                    int codepoint = input.codePointAt(inputIndex);
                    if (codepoint == slot.symbol()) {
                        inputIndex += Character.charCount(codepoint);
                    }
                }
                continue;
            }
            boolean accepted = false;
            while (inputIndex < input.length()) {
                int codepoint = input.codePointAt(inputIndex);
                inputIndex += Character.charCount(codepoint);
                if (allows(codepoint) && slot.accepts(codepoint)) {
                    result.appendCodePoint(codepoint);
                    written++;
                    accepted = true;
                    break;
                }
            }
            if (!accepted) {
                break;
            }
        }
        return result.toString();
    }

    private String filterForMaskSlots(String value, int filledTokens) {
        StringBuilder result = new StringBuilder();
        int maskIndex = maskIndexForRaw(filledTokens);
        int inputIndex = 0;
        while (maskIndex < maskSlots.size() && inputIndex < value.length()) {
            MaskSlot slot = maskSlots.get(maskIndex);
            if (slot.literal()) {
                maskIndex++;
                continue;
            }
            int codepoint = value.codePointAt(inputIndex);
            inputIndex += Character.charCount(codepoint);
            if (allows(codepoint) && slot.accepts(codepoint)) {
                result.appendCodePoint(codepoint);
                maskIndex++;
            }
        }
        return result.toString();
    }

    private int maskIndexForRaw(int filledTokens) {
        int tokens = 0;
        for (int index = 0; index < maskSlots.size(); index++) {
            if (maskSlots.get(index).literal()) {
                continue;
            }
            if (tokens == filledTokens) {
                return index;
            }
            tokens++;
        }
        return maskSlots.size();
    }

    private String filterAllowed(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); ) {
            int codepoint = value.codePointAt(index);
            index += Character.charCount(codepoint);
            if (allows(codepoint)) {
                result.appendCodePoint(codepoint);
            }
        }
        return result.toString();
    }

    /**
     * Reports whether the active restrictions accept a code point.
     *
     * @param codepoint code point to inspect
     * @return true when the code point may be stored
     */
    protected boolean allows(int codepoint) {
        return allowedCharacters.contains(kindOf(codepoint));
    }

    private static CharacterKind kindOf(int codepoint) {
        if (Character.isDigit(codepoint)) {
            return CharacterKind.DIGITS;
        }
        int type = Character.getType(codepoint);
        if (Character.isLetter(codepoint) || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK) {
            return CharacterKind.LETTERS;
        }
        return CharacterKind.SYMBOLS;
    }

    /**
     * Maps a raw caret index to the equivalent display index.
     *
     * @param rawIndex UTF-16 index in the stored value
     * @return the UTF-16 index in the display text
     */
    protected final int displayIndexForRaw(int rawIndex) {
        int clamped = clampIndex(rawIndex);
        if (maskSlots.isEmpty()) {
            return secret ? text.codePointCount(0, clamped) : clamped;
        }
        if (text.isEmpty()) {
            return 0;
        }
        int display = 0;
        int position = 0;
        for (MaskSlot slot : maskSlots) {
            if (slot.literal()) {
                display++;
                continue;
            }
            if (position >= clamped) {
                break;
            }
            int codepoint = text.codePointAt(position);
            position += Character.charCount(codepoint);
            display += secret ? 1 : Character.charCount(codepoint);
        }
        return display;
    }

    /**
     * Maps a display index to the equivalent raw caret index.
     *
     * @param displayIndex UTF-16 index in the display text
     * @return the closest UTF-16 index in the stored value
     */
    protected final int rawIndexForDisplay(int displayIndex) {
        int limit = displayText().length();
        int index = Math.clamp(displayIndex, 0, limit);
        if (maskSlots.isEmpty()) {
            if (!secret) {
                return clampIndex(index);
            }
            int codepoints = Math.min(index, text.codePointCount(0, text.length()));
            return text.offsetByCodePoints(0, codepoints);
        }
        if (text.isEmpty()) {
            return 0;
        }
        int display = 0;
        int position = 0;
        for (MaskSlot slot : maskSlots) {
            if (slot.literal()) {
                if (index <= display) {
                    return position;
                }
                display++;
                continue;
            }
            if (position >= text.length()) {
                return position;
            }
            if (index <= display) {
                return position;
            }
            int codepoint = text.codePointAt(position);
            int units = secret ? 1 : Character.charCount(codepoint);
            if (index < display + units) {
                return position;
            }
            display += units;
            position += Character.charCount(codepoint);
        }
        return position;
    }

    private static List<MaskSlot> parseMask(String value) {
        List<MaskSlot> slots = new ArrayList<>();
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (escaped) {
                slots.add(MaskSlot.literal(character));
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (isMaskToken(character)) {
                slots.add(MaskSlot.token(character));
            } else {
                slots.add(MaskSlot.literal(character));
            }
        }
        if (escaped) {
            slots.add(MaskSlot.literal('\\'));
        }
        return List.copyOf(slots);
    }

    private static boolean isMaskToken(char character) {
        return character == 'x' || character == '9' || character == 'a' || character == '*';
    }

    private record MaskSlot(boolean literal, char symbol) {
        private static MaskSlot literal(char symbol) {
            return new MaskSlot(true, symbol);
        }

        private static MaskSlot token(char symbol) {
            return new MaskSlot(false, symbol);
        }

        private boolean accepts(int codepoint) {
            if (literal) {
                return false;
            }
            return switch (symbol) {
                case 'x' -> true;
                case '9' -> Character.isDigit(codepoint);
                case 'a' -> Character.isLetter(codepoint);
                case '*' -> Character.isLetterOrDigit(codepoint);
                default -> false;
            };
        }
    }

    /**
     * Replaces the stored value and selection while recording an undo step.
     * Subclasses can use this for structural edits such as line indentation.
     *
     * @param value new stored value
     * @param newCaret new caret index
     * @param newAnchor new selection anchor
     */
    protected final void replaceValue(String value, int newCaret, int newAnchor) {
        if (!isEnabled() || readOnly) {
            return;
        }
        applyEdit(value, newCaret, newAnchor);
    }

    private void applyEdit(String updated, int newCaret, int newAnchor) {
        int nextCaret = clampIndex(updated, newCaret);
        int nextAnchor = clampIndex(updated, newAnchor);
        if (updated.equals(text) && nextCaret == caret && nextAnchor == anchor) {
            return;
        }
        pushHistory();
        text = updated;
        caret = nextCaret;
        anchor = nextAnchor;
        resetBlink();
        notifyChange();
    }

    private void pushHistory() {
        undoHistory.push(new EditState(text, caret, anchor));
        while (undoHistory.size() > MAX_HISTORY) {
            undoHistory.removeLast();
        }
        redoHistory.clear();
    }

    private boolean undo() {
        if (!isEnabled() || readOnly) {
            return true;
        }
        EditState state = undoHistory.poll();
        if (state == null) {
            return true;
        }
        redoHistory.push(new EditState(text, caret, anchor));
        while (redoHistory.size() > MAX_HISTORY) {
            redoHistory.removeLast();
        }
        restore(state);
        return true;
    }

    private boolean redo() {
        if (!isEnabled() || readOnly) {
            return true;
        }
        EditState state = redoHistory.poll();
        if (state == null) {
            return true;
        }
        undoHistory.push(new EditState(text, caret, anchor));
        while (undoHistory.size() > MAX_HISTORY) {
            undoHistory.removeLast();
        }
        restore(state);
        return true;
    }

    private void restore(EditState state) {
        boolean changed = !text.equals(state.text());
        text = state.text();
        caret = clampIndex(state.caret());
        anchor = clampIndex(state.anchor());
        scrollX = 0;
        resetBlink();
        if (changed) {
            notifyChange();
        }
    }

    private boolean copySelection() {
        if (!hasSelection()) {
            return false;
        }
        if (secret) {
            return true;
        }
        UiContext context = context();
        if (context == null) {
            return false;
        }
        context.setClipboardText(text.substring(selectionStart(), selectionEnd()));
        return true;
    }

    private boolean cutSelection() {
        if (!hasSelection()) {
            return false;
        }
        if (secret || readOnly || !isEnabled()) {
            return true;
        }
        copySelection();
        deleteSelection();
        return true;
    }

    private boolean paste() {
        UiContext context = context();
        if (context == null) {
            return false;
        }
        if (readOnly || !isEnabled()) {
            return true;
        }
        insertText(context.clipboardText(), true);
        return true;
    }

    /**
     * Invokes the submit callback when the field is enabled.
     */
    protected final void submit() {
        if (!isEnabled()) {
            return;
        }
        if (onSubmit != null) {
            onSubmit.run();
        }
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.accept(text());
        }
    }

    private void resetBlink() {
        blinkStart = lastElapsed;
    }

    /**
     * Reports whether the blinking caret is in its visible phase.
     *
     * @param elapsedSeconds current elapsed frame time
     * @return true when the caret should be painted
     */
    protected final boolean isCaretVisible(double elapsedSeconds) {
        double phase = (elapsedSeconds - blinkStart) % CARET_BLINK_PERIOD;
        if (phase < 0) {
            phase += CARET_BLINK_PERIOD;
        }
        return phase < CARET_BLINK_HALF_PERIOD;
    }

    /**
     * Returns the caret stroke width for a font.
     *
     * @param font font used by the field
     * @return the caret width in logical UI units
     */
    protected static float caretWidth(Font font) {
        return Math.max(1, font.pixelSize() * 0.075f);
    }

    private int previousIndex(int index) {
        int position = clampIndex(index);
        if (position <= 0) {
            return 0;
        }
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);
        return iterator.preceding(position);
    }

    private int nextIndex(int index) {
        int position = clampIndex(index);
        if (position >= text.length()) {
            return text.length();
        }
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);
        int next = iterator.following(position);
        return next == BreakIterator.DONE ? text.length() : next;
    }

    private void trackClick(float x, float y) {
        double now = lastElapsed;
        boolean sameSpot = now - lastPressTime <= MULTI_CLICK_SECONDS
                && Math.hypot(x - lastPressX, y - lastPressY) <= MULTI_CLICK_SLOP;
        clickCount = sameSpot ? clickCount + 1 : 1;
        lastPressTime = now;
        lastPressX = x;
        lastPressY = y;
    }

    private int codePointAt(int index) {
        return index >= 0 && index < text.length() ? text.codePointAt(index) : -1;
    }

    private int codePointBefore(int index) {
        return index > 0 && index <= text.length() ? text.codePointBefore(index) : -1;
    }

    private int clampIndex(int index) {
        return clampIndex(text, index);
    }

    private static int clampIndex(String value, int index) {
        int clamped = Math.clamp(index, 0, value.length());
        if (clamped > 0 && clamped < value.length()
                && Character.isLowSurrogate(value.charAt(clamped))
                && Character.isHighSurrogate(value.charAt(clamped - 1))) {
            return clamped - 1;
        }
        return clamped;
    }

    private static boolean isWordCodePoint(int codepoint) {
        if (codepoint < 0) {
            return false;
        }
        int type = Character.getType(codepoint);
        return Character.isLetterOrDigit(codepoint) || codepoint == '_'
                || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    /**
     * Normalizes entered text before it is stored or edited.
     * The default implementation collapses line breaks and tabs to spaces, because this field is
     * single-line. Subclasses can preserve other whitespace.
     *
     * @param value text to normalize
     * @return the normalized text
     */
    protected String sanitize(String value) {
        return normalizeSingleLine(value);
    }

    private static String normalizeSingleLine(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); ) {
            int codepoint = value.codePointAt(index);
            index += Character.charCount(codepoint);
            if (codepoint == '\r' || codepoint == '\n' || codepoint == '\t') {
                if (result.isEmpty() || result.charAt(result.length() - 1) != ' ') {
                    result.append(' ');
                }
            } else if (!Character.isISOControl(codepoint)) {
                result.appendCodePoint(codepoint);
            }
        }
        return result.toString();
    }

    private static String truncateToCodePoints(String value, int maxCodePoints) {
        if (countCodePoints(value) <= maxCodePoints) {
            return value;
        }
        if (maxCodePoints <= 0) {
            return "";
        }
        int end = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, end);
    }

    private static int countCodePoints(String value) {
        return value.codePointCount(0, value.length());
    }

    private record EditState(String text, int caret, int anchor) {
    }
}
