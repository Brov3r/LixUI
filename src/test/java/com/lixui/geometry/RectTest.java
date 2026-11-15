package com.lixui.geometry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RectTest {
    @Test
    void exposesEdgesAndEmptiness() {
        Rect rect = new Rect(10, 20, 100, 50);
        assertEquals(10, rect.x());
        assertEquals(20, rect.y());
        assertEquals(100, rect.width());
        assertEquals(50, rect.height());
        assertEquals(110, rect.right());
        assertEquals(70, rect.bottom());
        assertFalse(rect.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0, 0, 10",
        "0, 0, 10, 0",
        "0, 0, 0, 0"
    })
    void detectsEmptyDimensions(float x, float y, float width, float height) {
        assertTrue(new Rect(x, y, width, height).isEmpty());
    }

    @Test
    void normalizesNegativeZero() {
        Rect rect = new Rect(-0.0f, -0.0f, -0.0f, -0.0f);
        assertEquals(0, Float.floatToIntBits(rect.x()));
        assertEquals(0, Float.floatToIntBits(rect.y()));
        assertEquals(0, Float.floatToIntBits(rect.width()));
        assertEquals(0, Float.floatToIntBits(rect.height()));
    }

    @ParameterizedTest
    @CsvSource({
        "NaN, 0, 10, 10",
        "0, NaN, 10, 10",
        "0, 0, NaN, 10",
        "0, 0, 10, NaN",
        "Infinity, 0, 10, 10",
        "0, -Infinity, 10, 10",
        "0, 0, Infinity, 10",
        "0, 0, 10, -Infinity",
        "0, 0, -1, 10",
        "0, 0, 10, -1"
    })
    void rejectsInvalidComponents(float x, float y, float width, float height) {
        assertThrows(IllegalArgumentException.class, () -> new Rect(x, y, width, height));
    }

    @Test
    void intersectsPartiallyOverlappingRectangles() {
        Rect first = new Rect(0, 0, 10, 10);
        assertEquals(new Rect(5, 5, 5, 5), first.intersect(new Rect(5, 5, 10, 10)));
        assertEquals(new Rect(5, 5, 5, 5), new Rect(5, 5, 10, 10).intersect(first));
    }

    @Test
    void returnsTheContainedRectangleWhenItFitsCompletely() {
        Rect outer = new Rect(0, 0, 100, 100);
        Rect inner = new Rect(20, 30, 10, 15);
        assertEquals(inner, outer.intersect(inner));
        assertEquals(inner, inner.intersect(outer));
    }

    @Test
    void returnsEmptyForTouchingOrDisjointRectangles() {
        Rect rect = new Rect(0, 0, 10, 10);
        assertSame(Rect.EMPTY, rect.intersect(new Rect(10, 0, 5, 10)));
        assertSame(Rect.EMPTY, rect.intersect(new Rect(0, 10, 10, 5)));
        assertSame(Rect.EMPTY, rect.intersect(new Rect(20, 20, 5, 5)));
        assertSame(Rect.EMPTY, rect.intersect(Rect.EMPTY));
        assertSame(Rect.EMPTY, Rect.EMPTY.intersect(rect));
    }

    @Test
    void rejectsNullIntersectionTarget() {
        assertThrows(NullPointerException.class, () -> new Rect(0, 0, 1, 1).intersect(null));
    }
}
