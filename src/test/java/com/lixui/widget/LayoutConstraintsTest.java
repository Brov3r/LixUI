package com.lixui.widget;

import com.lixui.geometry.Size;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LayoutConstraintsTest {
    @Test
    void looseConstraintsAllowAnySizeUpToTheMaximum() {
        LayoutConstraints constraints = LayoutConstraints.loose(320, 200);

        assertSize(constraints.constrain(new Size(100, 50)), 100, 50);
        assertSize(constraints.constrain(new Size(400, 300)), 320, 200);
    }

    @Test
    void tightConstraintsForceTheExactSize() {
        LayoutConstraints constraints = LayoutConstraints.tight(120, 80);

        assertSize(constraints.constrain(new Size(10, 20)), 120, 80);
        assertSize(constraints.constrain(new Size(300, 400)), 120, 80);
    }

    @Test
    void minimumsAreAppliedToSmallerSizes() {
        LayoutConstraints constraints = new LayoutConstraints(40, 100, 30, 90);

        assertSize(constraints.constrain(10, 5), 40, 30);
    }

    @Test
    void rejectsInvalidBoundsAndMeasuredSizes() {
        assertThrows(IllegalArgumentException.class, () -> LayoutConstraints.loose(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> LayoutConstraints.tight(Float.NaN, 10));
        assertThrows(IllegalArgumentException.class, () -> new LayoutConstraints(10, 5, 0, 10));
        assertThrows(NullPointerException.class, () -> LayoutConstraints.loose(10, 10).constrain((Size) null));
        assertThrows(IllegalArgumentException.class,
                () -> LayoutConstraints.loose(10, 10).constrain(-1, 5));
    }

    @ParameterizedTest
    @ValueSource(floats = {-Float.MIN_VALUE, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY})
    void rejectsNonFiniteOrNegativeDimensions(float value) {
        assertThrows(IllegalArgumentException.class, () -> LayoutConstraints.loose(value, 10));
        assertThrows(IllegalArgumentException.class, () -> LayoutConstraints.loose(10, value));
        assertThrows(IllegalArgumentException.class, () -> LayoutConstraints.tight(value, 10));
        assertThrows(IllegalArgumentException.class, () -> LayoutConstraints.tight(10, value));
    }

    private static void assertSize(Size size, float width, float height) {
        assertEquals(width, size.width());
        assertEquals(height, size.height());
    }
}
