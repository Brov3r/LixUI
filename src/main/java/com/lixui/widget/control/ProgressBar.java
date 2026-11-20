package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.GradientStop;
import com.lixui.graphics.Paint;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A value indicator drawn as a horizontal bar or a circular ring.
 * Horizontal bars can be sharp, rounded or pill-shaped and can be filled or outline-only.
 * Circular progress is drawn as an antialiased ring with rounded ends. Value changes are animated
 * with an exponential transition, and percentage text can be shown in the bar or ring center.
 */
public class ProgressBar extends Widget {
    /**
     * Visual geometry of the progress indicator.
     */
    public enum Shape {
        /** A sharp-cornered rectangle. */
        RECTANGLE,
        /** A rectangle with a configurable corner radius. */
        ROUNDED,
        /** A rectangle with fully rounded ends. */
        PILL,
        /** A circular ring. */
        CIRCLE,
        /** A custom polygon defined in normalized coordinates. */
        POLYGON
    }

    /**
     * Whether the indicator is filled or drawn as an outline.
     * Circular outline mode hides the unfilled track ring.
     */
    public enum Appearance {
        /** Filled track and progress. */
        FILLED,
        /** Outline-only track and progress. */
        OUTLINE,
        /** Progress travels clockwise around the full perimeter. */
        PERIMETER,
        /** The shape fills with a clockwise radial sweep starting from the top. */
        RADIAL
    }

    private static final float DEFAULT_ANIMATION_RATE = 12;
    private static final float DEFAULT_THICKNESS = 8;
    private static final float DEFAULT_CORNER_RADIUS = 6;
    private static final float DEFAULT_BAR_WIDTH = 180;
    private static final float DEFAULT_BAR_HEIGHT = 12;
    private static final float DEFAULT_CIRCLE_SIZE = 72;
    private static final float ANIMATION_SNAP = 0.001f;
    private static final float PATH_EPSILON = 0.0001f;

    private float minimum;
    private float maximum = 1;
    private float value;
    private float displayedValue;
    private float animationRate = DEFAULT_ANIMATION_RATE;
    private Shape shape = Shape.ROUNDED;
    private Appearance appearance = Appearance.FILLED;
    private float thickness = DEFAULT_THICKNESS;
    private float cornerRadius = DEFAULT_CORNER_RADIUS;
    private Color trackColor;
    private Color fillColor;
    private Color textColor;
    private List<Point> polygon = List.of();
    private boolean showText;

    /**
     * Creates a progress bar with a zero value.
     */
    public ProgressBar() {
    }

    /**
     * Creates a progress bar with an initial value in the default {@code [0, 1]} range.
     *
     * @param value initial value
     * @throws IllegalArgumentException if the value is not finite
     */
    public ProgressBar(float value) {
        setValue(value);
    }

    /**
     * Returns the minimum value.
     *
     * @return the minimum value
     */
    public float minimum() {
        return minimum;
    }

    /**
     * Returns the maximum value.
     *
     * @return the maximum value
     */
    public float maximum() {
        return maximum;
    }

    /**
     * Replaces the value range and clamps the current target and displayed values.
     *
     * @param minimum inclusive lower bound
     * @param maximum inclusive upper bound
     * @throws IllegalArgumentException if a bound is non-finite or the maximum is not above the minimum
     */
    public void setRange(float minimum, float maximum) {
        if (!Float.isFinite(minimum) || !Float.isFinite(maximum) || maximum <= minimum) {
            throw new IllegalArgumentException("Progress range must be finite with maximum above minimum.");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        value = Math.clamp(value, minimum, maximum);
        displayedValue = Math.clamp(displayedValue, minimum, maximum);
    }

    /**
     * Returns the target value.
     *
     * @return the target value
     */
    public float value() {
        return value;
    }

    /**
     * Sets the target value and lets the indicator animate toward it.
     * Values outside the configured range are clamped.
     *
     * @param value target value
     * @throws IllegalArgumentException if the value is non-finite
     */
    public void setValue(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Progress value must be finite.");
        }
        this.value = Math.clamp(value, minimum, maximum);
        if (context() == null || animationRate == 0) {
            displayedValue = this.value;
        }
    }

