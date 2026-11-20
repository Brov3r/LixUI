package com.lixui.text;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.render.ImageData;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A rasterized TrueType font baked into an RGBA atlas.
 * Glyphs are rasterized with stb_truetype at construction time and packed into a single atlas whose
 * white RGB channels carry coverage in the alpha channel. The atlas is uploaded to a backend texture
 * by {@code RenderManager.createFont(FontData, float)}.
 * Layout uses a top-left origin and supports kerning, explicit newlines and tab stops.
 */
public final class Font {
    private static final int ATLAS_PADDING = 1;
    private static final int MAX_ATLAS_SIZE = 8192;
    private static final int MAX_KERNING_CODEPOINTS = 512;
    private static final int MAX_LAYOUT_CACHE_ENTRIES = 256;
    private static final float ITALIC_SLANT = 0.21f;
    private static final int[] DEFAULT_CODEPOINTS = buildDefaultCodepoints();

    private final ImageData atlas;
    private final float pixelSize;
    private final float ascent;
    private final float descent;
    private final float lineHeight;
    private final float letterSpacing;
    private final FontStyle style;
    private final Map<Integer, Glyph> glyphs;
    private final Map<Long, Float> kerning;
    private final Map<LayoutKey, List<TextGlyph>> layoutCache =
            new LinkedHashMap<>(16, 0.75f, true);
    private final Map<TextLayoutKey, TextLayout> textLayoutCache =
            new LinkedHashMap<>(16, 0.75f, true);

    Font(ImageData atlas, float pixelSize, float ascent, float descent, float lineHeight,
            float letterSpacing, FontStyle style, Map<Integer, Glyph> glyphs,
            Map<Long, Float> kerning) {
        this.atlas = Objects.requireNonNull(atlas, "atlas");
        this.style = Objects.requireNonNull(style, "style");
        Objects.requireNonNull(glyphs, "glyphs");
        Objects.requireNonNull(kerning, "kerning");
        if (!Float.isFinite(pixelSize) || pixelSize <= 0) {
            throw new IllegalArgumentException("Font pixel size must be finite and positive.");
        }
        if (!Float.isFinite(ascent) || !Float.isFinite(descent) || !Float.isFinite(lineHeight)) {
            throw new IllegalArgumentException("Font metrics must be finite.");
        }
        if (lineHeight <= 0) {
            throw new IllegalArgumentException("Font line height must be positive.");
        }
        if (!Float.isFinite(letterSpacing)) {
            throw new IllegalArgumentException("Letter spacing must be finite.");
        }
        this.pixelSize = pixelSize;
        this.ascent = ascent;
        this.descent = descent;
        this.lineHeight = lineHeight;
        this.letterSpacing = letterSpacing;
        this.glyphs = Map.copyOf(glyphs);
        this.kerning = Map.copyOf(kerning);
    }

    /**
     * Rasterizes a font at the supplied pixel size with the default character set.
     * The default set covers printable ASCII, Latin-1 and Cyrillic plus common punctuation.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @return the rasterized font
     * @throws NullPointerException if {@code data} is null
     * @throws IllegalArgumentException if the size is invalid or the data has no TrueType outlines
     */
    public static Font load(FontData data, float pixelSize) {
        return load(data, pixelSize, DEFAULT_CODEPOINTS, FontStyle.REGULAR, 0);
    }

    /**
     * Rasterizes a font at the supplied pixel size with the default character set and letter spacing.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @param letterSpacing extra space between glyphs in logical UI units
     * @return the rasterized font
     * @throws NullPointerException if {@code data} is null
     * @throws IllegalArgumentException if the size or spacing is invalid, or the data has no
     *                                  TrueType outlines
     */
    public static Font load(FontData data, float pixelSize, float letterSpacing) {
        return load(data, pixelSize, DEFAULT_CODEPOINTS, FontStyle.REGULAR, letterSpacing);
    }

    /**
     * Rasterizes a font with the default character set and a synthetic style.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @param style synthetic bold or italic style
     * @return the rasterized font
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the size is invalid or the data has no TrueType outlines
     */
    public static Font load(FontData data, float pixelSize, FontStyle style) {
        return load(data, pixelSize, DEFAULT_CODEPOINTS, style, 0);
    }

    /**
     * Rasterizes a font with the default character set, a synthetic style and letter spacing.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @param style synthetic bold or italic style
     * @param letterSpacing extra space between glyphs in logical UI units
     * @return the rasterized font
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the size or spacing is invalid, or the data has no
     *                                  TrueType outlines
     */
    public static Font load(FontData data, float pixelSize, FontStyle style, float letterSpacing) {
        return load(data, pixelSize, DEFAULT_CODEPOINTS, style, letterSpacing);
    }

