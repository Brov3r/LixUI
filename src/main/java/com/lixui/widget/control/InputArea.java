package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextLayout;
import com.lixui.text.TextLine;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;

/**
 * A focusable multiline text area with soft wrapping, scrolling and the editing features of
 * {@link Input}.
 * The area preserves explicit line breaks and supports wrapped lines, vertical navigation,
 * Home and End per line, page navigation, selection, clipboard shortcuts, undo and redo. Masks,
 * secret mode and character restrictions inherited from {@link Input} apply to the stored value;
 * line breaks remain structural and stay visible in secret mode.
 */
public class InputArea extends Input {
    private static final float DEFAULT_CONTENT_WIDTH = 240;
    private static final int DEFAULT_VISIBLE_LINES = 4;
    private static final float WHEEL_LINE_STEP = 3;

    private boolean wrap = true;
    private float verticalScroll;
    private float horizontalScroll;

    /**
     * Creates an empty text area.
     */
    public InputArea() {
        super();
    }

    /**
     * Creates a text area with initial text and the caret at the end.
     *
     * @param text initial text
     * @throws NullPointerException if {@code text} is null
     */
    public InputArea(String text) {
        super();
        setText(text);
    }

    /**
     * Reports whether long lines wrap at the text area width.
     *
     * @return true when soft wrapping is enabled
     */
    public final boolean isWrap() {
        return wrap;
    }

    /**
     * Enables or disables soft wrapping of long lines.
     * Disabling wrapping switches the area to horizontal scrolling and resets the horizontal
     * scroll position.
     *
     * @param wrap true to wrap long lines
     */
    public final void setWrap(boolean wrap) {
        if (this.wrap == wrap) {
            return;
        }
        this.wrap = wrap;
        horizontalScroll = 0;
    }