    /**
     * Returns the normalized target progress.
     *
     * @return progress in {@code [0, 1]}
     */
    public float progress() {
        return (value - minimum) / (maximum - minimum);
    }

    /**
     * Returns the normalized progress currently drawn by the indicator.
     *
     * @return animated progress in {@code [0, 1]}
     */
    public float animatedProgress() {
        return (displayedValue - minimum) / (maximum - minimum);
    }

    /**
     * Returns the transition speed.
     *
     * @return the exponential animation rate per second, or zero for instant updates
     */
    public float animationRate() {
        return animationRate;
    }

    /**
     * Sets the transition speed.
     * Zero applies value changes instantly.
     *
     * @param animationRate non-negative rate per second
     * @throws IllegalArgumentException if the rate is negative or non-finite
     */
    public void setAnimationRate(float animationRate) {
        if (!Float.isFinite(animationRate) || animationRate < 0) {
            throw new IllegalArgumentException("Animation rate must be finite and non-negative.");
        }
        this.animationRate = animationRate;
        if (animationRate == 0) {
            displayedValue = value;
        }
    }

    /**
     * Returns the indicator geometry.
     *
     * @return the shape
     */
    public Shape shape() {
        return shape;
    }

    /**
     * Sets the indicator geometry.
     *
     * @param shape indicator geometry
     * @throws NullPointerException if {@code shape} is null
     */
    public void setShape(Shape shape) {
        this.shape = Objects.requireNonNull(shape, "shape");
    }

    /**
     * Returns the normalized polygon vertices used by {@link Shape#POLYGON}.
     *
     * @return an immutable snapshot of the polygon vertices
     */
    public List<Point> polygon() {
        return polygon;
    }

    /**
     * Assigns polygon vertices in normalized coordinates relative to the widget bounds.
     * Vertices use the {@code [0, 1]} range and must contain at least three distinct points.
     * The polygon is used by {@link Shape#POLYGON}.
     *
     * @param polygon normalized polygon vertices in path order
     * @throws NullPointerException if {@code polygon} or a vertex is null
     * @throws IllegalArgumentException if fewer than three vertices are supplied or a coordinate
     *                                  is outside {@code [0, 1]}
     */
    public void setPolygon(List<Point> polygon) {
        Objects.requireNonNull(polygon, "polygon");
        if (polygon.size() < 3) {
            throw new IllegalArgumentException("A polygon requires at least three points.");
        }
        for (Point point : polygon) {
            Objects.requireNonNull(point, "point");
            if (point.x() < 0 || point.x() > 1 || point.y() < 0 || point.y() > 1) {
                throw new IllegalArgumentException("Polygon coordinates must be in the [0, 1] range.");
            }
        }
        this.polygon = List.copyOf(polygon);
    }

    /**
     * Creates a regular polygon in normalized center-based coordinates.
     * The first vertex points upward and the remaining vertices follow clockwise.
     *
     * @param sides number of sides, at least three
     * @return normalized polygon vertices
     * @throws IllegalArgumentException if {@code sides} is below three
     */
    public static List<Point> regularPolygon(int sides) {
        if (sides < 3) {
            throw new IllegalArgumentException("A regular polygon requires at least three sides.");
        }
        List<Point> points = new ArrayList<>(sides);
        for (int index = 0; index < sides; index++) {
            double angle = -Math.PI / 2 + Math.PI * 2 * index / sides;
            float x = Math.clamp(0.5f + (float) Math.cos(angle) * 0.5f, 0f, 1f);
            float y = Math.clamp(0.5f + (float) Math.sin(angle) * 0.5f, 0f, 1f);
            points.add(new Point(x, y));
        }
        return List.copyOf(points);
    }

    /**
     * Returns the fill appearance.
     *
     * @return the appearance
     */
    public Appearance appearance() {
        return appearance;
    }

