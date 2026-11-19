package com.lixui.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InsetsTest {
    @Test
    void exposesUniformAndZeroInsets() {
        assertEquals(new Insets(4, 4, 4, 4), Insets.uniform(4));
        assertEquals(0, Insets.NONE.left());
        assertEquals(0, Insets.NONE.top());
        assertEquals(0, Insets.NONE.right());
        assertEquals(0, Insets.NONE.bottom());
    }

    @Test
    void rejectsNegativeAndNonFiniteSides() {
        assertThrows(IllegalArgumentException.class, () -> new Insets(-1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Insets(0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Insets(0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Insets(0, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Insets(Float.NaN, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Insets.uniform(Float.POSITIVE_INFINITY));
    }

    @Test
    void normalizesNegativeZero() {
        Insets insets = new Insets(-0f, -0f, -0f, -0f);

        assertEquals(Float.floatToIntBits(0f), Float.floatToIntBits(insets.left()));
        assertEquals(Float.floatToIntBits(0f), Float.floatToIntBits(insets.top()));
        assertEquals(Float.floatToIntBits(0f), Float.floatToIntBits(insets.right()));
        assertEquals(Float.floatToIntBits(0f), Float.floatToIntBits(insets.bottom()));
    }
}
