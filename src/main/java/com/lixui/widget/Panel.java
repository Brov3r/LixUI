package com.lixui.widget;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Paint;
import com.lixui.render.RenderManager;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A plain container that paints a state-aware rounded background and border.
 * The panel uses the context panel style unless a local or inherited style is assigned.
 * By default every corner uses the style radius; explicit diagonal or mixed radii can be set with
 * {@link #setCorners(Corners)}. Children are positioned freely and drawn after the panel background.
 */
public class Panel extends Widget {
    private Corners corners;

    /**
     * Creates an empty panel.
     */
    public Panel() {
    }

    /**
     * Returns the explicit corner radii.
     *
     * @return the corner radii, or null when all corners use the style radius
     */
    public final Corners corners() {
        return corners;
    }

    /**
     * Assigns individual corner radii and overrides the style radius.
     *
     * @param corners corner radii in logical UI units
     * @throws NullPointerException if {@code corners} is null
     */
    public final void setCorners(Corners corners) {
        this.corners = Objects.requireNonNull(corners, "corners");
    }

    /**
     * Removes the explicit corner radii and restores the uniform style radius.
     */
    public final void clearCorners() {
        corners = null;
    }

    /**
     * Returns the panel style from the current UI context.
     *
     * @return the context panel style, or null when the panel is not attached
     */
    @Override
    protected WidgetStyle defaultStyle() {
        return context() == null ? null : context().theme().panelStyle();
    }

    /**
     * Paints the panel background and border in the current interaction state.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        WidgetStyle style = style();
        if (style == null || localBounds().isEmpty()) {
            return;
        }
        WidgetState state = state();
        WidgetState borderState = borderState(state);
        if (corners == null) {
            renderer.roundedRect(localBounds(), style.cornerRadius(), style.background(state));
            if (style.borderWidth() > 0) {
                renderer.strokeRoundedRect(localBounds(), style.cornerRadius(), style.borderWidth(),
                        Paint.solid(style.border(borderState)));
            }
            return;
        }
        List<Point> outline = outline(corners);
        renderer.fillPolygon(outline, Paint.solid(style.background(state)));
        if (style.borderWidth() > 0) {
            renderer.strokePolygon(outline, style.borderWidth(), Paint.solid(style.border(borderState)));
        }
    }

    private WidgetState borderState(WidgetState state) {
        return state == WidgetState.FOCUSED && !isFocusOutlineEnabled()
                ? WidgetState.NORMAL : state;
    }

    private List<Point> outline(Corners radii) {
        Rect bounds = localBounds();
        float limit = Math.min(bounds.width(), bounds.height()) / 2;
        float topLeft = Math.min(radii.topLeft(), limit);
        float topRight = Math.min(radii.topRight(), limit);
        float bottomRight = Math.min(radii.bottomRight(), limit);
        float bottomLeft = Math.min(radii.bottomLeft(), limit);
        float left = bounds.x();
        float top = bounds.y();
        float right = bounds.right();
        float bottom = bounds.bottom();
        List<Point> points = new ArrayList<>();
        points.add(new Point(left + topLeft, top));
        points.add(new Point(right - topRight, top));
        if (topRight > 0) {
            appendArc(points, right - topRight, top + topRight, topRight,
                    (float) (-Math.PI / 2));
        } else {
            points.add(new Point(right, top));
        }
        if (bottomRight > 0) {
            appendArc(points, right - bottomRight, bottom - bottomRight, bottomRight, 0);
        } else {
            points.add(new Point(right, bottom));
        }
        if (bottomLeft > 0) {
            appendArc(points, left + bottomLeft, bottom - bottomLeft, bottomLeft,
                    (float) (Math.PI / 2));
        } else {
            points.add(new Point(left, bottom));
        }
        if (topLeft > 0) {
            appendArc(points, left + topLeft, top + topLeft, topLeft, (float) Math.PI);
        } else {
            points.add(new Point(left, top));
        }
        return points;
    }

    private static void appendArc(List<Point> points, float centerX, float centerY, float radius,
                                  float startAngle) {
        int segments = Math.max(2, Math.min(16, (int) Math.ceil(radius / 4)));
        for (int index = 0; index <= segments; index++) {
            float angle = startAngle + (float) (Math.PI / 2) * index / segments;
            points.add(new Point(centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius));
        }
    }

    /**
     * Resolves the interaction state used to pick surface colors.
     *
     * @return the most specific active state
     */
    protected WidgetState state() {
        if (!isEnabled()) {
            return WidgetState.DISABLED;
        }
        if (isPressed()) {
            return WidgetState.PRESSED;
        }
        if (isHovered()) {
            return WidgetState.HOVERED;
        }
        if (isFocused()) {
            return WidgetState.FOCUSED;
        }
        return WidgetState.NORMAL;
    }

    /**
     * Individual corner radii in logical UI units.
     * Zero keeps a square corner; adjacent radii are clamped to half of the panel size when drawn.
     *
     * @param topLeft top-left corner radius
     * @param topRight top-right corner radius
     * @param bottomRight bottom-right corner radius
     * @param bottomLeft bottom-left corner radius
     */
    public record Corners(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        /**
         * Creates corner radii.
         *
         * @param topLeft top-left corner radius
         * @param topRight top-right corner radius
         * @param bottomRight bottom-right corner radius
         * @param bottomLeft bottom-left corner radius
         * @throws IllegalArgumentException if a radius is negative or non-finite
         */
        public Corners {
            validate(topLeft, "Top-left");
            validate(topRight, "Top-right");
            validate(bottomRight, "Bottom-right");
            validate(bottomLeft, "Bottom-left");
        }

        private static void validate(float radius, String name) {
            if (!Float.isFinite(radius) || radius < 0) {
                throw new IllegalArgumentException(name + " corner radius must be finite and non-negative.");
            }
        }
    }
}
