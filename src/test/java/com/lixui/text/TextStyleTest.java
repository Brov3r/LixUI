package com.lixui.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextStyleTest {
    @Test
    void createsNonWrappingStyleWithAlignment() {
        TextStyle style = TextStyle.of(HorizontalAlignment.CENTER, VerticalAlignment.BOTTOM);

        assertEquals(0, style.maxWidth());
        assertEquals(0, style.lineHeight());
        assertEquals(0, style.maxLines());
        assertEquals(TextOverflow.CLIP, style.overflow());
        assertEquals(HorizontalAlignment.CENTER, style.horizontal());
        assertEquals(VerticalAlignment.BOTTOM, style.vertical());
    }

    @Test
    void rejectsInvalidDimensionsAndNulls() {
        assertThrows(IllegalArgumentException.class, () -> new TextStyle(
                -1, 0, 0, TextOverflow.CLIP, HorizontalAlignment.LEFT, VerticalAlignment.TOP));
        assertThrows(IllegalArgumentException.class, () -> new TextStyle(
                0, -1, 0, TextOverflow.CLIP, HorizontalAlignment.LEFT, VerticalAlignment.TOP));
        assertThrows(IllegalArgumentException.class, () -> new TextStyle(
                0, 0, -1, TextOverflow.CLIP, HorizontalAlignment.LEFT, VerticalAlignment.TOP));
        assertThrows(NullPointerException.class, () -> new TextStyle(
                0, 0, 0, null, HorizontalAlignment.LEFT, VerticalAlignment.TOP));
        assertThrows(NullPointerException.class, () -> new TextStyle(
                0, 0, 0, TextOverflow.CLIP, null, VerticalAlignment.TOP));
        assertThrows(NullPointerException.class, () -> new TextStyle(
                0, 0, 0, TextOverflow.CLIP, HorizontalAlignment.LEFT, null));
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY})
    void rejectsNonFiniteDimensions(float value) {
        assertThrows(IllegalArgumentException.class, () -> new TextStyle(
                value, 0, 0, TextOverflow.CLIP, HorizontalAlignment.LEFT, VerticalAlignment.TOP));
        assertThrows(IllegalArgumentException.class, () -> new TextStyle(
                0, value, 0, TextOverflow.CLIP, HorizontalAlignment.LEFT, VerticalAlignment.TOP));
    }
}
