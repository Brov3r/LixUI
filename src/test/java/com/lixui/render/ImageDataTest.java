package com.lixui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
