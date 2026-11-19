package com.lixui.widget.testsupport;

import com.lixui.render.Viewport;

/**
 * Reusable viewport sizes for widget and render tests.
 */
public final class TestViewports {
    /** A high-density logical viewport. */
    public static final Viewport HIDPI = new Viewport(1600, 1200, 800, 600);

    /** A one-to-one logical viewport. */
    public static final Viewport LOGICAL = new Viewport(800, 600, 800, 600);

    /** A compact framebuffer used by GPU tests. */
    public static final Viewport FRAMEBUFFER = new Viewport(128, 128, 128, 128);

    private TestViewports() {
    }
}
