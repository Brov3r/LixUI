package com.lixui.render;

/**
 * Tessellation quality and a sample-count hint for antialiasing.
 * The sample count is a render-target property; use {@link #samples()} when creating the window or
 * Vulkan render pass. The tessellation scale controls curve and gradient mesh density.
 */
public enum Antialiasing {
    /** Minimum tessellation and a single sample. */
    OFF(1, 0.75f),
    /** Two-sample hint and baseline tessellation. */
    LOW(2, 1.0f),
    /** Four-sample hint and denser tessellation. */
    MEDIUM(4, 1.25f),
    /** Eight-sample hint and the densest tessellation. */
    HIGH(8, 1.5f);

    private final int samples;
    private final float tessellationScale;

    Antialiasing(int samples, float tessellationScale) {
        this.samples = samples;
        this.tessellationScale = tessellationScale;
    }

    /**
     * Returns the suggested MSAA sample count for a render target.
     *
     * @return the positive sample count hint
     */
    public int samples() {
        return samples;
    }

    /**
     * Returns the multiplier applied to curve, ring and gradient segment counts.
     *
     * @return the positive tessellation scale
     */
    public float tessellationScale() {
        return tessellationScale;
    }
}
