package com.lixui.text;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A wrapped and optionally truncated text block relative to its top-left origin.
 * The layout stores visible glyphs and line ranges, and exposes caret and selection geometry for
 * editable controls.
 */
public final class TextLayout {
    private final Font font;
    private final String text;
    private final TextStyle style;
    private final Size size;
    private final float lineHeight;
    private final List<TextGlyph> glyphs;
    private final List<TextLine> lines;

    TextLayout(Font font, String text, TextStyle style, Size size, float lineHeight,
               List<TextGlyph> glyphs, List<TextLine> lines) {
        this.font = Objects.requireNonNull(font, "font");
        this.text = Objects.requireNonNull(text, "text");
        this.style = Objects.requireNonNull(style, "style");
        this.size = Objects.requireNonNull(size, "size");
        this.lineHeight = lineHeight;
        this.glyphs = List.copyOf(glyphs);
        this.lines = List.copyOf(lines);
    }

    /**
     * Returns the source text.
     *
     * @return the laid-out text
     */
    public String text() {
        return text;
    }

    /**
     * Returns the layout style.
     *
     * @return the text style used for this layout
     */
    public TextStyle style() {
        return style;
    }

    /**
     * Returns the block size.
     *
     * @return the width and height in logical UI units
     */
    public Size size() {
        return size;
    }

    /**
     * Returns the resolved distance between baselines.
     *
     * @return the positive line height in logical UI units
     */
    public float lineHeight() {
        return lineHeight;
    }

    /**
     * Returns positioned glyph quads relative to the block origin.
     *
     * @return an immutable glyph list in drawing order
     */
    public List<TextGlyph> glyphs() {
        return glyphs;
    }

    /**
     * Returns laid-out line ranges relative to the block origin.
     *
     * @return an immutable line list in visual order
     */
    public List<TextLine> lines() {
        return lines;
    }

    /**
     * Returns the caret position for a UTF-16 text index.
     * The result is relative to the block origin.
     *
     * @param index UTF-16 index in the source text
     * @return the caret point at the top of the corresponding line
     */
    public Point caretPosition(int index) {
        TextLine line = lineAt(index);
        float x = line.x() + caretX(line, index);
        return new Point(x, line.y());
    }

    /**
     * Returns the closest UTF-16 text index for a point in block-local coordinates.
     *
     * @param x horizontal position relative to the block origin
     * @param y vertical position relative to the block origin
     * @return the closest text index
     */
    public int caretIndexAt(float x, float y) {
        TextLine line = lineAtY(y);
        int bestIndex = line.start();
        float bestDistance = Math.abs(x - line.x());
        int index = line.start();
        while (index < line.end()) {
            int codepoint = text.codePointAt(index);
            index += Character.charCount(codepoint);
            float distance = Math.abs(x - (line.x() + caretX(line, index)));
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    /**
     * Returns selection rectangles between two UTF-16 text indexes.
     * Rectangles are relative to the block origin and may span multiple lines.
     *
     * @param start selection start index
     * @param end selection end index
     * @return an immutable list of selection rectangles
     */
    public List<Rect> selectionRects(int start, int end) {
        int from = Math.max(0, Math.min(start, end));
        int to = Math.min(text.length(), Math.max(start, end));
        List<Rect> rects = new ArrayList<>();
        if (from == to) {
            return List.of();
        }
        for (TextLine line : lines) {
            int lineStart = Math.max(from, line.start());
            int lineEnd = Math.min(to, line.end());
            if (lineStart >= lineEnd) {
                continue;
            }
            float first = line.x() + caretX(line, lineStart);
            float second = line.x() + caretX(line, lineEnd);
            rects.add(new Rect(Math.min(first, second), line.y(),
                    Math.abs(second - first), lineHeight));
        }
        return List.copyOf(rects);
    }

    private TextLine lineAt(int index) {
        int clamped = Math.max(0, Math.min(index, text.length()));
        TextLine selected = lines.get(0);
        for (TextLine line : lines) {
            if (clamped < line.start()) {
                break;
            }
            selected = line;
        }
        return selected;
    }

    private TextLine lineAtY(float y) {
        if (lines.size() == 1 || lineHeight <= 0) {
            return lines.get(0);
        }
        float relative = y - lines.get(0).y();
        int index = (int) Math.floor(relative / lineHeight);
        int clamped = Math.max(0, Math.min(index, lines.size() - 1));
        return lines.get(clamped);
    }

    private float caretX(TextLine line, int index) {
        int clamped = Math.max(line.start(), Math.min(index, line.end()));
        String prefix = text.substring(line.start(), clamped);
        return font.measure(prefix).width() + extraSpacing(line) * Font.spaceCount(prefix);
    }

    private float extraSpacing(TextLine line) {
        if (style.horizontal() != HorizontalAlignment.JUSTIFY || style.maxWidth() <= 0) {
            return 0;
        }
        if (line.end() >= text.length() || text.charAt(line.end()) == '\n') {
            return 0;
        }
        String content = text.substring(line.start(), line.end());
        int spaces = Font.spaceCount(content);
        if (spaces == 0) {
            return 0;
        }
        float natural = font.measure(content).width();
        return natural < style.maxWidth() ? (style.maxWidth() - natural) / spaces : 0;
    }
}
