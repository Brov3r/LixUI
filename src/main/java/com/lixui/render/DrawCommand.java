package com.lixui.render;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;

import java.util.Objects;

/**
 * A single rounded rectangle emitted by the UI and consumed by a render backend.
 * Bounds and clipping are in logical UI units, and the radius is clamped to half of the smaller dimension.
 *
 * @param bounds the rectangle to fill
 * @param radius corner radius in logical UI units
 * @param color straight-alpha fill color
 * @param clip clipping rectangle that applies to this command
 */
public record DrawCommand(Rect bounds, float radius, Color color, Rect clip) {
    /**
     * Creates a draw command, clamping the radius to the bounds.
     *
     * @param bounds the rectangle to fill
     * @param radius corner radius in logical UI units
     * @param color straight-alpha fill color
     * @param clip clipping rectangle that applies to this command
     * @throws NullPointerException if any reference argument is null
     * @throws IllegalArgumentException if radius is non-finite or negative
     */
    public DrawCommand {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(clip, "clip");
        if (!Float.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("Corner radius must be finite and non-negative.");
        }
        radius = Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2);
    }
}