    /**
     * Sets the fill appearance.
     *
     * @param appearance fill appearance
     * @throws NullPointerException if {@code appearance} is null
     */
    public void setAppearance(Appearance appearance) {
        this.appearance = Objects.requireNonNull(appearance, "appearance");
    }

    /**
     * Returns the circle ring or outline stroke thickness.
     *
     * @return the thickness in logical UI units
     */
    public float thickness() {
        return thickness;
    }

    /**
     * Sets the circle ring or outline stroke thickness.
     *
     * @param thickness positive thickness in logical UI units
     * @throws IllegalArgumentException if the thickness is not positive or non-finite
     */
    public void setThickness(float thickness) {
        if (!Float.isFinite(thickness) || thickness <= 0) {
            throw new IllegalArgumentException("Progress thickness must be finite and positive.");
        }
        this.thickness = thickness;
    }

    /**
     * Returns the rounded rectangle corner radius.
     *
     * @return the corner radius in logical UI units
     */
    public float cornerRadius() {
        return cornerRadius;
    }

    /**
     * Sets the rounded rectangle corner radius.
     *
     * @param cornerRadius non-negative radius in logical UI units
     * @throws IllegalArgumentException if the radius is negative or non-finite
     */
    public void setCornerRadius(float cornerRadius) {
        if (!Float.isFinite(cornerRadius) || cornerRadius < 0) {
            throw new IllegalArgumentException("Corner radius must be finite and non-negative.");
        }
        this.cornerRadius = cornerRadius;
    }

    /**
     * Returns the explicit track color.
     *
     * @return the track color, or null when the theme border color is used
     */
    public Color trackColor() {
        return trackColor;
    }

    /**
     * Assigns the track color.
     *
     * @param trackColor track color, or null to use the theme border color
     */
    public void setTrackColor(Color trackColor) {
        this.trackColor = trackColor;
    }

    /**
     * Returns the explicit progress color.
     *
     * @return the progress color, or null when the theme primary color is used
     */
    public Color fillColor() {
        return fillColor;
    }

    /**
     * Assigns the progress color.
     *
     * @param fillColor progress color, or null to use the theme primary color
     */
    public void setFillColor(Color fillColor) {
        this.fillColor = fillColor;
    }

    /**
     * Returns the explicit percentage text color.
     *
     * @return the text color, or null when the theme text color is used
     */
    public Color textColor() {
        return textColor;
    }

    /**
     * Assigns the percentage text color.
     *
     * @param textColor text color, or null to use the theme text color
     */
    public void setTextColor(Color textColor) {
        this.textColor = textColor;
    }

    /**
     * Reports whether percentage text is drawn.
     *
     * @return true when percentage text is shown
     */
    public boolean isShowText() {
        return showText;
    }

    /**
     * Shows or hides percentage text in the bar or ring center.
     *
     * @param showText true to draw percentage text
     */
    public void setShowText(boolean showText) {
        this.showText = showText;
    }

