package com.lixui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ViewportTest {
    @Test
    void computesUniformScale() {
        Viewport viewport = new Viewport(2560, 1440, 1280, 720);

        assertEquals(2.0f, viewport.scaleX(), 0.0001f);
        assertEquals(2.0f, viewport.scaleY(), 0.0001f);
    }

    @Test
    void computesIndependentScales() {
        Viewport viewport = new Viewport(1920, 1080, 1200, 800);

        assertEquals(1.6f, viewport.scaleX(), 0.0001f);
        assertEquals(1.35f, viewport.scaleY(), 0.0001f);
    }

    @Test
    void rejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new Viewport(0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Viewport(1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Viewport(1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Viewport(1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Viewport(-1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Viewport(1, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Viewport(1, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Viewport(1, 1, 1, -1));
    }
}
