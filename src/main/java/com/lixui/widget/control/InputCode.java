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
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A code editor built on {@link InputArea} with a line-number gutter, current-line highlighting
 * and configurable syntax highlighting.
 * Wrapping is disabled by default so logical lines stay aligned with the gutter. The highlighter
 * understands Java and C-like comments, strings, numbers, annotations, type and method names,
 * constants and operators, with a configurable keyword set and per-category colors. Tab inserts
 * indentation and Shift+Tab outdents the selected lines, while Ctrl+Tab keeps focus traversal.
 * Gutter padding, colors and every visual feature can be replaced independently.
 */
public class InputCode extends InputArea {
    private static final float DEFAULT_GUTTER_PADDING = 8;
    private static final float DEFAULT_CURRENT_LINE_ALPHA = 0.08f;
    private static final Set<String> DEFAULT_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "permits", "private", "protected", "public", "record",
            "return", "sealed", "short", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "try", "var", "void",
            "volatile", "while", "yield", "true", "false", "null");

    /**
     * Colors used by the syntax highlighter.
     *
     * @param keyword keyword color
     * @param string string literal color
     * @param number numeric literal color
     * @param comment comment color
     * @param annotation annotation color
     * @param type class, interface and other uppercase type-name color
     * @param method method name color
     * @param constant constant-name color
     * @param operator punctuation and operator color
     */
    public record SyntaxColors(Color keyword, Color string, Color number, Color comment,
                               Color annotation, Color type, Color method, Color constant,
                               Color operator) {
        /**
         * Creates a syntax palette.
         *
         * @param keyword keyword color
         * @param string string literal color
         * @param number numeric literal color
         * @param comment comment color
         * @param annotation annotation color
         * @param type class, interface and other uppercase type-name color
         * @param method method name color
         * @param constant constant-name color
         * @param operator punctuation and operator color
         * @throws NullPointerException if a color is null
         */
        public SyntaxColors {
            Objects.requireNonNull(keyword, "keyword");
            Objects.requireNonNull(string, "string");
            Objects.requireNonNull(number, "number");
            Objects.requireNonNull(comment, "comment");
            Objects.requireNonNull(annotation, "annotation");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(constant, "constant");
            Objects.requireNonNull(operator, "operator");
        }

        /**
         * Creates a compact palette that reuses the supplied colors for the additional categories.
         *
         * @param keyword keyword color
         * @param string string literal color
         * @param number numeric literal color
         * @param comment comment color
         * @param annotation annotation color
         * @throws NullPointerException if a color is null
         */
        public SyntaxColors(Color keyword, Color string, Color number, Color comment,
                            Color annotation) {
            this(keyword, string, number, comment, annotation, annotation, keyword, number, comment);
        }

        /**
         * Creates a readable default palette for a theme.
         *
         * @param theme active theme
         * @return the default syntax colors
         * @throws NullPointerException if {@code theme} is null
         */
        public static SyntaxColors defaults(Theme theme) {
            Objects.requireNonNull(theme, "theme");
            boolean light = theme.background().luminance() > 0.5f;
            return new SyntaxColors(
                    theme.primary(),
                    light ? Color.hex("#2E7D32") : Color.hex("#A5D6A7"),
                    light ? Color.hex("#1565C0") : Color.hex("#90CAF9"),
                    theme.textMuted(),
                    light ? Color.hex("#6A1B9A") : Color.hex("#CE93D8"),
                    light ? Color.hex("#B26A00") : Color.hex("#E5C07B"),
                    light ? Color.hex("#00695C") : Color.hex("#66D9EF"),
                    light ? Color.hex("#C2185B") : Color.hex("#F48FB1"),
                    light ? Color.hex("#5C6673") : Color.hex("#B0BEC5"));
        }
    }

    private boolean showLineNumbers = true;
    private boolean syntaxHighlighting = true;
    private boolean currentLineHighlight = true;
    private boolean tabInsertsIndentation = true;
    private boolean useSpacesForTabs = true;
    private int tabSize = 4;
    private float gutterPadding = DEFAULT_GUTTER_PADDING;
    private Set<String> keywords = new LinkedHashSet<>(DEFAULT_KEYWORDS);
    private SyntaxColors syntaxColors;
    private Color lineNumberColor;
    private Color currentLineNumberColor;
    private Color currentLineColor;
    private Color gutterBackgroundColor;

    /**
     * Creates an empty code editor.
     */
    public InputCode() {
        super();
        setWrap(false);
    }

    /**
     * Creates a code editor with initial text and the caret at the end.
     *
     * @param text initial code
     * @throws NullPointerException if {@code text} is null
     */
    public InputCode(String text) {
        super();
        setWrap(false);
        setText(text);
    }

    /**
     * Reports whether the line-number gutter is shown.
     *
     * @return true when line numbers are painted
     */
    public final boolean isShowLineNumbers() {
        return showLineNumbers;
    }

    /**
     * Shows or hides the line-number gutter.
     *
     * @param showLineNumbers true to reserve and paint the gutter
     */
    public final void setShowLineNumbers(boolean showLineNumbers) {
        this.showLineNumbers = showLineNumbers;
    }

    /**
     * Reports whether syntax highlighting is enabled.
     *
     * @return true when tokens are painted with syntax colors
     */
    public final boolean isSyntaxHighlighting() {
        return syntaxHighlighting;
    }

    /**
     * Enables or disables syntax highlighting.
     *
     * @param syntaxHighlighting true to highlight code tokens
     */
    public final void setSyntaxHighlighting(boolean syntaxHighlighting) {
        this.syntaxHighlighting = syntaxHighlighting;
    }

    /**
     * Reports whether the caret line receives a subtle background highlight.
     *
     * @return true when the current line is highlighted
     */
    public final boolean isCurrentLineHighlight() {
        return currentLineHighlight;
    }

    /**
     * Enables or disables the current-line background highlight.
     *
     * @param currentLineHighlight true to highlight the caret line while focused
     */
    public final void setCurrentLineHighlight(boolean currentLineHighlight) {
        this.currentLineHighlight = currentLineHighlight;
    }

    /**
     * Returns the configured keyword set.
     *
     * @return an immutable snapshot of the keywords
     */
    public final Set<String> keywords() {
        return Set.copyOf(keywords);
    }

    /**
     * Replaces the keywords highlighted as language keywords.
     *
     * @param keywords new keywords
     * @throws NullPointerException if the collection or an entry is null
     */
    public final void setKeywords(Collection<String> keywords) {
        Objects.requireNonNull(keywords, "keywords");
        Set<String> copy = new LinkedHashSet<>();
        for (String keyword : keywords) {
            copy.add(Objects.requireNonNull(keyword, "keyword"));
        }
        this.keywords = copy;
    }

    /**
     * Returns the explicit syntax palette.
     *
     * @return the palette, or null when theme defaults are used
     */
    public final SyntaxColors syntaxColors() {
        return syntaxColors;
    }

    /**
     * Assigns a syntax palette.
     *
     * @param syntaxColors palette to use, or null to restore theme defaults
     */
    public final void setSyntaxColors(SyntaxColors syntaxColors) {
        this.syntaxColors = syntaxColors;
    }

    /**
     * Returns the explicit line-number color.
     *
     * @return the color, or null when the muted theme color is used
     */
    public final Color lineNumberColor() {
        return lineNumberColor;
    }

    /**
     * Assigns the line-number color.
     *
     * @param lineNumberColor color, or null to use the muted theme color
     */
    public final void setLineNumberColor(Color lineNumberColor) {
        this.lineNumberColor = lineNumberColor;
    }

    /**
     * Returns the explicit current-line number color.
     *
     * @return the color, or null when the primary text color is used
     */
    public final Color currentLineNumberColor() {
        return currentLineNumberColor;
    }

    /**
     * Assigns the current-line number color.
     *
     * @param currentLineNumberColor color, or null to use the primary text color
     */
    public final void setCurrentLineNumberColor(Color currentLineNumberColor) {
        this.currentLineNumberColor = currentLineNumberColor;
    }

    /**
     * Returns the explicit current-line highlight color.
     *
     * @return the color, or null when a translucent theme accent is used
     */
    public final Color currentLineColor() {
        return currentLineColor;
    }

    /**
     * Assigns the current-line highlight color.
     *
     * @param currentLineColor color, or null to use a translucent theme accent
     */
    public final void setCurrentLineColor(Color currentLineColor) {
        this.currentLineColor = currentLineColor;
    }

    /**
     * Returns the explicit gutter background color.
     *
     * @return the color, or null when a translucent theme background is used
     */
    public final Color gutterBackgroundColor() {
        return gutterBackgroundColor;
    }

    /**
     * Assigns the gutter background color.
     *
     * @param gutterBackgroundColor color, or null to use a translucent theme background
     */
    public final void setGutterBackgroundColor(Color gutterBackgroundColor) {
        this.gutterBackgroundColor = gutterBackgroundColor;
    }

    /**
     * Returns the horizontal padding inside the line-number gutter.
     *
     * @return the padding in logical UI units
     */
    public final float gutterPadding() {
        return gutterPadding;
    }

    /**
     * Assigns the horizontal padding inside the line-number gutter.
     *
     * @param gutterPadding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is negative or non-finite
     */
    public final void setGutterPadding(float gutterPadding) {
        if (!Float.isFinite(gutterPadding) || gutterPadding < 0) {
            throw new IllegalArgumentException("Gutter padding must be finite and non-negative.");
        }
        this.gutterPadding = gutterPadding;
    }

    /**
     * Reports whether the Tab key inserts indentation instead of moving focus.
     *
     * @return true when Tab is consumed by the editor
     */
    public final boolean isTabInsertsIndentation() {
        return tabInsertsIndentation;
    }

    /**
     * Enables or disables Tab indentation.
     * Disabling makes Tab move focus to the next widget as usual. Ctrl+Tab always moves focus while
     * indentation is enabled.
     *
     * @param tabInsertsIndentation true to insert indentation on Tab
     */
    public final void setTabInsertsIndentation(boolean tabInsertsIndentation) {
        this.tabInsertsIndentation = tabInsertsIndentation;
    }

    /**
     * Reports whether indentation uses spaces.
     *
     * @return true for spaces, false for a tab character
     */
    public final boolean isUseSpacesForTabs() {
        return useSpacesForTabs;
    }

    /**
     * Enables or disables space-based indentation.
     *
     * @param useSpacesForTabs true to indent with spaces, false to insert tab characters
     */
    public final void setUseSpacesForTabs(boolean useSpacesForTabs) {
        this.useSpacesForTabs = useSpacesForTabs;
    }

    /**
     * Returns the indentation width for space-based indentation.
     *
     * @return the positive number of spaces per indentation level
     */
    public final int tabSize() {
        return tabSize;
    }

    /**
     * Assigns the indentation width for space-based indentation.
     *
     * @param tabSize positive number of spaces per indentation level
     * @throws IllegalArgumentException if the size is not positive
     */
    public final void setTabSize(int tabSize) {
        if (tabSize <= 0) {
            throw new IllegalArgumentException("Tab size must be positive.");
        }
        this.tabSize = tabSize;
    }

    /**
     * Returns the indentation string inserted by Tab.
     *
     * @return spaces or a single tab character according to the editor settings
     */
    public final String indentation() {
        return useSpacesForTabs ? " ".repeat(tabSize) : "\t";
    }

    /**
     * Handles Tab indentation and delegates all other keys to the text area.
     * Shift+Tab outdents the selected lines or the caret line. Ctrl+Tab (or Command+Tab) keeps the
     * normal focus traversal.
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
        if (key == Key.TAB) {
            boolean command = modifiers.superKey()
                    || (modifiers.control() && !modifiers.alt());
            if (!tabInsertsIndentation || command) {
                return false;
            }
            if (modifiers.shift()) {
                outdentSelection();
            } else {
                indentSelection();
            }
            return true;
        }
        return super.onKeyPressed(key, modifiers, repeat);
    }

    /**
     * Measures the text area and adds the line-number gutter width.
     *
     * @param constraints available and required dimensions
     * @return the natural code editor size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Size base = super.onMeasure(constraints);
        return new Size(base.width() + gutterWidth(font(), resolvedStyle()), base.height());
    }

    /**
     * Excludes the line-number gutter from the editable text rectangle.
     *
     * @param widgetStyle resolved style used for the field
     * @return the text rectangle in widget-local coordinates
     */
    @Override
    protected Rect textRect(WidgetStyle widgetStyle) {
        Rect content = super.textRect(widgetStyle);
        float gutter = gutterWidth(font(), widgetStyle);
        if (gutter <= 0 || content.isEmpty()) {
            return content;
        }
        return new Rect(content.x() + gutter, content.y(),
                Math.max(0, content.width() - gutter), content.height());
    }

    /**
     * Paints the editor and then the line-number gutter.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        super.onDraw(renderer, frame);
        drawGutter(renderer);
    }

    /**
     * Paints a subtle background behind the current caret line.
     *
     * @param renderer drawing manager for the current frame
     * @param layout layout of the display text
     * @param widgetStyle resolved style used for the field
     * @param content text rectangle inside the gutter
     * @param textLeft display text origin
     * @param textTop display text origin
     */
    @Override
    protected void drawTextBackground(RenderManager renderer, TextLayout layout,
                                      WidgetStyle widgetStyle, Rect content,
                                      float textLeft, float textTop) {
        if (!currentLineHighlight || !isEnabled() || !isFocused() || content.isEmpty()) {
            return;
        }
        Color color = currentLineColor;
        if (color == null) {
            Theme theme = context() == null ? null : context().theme();
            color = theme == null ? null : theme.primary().withAlpha(DEFAULT_CURRENT_LINE_ALPHA);
        }
        if (color == null) {
            return;
        }
        int lineIndex = lineIndexAt(layout,
                layout.caretPosition(displayIndexForRaw(caretIndex())).y());
        TextLine line = layout.lines().get(lineIndex);
        renderer.rect(new Rect(textLeft, textTop + line.y(), content.width(),
                layout.lineHeight()), color);
    }

    /**
     * Paints code tokens with the configured syntax palette.
     *
     * @param renderer drawing manager for the current frame
     * @param font font used for the text
     * @param layout layout of the display text
     * @param textStyle layout style used for the text
     * @param display display text to paint
     * @param textColor default text color
     * @param textLeft display text origin
     * @param textTop display text origin
     */
    @Override
    protected void drawText(RenderManager renderer, Font font, TextLayout layout,
                            TextStyle textStyle, String display, Color textColor,
                            float textLeft, float textTop) {
        if (!syntaxHighlighting || isSecret() || mask() != null || !isEnabled()
                || display.isEmpty()) {
            super.drawText(renderer, font, layout, textStyle, display, textColor,
                    textLeft, textTop);
            return;
        }
        Theme theme = context() == null ? null : context().theme();
        if (theme == null) {
            super.drawText(renderer, font, layout, textStyle, display, textColor,
                    textLeft, textTop);
            return;
        }
        SyntaxColors colors = syntaxColors == null ? SyntaxColors.defaults(theme) : syntaxColors;
        List<CodeSpan> spans = tokenize(display);
        int spanIndex = 0;
        for (TextLine line : layout.lines()) {
            int lineStart = line.start();
            int lineEnd = line.end();
            if (lineStart >= lineEnd) {
                continue;
            }
            while (spanIndex < spans.size() && spans.get(spanIndex).end() <= lineStart) {
                spanIndex++;
            }
            float lineY = textTop + line.y();
            for (int index = spanIndex;
                    index < spans.size() && spans.get(index).start() < lineEnd; index++) {
                CodeSpan span = spans.get(index);
                int start = Math.max(span.start(), lineStart);
                int end = Math.min(span.end(), lineEnd);
                if (start >= end) {
                    continue;
                }
                float x = textLeft + line.x()
                        + font.measure(display.substring(lineStart, start)).width();
                if (start > lineStart) {
                    x += font.letterSpacing()
                            + font.kerning(display.codePointBefore(start),
                            display.codePointAt(start));
                }
                renderer.text(font, display.substring(start, end), x, lineY,
                        colorFor(span.kind(), textColor, colors), TextStyle.DEFAULT);
            }
        }
    }

    private void drawGutter(RenderManager renderer) {
        if (!showLineNumbers) {
            return;
        }
        Font font = font();
        WidgetStyle widgetStyle = resolvedStyle();
        if (font == null || widgetStyle == null) {
            return;
        }
        Rect content = contentRect(widgetStyle);
        float gutter = gutterWidth(font, widgetStyle);
        if (content.isEmpty() || gutter <= 0) {
            return;
        }
        Theme theme = context() == null ? null : context().theme();
        Rect gutterRect = new Rect(content.x(), content.y(), gutter, content.height());
        Color background = gutterBackgroundColor;
        if (background == null && theme != null) {
            background = theme.background().withAlpha(0.30f);
        }
        if (background != null) {
            renderer.rect(gutterRect, background);
        }
        if (theme != null) {
            renderer.rect(new Rect(gutterRect.right(), content.y(), 1, content.height()),
                    theme.border().withAlpha(0.8f));
        }
        Rect text = textRect(widgetStyle);
        TextLayout layout = font.layoutBlockRelative(displayText(), textStyle(text));
        float textTop = content.y() - verticalScrollOffset();
        int currentLine = lineIndexAt(layout,
                layout.caretPosition(displayIndexForRaw(caretIndex())).y());
        Color normal = lineNumberColor;
        Color current = currentLineNumberColor;
        if (!isEnabled()) {
            normal = widgetStyle.text(WidgetState.DISABLED);
            current = normal;
        } else if (theme != null) {
            if (normal == null) {
                normal = theme.textMuted();
            }
            if (current == null) {
                current = theme.text();
            }
        } else {
            if (normal == null) {
                normal = widgetStyle.text(WidgetState.NORMAL);
            }
            if (current == null) {
                current = normal;
            }
        }
        Color normalColor = normal;
        Color currentColor = current;
        Point origin = absolutePosition();
        Rect clip = new Rect(origin.x() + gutterRect.x(), origin.y() + gutterRect.y(),
                gutterRect.width(), gutterRect.height());
        renderer.withClip(clip, () -> {
            for (int index = 0; index < layout.lines().size(); index++) {
                TextLine line = layout.lines().get(index);
                Color color = isFocused() && index == currentLine ? currentColor : normalColor;
                renderer.text(font, Integer.toString(index + 1),
                        gutterRect.right() - gutterPadding, textTop + line.y(), color,
                        HorizontalAlignment.RIGHT, VerticalAlignment.TOP);
            }
        });
    }

    private float gutterWidth(Font font, WidgetStyle widgetStyle) {
        if (!showLineNumbers || font == null || widgetStyle == null) {
            return 0;
        }
        int digits = Math.max(1, Integer.toString(logicalLineCount()).length());
        float digitWidth = font.advance('9');
        if (digitWidth <= 0) {
            digitWidth = font.pixelSize() * 0.6f;
        }
        return digits * digitWidth + gutterPadding * 2;
    }

    private int logicalLineCount() {
        String raw = rawText();
        long count = raw.chars().filter(character -> character == '\n').count() + 1;
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    private void indentSelection() {
        if (!isEnabled() || isReadOnly()) {
            return;
        }
        String raw = rawText();
        int start = selectionStart();
        int end = selectionEnd();
        if (start == end) {
            insertText(indentation(), false);
            return;
        }
        boolean forward = caretIndex() == end;
        int effectiveEnd = end > 0 && raw.charAt(end - 1) == '\n' ? end - 1 : end;
        int firstLineStart = lineStart(raw, start);
        int lastLineEnd = lineEnd(raw, effectiveEnd);
        String indent = indentation();
        StringBuilder updated = new StringBuilder(raw.length() + indent.length() * 4);
        int last = 0;
        int currentLineStart = firstLineStart;
        int inserted = 0;
        while (true) {
            updated.append(raw, last, currentLineStart);
            updated.append(indent);
            inserted += indent.length();
            int currentLineEnd = lineEnd(raw, currentLineStart);
            updated.append(raw, currentLineStart, currentLineEnd);
            last = currentLineEnd;
            if (currentLineEnd >= lastLineEnd) {
                break;
            }
            currentLineStart = currentLineEnd + 1;
        }
        updated.append(raw, last, raw.length());
        applyIndentedSelection(updated.toString(), firstLineStart, effectiveEnd + inserted,
                forward);
    }

    private void outdentSelection() {
        if (!isEnabled() || isReadOnly()) {
            return;
        }
        String raw = rawText();
        int start = selectionStart();
        int end = selectionEnd();
        if (start == end) {
            int currentLineStart = lineStart(raw, caretIndex());
            int removed = indentationLength(raw, currentLineStart);
            if (removed == 0) {
                return;
            }
            String updated = raw.substring(0, currentLineStart)
                    + raw.substring(currentLineStart + removed);
            int newCaret = Math.max(currentLineStart, caretIndex() - removed);
            replaceValue(updated, newCaret, newCaret);
            return;
        }
        boolean forward = caretIndex() == end;
        int effectiveEnd = end > 0 && raw.charAt(end - 1) == '\n' ? end - 1 : end;
        int firstLineStart = lineStart(raw, start);
        int lastLineEnd = lineEnd(raw, effectiveEnd);
        StringBuilder updated = new StringBuilder(raw.length());
        int last = 0;
        int currentLineStart = firstLineStart;
        int removedTotal = 0;
        while (true) {
            updated.append(raw, last, currentLineStart);
            int removed = indentationLength(raw, currentLineStart);
            removedTotal += removed;
            int currentLineEnd = lineEnd(raw, currentLineStart);
            updated.append(raw, currentLineStart + removed, currentLineEnd);
            last = currentLineEnd;
            if (currentLineEnd >= lastLineEnd) {
                break;
            }
            currentLineStart = currentLineEnd + 1;
        }
        if (removedTotal == 0) {
            return;
        }
        updated.append(raw, last, raw.length());
        int newEnd = Math.max(firstLineStart, effectiveEnd - removedTotal);
        applyIndentedSelection(updated.toString(), firstLineStart, newEnd, forward);
    }

    private void applyIndentedSelection(String value, int start, int end, boolean forward) {
        if (forward) {
            replaceValue(value, end, start);
        } else {
            replaceValue(value, start, end);
        }
    }

    private int indentationLength(String text, int lineStart) {
        int index = lineStart;
        while (index < text.length() && text.charAt(index) == ' ') {
            index++;
        }
        int spaces = index - lineStart;
        if (spaces > 0) {
            return Math.min(spaces, tabSize);
        }
        return lineStart < text.length() && text.charAt(lineStart) == '\t' ? 1 : 0;
    }

    private static int lineStart(String text, int index) {
        int clamped = Math.clamp(index, 0, text.length());
        return text.lastIndexOf('\n', clamped - 1) + 1;
    }

    private static int lineEnd(String text, int index) {
        int clamped = Math.clamp(index, 0, text.length());
        int newline = text.indexOf('\n', clamped);
        return newline < 0 ? text.length() : newline;
    }

    private static Color colorFor(TokenKind kind, Color textColor, SyntaxColors colors) {
        return switch (kind) {
            case KEYWORD -> colors.keyword();
            case STRING -> colors.string();
            case NUMBER -> colors.number();
            case COMMENT -> colors.comment();
            case ANNOTATION -> colors.annotation();
            case TYPE -> colors.type();
            case METHOD -> colors.method();
            case CONSTANT -> colors.constant();
            case OPERATOR -> colors.operator();
            case TEXT -> textColor;
        };
    }

    private List<CodeSpan> tokenize(String text) {
        List<CodeSpan> spans = new ArrayList<>();
        int plainStart = 0;
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == '/' && index + 1 < text.length()) {
                char next = text.charAt(index + 1);
                if (next == '/') {
                    plainStart = addPlain(spans, text, plainStart, index);
                    int end = index + 2;
                    while (end < text.length() && text.charAt(end) != '\n') {
                        end++;
                    }
                    spans.add(new CodeSpan(index, end, TokenKind.COMMENT));
                    index = end;
                    plainStart = index;
                    continue;
                }
                if (next == '*') {
                    plainStart = addPlain(spans, text, plainStart, index);
                    int end = text.indexOf("*/", index + 2);
                    end = end < 0 ? text.length() : end + 2;
                    spans.add(new CodeSpan(index, end, TokenKind.COMMENT));
                    index = end;
                    plainStart = index;
                    continue;
                }
            }
            if (character == '"' && text.startsWith("\"\"\"", index)) {
                plainStart = addPlain(spans, text, plainStart, index);
                int end = text.indexOf("\"\"\"", index + 3);
                end = end < 0 ? text.length() : end + 3;
                spans.add(new CodeSpan(index, end, TokenKind.STRING));
                index = end;
                plainStart = index;
                continue;
            }
            if (character == '"' || character == '\'') {
                plainStart = addPlain(spans, text, plainStart, index);
                int end = index + 1;
                while (end < text.length()) {
                    char current = text.charAt(end);
                    if (current == '\\') {
                        end = Math.min(text.length(), end + 2);
                        continue;
                    }
                    if (current == character) {
                        end++;
                        break;
                    }
                    if (current == '\n') {
                        break;
                    }
                    end++;
                }
                spans.add(new CodeSpan(index, end, TokenKind.STRING));
                index = end;
                plainStart = index;
                continue;
            }
            if (character == '@' && index + 1 < text.length()
                    && isIdentifierStart(text.charAt(index + 1))) {
                plainStart = addPlain(spans, text, plainStart, index);
                int end = index + 2;
                while (end < text.length() && isIdentifierPart(text.charAt(end))) {
                    end++;
                }
                spans.add(new CodeSpan(index, end, TokenKind.ANNOTATION));
                index = end;
                plainStart = index;
                continue;
            }
            if (Character.isDigit(character)
                    && (index == 0 || !isIdentifierPart(text.charAt(index - 1)))) {
                plainStart = addPlain(spans, text, plainStart, index);
                int end = index + 1;
                while (end < text.length() && isNumberPart(text.charAt(end))) {
                    end++;
                }
                spans.add(new CodeSpan(index, end, TokenKind.NUMBER));
                index = end;
                plainStart = index;
                continue;
            }
            if (isIdentifierStart(character)) {
                int end = index + 1;
                while (end < text.length() && isIdentifierPart(text.charAt(end))) {
                    end++;
                }
                String word = text.substring(index, end);
                TokenKind kind = null;
                if (keywords.contains(word)) {
                    kind = TokenKind.KEYWORD;
                } else if (isConstantName(word)) {
                    kind = TokenKind.CONSTANT;
                } else if (isTypeName(word)) {
                    kind = TokenKind.TYPE;
                } else if (isMethodName(text, end)) {
                    kind = TokenKind.METHOD;
                }
                if (kind != null) {
                    plainStart = addPlain(spans, text, plainStart, index);
                    spans.add(new CodeSpan(index, end, kind));
                    plainStart = end;
                }
                index = end;
                continue;
            }
            if (isOperator(character)) {
                plainStart = addPlain(spans, text, plainStart, index);
                int end = index + 1;
                while (end < text.length() && isOperator(text.charAt(end))
                        && !(text.charAt(end) == '/' && end + 1 < text.length()
                        && (text.charAt(end + 1) == '/' || text.charAt(end + 1) == '*'))) {
                    end++;
                }
                spans.add(new CodeSpan(index, end, TokenKind.OPERATOR));
                index = end;
                plainStart = index;
                continue;
            }
            index++;
        }
        addPlain(spans, text, plainStart, text.length());
        return spans;
    }

    private static int addPlain(List<CodeSpan> spans, String text, int start, int end) {
        if (start < end) {
            spans.add(new CodeSpan(start, end, TokenKind.TEXT));
        }
        return end;
    }

    private static boolean isIdentifierStart(char character) {
        return Character.isLetter(character) || character == '_' || character == '$';
    }

    private static boolean isIdentifierPart(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '$';
    }

    private static boolean isNumberPart(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '.';
    }

    private static boolean isConstantName(String word) {
        boolean letter = false;
        for (int index = 0; index < word.length(); index++) {
            char character = word.charAt(index);
            if (Character.isLetter(character)) {
                letter = true;
                if (!Character.isUpperCase(character)) {
                    return false;
                }
            } else if (!Character.isDigit(character) && character != '_') {
                return false;
            }
        }
        return letter;
    }

    private static boolean isTypeName(String word) {
        return !word.isEmpty() && Character.isUpperCase(word.codePointAt(0));
    }

    private static boolean isMethodName(String text, int end) {
        int index = end;
        while (index < text.length()
                && (text.charAt(index) == ' ' || text.charAt(index) == '\t')) {
            index++;
        }
        return index < text.length() && text.charAt(index) == '(';
    }

    private static boolean isOperator(char character) {
        return "(){}[];,.:?+-*/%&|^!~=<>@#".indexOf(character) >= 0;
    }

    private enum TokenKind {
        /** Plain source text. */
        TEXT,
        /** Language keyword. */
        KEYWORD,
        /** String or character literal. */
        STRING,
        /** Numeric literal. */
        NUMBER,
        /** Line or block comment. */
        COMMENT,
        /** Annotation name including the leading at sign. */
        ANNOTATION,
        /** Class or interface type name. */
        TYPE,
        /** Method call or declaration name. */
        METHOD,
        /** Constant name. */
        CONSTANT,
        /** Punctuation or operator. */
        OPERATOR
    }

    private record CodeSpan(int start, int end, TokenKind kind) {
    }
}
