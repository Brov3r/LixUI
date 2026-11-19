package com.lixui.text;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FontFamilyTest {
    @Test
    void resolvesEveryStyleWithFallbacks() {
        FontData data = fontData();
        Font regular = Font.load(data, 16);
        Font bold = Font.load(data, 16, FontStyle.BOLD);
        Font italic = Font.load(data, 16, FontStyle.ITALIC);
        Font boldItalic = Font.load(data, 16, FontStyle.BOLD_ITALIC);

        FontFamily full = new FontFamily(regular, bold, italic, boldItalic);
        assertSame(regular, full.resolve(FontStyle.REGULAR));
        assertSame(bold, full.resolve(FontStyle.BOLD));
        assertSame(italic, full.resolve(FontStyle.ITALIC));
        assertSame(boldItalic, full.resolve(FontStyle.BOLD_ITALIC));

        FontFamily partial = new FontFamily(regular, bold, null, null);
        assertSame(regular, partial.resolve(FontStyle.ITALIC));
        assertSame(bold, partial.resolve(FontStyle.BOLD_ITALIC));

        FontFamily onlyItalic = new FontFamily(regular, null, italic, null);
        assertSame(italic, onlyItalic.resolve(FontStyle.BOLD_ITALIC));

        FontFamily onlyRegular = FontFamily.of(regular);
        assertSame(regular, onlyRegular.resolve(FontStyle.BOLD_ITALIC));
    }

    @Test
    void rejectsMissingRegularFaceAndStyle() {
        Font regular = Font.load(fontData(), 16);
        assertThrows(NullPointerException.class, () -> new FontFamily(null, null, null, null));
        assertThrows(NullPointerException.class, () -> new FontFamily(regular, null, null, null)
                .resolve(null));
    }

    private static FontData fontData() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        return data.get();
    }
}
