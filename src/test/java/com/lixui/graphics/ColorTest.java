package com.lixui.graphics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ColorTest {
    @Test
    void createsColorsFromByteAndNormalizedComponents() {
        assertEquals(new Color(1, 0, 0, 1), Color.rgb(255, 0, 0));
        assertEquals(new Color(1, 0, 0, 1), Color.rgb(1.0f, 0.0f, 0.0f));
        assertEquals(new Color(1, 0, 0, 128 / 255.0f), Color.rgba(255, 0, 0, 128));
        assertEquals(new Color(1, 0, 0, 0.5f), Color.rgba(1.0f, 0.0f, 0.0f, 0.5f));
        assertEquals(0x010000FF, Color.rgb(1, 0, 0).toRgba());
    }

    @ParameterizedTest
    @CsvSource({
        "'#000', 000000FF",
        "'#fff', FFFFFFFF",
        "'#f80', FF8800FF",
        "'#f808', FF880088",
        "'#aBcD', AABBCCDD",
        "'#123456', 123456FF",
        "'#12345678', 12345678",
        "'#00000000', 00000000",
        "'#FFFFFFFF', FFFFFFFF",
        "f80, FF8800FF",
        "f808, FF880088",
        "12AbEF, 12ABEFFF",
        "12AbEF80, 12ABEF80"
    })
    void parsesHexNotation(String hex, String expected) {
        int packed = Integer.parseUnsignedInt(expected, 16);
        assertEquals(packed, Color.hex(hex).toRgba());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "#", "12", "12345", "1234567", "123456789", "##fff",
        "ggg", "#12z", "#12345g", "#1234567g", "0xFF0000", " #fff", "#fff ",
        "+fff", "-fff", "\uFF11\uFF12\uFF13"
    })
    void rejectsInvalidHexNotation(String hex) {
        assertThrows(IllegalArgumentException.class, () -> Color.hex(hex));
    }

    @Test
    void rejectsNullHex() {
        assertThrows(NullPointerException.class, () -> Color.hex(null));
    }

    @Test
    void preservesPackedByteValuesAcrossRoundTrips() {
        for (int value = 0; value <= 255; value++) {
            int packed = (value << 24) | ((255 - value) << 16) | ((value ^ 0x55) << 8) | value;
            Color color = Color.fromRgba(packed);
            assertEquals(packed, color.toRgba());
            assertEquals(packed >>> 8, color.toRgb());
            assertEquals(color, Color.hex(color.toHex()));
            assertEquals(packed >>> 8, Color.fromRgb(packed >>> 8).toRgb());
            assertEquals(1.0f, Color.fromRgb(packed >>> 8).a());
        }
    }

    @Test
    void roundsNormalizedComponentsWithoutPremultiplying() {
        Color color = Color.rgba(0.5f, 0.25f, 0.75f, 0.0f);
        assertEquals(0x8040BF00, color.toRgba());
        assertEquals(0x8040BF, color.toRgb());
        assertEquals("#8040BF00", color.toHex());
        assertEquals("#00000000", Color.TRANSPARENT.toHex());
        assertEquals("#000000FF", Color.BLACK.toHex());
        assertEquals("#FFFFFFFF", Color.WHITE.toHex());
    }

    @Test
    void changesAlphaWithoutChangingOriginalOrQuantizingRgb() {
        Color original = new Color(0.1234f, 0.5678f, 0.9012f, 1.0f);
        Color translucent = original.withAlpha(0.25f);
        assertEquals(new Color(0.1234f, 0.5678f, 0.9012f, 0.25f), translucent);
        assertEquals(1.0f, original.a());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 256, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void rejectsInvalidByteComponents(int value) {
        assertThrows(IllegalArgumentException.class, () -> Color.rgba(value, 0, 0, 255));
        assertThrows(IllegalArgumentException.class, () -> Color.rgba(0, value, 0, 255));
        assertThrows(IllegalArgumentException.class, () -> Color.rgba(0, 0, value, 255));
        assertThrows(IllegalArgumentException.class, () -> Color.rgba(0, 0, 0, value));
    }

    @ParameterizedTest
    @ValueSource(floats = {-0.001f, 1.001f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY})
    void rejectsInvalidNormalizedComponents(float value) {
        assertThrows(IllegalArgumentException.class, () -> new Color(value, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Color(0, value, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Color(0, 0, value, 1));
        assertThrows(IllegalArgumentException.class, () -> new Color(0, 0, 0, value));
        assertThrows(IllegalArgumentException.class, () -> Color.WHITE.withAlpha(value));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0x01000000, Integer.MIN_VALUE})
    void rejectsPackedRgbWithExtraBits(int rgb) {
        assertThrows(IllegalArgumentException.class, () -> Color.fromRgb(rgb));
    }

    @Test
    void treatsNegativeZeroAsZeroForEqualityAndHashing() {
        Color negativeZero = new Color(-0.0f, -0.0f, -0.0f, -0.0f);
        assertEquals(Color.TRANSPARENT, negativeZero);
        assertEquals(Color.TRANSPARENT.hashCode(), negativeZero.hashCode());
    }
}
