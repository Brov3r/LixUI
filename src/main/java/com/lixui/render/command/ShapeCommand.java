package com.lixui.render.command;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;

import java.util.Objects;

/**
 * An analytic filled shape evaluated per fragment by a supporting backend.
 * Paint coordinates and corner radii are local, before the transform is applied.
 *
 * @param bounds local shape bounds
 * @param radius rounded rectangle corner radius, ignored for ellipses
 * @param ellipse true for an ellipse, false for a rounded rectangle
 * @param paint solid color or a gradient with at most four stops
 * @param clip viewport-space clipping rectangle
 * @param transform local-to-viewport transform
 */
public record ShapeCommand(Rect bounds, float radius, boolean ellipse, Paint paint,
                           Rect clip, Transform transform) implements DrawCommand {
    /**
     * Validates the shape and clamps the corner radius to its bounds.
     *
     * @param bounds local shape bounds
     * @param radius non-negative corner radius
     * @param ellipse whether the shape is an ellipse
     * @param paint supported fill paint
     * @param clip viewport-space clip
     * @param transform local-to-viewport transform
     * @throws NullPointerException if a reference argument is null
     * @throws IllegalArgumentException if the radius is invalid or the paint has too many stops
     */
    public ShapeCommand {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(paint, "paint");
        Objects.requireNonNull(clip, "clip");
        Objects.requireNonNull(transform, "transform");
        if (!Float.isFinite(radius) || radius < 0 || !supportsPaint(paint)) {
            throw new IllegalArgumentException("Invalid radius or unsupported analytic paint.");
        }
        radius = ellipse ? 0 : Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2);
    }

    /**
     * Returns the solid fill color, or white for a gradient evaluated by the backend.
     *
     * @return straight-alpha solid color or white
     */
    @Override
    public Color color() {
        return paint instanceof Paint.Solid solid ? solid.color() : Color.WHITE;
    }

    /**
     * Reports whether a paint fits the portable analytic shader parameters.
     *
     * @param paint paint to inspect
     * @return true for solids and gradients containing at most four stops
     * @throws NullPointerException if the paint is null
     */
    public static boolean supportsPaint(Paint paint) {
        Objects.requireNonNull(paint, "paint");
        return switch (paint) {
            case Paint.Solid ignored -> true;
            case Paint.LinearGradient gradient -> gradient.stops().size() <= 4;
            case Paint.RadialGradient gradient -> gradient.stops().size() <= 4;
        };
    }
}
