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
    private final float[] spacing;

    TextLayout(Font font, String text, TextStyle style, Size size, float lineHeight,
               List<TextGlyph> glyphs, List<TextLine> lines, float[] spacing) {
        this.font = Objects.requireNonNull(font, "font");
        this.text = Objects.requireNonNull(text, "text");
        this.style = Objects.requireNonNull(style, "style");
        this.size = Objects.requireNonNull(size, "size");
        this.lineHeight = lineHeight;
        this.glyphs = List.copyOf(glyphs);
        this.lines = List.copyOf(lines);
        this.spacing = spacing;
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
        int row = lineAt(index);
        TextLine line = lines.get(row);
        float x = line.x() + caretX(line, index, spacing[row]);
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
        int row = lineAtY(y);
        TextLine line = lines.get(row);
        int bestIndex = line.start();
        float bestDistance = Math.abs(x - line.x());
        int index = line.start();
        Font.LinePen pen = font.new LinePen();
        while (index < line.end()) {
            int codepoint = text.codePointAt(index);
            index += Character.charCount(codepoint);
            pen.advance(codepoint, spacing[row]);
            float distance = Math.abs(x - (line.x() + pen.position()));
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
        for (int row = 0; row < lines.size(); row++) {
            TextLine line = lines.get(row);
            int lineStart = Math.max(from, line.start());
            int lineEnd = Math.min(to, line.end());
            if (lineStart >= lineEnd) {
                continue;
            }
            float first = line.x() + caretX(line, lineStart, spacing[row]);
            float second = line.x() + caretX(line, lineEnd, spacing[row]);
            rects.add(new Rect(Math.min(first, second), line.y(),
                    Math.abs(second - first), lineHeight));
        }
        return List.copyOf(rects);
    }

    private int lineAt(int index) {
        int clamped = Math.clamp(index, 0, text.length());
        int selected = 0;
        for (int row = 1; row < lines.size(); row++) {
            if (clamped < lines.get(row).start()) {
                break;
            }
            selected = row;
        }
        return selected;
    }

    private int lineAtY(float y) {
        if (lines.size() == 1 || lineHeight <= 0) {
            return 0;
        }
        float relative = y - lines.get(0).y();
        int index = (int) Math.floor(relative / lineHeight);
        return Math.clamp(index, 0, lines.size() - 1);
    }

    private float caretX(TextLine line, int index, float extraSpace) {
        Font.LinePen pen = font.new LinePen();
        int end = Math.min(index, line.end());
        for (int start = line.start(); start < end; ) {
            int codepoint = text.codePointAt(start);
            start += Character.charCount(codepoint);
            if (start <= end) {
                pen.advance(codepoint, extraSpace);
            }
        }
        return pen.position();
    }
}
