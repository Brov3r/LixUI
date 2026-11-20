package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
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
 * indentation and Shift+Tab outdents the selected lines, Enter copies the current indentation and
 * Ctrl+/ toggles line comments, while Ctrl+Tab keeps focus traversal. Pressing a line number selects
 * its line. Indent guides, matching bracket highlights and a caret line/column indicator can be
 * enabled, disabled or recolored independently.
 */
public class InputCode extends InputArea {
    private static final float DEFAULT_GUTTER_PADDING = 8;
    private static final float GUTTER_GAP = 6;
    private static final float INDICATOR_PADDING = 6;
    private static final float DEFAULT_CURRENT_LINE_ALPHA = 0.08f;
    private static final float DEFAULT_CURRENT_LINE_NUMBER_ALPHA = 0.16f;
    private static final float DEFAULT_INDENT_GUIDE_ALPHA = 0.35f;
    private static final float DEFAULT_BRACKET_ALPHA = 0.28f;
    private static final float GUIDE_WIDTH = 1;
    private static final float BRACKET_PADDING = 1;
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
     * Placement of the caret line and column indicator.
     */
    public enum LineColumnPlacement {
        /** The indicator is hidden. */
        NONE,
        /** The indicator sits at the bottom of the line-number gutter. */
        GUTTER,
        /** The indicator sits at the bottom right corner of the editor. */
        BOTTOM
    }

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
    private boolean autoIndent = true;
    private boolean lineNumberClick = true;
    private boolean highlightCurrentLineNumber = true;
    private boolean bracketHighlight = true;
    private boolean indentGuides = true;
    private boolean tabInsertsIndentation = true;
    private boolean useSpacesForTabs = true;
    private int tabSize = 4;
    private float gutterPadding = DEFAULT_GUTTER_PADDING;
    private String lineComment = "//";
    private LineColumnPlacement lineColumnPlacement = LineColumnPlacement.NONE;
    private Set<String> keywords = new LinkedHashSet<>(DEFAULT_KEYWORDS);
    private SyntaxColors syntaxColors;
    private Color lineNumberColor;
    private Color currentLineNumberColor;
    private Color currentLineNumberBackgroundColor;
    private Color currentLineColor;
    private Color gutterBackgroundColor;
    private Color indentGuideColor;
    private Color bracketHighlightColor;
    private Color lineColumnColor;
    private String tokenizedText;
    private List<CodeSpan> tokenizedSpans = List.of();
    private String ignoredText;
    private boolean[] ignoredCache;
    private String matchedText;
    private int matchedCaret = -1;
    private int[] matchedPair;

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
     * Reports whether a new line copies the indentation of the current one.
     *
     * @return true when Enter keeps the leading whitespace
     */
    public final boolean isAutoIndent() {
        return autoIndent;
    }

    /**
     * Enables or disables copying the current indentation to a new line.
     *
     * @param autoIndent true to keep the leading whitespace on Enter
     */
    public final void setAutoIndent(boolean autoIndent) {
        this.autoIndent = autoIndent;
    }

    /**
     * Returns the prefix inserted and removed by the line comment shortcut.
     *
     * @return the line comment prefix, or an empty string when the shortcut is disabled
     */
    public final String lineComment() {
        return lineComment;
    }

    /**
     * Assigns the line comment prefix used by Ctrl+/.
     * An empty prefix disables the shortcut.
     *
     * @param lineComment line comment prefix
     * @throws NullPointerException if {@code lineComment} is null
     */
    public final void setLineComment(String lineComment) {
        this.lineComment = Objects.requireNonNull(lineComment, "lineComment");
    }

    /**
     * Reports whether pressing a line number selects its line.
     *
     * @return true when gutter presses select lines
     */
    public final boolean isLineNumberClick() {
        return lineNumberClick;
    }

    /**
     * Enables or disables selecting a line by pressing its number.
     *
     * @param lineNumberClick true to select lines from the gutter
     */
    public final void setLineNumberClick(boolean lineNumberClick) {
        this.lineNumberClick = lineNumberClick;
    }

    /**
     * Reports whether the current line number is highlighted.
     *
     * @return true when the caret line number receives a background marker
     */
    public final boolean isHighlightCurrentLineNumber() {
        return highlightCurrentLineNumber;
    }

    /**
     * Enables or disables the current line number background marker.
     *
     * @param highlightCurrentLineNumber true to highlight the caret line number
     */
    public final void setHighlightCurrentLineNumber(boolean highlightCurrentLineNumber) {
        this.highlightCurrentLineNumber = highlightCurrentLineNumber;
    }

