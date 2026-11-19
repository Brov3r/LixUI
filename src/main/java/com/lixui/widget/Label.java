package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.FontFamily;
import com.lixui.text.FontStyle;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextDecoration;
import com.lixui.text.TextLayout;
import com.lixui.text.TextLine;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextShadow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.WidgetStyle;

import java.util.Objects;

/**
 * A single-style text label that measures itself from its content.
 * The label wraps at its width by default, can limit lines and ellipsize overflow, and uses the
 * inherited font and the state-aware style text color unless an explicit color is assigned.
 */
public class Label extends Widget {
    private static final float STRIKE_RATIO = 0.3f;

    private String text;
    private Color textColor;
    private FontFamily fontFamily;
    private FontStyle fontStyle = FontStyle.REGULAR;
    private TextDecoration decoration = TextDecoration.NONE;
    private TextShadow shadow;
    private HorizontalAlignment horizontal = HorizontalAlignment.LEFT;
    private VerticalAlignment vertical = VerticalAlignment.TOP;
    private boolean wrap = true;
    private int maxLines;
    private TextOverflow overflow = TextOverflow.CLIP;
    private float lineHeight;

    /**
     * Creates an empty label.
     */
    public Label() {
        this("");
    }

    /**
     * Creates a label with text.
     *
     * @param text label text
     * @throws NullPointerException if {@code text} is null
     */
    public Label(String text) {
        this.text = Objects.requireNonNull(text, "text");
        setAutoSize(true);
    }

    /**
     * Returns the label text.
     *
     * @return the current text
     */
    public String text() {
        return text;
    }

