package com.lixui.text;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemFontsTest {
    @Test
    void familyLookupPrefersRegularEvenWhenBoldIsScannedFirst(@TempDir Path directory) throws IOException {
        for (String family : List.of("Arial", "DejaVu Sans", "Liberation Sans")) {
            Optional<FontData> regular = SystemFonts.find(family);
            Optional<FontData> bold = SystemFonts.find(family, FontStyle.BOLD);
            if (regular.isEmpty() || bold.isEmpty()) {
                continue;
            }
            Path first = Files.createDirectory(directory.resolve("first"));
            Path second = Files.createDirectory(directory.resolve("second"));
            Path boldFile = first.resolve("face.ttf");
            Path regularFile = second.resolve("face.ttf");
            assertTrue(SystemFonts.find("  " + family + "  ", FontStyle.BOLD).isPresent());
            Files.write(boldFile, bytes(bold.get()));
            Files.write(regularFile, bytes(regular.get()));
            SystemFonts.Index index = SystemFonts.scan(List.of(first, second));
            String key = family.toLowerCase(Locale.ROOT);
            assertEquals(regularFile, index.paths().get(key));
            assertEquals(boldFile, index.paths().get(key + " bold"));
            return;
        }
        Assumptions.abort("No installed regular/bold test pair.");
    }

    private static byte[] bytes(FontData data) {
        ByteBuffer buffer = data.buffer().duplicate();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    @Test
    void resolvesInstalledBoldAndItalicFacesWhenAvailable() {
        String family = null;
        Optional<FontData> bold = Optional.empty();
        Optional<FontData> italic = Optional.empty();
        for (String candidate : List.of("Segoe UI", "Arial", "Helvetica Neue", "Helvetica",
                "DejaVu Sans", "Liberation Sans", "Noto Sans", "Tahoma", "Verdana")) {
            Optional<FontData> boldFace = SystemFonts.find(candidate, FontStyle.BOLD);
            Optional<FontData> italicFace = SystemFonts.find(candidate, FontStyle.ITALIC);
            if (boldFace.isPresent() && italicFace.isPresent()) {
                family = candidate;
                bold = boldFace;
                italic = italicFace;
                break;
            }
        }
        Assumptions.assumeTrue(family != null, "No installed bold and italic faces are available.");

        Font boldFont = Font.load(bold.orElseThrow(), 20, new int[]{'A'});
        Font italicFont = Font.load(italic.orElseThrow(), 20, new int[]{'A'});

        assertEquals(FontStyle.REGULAR, boldFont.style());
        assertEquals(FontStyle.REGULAR, italicFont.style());
        assertNotNull(boldFont.glyph('A'));
        assertNotNull(italicFont.glyph('A'));
    }

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
