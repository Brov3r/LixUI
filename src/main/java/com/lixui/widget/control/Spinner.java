package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.render.RenderManager;
import com.lixui.theme.Theme;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;

/**
 * An indeterminate loading indicator drawn as a rotating arc on a circle.
 * The arc advances with the frame clock, so the spinner keeps moving without external animation
 * management. The radius follows the smaller assigned dimension, which allows the natural diameter
 * to be overridden by a layout container. The track and the arc colors can be themed or assigned
 * explicitly.
 */
public class Spinner extends Widget {
    private static final float DEFAULT_DIAMETER = 32;
    private static final float DEFAULT_THICKNESS = 3;
    private static final float DEFAULT_SPEED = 1.2f;
    private static final float DEFAULT_ARC_DEGREES = 270;
    private static final float FULL_TURN_DEGREES = 360;
    private static final float TAU = (float) (Math.PI * 2);
    private static final float DEFAULT_TRACK_ALPHA = 0.35f;

    private float diameter = DEFAULT_DIAMETER;
    private float thickness = DEFAULT_THICKNESS;
    private float speed = DEFAULT_SPEED;
    private float arcDegrees = DEFAULT_ARC_DEGREES;
    private Color color;
    private Color trackColor;
    private boolean trackVisible = true;
    private float rotation;

    /**
     * Creates a spinner with the default diameter, thickness and speed.
     */
    public Spinner() {
    }

    /**
     * Returns the natural diameter.
     *
     * @return the diameter in logical UI units
     */
    public final float diameter() {
        return diameter;
    }

    /**
     * Sets the natural diameter used when a parent measures the spinner.
     *
     * @param diameter positive diameter in logical UI units
     * @throws IllegalArgumentException if the diameter is not positive or non-finite
     */
    public final void setDiameter(float diameter) {
        if (!Float.isFinite(diameter) || diameter <= 0) {
            throw new IllegalArgumentException("Spinner diameter must be finite and positive.");
        }
        this.diameter = diameter;
    }

    /**
     * Returns the arc and track thickness.
     *
     * @return the thickness in logical UI units
     */
    public final float thickness() {
        return thickness;
    }

    /**
     * Sets the arc and track thickness.
     *
     * @param thickness positive thickness in logical UI units
     * @throws IllegalArgumentException if the thickness is not positive or non-finite
     */
    public final void setThickness(float thickness) {
        if (!Float.isFinite(thickness) || thickness <= 0) {
            throw new IllegalArgumentException("Spinner thickness must be finite and positive.");
        }
        this.thickness = thickness;
    }

    /**
     * Returns the rotation speed in revolutions per second.
     *
     * @return the rotation speed
     */
    public final float speed() {
        return speed;
    }

    /**
     * Sets the rotation speed in revolutions per second.
     * Zero keeps the arc static.
     *
     * @param speed non-negative speed in revolutions per second
     * @throws IllegalArgumentException if the speed is negative or non-finite
     */
    public final void setSpeed(float speed) {
        if (!Float.isFinite(speed) || speed < 0) {
            throw new IllegalArgumentException("Spinner speed must be finite and non-negative.");
        }
        this.speed = speed;
    }

    /**
     * Returns the visible arc length.
     *
     * @return the arc length in degrees
     */
    public final float arcDegrees() {
        return arcDegrees;
    }

    /**
     * Sets the visible arc length.
     *
     * @param arcDegrees arc length in degrees from above zero up to a full turn
     * @throws IllegalArgumentException if the length is not in the range {@code (0, 360]}
     */
    public final void setArcDegrees(float arcDegrees) {
        if (!Float.isFinite(arcDegrees) || arcDegrees <= 0 || arcDegrees > FULL_TURN_DEGREES) {
            throw new IllegalArgumentException("Arc length must be in the range (0, 360].");
        }
        this.arcDegrees = arcDegrees;
    }

    /**
     * Returns the explicit arc color.
     *
     * @return the arc color, or null when the theme primary color is used
     */
    public final Color color() {
        return color;
    }

    /**
     * Assigns the arc color.
     *
     * @param color arc color, or null to use the theme primary color
     */
    public final void setColor(Color color) {
        this.color = color;
    }

    /**
     * Returns the explicit track color.
     *
     * @return the track color, or null when the muted theme border color is used
     */
    public final Color trackColor() {
        return trackColor;
    }

    /**
     * Assigns the track color.
     *
     * @param trackColor track color, or null to use the muted theme border color
     */
    public final void setTrackColor(Color trackColor) {
        this.trackColor = trackColor;
    }

    /**
     * Reports whether the background track is drawn.
     *
     * @return true when the track is visible
     */
    public final boolean isTrackVisible() {
        return trackVisible;
    }

    /**
     * Shows or hides the background track.
     *
     * @param trackVisible true to draw the full circle behind the arc
     */
    public final void setTrackVisible(boolean trackVisible) {
        this.trackVisible = trackVisible;
    }

    /**
     * Returns the current arc rotation.
     *
     * @return the rotation in radians in the range {@code [0, 2π)}
     */
    public final float rotation() {
        return rotation;
    }

    /**
     * Measures the natural diameter as a square.
     *
     * @param constraints available and required dimensions
     * @return the natural square size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return new Size(diameter, diameter);
    }

    /**
     * Advances the arc rotation by the frame duration.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        if (speed <= 0 || frame.deltaSeconds() <= 0) {
            return;
        }
        rotation = (rotation + frame.deltaSeconds() * speed * TAU) % TAU;
    }

    /**
     * Paints the optional track and the rotating arc.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (localBounds().isEmpty()) {
            return;
        }
        float radius = Math.min(localBounds().width(), localBounds().height()) / 2 - thickness / 2;
        if (radius <= 0) {
            return;
        }
        Point center = new Point(localBounds().width() / 2, localBounds().height() / 2);
        Theme theme = context().theme();
        if (trackVisible) {
            Color track = trackColor != null ? trackColor
                    : theme.border().withAlpha(DEFAULT_TRACK_ALPHA);
            if (track.a() > 0) {
                renderer.strokeCircle(center, radius, thickness, Paint.solid(track));
            }
        }
        Color arc = color != null ? color : theme.primary();
        renderer.strokeArc(center, radius, radius, rotation,
                (float) Math.toRadians(arcDegrees), thickness, Paint.solid(arc));
    }
}
