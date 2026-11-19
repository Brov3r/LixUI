package com.lixui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageDataTest {
    @Test
    void acceptsRgbaPayloadWithExactSize() {
        byte[] pixels = new byte[2 * 3 * 4];
        ImageData image = new ImageData(2, 3, pixels);

        assertEquals(2, image.width());
        assertEquals(3, image.height());
        assertSame(pixels, image.pixels());
    }

    @Test
    void rejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new ImageData(0, 1, new byte[4]));
        assertThrows(IllegalArgumentException.class, () -> new ImageData(1, 0, new byte[4]));
        assertThrows(IllegalArgumentException.class, () -> new ImageData(-1, 1, new byte[4]));
    }

    @Test
    void rejectsPayloadWithWrongSize() {
        assertThrows(IllegalArgumentException.class, () -> new ImageData(2, 2, new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> new ImageData(2, 2, new byte[17]));
    }

    @Test
    void rejectsNullPixels() {
        assertThrows(NullPointerException.class, () -> new ImageData(1, 1, null));
    }

    @Test
    void loadsAndScalesClasspathImages() {
        ImageData icon = ImageData.fromResource("assets/icons-128/light-mode.png", 16, 16);

        assertEquals(16, icon.width());
        assertEquals(16, icon.height());
        assertEquals(16 * 16 * 4, icon.pixels().length);
        boolean hasCoverage = false;
        for (int index = 3; index < icon.pixels().length; index += 4) {
            if (icon.pixels()[index] != 0) {
                hasCoverage = true;
                break;
            }
        }
        assertTrue(hasCoverage);
    }

    @Test
    void rejectsDimensionsThatOverflowTheRgbaPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> new ImageData(Integer.MAX_VALUE, Integer.MAX_VALUE, new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> ImageData.fromResource("assets/icons-128/light-mode.png", 65536, 65536));
    }

    @Test
    void rejectsMissingResourcesAndInvalidScales() {
        assertThrows(NullPointerException.class, () -> ImageData.fromResource(null));
        assertThrows(IllegalArgumentException.class, () -> ImageData.fromResource("missing.png"));
        assertThrows(IllegalArgumentException.class,
                () -> ImageData.fromResource("assets/icons-128/light-mode.png", 0, 16));
        assertThrows(IllegalArgumentException.class,
                () -> ImageData.fromResource("assets/icons-128/light-mode.png", 16, -1));
    }
}