    /**
     * Returns the explicit current line number background color.
     *
     * @return the marker color, or null when the translucent accent is used
     */
    public final Color currentLineNumberBackgroundColor() {
        return currentLineNumberBackgroundColor;
    }

    /**
     * Assigns the current line number background marker color.
     *
     * @param currentLineNumberBackgroundColor marker color, or null for a translucent accent
     */
    public final void setCurrentLineNumberBackgroundColor(Color currentLineNumberBackgroundColor) {
        this.currentLineNumberBackgroundColor = currentLineNumberBackgroundColor;
    }

    /**
     * Reports whether matching brackets are highlighted around the caret.
     *
     * @return true when bracket pairs are highlighted
     */
    public final boolean isBracketHighlight() {
        return bracketHighlight;
    }

    /**
     * Enables or disables matching bracket highlighting.
     *
     * @param bracketHighlight true to highlight the bracket pair at the caret
     */
    public final void setBracketHighlight(boolean bracketHighlight) {
        this.bracketHighlight = bracketHighlight;
    }

    /**
     * Returns the explicit bracket highlight color.
     *
     * @return the highlight color, or null when a translucent accent is used
     */
    public final Color bracketHighlightColor() {
        return bracketHighlightColor;
    }

    /**
     * Assigns the bracket highlight color.
     *
     * @param bracketHighlightColor highlight color, or null for a translucent accent
     */
    public final void setBracketHighlightColor(Color bracketHighlightColor) {
        this.bracketHighlightColor = bracketHighlightColor;
    }

    /**
     * Reports whether indentation guides are painted.
     *
     * @return true when indent guides are drawn
     */
    public final boolean isIndentGuides() {
        return indentGuides;
    }

    /**
     * Enables or disables indentation guides.
     *
     * @param indentGuides true to draw a guide per indentation level
     */
    public final void setIndentGuides(boolean indentGuides) {
        this.indentGuides = indentGuides;
    }

    /**
     * Returns the explicit indentation guide color.
     *
     * @return the guide color, or null when a translucent border color is used
     */
    public final Color indentGuideColor() {
        return indentGuideColor;
    }

    /**
     * Assigns the indentation guide color.
     *
     * @param indentGuideColor guide color, or null for a translucent border color
     */
    public final void setIndentGuideColor(Color indentGuideColor) {
        this.indentGuideColor = indentGuideColor;
    }

    /**
     * Returns the caret line and column indicator placement.
     *
     * @return the indicator placement
     */
    public final LineColumnPlacement lineColumnPlacement() {
        return lineColumnPlacement;
    }

    /**
     * Places the caret line and column indicator in the gutter, at the bottom right corner or
     * hides it. The indicator occupies a footer strip that does not overlap the text.
     *
     * @param lineColumnPlacement indicator placement
     * @throws NullPointerException if {@code lineColumnPlacement} is null
     */
    public final void setLineColumnPlacement(LineColumnPlacement lineColumnPlacement) {
        this.lineColumnPlacement = Objects.requireNonNull(lineColumnPlacement, "lineColumnPlacement");
    }

    /**
     * Returns the explicit indicator text color.
     *
     * @return the indicator color, or null when the muted text color is used
     */
    public final Color lineColumnColor() {
        return lineColumnColor;
    }

