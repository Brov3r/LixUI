package com.lixui.text;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.render.ImageData;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontTest {
    private static final Rect UV = new Rect(0, 0, 0.25f, 0.5f);

    @Test
    void positionsGlyphsWithAdvancesKerningAndLineHeight() {
        Font font = testFont();

        List<TextGlyph> quads = font.layout("AB", 100, 50);

        assertEquals(2, quads.size());
        assertEquals(new Rect(101, 52, 8, 12), quads.get(0).bounds());
        assertEquals(UV, quads.get(0).uv());
        assertEquals(new Rect(108.5f, 54, 6, 10), quads.get(1).bounds());
        assertEquals(UV, quads.get(1).uv());
    }

    @Test
    void alignsTextAroundTheAnchor() {
        Font font = testFont();

        TextGlyph left = font.layout("A", 100, 50,
                HorizontalAlignment.LEFT, VerticalAlignment.TOP).get(0);
        assertEquals(new Rect(101, 52, 8, 12), left.bounds());

        TextGlyph centered = font.layout("A", 100, 50,
                HorizontalAlignment.CENTER, VerticalAlignment.CENTER).get(0);
        assertEquals(new Rect(96, 43, 8, 12), centered.bounds());

        TextGlyph right = font.layout("A", 100, 50,
                HorizontalAlignment.RIGHT, VerticalAlignment.BOTTOM).get(0);
        assertEquals(new Rect(91, 34, 8, 12), right.bounds());
    }

    @Test
    void alignsEveryLineHorizontallyWithinTheAnchor() {
        Font font = testFont();

        List<TextGlyph> quads = font.layout("A\nAB", 100, 50,
                HorizontalAlignment.CENTER, VerticalAlignment.TOP);

        assertEquals(new Rect(96, 52, 8, 12), quads.get(0).bounds());
        assertEquals(new Rect(93.25f, 70, 8, 12), quads.get(1).bounds());
    }

    @Test
    void alignsTheBlockVertically() {
        Font font = testFont();

        List<TextGlyph> quads = font.layout("A\nAB", 0, 50,
                HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        assertEquals(new Rect(1, 34, 8, 12), quads.get(0).bounds());
        assertEquals(new Rect(1, 52, 8, 12), quads.get(1).bounds());
    }

    @Test
    void rejectsNullAlignments() {
        Font font = testFont();

        assertThrows(NullPointerException.class, () -> font.layout("A", 0, 0, null,
                VerticalAlignment.TOP));
        assertThrows(NullPointerException.class, () -> font.layout("A", 0, 0,
                HorizontalAlignment.LEFT, null));
    }

    @Test
    void cachesRelativeLayoutsByTextAndAlignment() {
        Font font = testFont();

        List<TextGlyph> first = font.layoutRelative("AB",
                HorizontalAlignment.LEFT, VerticalAlignment.TOP);
        List<TextGlyph> second = font.layoutRelative("AB",
                HorizontalAlignment.LEFT, VerticalAlignment.TOP);

        assertSame(first, second);
        assertNotSame(first, font.layoutRelative("AB",
                HorizontalAlignment.CENTER, VerticalAlignment.TOP));
        assertEquals(font.layout("AB", 0, 0), first);
    }

    @Test
    void newlinesResetThePenAndAdvanceTheBaseline() {
        Font font = testFont();

        List<TextGlyph> quads = font.layout("A\nB", 0, 0);

        assertEquals(2, quads.size());
        assertEquals(new Rect(1, 2, 8, 12), quads.get(0).bounds());
        assertEquals(new Rect(0, 22, 6, 10), quads.get(1).bounds());
    }

    @Test
    void whitespaceAdvancesWithoutProducingQuadsAndTabsExpandToFourSpaces() {
        Font font = testFont();

        assertEquals(List.of(), font.layout("   ", 0, 0));
        assertEquals(new Rect(5, 2, 8, 12), font.layout(" A", 0, 0).get(0).bounds());
        assertEquals(new Rect(17, 2, 8, 12), font.layout("\tA", 0, 0).get(0).bounds());
    }

    @Test
    void skipsUnbakedCodePoints() {
        Font font = testFont();

        assertTrue(font.layout("\u4E00", 0, 0).isEmpty());
        assertEquals(0, font.measure("\u4E00").width(), 0.0001f);
    }

    @Test
    void measuresAdvancesKerningAndMultilineBlocks() {
        Font font = testFont();

        assertSize(new Size(10, 18), font.measure("A"));
        assertSize(new Size(15.5f, 18), font.measure("AB"));
        assertSize(new Size(14, 18), font.measure(" A"));
        assertSize(new Size(16, 18), font.measure("\t"));
        assertSize(new Size(15.5f, 36), font.measure("A\nAB"));
        assertSize(new Size(0, 18), font.measure(""));
    }

    @Test
    void wrapsWordsAtTheMaximumWidth() {
        Font font = testFont();
        TextStyle style = new TextStyle(25, 0, 0, TextOverflow.CLIP,
                HorizontalAlignment.LEFT, VerticalAlignment.TOP);

        TextLayout layout = font.layoutBlockRelative("A A A", style);

        assertSize(new Size(24, 36), layout.size());
        assertEquals(2, layout.lines().size());
        assertEquals(new TextLine(0, 3, 0, 0, 24), layout.lines().get(0));
        assertEquals(new TextLine(4, 5, 0, 18, 10), layout.lines().get(1));
        assertEquals(3, layout.glyphs().size());
        assertSize(new Size(24, 36), font.measure("A A A", style));
    }

    @Test
    void hardBreaksWordsThatExceedTheMaximumWidth() {
        Font font = testFont();
        TextStyle style = new TextStyle(12, 0, 0, TextOverflow.CLIP,
                HorizontalAlignment.LEFT, VerticalAlignment.TOP);

        TextLayout layout = font.layoutBlockRelative("AB", style);

        assertEquals(2, layout.lines().size());
        assertEquals(10, layout.lines().get(0).width());
        assertEquals(7, layout.lines().get(1).width());
        assertEquals(36, layout.size().height());
    }

    @Test
    void appliesExplicitLineHeightAndAlignment() {
        Font font = testFont();
        TextStyle style = new TextStyle(25, 20, 0, TextOverflow.CLIP,
                HorizontalAlignment.CENTER, VerticalAlignment.TOP);

        TextLayout layout = font.layoutBlockRelative("A A A", style);

        assertEquals(40, layout.size().height());
        assertEquals(-12, layout.lines().get(0).x());
        assertEquals(0, layout.lines().get(0).y());
        assertEquals(20, layout.lines().get(1).y());
    }

    @Test
    void clipsOrEllipsizesLinesBeyondTheMaximum() {
        Font font = testFont();
        TextStyle clip = new TextStyle(25, 0, 1, TextOverflow.CLIP,
                HorizontalAlignment.LEFT, VerticalAlignment.TOP);
        TextStyle ellipsis = new TextStyle(25, 0, 1, TextOverflow.ELLIPSIS,
                HorizontalAlignment.LEFT, VerticalAlignment.TOP);

        TextLayout clipped = font.layoutBlockRelative("A A A", clip);
        TextLayout truncated = font.layoutBlockRelative("A A A", ellipsis);

        assertEquals(1, clipped.lines().size());
        assertEquals(24, clipped.lines().get(0).width());
        assertEquals(1, truncated.lines().size());
        assertEquals(22, truncated.lines().get(0).width());
        assertEquals(1, truncated.lines().get(0).end());
    }

    @Test
    void exposesCaretAndSelectionGeometry() {
        Font font = testFont();
        TextLayout layout = font.layoutBlockRelative("A A", TextStyle.DEFAULT);

        assertSize(new Size(24, 18), layout.size());
        assertEquals(10, layout.caretPosition(1).x());
        assertEquals(14, layout.caretPosition(2).x());
        assertEquals(24, layout.caretPosition(3).x());
        assertEquals(1, layout.caretIndexAt(11, 5));
        assertEquals(2, layout.caretIndexAt(13, 5));
        assertEquals(List.of(new Rect(0, 0, 14, 18)), layout.selectionRects(0, 2));
        assertEquals(List.of(new Rect(10, 0, 14, 18)), layout.selectionRects(1, 3));
    }

    @Test
    void letterSpacingAddsSpaceBetweenGlyphsOnly() {
        Font font = testFont(2);

        List<TextGlyph> quads = font.layout("AB", 100, 50);

        assertEquals(new Rect(101, 52, 8, 12), quads.get(0).bounds());
        assertEquals(new Rect(110.5f, 54, 6, 10), quads.get(1).bounds());
        assertEquals(17.5f, font.measure("AB").width(), 0.0001f);
        assertEquals(10, font.measure("A").width(), 0.0001f);
        assertEquals(24, font.measure("\t").width(), 0.0001f);
        assertEquals(2, font.letterSpacing());
    }

    @Test
    void exposesGlyphsAdvancesAndKerning() {
        Font font = testFont();

        assertEquals(10, font.advance('A'));
        assertEquals(0, font.advance('Z'));
        assertEquals(-1.5f, font.kerning('A', 'B'));
        assertEquals(0, font.kerning('B', 'A'));
        assertNotNull(font.glyph('A'));
        assertNull(font.glyph('Z'));
        assertEquals(12, font.ascent());
        assertEquals(-4, font.descent());
        assertEquals(18, font.lineHeight());
        assertEquals(16, font.pixelSize());
        assertEquals(4, font.atlas().width());
    }

    @Test
    void rejectsInvalidLayoutArguments() {
        Font font = testFont();

        assertThrows(NullPointerException.class, () -> font.layout(null, 0, 0));
        assertThrows(NullPointerException.class, () -> font.measure(null));
        assertThrows(IllegalArgumentException.class, () -> font.layout("A", Float.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> font.layout("A", 0, Float.POSITIVE_INFINITY));
    }

    @Test
    void loadsTrueTypeFontFromTheSystemWhenAvailable() {
        FontData data = SystemFonts.findDefault().orElse(null);
        Assumptions.assumeTrue(data != null, "No readable system font is installed.");

        Font font = Font.load(data, 24);
        Glyph glyph = font.glyph('A');

        assertTrue(font.atlas().width() > 0);
        assertTrue(font.atlas().height() > 0);
        assertTrue(font.ascent() > 0);
        assertTrue(font.lineHeight() > 0);
        assertNotNull(glyph);
        assertTrue(glyph.advance() > 0);
        assertTrue(coveredPixels(font, glyph) > 0);
        assertFalse(font.layout("AB", 0, 0).isEmpty());
        assertTrue(font.measure("AB").width() > font.measure("A").width());
    }

    @Test
    void bakesSyntheticBoldAndItalicStyles() {
        FontData data = SystemFonts.findDefault().orElse(null);
        Assumptions.assumeTrue(data != null, "No readable system font is installed.");

        Font regular = Font.load(data, 32, new int[]{'A'}, FontStyle.REGULAR);
        Font bold = Font.load(data, 32, new int[]{'A'}, FontStyle.BOLD);
        Font italic = Font.load(data, 32, new int[]{'A'}, FontStyle.ITALIC);
        Font boldItalic = Font.load(data, 32, new int[]{'A'}, FontStyle.BOLD_ITALIC);

        Glyph regularGlyph = regular.glyph('A');
        Glyph boldGlyph = bold.glyph('A');
        Glyph italicGlyph = italic.glyph('A');
        Glyph boldItalicGlyph = boldItalic.glyph('A');
        assertNotNull(regularGlyph);
        assertNotNull(boldGlyph);
        assertNotNull(italicGlyph);
        assertNotNull(boldItalicGlyph);
        assertEquals(FontStyle.REGULAR, regular.style());
        assertEquals(FontStyle.BOLD, bold.style());
        assertEquals(FontStyle.ITALIC, italic.style());
        assertEquals(FontStyle.BOLD_ITALIC, boldItalic.style());
        assertEquals(regularGlyph.advance(), boldGlyph.advance());
        assertEquals(regularGlyph.advance(), italicGlyph.advance());
        assertTrue(boldGlyph.width() > regularGlyph.width());
        assertEquals(regularGlyph.height(), boldGlyph.height());
        assertTrue(italicGlyph.width() > regularGlyph.width());
        assertTrue(boldItalicGlyph.width() >= italicGlyph.width());
        assertEquals(italicGlyph.height(), boldItalicGlyph.height());

        Font regularPair = Font.load(data, 32, new int[]{'A', 'B'});
        Font spacedPair = Font.load(data, 32, new int[]{'A', 'B'}, FontStyle.BOLD, 2);
        assertEquals(2, spacedPair.letterSpacing(), 0.0001f);
        assertEquals(regularPair.measure("AB").width() + 2, spacedPair.measure("AB").width(), 0.01f);
    }

    @Test
    void bakesOnlyRequestedCodePoints() {
        FontData data = SystemFonts.findDefault().orElse(null);
        Assumptions.assumeTrue(data != null, "No readable system font is installed.");

        Font font = Font.load(data, 18, new int[]{'A'});

        assertNotNull(font.glyph('A'));
        assertNull(font.glyph('B'));
        assertEquals(0, font.advance('B'));
    }

    @Test
    void rejectsInvalidFontLoadingArguments() {
        assertThrows(NullPointerException.class, () -> Font.load(null, 16));
        FontData invalid = FontData.of(new byte[]{1, 2, 3, 4});
        assertThrows(IllegalArgumentException.class, () -> Font.load(invalid, 16));
        FontData data = SystemFonts.findDefault().orElse(null);
        Assumptions.assumeTrue(data != null, "No readable system font is installed.");
        assertThrows(IllegalArgumentException.class, () -> Font.load(data, 0));
        assertThrows(IllegalArgumentException.class, () -> Font.load(data, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> Font.load(data, 16, new int[]{-1}));
        assertThrows(IllegalArgumentException.class,
                () -> Font.load(data, 16, new int[]{Character.MAX_CODE_POINT + 1}));
        assertThrows(NullPointerException.class, () -> Font.load(data, 16, (FontStyle) null));
        assertThrows(NullPointerException.class, () -> Font.load(data, 16, new int[]{'A'}, null));
        assertThrows(IllegalArgumentException.class, () -> Font.load(data, 16, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> Font.load(data, 16, Float.POSITIVE_INFINITY));
    }

    private static void assertSize(Size expected, Size actual) {
        assertEquals(expected.width(), actual.width(), 0.0001f);
        assertEquals(expected.height(), actual.height(), 0.0001f);
    }

    private static int coveredPixels(Font font, Glyph glyph) {
        ImageData atlas = font.atlas();
        int left = Math.round(glyph.uv().x() * atlas.width());
        int top = Math.round(glyph.uv().y() * atlas.height());
        int right = Math.round(glyph.uv().right() * atlas.width());
        int bottom = Math.round(glyph.uv().bottom() * atlas.height());
        int covered = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                if ((atlas.pixels()[(y * atlas.width() + x) * 4 + 3] & 0xFF) > 0) {
                    covered++;
                }
            }
        }
        return covered;
    }

    private static Font testFont() {
        return testFont(0);
    }

    private static Font testFont(float letterSpacing) {
        Map<Integer, Glyph> glyphs = new HashMap<>();
        glyphs.put((int) ' ', new Glyph(' ', Rect.EMPTY, 0, 0, 0, 0, 4));
        glyphs.put((int) '.', new Glyph('.', Rect.EMPTY, 0, 0, 0, 0, 4));
        glyphs.put((int) 'A', new Glyph('A', UV, 1, -10, 8, 12, 10));
        glyphs.put((int) 'B', new Glyph('B', UV, 0, -8, 6, 10, 7));
        Map<Long, Float> kerning = Map.of(((long) 'A' << 32) | 'B', -1.5f);
        return new Font(new ImageData(4, 4, new byte[64]), 16, 12, -4, 18,
                letterSpacing, FontStyle.REGULAR, glyphs, kerning);
    }
}