    /**
     * Measures a sensible default size for auto-sized progress bars.
     *
     * @param constraints available and required dimensions
     * @return the assigned size or the default size for the selected shape
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        if (!isAutoSize() && !localBounds().isEmpty()) {
            return new Size(bounds().width(), bounds().height());
        }
        return shape == Shape.CIRCLE || shape == Shape.POLYGON
                ? new Size(DEFAULT_CIRCLE_SIZE, DEFAULT_CIRCLE_SIZE)
                : new Size(DEFAULT_BAR_WIDTH, DEFAULT_BAR_HEIGHT);
    }

    /**
     * Advances the value transition.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        if (animationRate == 0) {
            displayedValue = value;
            return;
        }
        if (frame.deltaSeconds() <= 0) {
            return;
        }
        float factor = 1 - (float) Math.exp(-animationRate * frame.deltaSeconds());
        float next = displayedValue + (value - displayedValue) * factor;
        displayedValue = Math.abs(value - next) < ANIMATION_SNAP ? value : next;
    }

    /**
     * Paints the track, progress and optional percentage text.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (localBounds().isEmpty()) {
            return;
        }
        Theme theme = context().theme();
        float progress = Math.clamp(animatedProgress(), 0f, 1f);
        if (shape == Shape.CIRCLE) {
            drawCircle(renderer, theme, progress);
        } else if (shape == Shape.POLYGON) {
            drawPolygon(renderer, theme, progress);
        } else {
            drawBar(renderer, theme, progress);
        }
        drawText(renderer, theme, progress);
    }

    private void drawBar(RenderManager renderer, Theme theme, float progress) {
        Rect area = localBounds();
        float radius = barRadius(area.height());
        Color track = resolvedTrackColor(theme);
        Color fill = resolvedFillColor(theme);
        if (appearance == Appearance.OUTLINE) {
            drawOutlineBar(renderer, area, radius, progress, track, fill);
            return;
        }
        if (appearance == Appearance.PERIMETER) {
            drawPerimeterBar(renderer, area, progress, track, fill);
            return;
        }
        renderer.fillRoundedRect(area, radius, Paint.solid(track));
        float width = area.width() * progress;
        if (width <= 0) {
            return;
        }
        float progressRadius = Math.min(radius, width / 2);
        renderer.fillRoundedRect(new Rect(area.x(), area.y(), width, area.height()),
                progressRadius, Paint.solid(fill));
    }

    private void drawOutlineBar(RenderManager renderer, Rect area, float radius, float progress,
                                Color track, Color fill) {
        float inset = thickness / 2;
        Rect stroke = inset(area, inset);
        if (stroke.isEmpty()) {
            return;
        }
        float strokeRadius = Math.max(0, radius - inset);
        renderer.strokeRoundedRect(stroke, strokeRadius, thickness, Paint.solid(track));
        float width = area.width() * progress;
        if (width <= thickness * 2) {
            return;
        }
        Rect progressBounds = new Rect(area.x() + inset, area.y() + inset,
                width - thickness, Math.max(0, area.height() - thickness));
        if (!progressBounds.isEmpty()) {
            renderer.strokeRoundedRect(progressBounds, strokeRadius, thickness, Paint.solid(fill));
        }
    }

    private void drawPerimeterBar(RenderManager renderer, Rect area, float progress,
                                  Color track, Color fill) {
        float inset = thickness / 2;
        Rect trackBounds = inset(area, inset);
        if (trackBounds.isEmpty()) {
            return;
        }
        float radius = perimeterRadius(trackBounds.height());
        renderer.strokeRoundedRect(trackBounds, radius, thickness, Paint.solid(track));
        if (progress <= 0) {
            return;
        }
        List<Point> path = roundedRectPath(trackBounds, radius);
        List<Point> partial = partialPath(path, progress, true);
        drawPath(renderer, partial, thickness, fill);
    }

    private void drawPolygon(RenderManager renderer, Theme theme, float progress) {
        if (polygon.size() < 3) {
            return;
        }
        Color track = resolvedTrackColor(theme);
        Color fill = resolvedFillColor(theme);
        if (appearance == Appearance.RADIAL) {
            drawRadialPolygon(renderer, theme, progress);
            return;
        }
        if (appearance == Appearance.FILLED) {
            List<Point> points = polygonPoints(localBounds());
            renderer.fillPolygon(points, Paint.solid(track));
            if (progress <= 0) {
                return;
            }
            if (progress >= 0.999f) {
                renderer.fillPolygon(points, Paint.solid(fill));
                return;
            }
            Rect area = localBounds();
            renderer.pushMaskRect(new Rect(area.x(), area.y(), area.width() * progress,
                    area.height()));
            try {
                renderer.fillPolygon(points, Paint.solid(fill));
            } finally {
                renderer.popMask();
            }
            return;
        }
        float inset = thickness / 2;
        List<Point> path = polygonPoints(inset(localBounds(), inset));
        renderer.strokePolygon(path, thickness, Paint.solid(track));
        if (progress <= 0) {
            return;
        }
        List<Point> partial = partialPath(path, progress, true);
        drawPath(renderer, partial, thickness, fill);
    }

    private void drawRadialPolygon(RenderManager renderer, Theme theme, float progress) {
        List<Point> points = polygonPoints(localBounds());
        renderer.fillPolygon(points, Paint.solid(resolvedTrackColor(theme)));
        if (progress <= 0) {
            return;
        }
        Color fill = resolvedFillColor(theme);
        if (progress >= 0.999f) {
            renderer.fillPolygon(points, Paint.solid(fill));
            return;
        }
        Point center = polygonCenter(points);
        float radius = 0;
        for (Point point : points) {
            radius = Math.max(radius, distance(center, point));
        }
        float startAngle = (float) (-Math.PI / 2);
        float sweep = (float) (Math.PI * 2 * progress);
        renderer.pushMaskPolygon(wedgeMask(center, radius + 2, startAngle, sweep));
        try {
            renderer.fillPolygon(points, Paint.solid(fill));
        } finally {
            renderer.popMask();
        }
        float edgeThickness = Math.max(0.5f, 1f / context().density());
        renderer.pushMaskPolygon(points);
        try {
            drawCutEdge(renderer, center, radius + 2, startAngle, edgeThickness, fill);
            drawCutEdge(renderer, center, radius + 2, startAngle + sweep, edgeThickness, fill);
        } finally {
            renderer.popMask();
        }
    }

    private static Point polygonCenter(List<Point> points) {
        float x = 0;
        float y = 0;
        for (Point point : points) {
            x += point.x();
            y += point.y();
        }
        return new Point(x / points.size(), y / points.size());
    }

    private void drawCircle(RenderManager renderer, Theme theme, float progress) {
        Rect area = localBounds();
        float radius = Math.min(area.width(), area.height()) / 2;
        if (radius <= 0) {
            return;
        }
        if (appearance == Appearance.RADIAL) {
            drawRadialCircle(renderer, theme, area, radius, progress);
            return;
        }
        float ring = Math.min(thickness, radius);
        Point center = new Point(area.x() + area.width() / 2, area.y() + area.height() / 2);
        if (appearance != Appearance.OUTLINE) {
            drawRing(renderer, center, radius, ring, resolvedTrackColor(theme));
        }
        drawArc(renderer, center, radius, ring, progress, resolvedFillColor(theme));
    }

    private void drawRadialCircle(RenderManager renderer, Theme theme, Rect area, float radius,
                                  float progress) {
        Point center = new Point(area.x() + area.width() / 2, area.y() + area.height() / 2);
        renderer.fillCircle(center, radius, Paint.solid(resolvedTrackColor(theme)));
        if (progress <= 0) {
            return;
        }
        Color fill = resolvedFillColor(theme);
        if (progress >= 0.999f) {
            renderer.fillCircle(center, radius, Paint.solid(fill));
            return;
        }
        float startAngle = (float) (-Math.PI / 2);
        float sweep = (float) (Math.PI * 2 * progress);
        renderer.pushMaskPolygon(wedgeMask(center, radius + 2, startAngle, sweep));
        try {
            renderer.fillCircle(center, radius, Paint.solid(fill));
        } finally {
            renderer.popMask();
        }
        float edgeThickness = Math.max(0.5f, 1f / context().density());
        drawCutEdge(renderer, center, radius, startAngle, edgeThickness, fill);
        drawCutEdge(renderer, center, radius, startAngle + sweep, edgeThickness, fill);
    }

    private void drawCutEdge(RenderManager renderer, Point center, float radius, float angle,
                             float thickness, Color color) {
        Point end = capPoint(center, radius, angle);
        drawPath(renderer, List.of(center, end), thickness, color);
    }

    private static List<Point> wedgeMask(Point center, float radius, float startAngle,
                                         float sweep) {
        int segments = Math.max(8, (int) Math.ceil(64 * Math.abs(sweep) / (Math.PI * 2)));
        List<Point> mask = new ArrayList<>(segments + 2);
        mask.add(center);
        for (int index = 0; index <= segments; index++) {
            float angle = startAngle + sweep * index / segments;
            mask.add(new Point(center.x() + (float) Math.cos(angle) * radius,
                    center.y() + (float) Math.sin(angle) * radius));
        }
        return mask;
    }

    private void drawRing(RenderManager renderer, Point center, float radius, float thickness,
                          Color color) {
        if (color.a() <= 0) {
            return;
        }
        float innerRadius = radius - thickness;
        if (innerRadius <= 0) {
            renderer.fillCircle(center, radius, Paint.solid(color));
            return;
        }
        float density = Math.max(0.01f, context().density());
        float feather = 1f / (radius * density);
        float inner = innerRadius / radius;
        float start = Math.max(0, inner - feather / 2);
        float end = Math.min(1, inner + feather / 2);
        List<GradientStop> stops = List.of(
                new GradientStop(0, Color.TRANSPARENT),
                new GradientStop(start, Color.TRANSPARENT),
                new GradientStop(end, color),
                new GradientStop(1, color));
        renderer.fillEllipse(center, radius, radius, Paint.radial(center, radius, stops));
    }

    private void drawArc(RenderManager renderer, Point center, float radius, float thickness,
                         float progress, Color color) {
        if (progress <= 0.001f || color.a() <= 0) {
            return;
        }
        if (progress >= 0.999f) {
            drawRing(renderer, center, radius, thickness, color);
            return;
        }
        float startAngle = (float) (-Math.PI / 2);
        float sweep = (float) (Math.PI * 2 * progress);
        float outerMask = radius + thickness + 2;
        renderer.pushMaskPolygon(wedgeMask(center, outerMask, startAngle, sweep));
        try {
            drawRing(renderer, center, radius, thickness, color);
        } finally {
            renderer.popMask();
        }
        float capRadius = thickness / 2;
        float capCenterRadius = radius - capRadius;
        float endAngle = startAngle + sweep;
        renderer.fillCircle(capPoint(center, capCenterRadius, startAngle), capRadius,
                Paint.solid(color));
        renderer.fillCircle(capPoint(center, capCenterRadius, endAngle), capRadius,
                Paint.solid(color));
    }

    private void drawText(RenderManager renderer, Theme theme, float progress) {
        if (!showText) {
            return;
        }
        Font font = font();
        if (font == null) {
            return;
        }
        String text = Math.round(progress * 100) + "%";
        Rect area = localBounds();
        renderer.text(font, text, area.x() + area.width() / 2,
                area.y() + area.height() / 2, resolvedTextColor(theme),
                HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
    }

    private static Point capPoint(Point center, float radius, float angle) {
        return new Point(center.x() + (float) Math.cos(angle) * radius,
                center.y() + (float) Math.sin(angle) * radius);
    }

    private float perimeterRadius(float height) {
        float inset = thickness / 2;
        return switch (shape) {
            case RECTANGLE -> 0;
            case ROUNDED -> Math.max(0, Math.min(cornerRadius - inset, height / 2));
            case PILL -> height / 2;
            default -> 0;
        };
    }

    private List<Point> polygonPoints(Rect area) {
        List<Point> points = new ArrayList<>(polygon.size());
        for (Point point : polygon) {
            points.add(new Point(area.x() + point.x() * area.width(),
                    area.y() + point.y() * area.height()));
        }
        return points;
    }

    private static List<Point> roundedRectPath(Rect bounds, float radius) {
        float corner = Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2);
        List<Point> points = new ArrayList<>();
        float centerX = bounds.x() + bounds.width() / 2;
        points.add(new Point(centerX, bounds.y()));
        if (corner <= 0.0001f) {
            points.add(new Point(bounds.right(), bounds.y()));
            points.add(new Point(bounds.right(), bounds.bottom()));
            points.add(new Point(bounds.x(), bounds.bottom()));
            points.add(new Point(bounds.x(), bounds.y()));
            return points;
        }
        points.add(new Point(bounds.right() - corner, bounds.y()));
        appendArcPoints(points, bounds.right() - corner, bounds.y() + corner, corner,
                (float) (-Math.PI / 2), 0);
        points.add(new Point(bounds.right(), bounds.bottom() - corner));
        appendArcPoints(points, bounds.right() - corner, bounds.bottom() - corner, corner,
                0, (float) (Math.PI / 2));
        points.add(new Point(bounds.x() + corner, bounds.bottom()));
        appendArcPoints(points, bounds.x() + corner, bounds.bottom() - corner, corner,
                (float) (Math.PI / 2), (float) Math.PI);
        points.add(new Point(bounds.x(), bounds.y() + corner));
        appendArcPoints(points, bounds.x() + corner, bounds.y() + corner, corner,
                (float) Math.PI, (float) (Math.PI * 1.5));
        return points;
    }

    private static void appendArcPoints(List<Point> points, float centerX, float centerY,
                                        float radius, float startAngle, float endAngle) {
        int segments = Math.max(4, Math.min(24, (int) Math.ceil(radius)));
        for (int index = 1; index <= segments; index++) {
            float angle = startAngle + (endAngle - startAngle) * index / segments;
            points.add(new Point(centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius));
        }
    }

    private static List<Point> partialPath(List<Point> path, float fraction, boolean closed) {
        if (path.size() < 2 || fraction <= 0) {
            return List.of();
        }
        if (fraction >= 1) {
            if (!closed) {
                return List.copyOf(path);
            }
            List<Point> result = new ArrayList<>(path.size() + 1);
            result.addAll(path);
            result.add(path.getFirst());
            return result;
        }
        int segments = closed ? path.size() : path.size() - 1;
        float total = 0;
        for (int index = 0; index < segments; index++) {
            total += distance(path.get(index), path.get((index + 1) % path.size()));
        }
        float remaining = total * fraction;
        if (remaining < PATH_EPSILON) {
            return List.of();
        }
        List<Point> result = new ArrayList<>();
        result.add(path.getFirst());
        for (int index = 0; index < segments; index++) {
            Point start = path.get(index);
            Point end = path.get((index + 1) % path.size());
            float length = distance(start, end);
            if (remaining <= length) {
                if (length > PATH_EPSILON) {
                    float amount = remaining / length;
                    result.add(new Point(start.x() + (end.x() - start.x()) * amount,
                            start.y() + (end.y() - start.y()) * amount));
                }
                break;
            }
            remaining -= length;
            if (index + 1 < path.size()) {
                result.add(end);
            }
        }
        return result.size() >= 2 ? List.copyOf(result) : List.of();
    }

    /**
     * Strokes the path as one band. Drawing overlapping capsules per segment would double-blend
     * translucent fills at every join.
     */
    private void drawPath(RenderManager renderer, List<Point> path, float thickness, Color color) {
        if (path.size() < 2 || color.a() <= 0) {
            return;
        }
        renderer.strokePolyline(path, thickness, Paint.solid(color));
    }

    private static float distance(Point first, Point second) {
        return (float) Math.hypot(second.x() - first.x(), second.y() - first.y());
    }

    private float barRadius(float height) {
        return switch (shape) {
            case RECTANGLE -> 0;
            case ROUNDED -> Math.min(cornerRadius, height / 2);
            case PILL -> height / 2;
            default -> 0;
        };
    }

    private Color resolvedTrackColor(Theme theme) {
        return trackColor != null ? trackColor : theme.border().withAlpha(0.65f);
    }

    private Color resolvedFillColor(Theme theme) {
        return fillColor != null ? fillColor : theme.primary();
    }

    private Color resolvedTextColor(Theme theme) {
        return textColor != null ? textColor : theme.text();
    }

    private static Rect inset(Rect bounds, float amount) {
        return new Rect(bounds.x() + amount, bounds.y() + amount,
                Math.max(0, bounds.width() - amount * 2),
                Math.max(0, bounds.height() - amount * 2));
    }
}
