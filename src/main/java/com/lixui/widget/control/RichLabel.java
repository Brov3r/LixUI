package com.lixui.widget.control;

import com.lixui.widget.Widget;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.FontFamily;
import com.lixui.text.FontStyle;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextDecoration;
import com.lixui.text.TextShadow;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.WidgetStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A label made of styled runs that can mix colors, fonts, decorations and shadows on a single line.
 * Runs wrap between words at the label width, explicit newlines start a new line, and lines are
 * aligned across the label. Words wider than the line are not split. The label measures itself from
 * its content and inherits the default font and text color from the widget tree.
 */
public final class RichLabel extends Widget {
    private static final float STRIKE_RATIO = 0.3f;

    private final List<Span> spans = new ArrayList<>();
    private FontFamily fontFamily;
    private HorizontalAlignment horizontal = HorizontalAlignment.LEFT;
    private VerticalAlignment vertical = VerticalAlignment.TOP;
    private boolean wrap = true;

    /**
     * Creates an empty rich label.
     */
    public RichLabel() {
        setAutoSize(true);
    }

    /**
     * Appends a run using the inherited font and the supplied color.
     *
     * @param text run text, which may contain newlines
     * @param color run color, or null to use the label text color
     * @return this label for chaining
     * @throws NullPointerException if {@code text} is null
     */
    public RichLabel add(String text, Color color) {
        return add(new Span(text, color, null, FontStyle.REGULAR, TextDecoration.NONE, null));
    }

    /**
     * Appends a run with an explicit font.
     *
     * @param text run text, which may contain newlines
     * @param color run color, or null to use the label text color
     * @param font run font, or null to use the family or inherited font
     * @return this label for chaining
     * @throws NullPointerException if {@code text} is null
     */
    public RichLabel add(String text, Color color, Font font) {
        return add(new Span(text, color, font, FontStyle.REGULAR, TextDecoration.NONE, null));
    }

    /**
     * Appends a decorated run with an explicit font.
     *
     * @param text run text, which may contain newlines
     * @param color run color, or null to use the label text color
     * @param font run font, or null to use the family or inherited font
     * @param decoration underline, strikethrough or none
     * @return this label for chaining
     * @throws NullPointerException if {@code text} or {@code decoration} is null
     */
    public RichLabel add(String text, Color color, Font font, TextDecoration decoration) {
        return add(new Span(text, color, font, FontStyle.REGULAR, decoration, null));
    }

    /**
     * Appends a run that selects its face from the label font family.
     *
     * @param text run text, which may contain newlines
     * @param color run color, or null to use the label text color
     * @param fontStyle face to resolve from the label family
     * @param decoration underline, strikethrough or none
     * @return this label for chaining
     * @throws NullPointerException if {@code text}, {@code fontStyle} or {@code decoration} is null
     */
    public RichLabel add(String text, Color color, FontStyle fontStyle, TextDecoration decoration) {
        return add(new Span(text, color, null, fontStyle, decoration, null));
    }

    /**
     * Appends a run.
     *
     * @param span run to append
     * @return this label for chaining
     * @throws NullPointerException if {@code span} is null
     */
    public RichLabel add(Span span) {
        spans.add(Objects.requireNonNull(span, "span"));
        return this;
    }

    /**
     * Removes every run.
     */
    public void clear() {
        spans.clear();
    }

    /**
     * Returns the runs in drawing order.
     *
     * @return an immutable snapshot of the runs
     */
    public List<Span> spans() {
        return List.copyOf(spans);
    }

    /**
     * Returns the font family used for weight and slant variants.
     *
     * @return the font family, or null when runs use explicit or inherited fonts
     */
    public FontFamily fontFamily() {
        return fontFamily;
    }