    /**
     * Rasterizes a font at the supplied pixel size for an explicit set of Unicode code points.
     * Code points without a TrueType glyph are skipped. Kerning pairs are baked only when at most
     * 512 code points are requested, which keeps font creation time bounded for large character sets.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @param codepoints Unicode code points to bake
     * @return the rasterized font
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the size is invalid, a code point is out of range, the
     *                                  data has no TrueType outlines, or the atlas is too large
     */
    public static Font load(FontData data, float pixelSize, int[] codepoints) {
        return load(data, pixelSize, codepoints, FontStyle.REGULAR, 0);
    }

    /**
     * Rasterizes a font for an explicit set of Unicode code points and a synthetic style.
     * Code points without a TrueType glyph are skipped. Kerning pairs are baked only when at most
     * 512 code points are requested, which keeps font creation time bounded for large character sets.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @param codepoints Unicode code points to bake
     * @param style synthetic bold or italic style
     * @return the rasterized font
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the size is invalid, a code point is out of range, the
     *                                  data has no TrueType outlines, or the atlas is too large
     */
    public static Font load(FontData data, float pixelSize, int[] codepoints, FontStyle style) {
        return load(data, pixelSize, codepoints, style, 0);
    }

    /**
     * Rasterizes a font for an explicit set of Unicode code points, a synthetic style and letter spacing.
     * Code points without a TrueType glyph are skipped. Kerning pairs are baked only when at most
     * 512 code points are requested, which keeps font creation time bounded for large character sets.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @param codepoints Unicode code points to bake
     * @param style synthetic bold or italic style
     * @param letterSpacing extra space between glyphs in logical UI units
     * @return the rasterized font
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the size or spacing is invalid, a code point is out of
     *                                  range, the data has no TrueType outlines, or the atlas is too large
     */
    public static Font load(FontData data, float pixelSize, int[] codepoints,
                            FontStyle style, float letterSpacing) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(codepoints, "codepoints");
        Objects.requireNonNull(style, "style");
        if (!Float.isFinite(letterSpacing)) {
            throw new IllegalArgumentException("Letter spacing must be finite.");
        }
        if (!Float.isFinite(pixelSize) || pixelSize <= 0) {
            throw new IllegalArgumentException("Font pixel size must be finite and positive.");
        }
        Set<Integer> requested = new LinkedHashSet<>();
        for (int codepoint : codepoints) {
            if (codepoint < 0 || codepoint > Character.MAX_CODE_POINT) {
                throw new IllegalArgumentException("Code point is out of range: " + codepoint);
            }
            requested.add(codepoint);
        }

        ByteBuffer bytes = data.buffer();
        STBTTFontinfo info = STBTTFontinfo.create();
        int offset = STBTruetype.stbtt_GetFontOffsetForIndex(bytes, 0);
        if (offset < 0 || !STBTruetype.stbtt_InitFont(info, bytes, offset)) {
            throw new IllegalArgumentException("Font data has no TrueType outlines.");
        }
        float scale = STBTruetype.stbtt_ScaleForPixelHeight(info, pixelSize);