    /**
     * Replaces the label text.
     *
     * @param text label text
     * @throws NullPointerException if {@code text} is null
     */
    public void setText(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    /**
     * Returns the explicit text color.
     *
     * @return the text color, or null when the style text color is used
     */
    public Color textColor() {
        return textColor;
    }

    /**
     * Assigns an explicit text color.
     *
     * @param textColor text color, or null to use the style text color
     */
    public void setTextColor(Color textColor) {
        this.textColor = textColor;
    }

    /**
     * Returns the font family used for weight and slant variants.
     *
     * @return the font family, or null when the inherited font is used for every style
     */
    public FontFamily fontFamily() {
        return fontFamily;
    }

    /**
     * Assigns a font family for weight and slant variants.
     * While a family is set, the label resolves its face from the family instead of the inherited
     * font. Faces must be created through the render manager so their atlases are uploaded.
     * Pass null to use the inherited font again.
     *
     * @param fontFamily font family, or null to inherit the font
     */
    public void setFontFamily(FontFamily fontFamily) {
        this.fontFamily = fontFamily;
    }

    /**
     * Returns the requested font style.
     *
     * @return the font style
     */
    public FontStyle fontStyle() {
        return fontStyle;
    }

    /**
     * Selects a bold, italic or regular face from the font family.
     * The style is ignored when no family is assigned.
     *
     * @param fontStyle requested font style
     * @throws NullPointerException if {@code fontStyle} is null
     */
    public void setFontStyle(FontStyle fontStyle) {
        this.fontStyle = Objects.requireNonNull(fontStyle, "fontStyle");
    }

    /**
     * Returns the text decoration.
     *
     * @return the decoration
     */
    public TextDecoration decoration() {
        return decoration;
    }

    /**
     * Sets an underline, strikethrough or combined decoration.
     *
     * @param decoration text decoration
     * @throws NullPointerException if {@code decoration} is null
     */
    public void setDecoration(TextDecoration decoration) {
        this.decoration = Objects.requireNonNull(decoration, "decoration");
    }

    /**
     * Returns the drop shadow drawn behind the text.
     *
     * @return the shadow, or null when none is drawn
     */
    public TextShadow shadow() {
        return shadow;
    }

    /**
     * Assigns a drop shadow drawn behind the text.
     *
     * @param shadow text shadow, or null to remove it
     */
    public void setShadow(TextShadow shadow) {
        this.shadow = shadow;
    }

    /**
     * Reports whether lines wrap at the label width.
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
     * Returns the maximum number of visible lines.
     *
     * @return the line limit, or zero when unlimited
     */
    public int maxLines() {
        return maxLines;
    }

    /**
     * Limits the number of visible lines.
     *
     * @param maxLines line limit, or zero for no limit
     * @throws IllegalArgumentException if the limit is negative
     */
    public void setMaxLines(int maxLines) {
        if (maxLines < 0) {
            throw new IllegalArgumentException("Maximum line count must be non-negative.");
        }
        this.maxLines = maxLines;
    }

    /**
     * Returns the overflow behavior applied when lines exceed the limit.
     *
     * @return the overflow behavior
     */
    public TextOverflow overflow() {
        return overflow;
    }

    /**
     * Sets the overflow behavior applied when lines exceed the limit.
     * Truncation uses the wrapping width, so keep wrapping enabled for a single-line ellipsis.
     *
     * @param overflow overflow behavior
     * @throws NullPointerException if {@code overflow} is null
     */
    public void setOverflow(TextOverflow overflow) {
        this.overflow = Objects.requireNonNull(overflow, "overflow");
    }

    /**
     * Returns the explicit line height.
     *
     * @return the line height, or zero when the font line height is used
     */
    public float lineHeight() {
        return lineHeight;
    }

    /**
     * Sets an explicit line height.
     *
     * @param lineHeight line height in logical UI units, or zero to use the font line height
     * @throws IllegalArgumentException if the line height is negative or non-finite
     */
    public void setLineHeight(float lineHeight) {
        if (!Float.isFinite(lineHeight) || lineHeight < 0) {
            throw new IllegalArgumentException("Line height must be finite and non-negative.");
        }
        this.lineHeight = lineHeight;
    }

    /**
     * Returns the horizontal text alignment.
     *
     * @return the horizontal alignment
     */
    public HorizontalAlignment alignment() {
        return horizontal;
    }

    /**
     * Sets the horizontal text alignment across the label width.
     * {@link HorizontalAlignment#JUSTIFY} stretches every wrapped line except the last line of a
     * paragraph, matching word processors.
     *
     * @param alignment horizontal alignment
     * @throws NullPointerException if {@code alignment} is null
     */
    public void setAlignment(HorizontalAlignment alignment) {
        this.horizontal = Objects.requireNonNull(alignment, "alignment");
    }

    /**
     * Returns the vertical text alignment.
     *
     * @return the vertical alignment
     */
    public VerticalAlignment verticalAlignment() {
        return vertical;
    }

    /**
     * Sets the vertical text alignment across the label height.
     *
     * @param alignment vertical alignment
     * @throws NullPointerException if {@code alignment} is null
     */
    public void setVerticalAlignment(VerticalAlignment alignment) {
        this.vertical = Objects.requireNonNull(alignment, "alignment");
    }

    /**
     * Measures the wrapped text using the available width.
     *
     * @param constraints available and required dimensions
     * @return the natural label size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Font font = resolvedFont();
        if (font == null || text.isEmpty()) {
            return new Size(0, 0);
        }
        return font.measure(text, layoutStyle(constraints.maxWidth()));
    }

    /**
     * Paints the label text aligned inside the label bounds.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        Font font = resolvedFont();
        WidgetStyle widgetStyle = style();
        if (font == null || widgetStyle == null || text.isEmpty()) {
            return;
        }
        float width = localBounds().width();
        float height = localBounds().height();
        float anchorX = switch (horizontal) {
            case LEFT, JUSTIFY -> 0;
            case CENTER -> width / 2;
            case RIGHT -> width;
        };
        float anchorY = switch (vertical) {
            case TOP -> 0;
            case CENTER -> height / 2;
            case BOTTOM -> height;
        };
        TextStyle style = layoutStyle(width);
        Color color = textColor != null ? textColor : widgetStyle.text(state());
        if (shadow != null) {
            renderer.text(font, text, anchorX + shadow.offsetX(), anchorY + shadow.offsetY(),
                    shadow.color(), style);
        }
        if (decoration != TextDecoration.NONE) {
            drawDecoration(renderer, font.layoutBlockRelative(text, style), font,
                    anchorX, anchorY, color);
        }
        renderer.text(font, text, anchorX, anchorY, color, style);
    }

    private void drawDecoration(RenderManager renderer, TextLayout layout, Font font,
                                float anchorX, float anchorY, Color color) {
        float thickness = Math.max(1, font.pixelSize() * 0.07f);
        float underlineGap = Math.max(1, font.pixelSize() * 0.1f);
        for (TextLine line : layout.lines()) {
            if (line.width() <= 0) {
                continue;
            }
            float x = anchorX + line.x();
            float baseline = anchorY + line.y() + font.ascent();
            if (decoration.hasUnderline()) {
                renderer.rect(new Rect(x, baseline + underlineGap, line.width(), thickness), color);
            }
            if (decoration.hasStrikethrough()) {
                renderer.rect(new Rect(x, baseline - font.ascent() * STRIKE_RATIO,
                        line.width(), thickness), color);
            }
        }
    }

    private Font resolvedFont() {
        Font inherited = font();
        return fontFamily != null ? fontFamily.resolve(fontStyle) : inherited;
    }

    private TextStyle layoutStyle(float availableWidth) {
        return new TextStyle(wrap ? Math.max(0, availableWidth) : 0, lineHeight, maxLines,
                overflow, horizontal, vertical);
    }
}