    /**
     * Assigns a font family used to resolve run styles.
     * A run that specifies its own font ignores the family. Faces must be created through the
     * render manager so their atlases are uploaded. Pass null to use inherited fonts.
     *
     * @param fontFamily font family, or null to inherit the font
     */
    public void setFontFamily(FontFamily fontFamily) {
        this.fontFamily = fontFamily;
    }

    /**
     * Reports whether runs wrap at the label width.
     *
     * @return true when wrapping is enabled
     */
    public boolean isWrap() {
        return wrap;
    }

    /**
     * Enables or disables wrapping at the label width.
     *
     * @param wrap true to wrap long lines
     */
    public void setWrap(boolean wrap) {
        this.wrap = wrap;
    }

    /**
     * Returns the horizontal alignment of lines.
     *
     * @return the horizontal alignment
     */
    public HorizontalAlignment alignment() {
        return horizontal;
    }

    /**
     * Sets the horizontal alignment of lines across the label width.
     * {@link HorizontalAlignment#JUSTIFY} stretches every wrapped line except the last line of a
     * paragraph by widening its whitespace tokens.
     *
     * @param alignment horizontal alignment
     * @throws NullPointerException if {@code alignment} is null
     */
    public void setAlignment(HorizontalAlignment alignment) {
        this.horizontal = Objects.requireNonNull(alignment, "alignment");
    }

    /**
     * Returns the vertical alignment of the text block.
     *
     * @return the vertical alignment
     */
    public VerticalAlignment verticalAlignment() {
        return vertical;
    }

    /**
     * Sets the vertical alignment of the text block across the label height.
     *
     * @param alignment vertical alignment
     * @throws NullPointerException if {@code alignment} is null
     */
    public void setVerticalAlignment(VerticalAlignment alignment) {
        this.vertical = Objects.requireNonNull(alignment, "alignment");
    }