        float ascent;
        float descent;
        float lineHeight;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ascentBuffer = stack.mallocInt(1);
            IntBuffer descentBuffer = stack.mallocInt(1);
            IntBuffer lineGapBuffer = stack.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(info, ascentBuffer, descentBuffer, lineGapBuffer);
            ascent = ascentBuffer.get(0) * scale;
            descent = descentBuffer.get(0) * scale;
            lineHeight = ascent - descent + Math.max(0, lineGapBuffer.get(0) * scale);
        }
        if (lineHeight <= 0) {
            lineHeight = pixelSize;
        }

        List<GlyphDraft> drafts = new ArrayList<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer advanceBuffer = stack.mallocInt(1);
            IntBuffer bearingBuffer = stack.mallocInt(1);
            IntBuffer x0 = stack.mallocInt(1);
            IntBuffer y0 = stack.mallocInt(1);
            IntBuffer x1 = stack.mallocInt(1);
            IntBuffer y1 = stack.mallocInt(1);
            for (int codepoint : requested) {
                if (STBTruetype.stbtt_FindGlyphIndex(info, codepoint) == 0) {
                    continue;
                }
                STBTruetype.stbtt_GetCodepointHMetrics(info, codepoint, advanceBuffer, bearingBuffer);
                STBTruetype.stbtt_GetCodepointBitmapBoxSubpixel(
                        info, codepoint, scale, scale, 0, 0, x0, y0, x1, y1);
                drafts.add(new GlyphDraft(codepoint, x0.get(0), y0.get(0), x1.get(0), y1.get(0),
                        advanceBuffer.get(0) * scale));
            }
        }

        drafts.sort((first, second) -> {
            int byHeight = Integer.compare(second.height(), first.height());
            return byHeight != 0 ? byHeight : Integer.compare(second.width(), first.width());
        });
        boolean bold = style.bold();
        boolean italic = style.italic();
        int boldOffset = bold ? Math.max(1, Math.round(pixelSize / 24f)) : 0;
        List<StyledDraft> styled = new ArrayList<>(drafts.size());
        for (GlyphDraft draft : drafts) {
            int rawWidth = draft.width();
            int rawHeight = draft.height();
            if (rawWidth <= 0 || rawHeight <= 0) {
                styled.add(new StyledDraft(draft, draft.x0(), draft.y0(), 0, 0, 0, 0, 0));
                continue;
            }
            int minShift = 0;
            int maxShift = 0;
            if (italic) {
                float minExact = 0;
                float maxExact = 0;
                for (int row = 0; row < rawHeight; row++) {
                    float shift = -ITALIC_SLANT * (draft.y0() + row);
                    minExact = Math.min(minExact, shift);
                    maxExact = Math.max(maxExact, shift);
                }
                minShift = (int) Math.floor(minExact);
                maxShift = (int) Math.ceil(maxExact);
            }
            styled.add(new StyledDraft(draft,
                    draft.x0() + minShift,
                    draft.y0(),
                    rawWidth + (maxShift - minShift) + boldOffset,
                    rawHeight,
                    rawWidth, rawHeight, minShift));
        }
        styled.sort((first, second) -> {
            int byHeight = Integer.compare(second.height(), first.height());
            return byHeight != 0 ? byHeight : Integer.compare(second.width(), first.width());
        });

        Packing packing = pack(styled);
        Map<Integer, Glyph> glyphs = new HashMap<>();
        List<Glyph> baked = new ArrayList<>(styled.size());
        for (PlacedGlyph placed : packing.placed()) {
            StyledDraft draft = placed.draft();
            int width = draft.width();
            int height = draft.height();
            Rect uv = Rect.EMPTY;
            if (width > 0 && height > 0) {
                rasterize(info, draft, scale, bold, italic, boldOffset,
                        packing.width(), placed.x(), placed.y(), packing.pixels());
                uv = new Rect(placed.x() / (float) packing.width(),
                        placed.y() / (float) packing.height(),
                        width / (float) packing.width(),
                        height / (float) packing.height());
            }
            Glyph glyph = new Glyph(draft.codepoint(), uv, draft.offsetX(), draft.offsetY(),
                    width, height, draft.advance());
            glyphs.put(draft.codepoint(), glyph);
            baked.add(glyph);
        }

        Map<Long, Float> kerning = new HashMap<>();
        if (baked.size() <= MAX_KERNING_CODEPOINTS) {
            for (Glyph first : baked) {
                for (Glyph second : baked) {
                    int pair = STBTruetype.stbtt_GetCodepointKernAdvance(
                            info, first.codepoint(), second.codepoint());
                    if (pair != 0) {
                        kerning.put(pairKey(first.codepoint(), second.codepoint()), pair * scale);
                    }
                }
            }
        }

        return new Font(new ImageData(packing.width(), packing.height(), packing.pixels()),
                pixelSize, ascent, descent, lineHeight, letterSpacing, style, glyphs, kerning);
    }

    /**
     * Returns the default baked character set: printable ASCII, Latin-1, Cyrillic and common
     * typographic punctuation.
     *
     * @return a fresh copy of the default code points
     */
    public static int[] defaultCodepoints() {
        return DEFAULT_CODEPOINTS.clone();
    }

    /**
     * Returns the RGBA atlas generated at construction time.
     *
     * @return the font atlas image
     */
    public ImageData atlas() {
        return atlas;
    }

    /**
     * Returns the requested line size.
     *
     * @return the pixel size used when rasterizing the font
     */
    public float pixelSize() {
        return pixelSize;
    }

    /**
     * Returns the synthetic style baked into the glyph atlas.
     * Fonts created from a real bold or italic face with
     * {@code RenderManager.createFont(String, float, FontStyle)} report {@link FontStyle#REGULAR},
     * because their outlines already carry the style.
     *
     * @return the synthetic font style
     */
    public FontStyle style() {
        return style;
    }

    /**
     * Returns the extra space inserted between adjacent glyphs.
     *
     * @return the letter spacing in logical UI units
     */
    public float letterSpacing() {
        return letterSpacing;
    }

    /**
     * Returns the distance from the top of the line to the baseline.
     *
     * @return the positive ascent in logical UI units
     */
    public float ascent() {
        return ascent;
    }

    /**
     * Returns the distance from the baseline to the bottom of the line.
     *
     * @return the negative descent in logical UI units
     */
    public float descent() {
        return descent;
    }

    /**
     * Returns the vertical distance between baselines.
     *
     * @return the positive line height in logical UI units
     */
    public float lineHeight() {
        return lineHeight;
    }

    /**
     * Returns the baked glyph for a code point.
     *
     * @param codepoint Unicode code point
     * @return the glyph, or null when the code point is not baked or the font has no such glyph
     */
    public Glyph glyph(int codepoint) {
        return glyphs.get(codepoint);
    }

    /**
     * Returns the horizontal advance of a code point.
     *
     * @param codepoint Unicode code point
     * @return the advance in logical UI units, or zero when the glyph is not baked
     */
    public float advance(int codepoint) {
        Glyph glyph = glyphs.get(codepoint);
        return glyph == null ? 0 : glyph.advance();
    }

    /**
     * Returns the baked kerning adjustment between two code points.
     *
     * @param first leading code point
     * @param second trailing code point
     * @return the horizontal adjustment in logical UI units, or zero when no pair is baked
     */
    public float kerning(int first, int second) {
        Float value = kerning.get(pairKey(first, second));
        return value == null ? 0 : value;
    }

    /**
     * Positions the glyph quads of a text block.
     * The origin is the top-left corner of the first line. Newlines start a new line and tabs
     * advance by four spaces. Code points without a baked glyph are skipped.
     *
     * @param text text to lay out
     * @param x horizontal origin in logical UI units
     * @param y vertical origin in logical UI units
     * @return glyph quads in drawing order
     * @throws NullPointerException if {@code text} is null
     * @throws IllegalArgumentException if an origin is non-finite
     */
    public List<TextGlyph> layout(String text, float x, float y) {
        return layout(text, x, y, HorizontalAlignment.LEFT, VerticalAlignment.TOP);
    }

    /**
     * Positions the glyph quads of a text block relative to an anchor point.
     * Multiline text aligns every line horizontally within the anchor: left edges share the anchor,
     * centers share the anchor, or right edges share the anchor. Vertically the whole block is
     * placed below, around or above the anchor. Newlines start a new line, tabs advance by four
     * spaces and code points without a baked glyph are skipped.
     *
     * @param text text to lay out
     * @param x horizontal anchor in logical UI units
     * @param y vertical anchor in logical UI units
     * @param horizontal horizontal alignment applied to each line
     * @param vertical vertical alignment applied to the whole block
     * @return glyph quads in drawing order
     * @throws NullPointerException if {@code text} or an alignment is null
     * @throws IllegalArgumentException if an anchor is non-finite
     */
    public List<TextGlyph> layout(String text, float x, float y,
                                  HorizontalAlignment horizontal, VerticalAlignment vertical) {
        Objects.requireNonNull(horizontal, "horizontal");
        Objects.requireNonNull(vertical, "vertical");
        validateOrigin(x, y);
        List<TextGlyph> relative = layoutRelative(text, horizontal, vertical);
        if (x == 0 && y == 0) {
            return relative;
        }
        List<TextGlyph> quads = new ArrayList<>(relative.size());
        for (TextGlyph glyph : relative) {
            quads.add(glyph.translate(x, y));
        }
        return quads;
    }

    /**
     * Positions the glyph quads of a text block around the anchor {@code (0, 0)}.
     * The result is cached per text and alignment pair, so repeated frames reuse the same immutable
     * list. Callers can translate the quads to their destination without measuring the font again.
     *
     * @param text text to lay out
     * @param horizontal horizontal alignment applied to each line
     * @param vertical vertical alignment applied to the whole block
     * @return an immutable list of glyph quads
     * @throws NullPointerException if an argument is null
     */
    public List<TextGlyph> layoutRelative(String text, HorizontalAlignment horizontal,
                                          VerticalAlignment vertical) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(horizontal, "horizontal");
        Objects.requireNonNull(vertical, "vertical");
        LayoutKey key = new LayoutKey(text, horizontal, vertical);
        synchronized (layoutCache) {
            List<TextGlyph> cached = layoutCache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        List<TextGlyph> computed = List.copyOf(layoutAt(text, horizontal, vertical));
        synchronized (layoutCache) {
            layoutCache.put(key, computed);
            if (layoutCache.size() > MAX_LAYOUT_CACHE_ENTRIES) {
                Iterator<Map.Entry<LayoutKey, List<TextGlyph>>> iterator =
                        layoutCache.entrySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
        }
        return computed;
    }

    private List<TextGlyph> layoutAt(String text, HorizontalAlignment horizontal,
                                     VerticalAlignment vertical) {
        String[] lines = text.split("\n", -1);
        float[] widths = new float[lines.length];
        for (int index = 0; index < lines.length; index++) {
            widths[index] = walk(lines[index], 0, 0, null).width();
        }
        float top = alignedTop(0, lines.length * lineHeight, vertical);
        List<TextGlyph> quads = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            float lineX = alignedX(0, widths[index], horizontal);
            walk(lines[index], lineX, top + index * lineHeight, quads);
        }
        return quads;
    }

    /**
     * Measures a text block using baked advances and kerning.
     * The height is the number of lines multiplied by {@link #lineHeight()}.
     *
     * @param text text to measure
     * @return the non-negative width and height in logical UI units
     * @throws NullPointerException if {@code text} is null
     */
    public Size measure(String text) {
        Objects.requireNonNull(text, "text");
        return walk(text, 0, 0, null);
    }

    /**
     * Measures a text block using wrapping and truncation options.
     *
     * @param text text to measure
     * @param style layout options
     * @return the measured width and height in logical UI units
     * @throws NullPointerException if an argument is null
     */
    public Size measure(String text, TextStyle style) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(style, "style");
        return layoutBlockRelative(text, style).size();
    }

    /**
     * Positions the glyph quads of a wrapped text block.
     * The origin is the top-left corner of the block.
     *
     * @param text text to lay out
     * @param x horizontal origin in logical UI units
     * @param y vertical origin in logical UI units
     * @param style layout options
     * @return glyph quads in drawing order
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if an origin is non-finite
     */
    public List<TextGlyph> layout(String text, float x, float y, TextStyle style) {
        Objects.requireNonNull(style, "style");
        validateOrigin(x, y);
        List<TextGlyph> relative = layoutBlockRelative(text, style).glyphs();
        if (x == 0 && y == 0) {
            return relative;
        }
        List<TextGlyph> quads = new ArrayList<>(relative.size());
        for (TextGlyph glyph : relative) {
            quads.add(glyph.translate(x, y));
        }
        return quads;
    }

    /**
     * Positions a wrapped text block relative to the origin {@code (0, 0)}.
     * Results are cached by text and style and can be reused across frames.
     *
     * @param text text to lay out
     * @param style layout options
     * @return an immutable text layout
     * @throws NullPointerException if an argument is null
     */
    public TextLayout layoutBlockRelative(String text, TextStyle style) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(style, "style");
        TextLayoutKey key = new TextLayoutKey(text, style);
        synchronized (textLayoutCache) {
            TextLayout cached = textLayoutCache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        TextLayout computed = buildTextLayout(text, style);
        synchronized (textLayoutCache) {
            textLayoutCache.put(key, computed);
            if (textLayoutCache.size() > MAX_LAYOUT_CACHE_ENTRIES) {
                Iterator<Map.Entry<TextLayoutKey, TextLayout>> iterator =
                        textLayoutCache.entrySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
        }
        return computed;
    }

    private TextLayout buildTextLayout(String text, TextStyle style) {
        List<LineSpec> specs = wrapLines(text, style);
        float resolvedLineHeight = style.lineHeight() > 0 ? style.lineHeight() : lineHeight;
        float top = alignedTop(0, specs.size() * resolvedLineHeight, style.vertical());
        List<TextGlyph> quads = new ArrayList<>();
        List<TextLine> lines = new ArrayList<>(specs.size());
        float[] spacing = new float[specs.size()];
        boolean justify = style.horizontal() == HorizontalAlignment.JUSTIFY;
        float maxWidth = 0;
        for (int index = 0; index < specs.size(); index++) {
            LineSpec spec = specs.get(index);
            String lineText = spec.text();
            float width = walk(lineText, 0, 0, null).width();
            int spaces = spaceCount(lineText);
            float extraSpace = justify && !spec.paragraphEnd() && spaces > 0 && style.maxWidth() > width
                    ? (style.maxWidth() - width) / spaces : 0;
            spacing[index] = extraSpace;
            float lineX = justify ? 0 : alignedX(0, width, style.horizontal());
            float lineY = top + index * resolvedLineHeight;
            walk(lineText, lineX, lineY, quads, extraSpace);
            float visibleWidth = width + extraSpace * spaces;
            lines.add(new TextLine(spec.start(), spec.end(), lineX, lineY, visibleWidth));
            maxWidth = Math.max(maxWidth, visibleWidth);
        }
        return new TextLayout(this, text, style, new Size(maxWidth, specs.size() * resolvedLineHeight),
                resolvedLineHeight, quads, lines, spacing);
    }

    private static int spaceCount(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == ' ' || character == '\t') {
                count++;
            }
        }
        return count;
    }

    private List<LineSpec> wrapLines(String text, TextStyle style) {
        List<LineSpec> lines = new ArrayList<>();
        int paragraphStart = 0;
        for (int index = 0; index <= text.length(); index++) {
            if (index == text.length() || text.charAt(index) == '\n') {
                wrapParagraph(text.substring(paragraphStart, index), paragraphStart, style, lines);
                paragraphStart = index + 1;
            }
        }
        int maxLines = style.maxLines();
        if (maxLines > 0 && lines.size() > maxLines) {
            while (lines.size() > maxLines) {
                lines.remove(lines.size() - 1);
            }
            if (style.overflow() == TextOverflow.ELLIPSIS) {
                int lastIndex = lines.size() - 1;
                LineSpec last = lines.get(lastIndex);
                lines.set(lastIndex, truncate(last, style.maxWidth()));
            }
        }
        return lines;
    }

    private void wrapParagraph(String paragraph, int paragraphStart, TextStyle style,
                               List<LineSpec> lines) {
        if (paragraph.isEmpty()) {
            lines.add(new LineSpec(paragraphStart, paragraphStart, "", true));
            return;
        }
        float maxWidth = style.maxWidth();
        if (maxWidth <= 0) {
            lines.add(new LineSpec(paragraphStart, paragraphStart + paragraph.length(), paragraph, true));
            return;
        }
        int lineStart = 0;
        while (lineStart < paragraph.length()) {
            int lineEnd = findLineEnd(paragraph, lineStart, maxWidth);
            String lineText = paragraph.substring(lineStart, lineEnd);
            int start = paragraphStart + lineStart;
            lineStart = lineEnd;
            while (lineStart < paragraph.length()
                    && (paragraph.charAt(lineStart) == ' ' || paragraph.charAt(lineStart) == '\t')) {
                lineStart++;
            }
            lines.add(new LineSpec(start, paragraphStart + lineEnd, lineText, lineStart == paragraph.length()));
        }
    }

    private int findLineEnd(String paragraph, int start, float maxWidth) {
        int end = start;
        int lastBreak = -1;
        LinePen pen = new LinePen();
        while (end < paragraph.length()) {
            int codepoint = paragraph.codePointAt(end);
            int next = end + Character.charCount(codepoint);
            pen.advance(codepoint, 0);
            boolean overflow = pen.x > maxWidth;
            if (overflow) {
                if (end == start) {
                    end = next;
                } else if (codepoint == ' ' || codepoint == '\t') {
                    lastBreak = end;
                }
                break;
            }
            if (codepoint == ' ' || codepoint == '\t') {
                lastBreak = end;
            } else if (codepoint == '-') {
                lastBreak = next;
            }
            end = next;
        }
        if (end < paragraph.length() && lastBreak > start) {
            end = lastBreak;
        }
        return end;
    }

    private LineSpec truncate(LineSpec line, float maxWidth) {
        String ellipsis = glyphs.containsKey(0x2026) ? "\u2026" : "...";
        String text = line.text();
        int keep = text.length();
        if (maxWidth > 0) {
            while (keep > 0 && walk(text.substring(0, keep) + ellipsis, 0, 0, null).width() > maxWidth) {
                int previous = text.offsetByCodePoints(keep, -1);
                keep = previous;
            }
            if (walk(ellipsis, 0, 0, null).width() > maxWidth) {
                return new LineSpec(line.start(), line.start(), "", true);
            }
        }
        return new LineSpec(line.start(), line.start() + keep, text.substring(0, keep) + ellipsis, true);
    }

    private Size walk(String text, float x, float y, List<TextGlyph> quads) {
        return walk(text, x, y, quads, 0);
    }

    private Size walk(String text, float x, float y, List<TextGlyph> quads, float extraSpace) {
        LinePen pen = new LinePen();
        float baseline = y + ascent;
        float maxWidth = 0;
        int lines = 1;
        for (int index = 0; index < text.length(); ) {
            int codepoint = text.codePointAt(index);
            index += Character.charCount(codepoint);
            if (codepoint == '\n') {
                maxWidth = Math.max(maxWidth, pen.x);
                pen = new LinePen();
                baseline += lineHeight;
                lines++;
                continue;
            }
            Glyph glyph = pen.advance(codepoint, extraSpace);
            if (quads != null && glyph != null && glyph.width() > 0 && glyph.height() > 0) {
                quads.add(new TextGlyph(new Rect(x + pen.origin + glyph.offsetX(), baseline + glyph.offsetY(),
                        glyph.width(), glyph.height()), glyph.uv()));
            }
        }
        maxWidth = Math.max(maxWidth, pen.x);
        return new Size(maxWidth, lines * lineHeight);
    }

    final class LinePen {
        private float x;
        private float origin;
        private int previous = -1;

        float position() {
            return x;
        }

        Glyph advance(int codepoint, float extraSpace) {
            if (codepoint == '\r') {
                return null;
            }
            Glyph glyph = glyphs.get(codepoint == '\t' ? ' ' : codepoint);
            if (glyph == null || codepoint == '\t') {
                if (glyph != null) {
                    x += (glyph.advance() + letterSpacing) * 4 + extraSpace;
                }
                previous = -1;
                return null;
            }
            if (previous >= 0) {
                x += letterSpacing + kerning(previous, codepoint);
            }
            origin = x;
            x += glyph.advance() + (codepoint == ' ' ? extraSpace : 0);
            previous = codepoint;
            return glyph;
        }
    }

    private static void validateOrigin(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Text origin must be finite.");
        }
    }

    private static float alignedX(float anchorX, float lineWidth, HorizontalAlignment alignment) {
        return switch (alignment) {
            case LEFT, JUSTIFY -> anchorX;
            case CENTER -> anchorX - lineWidth / 2;
            case RIGHT -> anchorX - lineWidth;
            default -> throw new IllegalStateException("Unknown horizontal alignment.");
        };
    }

    private static float alignedTop(float anchorY, float blockHeight, VerticalAlignment alignment) {
        return switch (alignment) {
            case TOP -> anchorY;
            case CENTER -> anchorY - blockHeight / 2;
            case BOTTOM -> anchorY - blockHeight;
            default -> throw new IllegalStateException("Unknown vertical alignment.");
        };
    }

    private static long pairKey(int first, int second) {
        return ((long) first << 32) | (second & 0xFFFFFFFFL);
    }

    private static Packing pack(List<StyledDraft> drafts) {
        long area = 0;
        int widest = 0;
        for (StyledDraft draft : drafts) {
            if (draft.width() > 0 && draft.height() > 0) {
                int paddedWidth = draft.width() + 2 * ATLAS_PADDING;
                int paddedHeight = draft.height() + 2 * ATLAS_PADDING;
                area += (long) paddedWidth * paddedHeight;
                widest = Math.max(widest, paddedWidth);
            }
        }

        int width = 64;
        long target = (long) Math.ceil(Math.sqrt(Math.max(1, area)));
        while (width < target || width < widest) {
            width <<= 1;
        }
        if (width > MAX_ATLAS_SIZE) {
            throw new IllegalArgumentException("Font atlas width exceeds " + MAX_ATLAS_SIZE + " pixels.");
        }

        List<PlacedGlyph> placed = new ArrayList<>(drafts.size());
        int penX = ATLAS_PADDING;
        int penY = ATLAS_PADDING;
        int rowHeight = 0;
        for (StyledDraft draft : drafts) {
            int glyphWidth = draft.width();
            int glyphHeight = draft.height();
            if (glyphWidth <= 0 || glyphHeight <= 0) {
                placed.add(new PlacedGlyph(draft, 0, 0));
                continue;
            }
            if (penX + glyphWidth + ATLAS_PADDING > width) {
                penX = ATLAS_PADDING;
                penY += rowHeight + ATLAS_PADDING;
                rowHeight = 0;
            }
            placed.add(new PlacedGlyph(draft, penX, penY));
            penX += glyphWidth + ATLAS_PADDING;
            rowHeight = Math.max(rowHeight, glyphHeight);
        }

        int height = Math.max(1, penY + rowHeight + ATLAS_PADDING);
        if (height > MAX_ATLAS_SIZE) {
            throw new IllegalArgumentException("Font atlas height exceeds " + MAX_ATLAS_SIZE + " pixels.");
        }
        byte[] pixels = new byte[width * height * 4];
        // Linear filtering must interpolate coverage, not darken the tint at glyph borders.
        for (int offset = 0; offset < pixels.length; offset += 4) {
            pixels[offset] = (byte) 0xFF;
            pixels[offset + 1] = (byte) 0xFF;
            pixels[offset + 2] = (byte) 0xFF;
        }
        return new Packing(width, height, pixels, placed);
    }

    private static void rasterize(STBTTFontinfo info, StyledDraft draft, float scale,
                                  boolean bold, boolean italic, int boldOffset,
                                  int atlasWidth, int x, int y, byte[] pixels) {
        int rawWidth = draft.rawWidth();
        int rawHeight = draft.rawHeight();
        ByteBuffer coverage = BufferUtils.createByteBuffer(rawWidth * rawHeight);
        STBTruetype.stbtt_MakeCodepointBitmapSubpixel(info, coverage, rawWidth, rawHeight,
                rawWidth, scale, scale, 0, 0, draft.codepoint());

        int sourceWidth = rawWidth;
        byte[] source = new byte[rawWidth * rawHeight];
        for (int index = 0; index < source.length; index++) {
            source[index] = coverage.get(index);
        }
        if (italic) {
            source = shear(source, sourceWidth, rawHeight, draft.width() - boldOffset,
                    -ITALIC_SLANT * draft.raw().y0() - draft.italicShift());
            sourceWidth = draft.width() - boldOffset;
        }
        if (bold) {
            source = thicken(source, sourceWidth, draft.width(), draft.height(), boldOffset);
            sourceWidth = draft.width();
        }

        int styledHeight = draft.height();
        for (int row = 0; row < styledHeight; row++) {
            for (int column = 0; column < sourceWidth; column++) {
                int alpha = source[row * sourceWidth + column] & 0xFF;
                int offset = ((y + row) * atlasWidth + x + column) * 4;
                pixels[offset + 3] = (byte) alpha;
            }
        }
    }

    /**
     * Shears a glyph bitmap for the synthetic italic style.
     * Every row is sampled between source columns by {@code topShift - ITALIC_SLANT * row}: rounding
     * the shift to whole pixels instead would turn slanted edges into a staircase.
     */
    static byte[] shear(byte[] source, int sourceWidth, int height, int targetWidth, float topShift) {
        byte[] sheared = new byte[targetWidth * height];
        for (int row = 0; row < height; row++) {
            float shift = topShift - ITALIC_SLANT * row;
            int sourceRow = row * sourceWidth;
            int targetRow = row * targetWidth;
            for (int column = 0; column < targetWidth; column++) {
                float sourceColumn = column - shift;
                int left = (int) Math.floor(sourceColumn);
                float fraction = sourceColumn - left;
                int first = left >= 0 && left < sourceWidth
                        ? source[sourceRow + left] & 0xFF : 0;
                int second = left + 1 >= 0 && left + 1 < sourceWidth
                        ? source[sourceRow + left + 1] & 0xFF : 0;
                int value = Math.round(first * (1 - fraction) + second * fraction);
                if (value > 0) {
                    sheared[targetRow + column] = (byte) value;
                }
            }
        }
        return sheared;
    }

    /**
     * Widens glyph coverage to the right by {@code boldOffset} pixels.
     * The shifted copies accumulate instead of replacing each other: a thin stem is usually split
     * over several partially covered pixels, and only adding that coverage up produces a solid
     * stroke. Taking the maximum instead would leave a translucent ghost beside the original stem.
     */
    static byte[] thicken(byte[] source, int sourceWidth, int targetWidth, int height,
                          int boldOffset) {
        byte[] thickened = new byte[targetWidth * height];
        for (int row = 0; row < height; row++) {
            int sourceRow = row * sourceWidth;
            int targetRow = row * targetWidth;
            for (int column = 0; column < sourceWidth; column++) {
                int value = source[sourceRow + column] & 0xFF;
                if (value == 0) {
                    continue;
                }
                for (int offsetX = 0; offsetX <= boldOffset; offsetX++) {
                    int target = targetRow + column + offsetX;
                    int combined = thickened[target] & 0xFF;
                    thickened[target] = (byte) Math.min(255, combined + value);
                }
            }
        }
        return thickened;
    }

    private static int[] buildDefaultCodepoints() {
        List<Integer> codepoints = new ArrayList<>();
        appendRange(codepoints, 0x20, 0x7E);
        appendRange(codepoints, 0xA0, 0xFF);
        appendRange(codepoints, 0x400, 0x4FF);
        int[] extras = {0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D,
                0x2022, 0x2026, 0x20AC, 0x2116};
        for (int extra : extras) {
            codepoints.add(extra);
        }
        int[] result = new int[codepoints.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = codepoints.get(index);
        }
        return result;
    }

    private static void appendRange(List<Integer> codepoints, int start, int end) {
        for (int codepoint = start; codepoint <= end; codepoint++) {
            codepoints.add(codepoint);
        }
    }

    private record GlyphDraft(int codepoint, int x0, int y0, int x1, int y1, float advance) {
        int width() {
            return Math.max(0, x1 - x0);
        }

        int height() {
            return Math.max(0, y1 - y0);
        }
    }

    private record StyledDraft(GlyphDraft raw, int offsetX, int offsetY, int width, int height,
                               int rawWidth, int rawHeight, int italicShift) {
        int codepoint() {
            return raw.codepoint();
        }

        float advance() {
            return raw.advance();
        }
    }

    private record PlacedGlyph(StyledDraft draft, int x, int y) {
    }

    private record LayoutKey(String text, HorizontalAlignment horizontal,
                             VerticalAlignment vertical) {
    }

    private record TextLayoutKey(String text, TextStyle style) {
    }

    private record LineSpec(int start, int end, String text, boolean paragraphEnd) {
    }

    private record Packing(int width, int height, byte[] pixels, List<PlacedGlyph> placed) {
    }
}