    /**
     * Assigns the caret line and column indicator text color.
     *
     * @param lineColumnColor indicator color, or null for the muted text color
     */
    public final void setLineColumnColor(Color lineColumnColor) {
        this.lineColumnColor = lineColumnColor;
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
        boolean command = modifiers.superKey() || (modifiers.control() && !modifiers.alt());
        if (key == Key.SLASH && command && !modifiers.shift()) {
            return toggleLineComment();
        }
        if ((key == Key.ENTER || key == Key.KEYPAD_ENTER) && !command) {
            insertLineBreak();
            return true;
        }
        if (key == Key.TAB) {
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
     * Inserts a line break typed as a character.
     *
     * @param codepoint typed code point
     * @return true when the event is consumed
     */
    @Override
    protected boolean onCharTyped(int codepoint) {
        if (codepoint == '\r' || codepoint == '\n') {
            if (!isEnabled()) {
                return true;
            }
            insertLineBreak();
            return true;
        }
        return super.onCharTyped(codepoint);
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
        Font font = font();
        return new Size(base.width() + gutterInset(font, resolvedStyle()),
                base.height() + footerHeight(font));
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
        if (content.isEmpty()) {
            return content;
        }
        float gutter = gutterInset(font(), widgetStyle);
        float footer = footerHeight(font());
        return new Rect(content.x() + gutter, content.y(),
                Math.max(0, content.width() - gutter),
                Math.max(0, content.height() - footer));
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
        drawLineColumnIndicator(renderer);
    }

    /**
     * Selects the whole line when the pointer presses its number.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when the press was consumed by the gutter
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button == MouseButton.LEFT && lineNumberClick && showLineNumbers) {
            WidgetStyle widgetStyle = resolvedStyle();
            Font font = font();
            if (widgetStyle != null && font != null) {
                Rect content = contentRect(widgetStyle);
                float gutter = gutterWidth(font, widgetStyle);
                if (!content.isEmpty() && gutter > 0
                        && x >= content.x() && x < content.x() + gutter
                        && y >= content.y() && y < content.bottom()) {
                    Rect body = textRect(widgetStyle);
                    if (!body.isEmpty() && y < body.bottom()) {
                        selectLineAt(font, body, y);
                    }
                    return true;
                }
            }
        }
        return super.onPointerPress(x, y, button, modifiers);
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
        if (content.isEmpty()) {
            return;
        }
        Theme theme = context() == null ? null : context().theme();
        if (currentLineHighlight && isEnabled() && isFocused()) {
            Color color = currentLineColor;
            if (color == null && theme != null) {
                color = theme.primary().withAlpha(DEFAULT_CURRENT_LINE_ALPHA);
            }
            if (color != null) {
                int lineIndex = lineIndexAt(layout,
                        layout.caretPosition(displayIndexForRaw(caretIndex())).y());
                TextLine line = layout.lines().get(lineIndex);
                renderer.rect(new Rect(textLeft, textTop + line.y(), content.width(),
                        layout.lineHeight()), color);
            }
        }
        if (indentGuides) {
            drawIndentGuides(renderer, layout, content, textLeft, textTop, theme);
        }
        if (bracketHighlight) {
            drawBracketHighlight(renderer, layout, textLeft, textTop, theme);
        }
    }

    /**
     * Draws a vertical guide for every indentation level of each logical line.
     */
    private void drawIndentGuides(RenderManager renderer, TextLayout layout, Rect content,
                                  float textLeft, float textTop, Theme theme) {
        Font font = font();
        if (font == null) {
            return;
        }
        Color color = indentGuideColor;
        if (color == null) {
            if (theme == null) {
                return;
            }
            color = theme.border().withAlpha(DEFAULT_INDENT_GUIDE_ALPHA);
        }
        String raw = rawText();
        float indentWidth = Math.max(1, font.advance(' ') * tabSize);
        int[] visible = visibleLineRange(layout, content, textTop);
        for (int lineIndex = visible[0]; lineIndex < visible[1]; lineIndex++) {
            TextLine line = layout.lines().get(lineIndex);
            int rawStart = rawIndexForDisplay(line.start());
            if (rawStart > 0 && (rawStart >= raw.length() || raw.charAt(rawStart - 1) != '\n')) {
                continue;
            }
            int levels = indentationAt(raw, rawStart) / tabSize;
            float y = textTop + line.y();
            for (int level = 1; level <= levels; level++) {
                renderer.rect(new Rect(textLeft + level * indentWidth, y, GUIDE_WIDTH,
                        layout.lineHeight()), color);
            }
        }
    }

    /**
     * Highlights the bracket at the caret and its matching partner.
     */
    private void drawBracketHighlight(RenderManager renderer, TextLayout layout,
                                      float textLeft, float textTop, Theme theme) {
        Font font = font();
        if (font == null || mask() != null || isSecret()) {
            return;
        }
        String raw = rawText();
        int[] pair = matchingBrackets(raw);
        if (pair == null) {
            return;
        }
        Color color = bracketHighlightColor;
        if (color == null) {
            if (theme == null) {
                return;
            }
            color = theme.primary().withAlpha(DEFAULT_BRACKET_ALPHA);
        }
        for (int index : pair) {
            float width = font.advance(raw.charAt(index));
            if (width <= 0) {
                continue;
            }
            Point caret = layout.caretPosition(displayIndexForRaw(index));
            renderer.rect(new Rect(textLeft + caret.x() - BRACKET_PADDING, textTop + caret.y(),
                    width + BRACKET_PADDING * 2, layout.lineHeight()), color);
        }
    }

    /**
     * Returns the bracket at or before the caret and its matching partner, skipping strings and
     * comments.
     */
    private int[] matchingBrackets(String raw) {
        int caret = caretIndex();
        if (raw == matchedText && caret == matchedCaret) {
            return matchedPair;
        }
        matchedText = raw;
        matchedCaret = caret;
        matchedPair = findMatchingBrackets(raw, caret);
        return matchedPair;
    }

    private int[] findMatchingBrackets(String raw, int caret) {
        int position = -1;
        if (caret < raw.length() && isBracket(raw.charAt(caret))) {
            position = caret;
        } else if (caret > 0 && isBracket(raw.charAt(caret - 1))) {
            position = caret - 1;
        }
        if (position < 0) {
            return null;
        }
        char bracket = raw.charAt(position);
        char counterpart = counterpart(bracket);
        boolean forward = bracket == '(' || bracket == '[' || bracket == '{';
        boolean[] ignored = ignoredPositions(raw);
        int depth = 0;
        for (int index = position; index >= 0 && index < raw.length(); index += forward ? 1 : -1) {
            if (ignored[index]) {
                continue;
            }
            char current = raw.charAt(index);
            if (current == bracket) {
                depth++;
            } else if (current == counterpart) {
                depth--;
                if (depth == 0) {
                    return new int[]{position, index};
                }
            }
        }
        return null;
    }

    private boolean[] ignoredPositions(String raw) {
        if (raw == ignoredText && ignoredCache != null) {
            return ignoredCache;
        }
        boolean[] ignored = new boolean[raw.length()];
        for (CodeSpan span : spansFor(raw)) {
            if (span.kind() != TokenKind.STRING && span.kind() != TokenKind.COMMENT) {
                continue;
            }
            int end = Math.min(span.end(), ignored.length);
            for (int index = span.start(); index < end; index++) {
                ignored[index] = true;
            }
        }
        ignoredText = raw;
        ignoredCache = ignored;
        return ignored;
    }

    /**
     * Returns the token spans of the display text, reusing the previous result while the text
     * instance is unchanged so typing does not retokenize the whole document every frame.
     */
    private List<CodeSpan> spansFor(String text) {
        if (text != tokenizedText) {
            tokenizedText = text;
            tokenizedSpans = tokenize(text);
        }
        return tokenizedSpans;
    }

    /**
     * Returns the first visible line and the exclusive last visible line of the layout.
     */
    private static int[] visibleLineRange(TextLayout layout, Rect content, float textTop) {
        int size = layout.lines().size();
        int first = lineIndexAt(layout, content.y() - textTop);
        int last = lineIndexAt(layout, Math.max(content.y() - textTop,
                content.bottom() - textTop - 1)) + 1;
        last = Math.min(size, Math.max(first + 1, last));
        return new int[]{Math.min(first, size - 1), last};
    }

    private static boolean isBracket(char character) {
        return character == '(' || character == ')' || character == '['
                || character == ']' || character == '{' || character == '}';
    }

    private static char counterpart(char bracket) {
        return switch (bracket) {
            case '(' -> ')';
            case ')' -> '(';
            case '[' -> ']';
            case ']' -> '[';
            case '{' -> '}';
            case '}' -> '{';
            default -> bracket;
        };
    }

    private int indentationAt(String text, int start) {
        int spaces = 0;
        for (int index = start; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == ' ') {
                spaces++;
            } else if (character == '\t') {
                spaces += tabSize;
            } else {
                break;
            }
        }
        return spaces;
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
        List<CodeSpan> spans = spansFor(display);
        WidgetStyle widgetStyle = resolvedStyle();
        Rect content = widgetStyle == null ? localBounds() : textRect(widgetStyle);
        int[] visible = visibleLineRange(layout, content, textTop);
        int spanIndex = 0;
        if (visible[0] < layout.lines().size()) {
            int firstStart = layout.lines().get(visible[0]).start();
            while (spanIndex < spans.size() && spans.get(spanIndex).end() <= firstStart) {
                spanIndex++;
            }
        }
        for (int lineIndex = visible[0]; lineIndex < visible[1]; lineIndex++) {
            TextLine line = layout.lines().get(lineIndex);
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
        Rect body = textRect(widgetStyle);
        float gutter = gutterWidth(font, widgetStyle);
        if (content.isEmpty() || body.isEmpty() || gutter <= 0) {
            return;
        }
        Theme theme = context() == null ? null : context().theme();
        Rect gutterRect = new Rect(content.x(), content.y(), gutter, body.height());
        Color background = gutterBackgroundColor;
        if (background == null && theme != null) {
            background = theme.background().withAlpha(0.30f);
        }
        if (background != null) {
            renderer.rect(gutterRect, background);
        }
        if (theme != null) {
            renderer.rect(new Rect(gutterRect.right(), content.y(), 1, gutterRect.height()),
                    theme.border().withAlpha(0.8f));
        }
        TextLayout layout = font.layoutBlockRelative(displayText(), textStyle(body));
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
        Color markerColor = null;
        if (highlightCurrentLineNumber && isFocused() && isEnabled()) {
            markerColor = currentLineNumberBackgroundColor;
            if (markerColor == null && theme != null) {
                markerColor = theme.primary().withAlpha(DEFAULT_CURRENT_LINE_NUMBER_ALPHA);
            }
        }
        Color marker = markerColor;
        int[] visible = visibleLineRange(layout, body, textTop);
        Point origin = absolutePosition();
        Rect clip = new Rect(origin.x() + gutterRect.x(), origin.y() + gutterRect.y(),
                gutterRect.width(), gutterRect.height());
        renderer.withClip(clip, () -> {
            for (int index = visible[0]; index < visible[1]; index++) {
                TextLine line = layout.lines().get(index);
                String number = Integer.toString(index + 1);
                boolean active = isFocused() && index == currentLine;
                if (active && marker != null) {
                    float width = font.measure(number).width();
                    renderer.rect(new Rect(gutterRect.right() - gutterPadding - width - 2,
                            textTop + line.y(), width + 4, layout.lineHeight()), marker);
                }
                renderer.text(font, number, gutterRect.right() - gutterPadding,
                        textTop + line.y(), active ? currentColor : normalColor,
                        HorizontalAlignment.RIGHT, VerticalAlignment.TOP);
            }
        });
    }

    private void drawLineColumnIndicator(RenderManager renderer) {
        if (lineColumnPlacement == LineColumnPlacement.NONE) {
            return;
        }
        Font font = font();
        WidgetStyle widgetStyle = resolvedStyle();
        if (font == null || widgetStyle == null) {
            return;
        }
        Rect content = contentRect(widgetStyle);
        Rect body = textRect(widgetStyle);
        float height = footerHeight(font);
        if (content.isEmpty() || height <= 0) {
            return;
        }
        float top = content.y() + body.height();
        Rect footer = new Rect(content.x(), top, content.width(),
                Math.min(height, Math.max(0, content.bottom() - top)));
        if (footer.isEmpty()) {
            return;
        }
        Theme theme = context() == null ? null : context().theme();
        Color background = gutterBackgroundColor;
        if (background == null && theme != null) {
            background = theme.background().withAlpha(0.30f);
        }
        if (background != null) {
            renderer.rect(footer, background);
        }
        if (theme != null) {
            renderer.rect(new Rect(footer.x(), footer.y(), footer.width(), 1),
                    theme.border().withAlpha(0.8f));
        }
        Color color = lineColumnColor;
        if (color == null && theme != null) {
            color = theme.textMuted();
        }
        if (color == null) {
            return;
        }
        boolean gutter = lineColumnPlacement == LineColumnPlacement.GUTTER;
        renderer.text(font, caretPositionLabel(), gutter
                        ? content.x() + gutterPadding : footer.right() - INDICATOR_PADDING,
                footer.y() + footer.height() / 2, color,
                gutter ? HorizontalAlignment.LEFT : HorizontalAlignment.RIGHT,
                VerticalAlignment.CENTER);
    }

    private String caretPositionLabel() {
        String raw = rawText();
        int caret = caretIndex();
        int line = 1;
        int lineStart = 0;
        for (int index = 0; index < caret && index < raw.length(); index++) {
            if (raw.charAt(index) == '\n') {
                line++;
                lineStart = index + 1;
            }
        }
        return "Ln " + line + ", Col " + (caret - lineStart + 1);
    }

    private float footerHeight(Font font) {
        return font == null || lineColumnPlacement == LineColumnPlacement.NONE
                ? 0 : font.lineHeight() + INDICATOR_PADDING;
    }

    private void selectLineAt(Font font, Rect body, float y) {
        TextLayout layout = font.layoutBlockRelative(displayText(), textStyle(body));
        float textTop = body.y() - verticalScrollOffset();
        int lineIndex = lineIndexAt(layout, y - textTop);
        TextLine line = layout.lines().get(lineIndex);
        String raw = rawText();
        int index = rawIndexForDisplay(line.start());
        setSelection(lineStart(raw, index), lineEnd(raw, index));
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

    /**
     * Returns the gutter width plus the breathing space before the code text.
     */
    private float gutterInset(Font font, WidgetStyle widgetStyle) {
        float gutter = gutterWidth(font, widgetStyle);
        return gutter <= 0 ? 0 : gutter + GUTTER_GAP;
    }

    private int logicalLineCount() {
        String raw = rawText();
        long count = raw.chars().filter(character -> character == '\n').count() + 1;
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    /**
     * Inserts a newline that keeps the leading whitespace of the current line.
     */
    private void insertLineBreak() {
        if (!autoIndent || mask() != null || !allows(' ')) {
            insertText("\n", false);
            return;
        }
        String raw = rawText();
        int start = lineStart(raw, selectionStart());
        int end = start;
        while (end < raw.length() && (raw.charAt(end) == ' ' || raw.charAt(end) == '\t')) {
            end++;
        }
        insertText("\n" + raw.substring(start, end), false);
    }

    /**
     * Comments out or uncomments every line touched by the selection.
     *
     * @return true when the shortcut is enabled and the edit was attempted
     */
    private boolean toggleLineComment() {
        if (!isEnabled() || isReadOnly() || lineComment.isEmpty()) {
            return false;
        }
        String raw = rawText();
        int start = selectionStart();
        int end = selectionEnd();
        int effectiveEnd = end > start && raw.charAt(end - 1) == '\n' ? end - 1 : end;
        int firstLineStart = lineStart(raw, start);
        int lastLineEnd = lineEnd(raw, Math.max(start, effectiveEnd));
        List<Integer> lineStarts = new ArrayList<>();
        for (int position = firstLineStart; ; ) {
            lineStarts.add(position);
            int currentEnd = lineEnd(raw, position);
            if (currentEnd >= lastLineEnd) {
                break;
            }
            position = currentEnd + 1;
        }
        boolean allCommented = true;
        boolean anyContent = false;
        for (int position : lineStarts) {
            int currentEnd = lineEnd(raw, position);
            int contentStart = skipWhitespace(raw, position, currentEnd);
            if (contentStart >= currentEnd) {
                continue;
            }
            anyContent = true;
            if (!raw.startsWith(lineComment, contentStart)) {
                allCommented = false;
                break;
            }
        }
        String prefix = lineComment.endsWith(" ") ? lineComment : lineComment + " ";
        boolean commentOut = !anyContent || !allCommented;
        StringBuilder updated = new StringBuilder(raw.length() + prefix.length() * lineStarts.size());
        List<int[]> edits = new ArrayList<>();
        int cursor = 0;
        for (int position : lineStarts) {
            int currentEnd = lineEnd(raw, position);
            int contentStart = skipWhitespace(raw, position, currentEnd);
            updated.append(raw, cursor, contentStart);
            if (contentStart < currentEnd) {
                if (commentOut) {
                    updated.append(prefix);
                    edits.add(new int[]{contentStart, prefix.length()});
                    updated.append(raw, contentStart, currentEnd);
                } else {
                    int afterPrefix = contentStart + lineComment.length();
                    if (afterPrefix < currentEnd && raw.charAt(afterPrefix) == ' ') {
                        afterPrefix++;
                    }
                    edits.add(new int[]{contentStart, contentStart - afterPrefix});
                    updated.append(raw, afterPrefix, currentEnd);
                }
            }
            cursor = currentEnd;
        }
        updated.append(raw, cursor, raw.length());
        int caret = caretIndex();
        int anchor = caret == start ? end : start;
        replaceValue(updated.toString(), adjustIndex(caret, edits, updated.length()),
                adjustIndex(anchor, edits, updated.length()));
        return true;
    }

    private static int skipWhitespace(String text, int start, int end) {
        int index = start;
        while (index < end && (text.charAt(index) == ' ' || text.charAt(index) == '\t')) {
            index++;
        }
        return index;
    }

    private static int adjustIndex(int index, List<int[]> edits, int length) {
        int shifted = index;
        for (int[] edit : edits) {
            int position = edit[0];
            int delta = edit[1];
            if (delta > 0) {
                if (index >= position) {
                    shifted += delta;
                }
            } else if (index >= position - delta) {
                shifted += delta;
            } else if (index > position) {
                shifted += position - index;
            }
        }
        return Math.clamp(shifted, 0, length);
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