    /**
     * Measures the wrapped runs using the available width.
     *
     * @param constraints available and required dimensions
     * @return the natural label size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Layout layout = layout(constraints.maxWidth());
        return new Size(layout.width(), layout.height());
    }

    /**
     * Paints shadows, decorations and runs aligned inside the label bounds.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        Layout layout = layout(localBounds().width());
        if (layout.lines().isEmpty()) {
            return;
        }
        float width = localBounds().width();
        float height = localBounds().height();
        float top = switch (vertical) {
            case TOP -> 0;
            case CENTER -> (height - layout.height()) / 2;
            case BOTTOM -> height - layout.height();
        };
        for (Line line : layout.lines()) {
            float left = switch (horizontal) {
                case LEFT, JUSTIFY -> 0;
                case CENTER -> (width - line.width()) / 2;
                case RIGHT -> width - line.width();
            };
            drawShadows(renderer, line, left, top);
            drawDecorations(renderer, line, left, top);
            float x = left;
            for (Token token : line.tokens()) {
                renderer.text(token.font(), token.text(), x,
                        top + line.ascent() - token.font().ascent(), token.color());
                x += token.width();
            }
            top += line.height();
        }
    }

    private void drawShadows(RenderManager renderer, Line line, float left, float top) {
        float x = left;
        for (Token token : line.tokens()) {
            TextShadow shadow = token.shadow();
            if (shadow != null) {
                renderer.text(token.font(), token.text(), x + shadow.offsetX(),
                        top + line.ascent() - token.font().ascent() + shadow.offsetY(),
                        shadow.color());
            }
            x += token.width();
        }
    }

    private static void drawDecorations(RenderManager renderer, Line line, float left, float top) {
        float x = left;
        for (Token token : line.tokens()) {
            TextDecoration decoration = token.decoration();
            if (!token.whitespace() && decoration != TextDecoration.NONE) {
                float thickness = Math.max(1, token.font().pixelSize() * 0.07f);
                float underlineGap = Math.max(1, token.font().pixelSize() * 0.1f);
                float baseline = top + line.ascent();
                if (decoration.hasUnderline()) {
                    renderer.rect(new Rect(x, baseline + underlineGap, token.width(), thickness),
                            token.color());
                }
                if (decoration.hasStrikethrough()) {
                    renderer.rect(new Rect(x, baseline - token.font().ascent() * STRIKE_RATIO,
                            token.width(), thickness), token.color());
                }
            }
            x += token.width();
        }
    }

    private Layout layout(float availableWidth) {
        if (spans.isEmpty()) {
            return Layout.EMPTY;
        }
        Font inherited = font();
        Color defaultColor = defaultColor();
        Font fallback = inherited;
        List<Token> tokens = new ArrayList<>();
        for (Span span : spans) {
            Font font = resolvedFont(span, inherited);
            if (font == null) {
                continue;
            }
            if (fallback == null) {
                fallback = font;
            }
            Color color = span.color() != null ? span.color() : defaultColor;
            tokenize(span.text(), font, color, span.decoration(), span.shadow(), tokens);
        }
        if (fallback == null) {
            return Layout.EMPTY;
        }
        Font lineFont = fallback;
        float maxWidth = wrap ? Math.max(0, availableWidth) : 0;
        List<Line> lines = new ArrayList<>();
        List<Token> current = new ArrayList<>();
        float currentWidth = 0;
        for (Token token : tokens) {
            if (token.lineBreak()) {
                lines.add(buildLine(current, lineFont, true));
                current = new ArrayList<>();
                currentWidth = 0;
                continue;
            }
            if (token.whitespace() && current.isEmpty()) {
                continue;
            }
            if (maxWidth > 0 && !current.isEmpty() && currentWidth + token.width() > maxWidth) {
                lines.add(buildLine(current, lineFont, false));
                current = new ArrayList<>();
                currentWidth = 0;
                if (token.whitespace()) {
                    continue;
                }
            }
            current.add(token);
            currentWidth += token.width();
        }
        lines.add(buildLine(current, lineFont, true));
        if (horizontal == HorizontalAlignment.JUSTIFY) {
            for (int index = 0; index < lines.size(); index++) {
                lines.set(index, justify(lines.get(index), maxWidth));
            }
        }
        float width = 0;
        float height = 0;
        for (Line line : lines) {
            width = Math.max(width, line.width());
            height += line.height();
        }
        return new Layout(width, height, List.copyOf(lines));
    }

    private static Line justify(Line line, float maxWidth) {
        if (line.paragraphEnd() || maxWidth <= 0 || line.width() >= maxWidth) {
            return line;
        }
        int spaces = 0;
        for (Token token : line.tokens()) {
            if (token.whitespace()) {
                spaces += token.text().length();
            }
        }
        if (spaces == 0) {
            return line;
        }
        float extra = (maxWidth - line.width()) / spaces;
        List<Token> tokens = new ArrayList<>(line.tokens().size());
        for (Token token : line.tokens()) {
            if (token.whitespace()) {
                tokens.add(new Token(token.text(), token.color(), token.font(), true, false,
                        token.width() + extra * token.text().length(),
                        token.decoration(), token.shadow()));
            } else {
                tokens.add(token);
            }
        }
        return new Line(tokens, line.height(), line.ascent(), maxWidth, false);
    }

    private Font resolvedFont(Span span, Font inherited) {
        if (span.font() != null) {
            return span.font();
        }
        return fontFamily != null ? fontFamily.resolve(span.fontStyle()) : inherited;
    }

    private Color defaultColor() {
        WidgetStyle style = style();
        return style == null ? Color.WHITE : style.text(state());
    }

    private static void tokenize(String text, Font font, Color color, TextDecoration decoration,
                                 TextShadow shadow, List<Token> tokens) {
        int index = 0;
        while (index < text.length()) {
            int codepoint = text.codePointAt(index);
            if (codepoint == '\r') {
                index++;
                continue;
            }
            if (codepoint == '\n') {
                tokens.add(Token.breakToken());
                index++;
                continue;
            }
            boolean whitespace = Character.isWhitespace(codepoint);
            int start = index;
            while (index < text.length()) {
                int next = text.codePointAt(index);
                if (next == '\n' || next == '\r' || Character.isWhitespace(next) != whitespace) {
                    break;
                }
                index += Character.charCount(next);
            }
            String tokenText = text.substring(start, index);
            tokens.add(new Token(tokenText, color, font, whitespace, false,
                    font.measure(tokenText).width(), decoration, shadow));
        }
    }

    private static Line buildLine(List<Token> tokens, Font inherited, boolean paragraphEnd) {
        int end = tokens.size();
        while (end > 0 && tokens.get(end - 1).whitespace()) {
            end--;
        }
        if (end == 0) {
            return new Line(List.of(), inherited.lineHeight(), inherited.ascent(), 0, paragraphEnd);
        }
        List<Token> visible = List.copyOf(tokens.subList(0, end));
        float width = 0;
        float ascent = 0;
        float height = 0;
        for (Token token : visible) {
            width += token.width();
            ascent = Math.max(ascent, token.font().ascent());
            height = Math.max(height, token.font().lineHeight());
        }
        return new Line(visible, height, ascent, width, paragraphEnd);
    }

    /**
     * A styled run of text.
     *
     * @param text run text, which may contain newlines
     * @param color run color, or null to use the label text color
     * @param font explicit font, or null to resolve the family or inherited font
     * @param fontStyle face to resolve from the label family when {@code font} is null
     * @param decoration underline, strikethrough or none
     * @param shadow shadow drawn behind the run, or null for none
     */
    public record Span(String text, Color color, Font font, FontStyle fontStyle,
                       TextDecoration decoration, TextShadow shadow) {
        /**
         * Creates a run.
         *
         * @param text run text
         * @param color run color, or null to inherit the label text color
         * @param font explicit font, or null to inherit
         * @param fontStyle face to resolve from the label family
         * @param decoration text decoration
         * @param shadow shadow drawn behind the run, or null for none
         * @throws NullPointerException if {@code text}, {@code fontStyle} or {@code decoration} is null
         */
        public Span {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(fontStyle, "fontStyle");
            Objects.requireNonNull(decoration, "decoration");
        }

        /**
         * Creates a plain run with the inherited font and no decoration.
         *
         * @param text run text
         * @param color run color, or null to inherit the label text color
         * @throws NullPointerException if {@code text} is null
         */
        public Span(String text, Color color) {
            this(text, color, null, FontStyle.REGULAR, TextDecoration.NONE, null);
        }

        /**
         * Creates an undecorated run with an explicit font.
         *
         * @param text run text
         * @param color run color, or null to inherit the label text color
         * @param font explicit font, or null to inherit
         * @throws NullPointerException if {@code text} is null
         */
        public Span(String text, Color color, Font font) {
            this(text, color, font, FontStyle.REGULAR, TextDecoration.NONE, null);
        }

        /**
         * Creates a decorated run with an explicit font.
         *
         * @param text run text
         * @param color run color, or null to inherit the label text color
         * @param font explicit font, or null to inherit
         * @param decoration text decoration
         * @throws NullPointerException if {@code text} or {@code decoration} is null
         */
        public Span(String text, Color color, Font font, TextDecoration decoration) {
            this(text, color, font, FontStyle.REGULAR, decoration, null);
        }
    }

    private record Token(String text, Color color, Font font, boolean whitespace, boolean lineBreak,
                         float width, TextDecoration decoration, TextShadow shadow) {
        private static Token breakToken() {
            return new Token("", null, null, false, true, 0, TextDecoration.NONE, null);
        }
    }

    private record Line(List<Token> tokens, float height, float ascent, float width,
                        boolean paragraphEnd) {
    }

    private record Layout(float width, float height, List<Line> lines) {
        private static final Layout EMPTY = new Layout(0, 0, List.of());
    }
}
