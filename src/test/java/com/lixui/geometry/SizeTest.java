package com.lixui.geometry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SizeTest {
    @Test
    void changesDimensionsInPlaceWithoutAffectingOtherInstances() {
        Size size = new Size();
        Size other = new Size();
        assertSame(size, size.set(1280, 720));
        assertSame(size, size.setWidth(640.5f));
        assertEquals(720, size.height());
        assertSame(size, size.setHeight(360.25f));
        assertEquals(640.5f, size.width());
        assertEquals(360.25f, size.height());
        assertEquals(0, other.width());
        assertEquals(0, other.height());
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0, true",
        "0, 720, true",
        "1280, 0, true",
        "1280, 720, false",
        "0.25, 0.5, false"
    })
    void detectsEmptyDimensions(float width, float height, boolean empty) {
        assertEquals(empty, new Size(width, height).isEmpty());
    }

    @Test
    void acceptsFinitePositiveExtremesWithoutRelyingOnAreaMultiplication() {
        assertFalse(new Size(Float.MIN_VALUE, Float.MIN_VALUE).isEmpty());
        assertFalse(new Size(Float.MAX_VALUE, Float.MAX_VALUE).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(floats = {-Float.MIN_VALUE, -1, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY})
    void rejectsInvalidDimensionsWithoutPartialChanges(float value) {
        assertThrows(IllegalArgumentException.class, () -> new Size(value, 1));
        assertThrows(IllegalArgumentException.class, () -> new Size(1, value));
        Size size = new Size(100, 200);
        assertThrows(IllegalArgumentException.class, () -> size.set(value, 300));
        assertEquals(100, size.width());
        assertEquals(200, size.height());
        assertThrows(IllegalArgumentException.class, () -> size.set(300, value));
        assertEquals(100, size.width());
        assertEquals(200, size.height());
        assertThrows(IllegalArgumentException.class, () -> size.setWidth(value));
        assertThrows(IllegalArgumentException.class, () -> size.setHeight(value));
        assertEquals(100, size.width());
        assertEquals(200, size.height());
    }

    @Test
    void normalizesNegativeZeroDuringConstructionAndMutation() {
        Size size = new Size(-0.0f, -0.0f);
        assertEquals(0.0f, size.width());
        assertEquals(0.0f, size.height());
        size.set(10, 20).setWidth(-0.0f).setHeight(-0.0f);
        assertEquals(0.0f, size.width());
        assertEquals(0.0f, size.height());
    }
}
