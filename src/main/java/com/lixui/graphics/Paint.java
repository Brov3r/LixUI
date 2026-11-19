package com.lixui.graphics;

import com.lixui.geometry.Point;

import java.util.List;
import java.util.Objects;

/**
 * A fill or stroke paint: a solid color, a linear gradient, or a radial gradient.
 * Gradients use two or more ordered stops and can be evaluated on the CPU for geometry tessellation.
 */
public sealed interface Paint permits Paint.Solid, Paint.LinearGradient, Paint.RadialGradient {
    /**
     * Evaluates the paint at a point in the same coordinate space as the shape.
     *
     * @param point point to evaluate
     * @return the color at the point
     * @throws NullPointerException if {@code point} is null
     */
    Color colorAt(Point point);

    /**
     * Creates a solid paint.
     *
     * @param color fill color
     * @return the solid paint
     * @throws NullPointerException if {@code color} is null
     */
    static Paint solid(Color color) {
        return new Solid(color);
    }

    /**
     * Creates a linear gradient between two points.
     *
     * @param start gradient start
     * @param end gradient end
     * @param stops ordered gradient stops
     * @return the linear gradient
     * @throws NullPointerException if an argument or a stop is null
     * @throws IllegalArgumentException if the points are equal or the stops are invalid
     */
    static Paint linear(Point start, Point end, List<GradientStop> stops) {
        return new LinearGradient(start, end, stops);
    }

    /**
     * Creates a linear gradient between two points.
     *
     * @param start gradient start
     * @param end gradient end
     * @param stops ordered gradient stops
     * @return the linear gradient
     * @throws NullPointerException if an argument or a stop is null
     * @throws IllegalArgumentException if the points are equal or the stops are invalid
     */
    static Paint linear(Point start, Point end, GradientStop... stops) {
        return new LinearGradient(start, end, List.of(stops));
    }

    /**
     * Creates a radial gradient.
     *
     * @param center gradient center
     * @param radius gradient radius
     * @param stops ordered gradient stops
     * @return the radial gradient
     * @throws NullPointerException if an argument or a stop is null
     * @throws IllegalArgumentException if the radius is not positive or the stops are invalid
     */
    static Paint radial(Point center, float radius, List<GradientStop> stops) {
        return new RadialGradient(center, radius, stops);
    }

    /**
     * Creates a radial gradient.
     *
     * @param center gradient center
     * @param radius gradient radius
     * @param stops ordered gradient stops
     * @return the radial gradient
     * @throws NullPointerException if an argument or a stop is null
     * @throws IllegalArgumentException if the radius is not positive or the stops are invalid
     */
    static Paint radial(Point center, float radius, GradientStop... stops) {
        return new RadialGradient(center, radius, List.of(stops));
    }

    private static List<GradientStop> normalizeStops(List<GradientStop> stops) {
        Objects.requireNonNull(stops, "stops");
        if (stops.size() < 2) {
            throw new IllegalArgumentException("A gradient requires at least two stops.");
        }
        List<GradientStop> copy = List.copyOf(stops);
        float previous = 0;
        for (GradientStop stop : copy) {
            if (stop.offset() < previous) {
                throw new IllegalArgumentException("Gradient stops must be ordered by offset.");
            }
            previous = stop.offset();
        }
        return copy;
    }

    private static Color interpolate(List<GradientStop> stops, float position) {
        GradientStop first = stops.get(0);
        if (position <= first.offset()) {
            return first.color();
        }
        GradientStop last = stops.get(stops.size() - 1);
        if (position >= last.offset()) {
            return last.color();
        }
        for (int index = 1; index < stops.size(); index++) {
            GradientStop right = stops.get(index);
            if (position < right.offset()) {
                GradientStop left = stops.get(index - 1);
                float span = right.offset() - left.offset();
                if (span <= 0) {
                    return right.color();
                }
                return Color.mix(left.color(), right.color(), (position - left.offset()) / span);
            }
        }
        return last.color();
    }

    /**
     * A uniform solid color.
     *
     * @param color fill color
     */
    record Solid(Color color) implements Paint {
        /**
         * Creates a solid paint.
         *
         * @param color fill color
         * @throws NullPointerException if {@code color} is null
         */
        public Solid {
            Objects.requireNonNull(color, "color");
        }

        /**
         * Returns the solid color.
         *
         * @param point ignored point
         * @return the solid color
         * @throws NullPointerException if {@code point} is null
         */
        @Override
        public Color colorAt(Point point) {
            Objects.requireNonNull(point, "point");
            return color;
        }
    }

    /**
     * A linear gradient from {@code start} to {@code end}.
     *
     * @param start gradient start
     * @param end gradient end
     * @param stops ordered gradient stops
     */
    record LinearGradient(Point start, Point end, List<GradientStop> stops) implements Paint {
        /**
         * Creates a linear gradient.
         *
         * @param start gradient start
         * @param end gradient end
         * @param stops ordered gradient stops
         * @throws NullPointerException if an argument or a stop is null
         * @throws IllegalArgumentException if the points are equal or the stops are invalid
         */
        public LinearGradient {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (start.equals(end)) {
                throw new IllegalArgumentException("Linear gradient endpoints must differ.");
            }
            stops = normalizeStops(stops);
        }

        /**
         * Evaluates the gradient along the start-to-end axis.
         *
         * @param point point to evaluate
         * @return the interpolated color
         * @throws NullPointerException if {@code point} is null
         */
        @Override
        public Color colorAt(Point point) {
            Objects.requireNonNull(point, "point");
            float dx = end.x() - start.x();
            float dy = end.y() - start.y();
            float position = ((point.x() - start.x()) * dx + (point.y() - start.y()) * dy)
                    / (dx * dx + dy * dy);
            return interpolate(stops, position);
        }
    }

    /**
     * A radial gradient centered at {@code center}.
     *
     * @param center gradient center
     * @param radius gradient radius
     * @param stops ordered gradient stops
     */
    record RadialGradient(Point center, float radius, List<GradientStop> stops) implements Paint {
        /**
         * Creates a radial gradient.
         *
         * @param center gradient center
         * @param radius gradient radius
         * @param stops ordered gradient stops
         * @throws NullPointerException if an argument or a stop is null
         * @throws IllegalArgumentException if the radius is not positive or the stops are invalid
         */
        public RadialGradient {
            Objects.requireNonNull(center, "center");
            if (!Float.isFinite(radius) || radius <= 0) {
                throw new IllegalArgumentException("Gradient radius must be finite and positive.");
            }
            stops = normalizeStops(stops);
        }

        /**
         * Evaluates the gradient by distance from the center.
         *
         * @param point point to evaluate
         * @return the interpolated color
         * @throws NullPointerException if {@code point} is null
         */
        @Override
        public Color colorAt(Point point) {
            Objects.requireNonNull(point, "point");
            float dx = point.x() - center.x();
            float dy = point.y() - center.y();
            return interpolate(stops, (float) Math.hypot(dx, dy) / radius);
        }
    }
}