    /**
     * Normalizes entered text while preserving explicit line breaks and tabs.
     * Carriage returns and carriage-return/line-feed pairs become a single newline. Other control
     * characters are removed.
     *
     * @param value text to normalize
     * @return the normalized text
     */
    @Override
    protected String sanitize(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); ) {
            int codepoint = value.codePointAt(index);
            index += Character.charCount(codepoint);
            if (codepoint == '\r') {
                if (index < value.length() && value.charAt(index) == '\n') {
                    index++;
                }
                result.append('\n');
            } else if (codepoint == '\n' || codepoint == '\t'
                    || !Character.isISOControl(codepoint)) {
                result.appendCodePoint(codepoint);
            }
        }
        return result.toString();
    }

    /**
     * Accepts structural whitespace in addition to the configured character restrictions.
     *
     * @param codepoint code point to inspect
     * @return true for newlines, tabs and allowed characters
     */
    @Override
    protected boolean allows(int codepoint) {
        return codepoint == '\n' || codepoint == '\t' || super.allows(codepoint);
    }

    /**
     * Preserves newlines while masking secret characters.
     *
     * @param codepoint code point to inspect
     * @return true for the newline code point
     */
    @Override
    protected boolean isLineBreak(int codepoint) {
        return codepoint == '\n';
    }

    /**
     * Measures a preferred four-line area using the available padding.
     *
     * @param constraints available and required dimensions
     * @return the natural text area size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        WidgetStyle widgetStyle = resolvedStyle();
        if (widgetStyle == null) {
            return super.onMeasure(constraints);
        }
        Font font = font();
        float padding = widgetStyle.padding();
        float contentWidth = DEFAULT_CONTENT_WIDTH;
        float contentHeight = 0;
        if (font != null) {
            contentHeight = font.lineHeight() * DEFAULT_VISIBLE_LINES;
            if (!wrap) {
                contentWidth = Math.max(contentWidth, font.measure(displayText()).width());
            }
            if (rawText().isEmpty()) {
                contentWidth = Math.max(contentWidth, font.measure(placeholder()).width());
            }
        }
        return new Size(contentWidth + padding * 2, contentHeight + padding * 2);
    }

    /**
     * Paints the surface, selection, wrapped text, placeholder and blinking caret.
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
        TextStyle textStyle = textStyle(content);
        TextLayout layout = font.layoutBlockRelative(displayText(), textStyle);
        ensureCaretVisible(layout, content, font);
        float textLeft = content.x() - horizontalScroll;
        float textTop = content.y() - verticalScroll;
        int displayCaret = displayIndexForRaw(caretIndex());
        Point origin = absolutePosition();
        Rect contentClip = new Rect(origin.x() + content.x(), origin.y() + content.y(),
                content.width(), content.height());
        renderer.withClip(contentClip, () -> drawContent(renderer, frame, layout, font, widgetStyle,
                textStyle, displayCaret, textLeft, textTop));
    }

    /**
     * Maps a widget-local point to the closest caret index, honoring wrapping and scrolling.
     *
     * @param x horizontal coordinate relative to the widget
     * @param y vertical coordinate relative to the widget
     * @return the closest UTF-16 index in the raw value
     */
    @Override
    protected int indexAt(float x, float y) {
        Font font = font();
        WidgetStyle widgetStyle = resolvedStyle();
        if (font == null || widgetStyle == null) {
            return caretIndex();
        }
        Rect content = contentRect(widgetStyle);
        if (content.isEmpty()) {
            return caretIndex();
        }
        TextLayout layout = font.layoutBlockRelative(displayText(), textStyle(content));
        float textLeft = content.x() - horizontalScroll;
        float textTop = content.y() - verticalScroll;
        return rawIndexForDisplay(layout.caretIndexAt(x - textLeft, y - textTop));
    }

    /**
     * Applies multiline navigation and delegates editing shortcuts to the single-line editor.
     * Enter inserts a newline, Ctrl+Enter submits, arrow keys move vertically, Home and End work per
     * line, and Ctrl+Home/Ctrl+End move to the document edges.
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
        if ((key == Key.ENTER || key == Key.KEYPAD_ENTER) && command) {
            if (!repeat) {
                submit();
            }
            return true;
        }
        switch (key) {
            case ENTER, KEYPAD_ENTER -> {
                insertText("\n", false);
                return true;
            }
            case UP -> {
                moveVertically(-1, shift);
                return true;
            }
            case DOWN -> {
                moveVertically(1, shift);
                return true;
            }
            case PAGE_UP -> {
                moveVertically(-visibleLines(), shift);
                return true;
            }
            case PAGE_DOWN -> {
                moveVertically(visibleLines(), shift);
                return true;
            }
            case HOME -> {
                moveToLineEdge(true, command, shift);
                return true;
            }
            case END -> {
                moveToLineEdge(false, command, shift);
                return true;
            }
            default -> {
                return super.onKeyPressed(key, modifiers, repeat);
            }
        }
    }

    /**
     * Inserts line breaks typed as characters and delegates all other text to the editor.
     *
     * @param codepoint typed code point
     * @return true when the event is consumed
     */
    @Override
    protected boolean onCharTyped(int codepoint) {
        if (codepoint == '\r' || codepoint == '\n') {
            if (!isEnabled() || isReadOnly()) {
                return true;
            }
            insertText("\n", false);
            return true;
        }
        return super.onCharTyped(codepoint);
    }

    /**
     * Scrolls the text vertically and, when wrapping is disabled, horizontally.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param deltaX horizontal scroll amount in wheel steps
     * @param deltaY vertical scroll amount in wheel steps
     * @return true when the scroll position changed
     */
    @Override
    protected boolean onPointerScroll(float x, float y, float deltaX, float deltaY) {
        if (!isEnabled()) {
            return false;
        }
        Font font = font();
        WidgetStyle widgetStyle = resolvedStyle();
        if (font == null || widgetStyle == null) {
            return false;
        }
        Rect content = contentRect(widgetStyle);
        if (content.isEmpty()) {
            return false;
        }
        TextLayout layout = font.layoutBlockRelative(displayText(), textStyle(content));
        float previousY = verticalScroll;
        float previousX = horizontalScroll;
        if (deltaY != 0) {
            float max = Math.max(0, layout.size().height() - content.height());
            verticalScroll = Math.clamp(verticalScroll
                    - deltaY * layout.lineHeight() * WHEEL_LINE_STEP, 0, max);
        }
        if (deltaX != 0 && !wrap) {
            float max = Math.max(0, layout.size().width() - content.width());
            horizontalScroll = Math.clamp(horizontalScroll + deltaX * 24, 0, max);
        }
        return verticalScroll != previousY || horizontalScroll != previousX;
    }

    private void drawContent(RenderManager renderer, FrameInfo frame, TextLayout layout,
                             Font font, WidgetStyle widgetStyle, TextStyle textStyle,
                             int displayCaret, float textLeft, float textTop) {
        Color textColor = widgetStyle.text(state());
        drawSelection(renderer, layout, widgetStyle,
                displayIndexForRaw(selectionStart()), displayIndexForRaw(selectionEnd()),
                textLeft, textTop);
        if (rawText().isEmpty()) {
            if (!placeholder().isEmpty()) {
                renderer.text(font, placeholder(), textLeft, textTop,
                        placeholderColor(widgetStyle), textStyle);
            }
        } else {
            renderer.text(font, displayText(), textLeft, textTop, textColor, textStyle);
        }
        if (!isEnabled() || !isFocused() || hasSelection()
                || !isCaretVisible(frame.elapsedSeconds())) {
            return;
        }
        Point caret = layout.caretPosition(displayCaret);
        renderer.rect(new Rect(textLeft + caret.x(), textTop + caret.y(),
                caretWidth(font), layout.lineHeight()), textColor);
    }

    private TextStyle textStyle(Rect content) {
        return new TextStyle(wrap ? content.width() : 0, 0, 0, TextOverflow.CLIP,
                HorizontalAlignment.LEFT, VerticalAlignment.TOP);
    }

    private void ensureCaretVisible(TextLayout layout, Rect content, Font font) {
        if (content.isEmpty()) {
            return;
        }
        Point caret = layout.caretPosition(displayIndexForRaw(caretIndex()));
        float maxY = Math.max(0, layout.size().height() - content.height());
        if (caret.y() < verticalScroll) {
            verticalScroll = caret.y();
        } else if (caret.y() + layout.lineHeight() > verticalScroll + content.height()) {
            verticalScroll = caret.y() + layout.lineHeight() - content.height();
        }
        verticalScroll = Math.clamp(verticalScroll, 0, maxY);
        if (wrap) {
            horizontalScroll = 0;
            return;
        }
        float width = caretWidth(font);
        float maxX = Math.max(0, layout.size().width() - content.width() + width);
        if (caret.x() < horizontalScroll) {
            horizontalScroll = caret.x();
        } else if (caret.x() + width - horizontalScroll > content.width()) {
            horizontalScroll = caret.x() + width - content.width();
        }
        horizontalScroll = Math.clamp(horizontalScroll, 0, maxX);
    }

    private void moveVertically(int lineDelta, boolean extend) {
        Font font = font();
        WidgetStyle widgetStyle = resolvedStyle();
        if (font == null || widgetStyle == null) {
            return;
        }
        Rect content = contentRect(widgetStyle);
        if (content.isEmpty()) {
            return;
        }
        String display = displayText();
        TextLayout layout = font.layoutBlockRelative(display, textStyle(content));
        Point caret = layout.caretPosition(displayIndexForRaw(caretIndex()));
        int lineIndex = lineIndexAt(layout, caret.y());
        int targetLine = lineIndex + lineDelta;
        int displayTarget;
        if (targetLine < 0) {
            displayTarget = 0;
        } else if (targetLine >= layout.lines().size()) {
            displayTarget = display.length();
        } else {
            TextLine line = layout.lines().get(targetLine);
            displayTarget = layout.caretIndexAt(caret.x(), line.y() + layout.lineHeight() / 2);
        }
        moveCaret(rawIndexForDisplay(displayTarget), extend);
    }

    private void moveToLineEdge(boolean start, boolean command, boolean extend) {
        Font font = font();
        WidgetStyle widgetStyle = resolvedStyle();
        if (font == null || widgetStyle == null) {
            return;
        }
        Rect content = contentRect(widgetStyle);
        if (content.isEmpty()) {
            return;
        }
        String display = displayText();
        int displayTarget;
        if (command) {
            displayTarget = start ? 0 : display.length();
        } else {
            TextLayout layout = font.layoutBlockRelative(display, textStyle(content));
            Point caret = layout.caretPosition(displayIndexForRaw(caretIndex()));
            TextLine line = layout.lines().get(lineIndexAt(layout, caret.y()));
            displayTarget = start ? line.start() : line.end();
        }
        moveCaret(rawIndexForDisplay(displayTarget), extend);
    }

    private int visibleLines() {
        Font font = font();
        WidgetStyle widgetStyle = resolvedStyle();
        if (font == null || widgetStyle == null) {
            return 1;
        }
        Rect content = contentRect(widgetStyle);
        if (content.isEmpty()) {
            return 1;
        }
        return Math.max(1, (int) Math.floor(content.height() / font.lineHeight()));
    }

    private static int lineIndexAt(TextLayout layout, float y) {
        if (layout.lines().size() <= 1) {
            return 0;
        }
        float top = layout.lines().getFirst().y();
        int index = (int) Math.floor((y - top) / layout.lineHeight());
        return Math.clamp(index, 0, layout.lines().size() - 1);
    }
}
