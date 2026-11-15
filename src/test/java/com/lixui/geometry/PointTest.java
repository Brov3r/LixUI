package com.lixui.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PointTest {
    @Test
    void exposesCoordinatesAndNormalizesNegativeZero() {
        Point point = new Point(-0f, 3);

        assertEquals(0, point.x());
        assertEquals(3, point.y());
    }

    @Test
    void translatesAndMeasuresDistance() {
        Point point = new Point(1, 2);

        Point shifted = point.translate(3, -1);

        assertEquals(new Point(4, 1), shifted);
        assertEquals(5, point.distance(new Point(4, 6)));
    }

    @Test
    void interpolatesBetweenPoints() {
        Point start = new Point(0, 0);
        Point end = new Point(10, 20);

        assertEquals(new Point(5, 10), start.lerp(end, 0.5f));
        assertEquals(new Point(-10, -20), start.lerp(end, -1));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new Point(Float.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new Point(0, Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new Point(0, 0).translate(Float.NaN, 0));
        assertThrows(NullPointerException.class, () -> new Point(0, 0).distance(null));
        assertThrows(NullPointerException.class, () -> new Point(0, 0).lerp(null, 0.5f));
        assertThrows(IllegalArgumentException.class, () -> new Point(0, 0).lerp(new Point(1, 1), Float.NaN));
    }
}
