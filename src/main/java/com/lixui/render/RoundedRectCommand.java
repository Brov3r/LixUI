package com.lixui.render;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;

import java.util.Objects;

/**
 * A solid rounded rectangle primitive.
 *
 * @param bounds the rectangle to fill
 * @param radius corner radius in logical UI units
 * @param color straight-alpha fill color
 * @param clip clipping rectangle that applies to this command
 */
public record RoundedRectCommand(Rect bounds, float radius, Color color, Rect clip) implements DrawCommand {
    /**
     * Creates a rounded rectangle command, clamping the radius to the bounds.
     *
     * @param bounds the rectangle to fill
     * @param radius corner radius in logical UI units
     * @param color straight-alpha fill color
     * @param clip clipping rectangle that applies to this command
     * @throws NullPointerException if any reference argument is null
     * @throws IllegalArgumentException if radius is non-finite or negative
     */
    public RoundedRectCommand {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(clip, "clip");
        if (!Float.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("Corner radius must be finite and non-negative.");
        }
        radius = Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2);
    }
}
