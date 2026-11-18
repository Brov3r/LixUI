package com.lixui.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasingTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void standardPresetsHaveExpectedEndpointsAndMidpoints() {
        assertEquals(0, Easing.LINEAR.apply(0), EPSILON);
        assertEquals(0.5f, Easing.LINEAR.apply(0.5f), EPSILON);
        assertEquals(1, Easing.LINEAR.apply(1), EPSILON);

        assertEquals(0.125f, Easing.EASE_IN.apply(0.5f), EPSILON);
        assertEquals(0.875f, Easing.EASE_OUT.apply(0.5f), EPSILON);
        assertEquals(0.5f, Easing.EASE_IN_OUT.apply(0.5f), EPSILON);
        assertEquals(1 - (float) Math.cos(Math.PI / 4), Easing.SINE_IN.apply(0.5f), EPSILON);
        assertEquals(0, Easing.STEP_START.apply(0), EPSILON);
        assertEquals(1, Easing.STEP_START.apply(0.5f), EPSILON);
        assertEquals(0, Easing.STEP_END.apply(0.5f), EPSILON);
        assertEquals(1, Easing.STEP_END.apply(1), EPSILON);
    }

    @Test
    void presetFamiliesUseTheSameEndpoints() {
        Easing[] presets = {
                Easing.QUAD_IN, Easing.QUAD_OUT, Easing.QUAD_IN_OUT,
                Easing.QUART_IN, Easing.QUART_OUT, Easing.QUART_IN_OUT,
                Easing.QUINT_IN, Easing.QUINT_OUT, Easing.QUINT_IN_OUT,
                Easing.SINE_IN, Easing.SINE_OUT, Easing.SINE_IN_OUT,
                Easing.EXPO_IN, Easing.EXPO_OUT, Easing.EXPO_IN_OUT,
                Easing.CIRC_IN, Easing.CIRC_OUT, Easing.CIRC_IN_OUT,
                Easing.BACK_IN, Easing.BACK_OUT, Easing.BACK_IN_OUT,
                Easing.ELASTIC_IN, Easing.ELASTIC_OUT, Easing.ELASTIC_IN_OUT,
                Easing.BOUNCE_IN, Easing.BOUNCE_OUT, Easing.BOUNCE_IN_OUT
        };
        for (Easing easing : presets) {
            assertEquals(0, easing.apply(0), EPSILON);
            assertEquals(1, easing.apply(1), EPSILON);
        }
    }

    @Test
    void cubicBezierAllowsCustomCurves() {
        Easing custom = Easing.cubicBezier(0.42f, 0, 0.58f, 1);

        assertEquals(0, custom.apply(0), EPSILON);
        assertEquals(1, custom.apply(1), EPSILON);
        assertEquals(0.5f, custom.apply(0.5f), 0.01f);
        assertNotEquals(Easing.LINEAR.apply(0.25f), custom.apply(0.25f), 0.01f);

        Easing overshoot = Easing.cubicBezier(0.34f, 1.56f, 0.64f, 1);
        assertTrue(overshoot.apply(0.5f) > 0.5f);
    }

    @Test
    void rejectsInvalidBezierControlPoints() {
        assertThrows(IllegalArgumentException.class, () -> Easing.cubicBezier(-0.1f, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Easing.cubicBezier(0, 0, 1.1f, 1));
        assertThrows(IllegalArgumentException.class, () -> Easing.cubicBezier(0, Float.NaN, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> Easing.cubicBezier(0, 0, 1, Float.POSITIVE_INFINITY));
    }
}
