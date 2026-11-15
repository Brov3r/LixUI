package com.lixui.graphics;

import com.lixui.geometry.Point;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaintTest {
    private static final Color RED = Color.rgb(255, 0, 0);
    private static final Color GREEN = Color.rgb(0, 255, 0);
    private static final Color BLUE = Color.rgb(0, 0, 255);

    @Test
    void solidPaintReturnsItsColor() {
        Paint paint = Paint.solid(RED);

        assertSame(RED, paint.colorAt(new Point(100, 200)));
        assertEquals(new Paint.Solid(RED), paint);
    }

    @Test
    void linearGradientInterpolatesMultipleStopsAndClamps() {
        Paint paint = Paint.linear(new Point(0, 0), new Point(100, 0),
                new GradientStop(0, RED),
                new GradientStop(0.5f, GREEN),
                new GradientStop(1, BLUE));

        assertEquals(RED, paint.colorAt(new Point(-10, 0)));
        assertEquals(RED, paint.colorAt(new Point(0, 0)));
        assertEquals(new Color(0.5f, 0.5f, 0, 1), paint.colorAt(new Point(25, 0)));
        assertEquals(GREEN, paint.colorAt(new Point(50, 0)));
        assertEquals(BLUE, paint.colorAt(new Point(100, 0)));
        assertEquals(BLUE, paint.colorAt(new Point(200, 0)));
    }

    @Test
    void radialGradientUsesDistanceFromTheCenter() {
        Paint paint = Paint.radial(new Point(0, 0), 100,
                new GradientStop(0, RED),
                new GradientStop(1, BLUE));

        assertEquals(RED, paint.colorAt(new Point(0, 0)));
        assertEquals(new Color(0.5f, 0, 0.5f, 1), paint.colorAt(new Point(50, 0)));
        assertEquals(BLUE, paint.colorAt(new Point(150, 0)));
    }

    @Test
    void supportsHardStopsAndDefensiveCopies() {
        List<GradientStop> stops = new ArrayList<>();
        stops.add(new GradientStop(0, RED));
        stops.add(new GradientStop(0.5f, GREEN));
        stops.add(new GradientStop(0.5f, BLUE));
        stops.add(new GradientStop(1, RED));

        Paint paint = Paint.linear(new Point(0, 0), new Point(100, 0), stops);
        stops.clear();

        assertEquals(4, ((Paint.LinearGradient) paint).stops().size());
        assertEquals(BLUE, paint.colorAt(new Point(50, 0)));
    }

    @Test
    void rejectsInvalidPaintDefinitions() {
        assertThrows(NullPointerException.class, () -> Paint.solid(null));
        assertThrows(NullPointerException.class,
                () -> Paint.linear(null, new Point(1, 0), new GradientStop(0, RED), new GradientStop(1, BLUE)));
        assertThrows(IllegalArgumentException.class,
                () -> Paint.linear(new Point(1, 1), new Point(1, 1),
                        new GradientStop(0, RED), new GradientStop(1, BLUE)));
        assertThrows(IllegalArgumentException.class,
                () -> Paint.linear(new Point(0, 0), new Point(1, 0), new GradientStop(0, RED)));
        assertThrows(IllegalArgumentException.class,
                () -> Paint.linear(new Point(0, 0), new Point(1, 0),
                        new GradientStop(1, RED), new GradientStop(0, BLUE)));
        assertThrows(NullPointerException.class,
                () -> Paint.linear(new Point(0, 0), new Point(1, 0),
                        new GradientStop(0, RED), null));
        assertThrows(IllegalArgumentException.class,
                () -> new GradientStop(-0.1f, RED));
        assertThrows(IllegalArgumentException.class,
                () -> new GradientStop(1.1f, RED));
        assertThrows(NullPointerException.class, () -> new GradientStop(0, null));
        assertThrows(IllegalArgumentException.class,
                () -> Paint.radial(new Point(0, 0), 0,
                        new GradientStop(0, RED), new GradientStop(1, BLUE)));
        assertThrows(IllegalArgumentException.class,
                () -> Paint.radial(new Point(0, 0), Float.NaN,
                        new GradientStop(0, RED), new GradientStop(1, BLUE)));
        assertThrows(NullPointerException.class, () -> Paint.solid(RED).colorAt(null));
    }
}
