package com.lixui.text;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemFontsTest {
    @Test
    void resolvesMissingFamiliesToEmpty() {
        assertTrue(SystemFonts.find("lixui-font-that-does-not-exist").isEmpty());
        assertTrue(SystemFonts.find("   ").isEmpty());
        assertThrows(NullPointerException.class, () -> SystemFonts.find(null));
    }

    @Test
    void findsAndLoadsADefaultFontWhenTheSystemProvidesOne() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");

        Font font = Font.load(data.get(), 16);

        assertNotNull(font.glyph('A'));
        assertTrue(font.ascent() > 0);
        assertTrue(font.lineHeight() > 0);
    }

    @Test
    void findsAFamilyByNameCaseInsensitivelyWhenAvailable() {
        Optional<FontData> any = SystemFonts.findDefault();
        Assumptions.assumeTrue(any.isPresent(), "No readable system font is installed.");

        String family = SystemFonts.families().get(0);
        Optional<FontData> found = SystemFonts.find(family.toLowerCase());

        assertTrue(found.isPresent());
    }
}
