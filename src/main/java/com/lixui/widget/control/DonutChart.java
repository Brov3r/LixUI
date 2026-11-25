package com.lixui.widget.control;

import com.lixui.animation.Animation;
import com.lixui.animation.Animator;
import com.lixui.animation.Easing;
import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;
import com.lixui.widget.tooltip.TextTooltip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * A donut chart with value-proportional segments, a legend and a center label.
 * Segments form a ring around an inner radius and start at the top in clockwise order. The chart
 * sweeps in when it attaches, hover highlights the segment under the pointer and updates a tooltip,
 * and a completed click reports the segment index. Values, labels and colors can be changed at any
 * time; call {@link #replayAnimation()} to play the appearance animation again.
 */
public class DonutChart extends Widget {
    /**
     * One value slice of the chart.
     */
    public static final class Segment {
        private String label;
        private float value;
        private Color color;

        private Segment(String label, float value, Color color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }

        /**
         * Returns the segment label.
         *
         * @return the label, never null
         */
        public String label() {
            return label;
        }

        /**
         * Replaces the segment label.
         *
         * @param label new label
         * @throws NullPointerException if {@code label} is null
         */
        public void setLabel(String label) {
            this.label = Objects.requireNonNull(label, "label");
        }

        /**
         * Returns the segment value.
         *
         * @return the non-negative value
         */
        public float value() {
            return value;
        }

        /**
         * Replaces the segment value.
         *
         * @param value non-negative value
         * @throws IllegalArgumentException if the value is negative or non-finite
         */
        public void setValue(float value) {
            validateValue(value);
            this.value = value;
        }

        /**
         * Returns the segment fill color.
         *
         * @return the color, never null
         */
        public Color color() {
            return color;
        }

        /**
         * Replaces the segment fill color.
         *
         * @param color new color
         * @throws NullPointerException if {@code color} is null
         */
        public void setColor(Color color) {
            this.color = Objects.requireNonNull(color, "color");
        }
    }

    private static final float DEFAULT_DIAMETER = 180;
    private static final float DEFAULT_INNER_RADIUS_RATIO = 0.62f;
    private static final float DEFAULT_ANIMATION_SECONDS = 0.6f;
    private static final float PADDING = 4;
    private static final float LEGEND_GAP = 16;
    private static final float LEGEND_SWATCH = 8;
    private static final float LEGEND_TEXT_GAP = 8;
    private static final float LEGEND_ROW_GAP = 4;
    private static final float SEGMENT_GAP = 0.02f;
    private static final float HOVER_LIFT = 4;
    private static final double TAU = Math.PI * 2;
    private static final float FULL_TURN = (float) TAU;
    private static final float START_ANGLE = (float) (-Math.PI / 2);
    private static final double SEGMENT_GAP_FRACTION = SEGMENT_GAP / TAU;
    private static final float ANIMATION_SNAP = 0.001f;
    private static final int ARC_STEPS = 96;

    private final List<Segment> segments = new ArrayList<>();
    private final TextTooltip segmentTooltip = new TextTooltip();

    private float diameter = DEFAULT_DIAMETER;
    private float innerRadiusRatio = DEFAULT_INNER_RADIUS_RATIO;
    private float animationDuration = DEFAULT_ANIMATION_SECONDS;
    private Easing animationEasing = Easing.EASE_OUT;
    private String centerLabel = "";
    private boolean showLegend = true;
    private float appearance;
    private int hoveredIndex = -1;
    private IntConsumer onSegmentClick;
    private Animator animationOwner;
    private Animation animation;

    /**
     * Creates an empty donut chart.
     */
    public DonutChart() {
    }

    /**
     * Returns the segments in drawing order.
     *
     * @return an immutable snapshot of the segments
     */
    public final List<Segment> segments() {
        return List.copyOf(segments);
    }

    /**
     * Appends a segment.
     *
     * @param label segment label used by the legend and the tooltip
     * @param value non-negative value
     * @param color segment fill color
     * @return the created segment
     * @throws NullPointerException if {@code label} or {@code color} is null
     * @throws IllegalArgumentException if the value is negative or non-finite
     */
    public final Segment addSegment(String label, float value, Color color) {
        validateValue(value);
        Segment segment = new Segment(Objects.requireNonNull(label, "label"), value,
                Objects.requireNonNull(color, "color"));
        segments.add(segment);
        syncTooltip();
        return segment;
    }

    /**
     * Removes a segment and clears hover feedback when it disappears.
     *
     * @param segment segment to remove
     * @return true when the segment belonged to this chart
     * @throws NullPointerException if {@code segment} is null
     */
    public final boolean removeSegment(Segment segment) {
        Objects.requireNonNull(segment, "segment");
        int index = indexOf(segment);
        if (index < 0) {
            return false;
        }
        segments.remove(index);
        if (hoveredIndex == index) {
            hoveredIndex = -1;
        } else if (hoveredIndex > index) {
            hoveredIndex--;
        }
        syncTooltip();
        return true;
    }

    /**
     * Removes every segment and clears hover feedback.
     */
    public final void clearSegments() {
        segments.clear();
        hoveredIndex = -1;
        syncTooltip();
    }

    /**
     * Returns the sum of every segment value.
     * The sum is accumulated in double precision so large finite values keep proportional arcs.
     *
     * @return the non-negative total
     */
    public final double total() {
        double total = 0;
        for (Segment segment : segments) {
            total += segment.value;
        }
        return total;
    }

    /**
     * Returns the preferred chart diameter.
     *
     * @return the diameter in logical UI units
     */
    public final float diameter() {
        return diameter;
    }

    /**
     * Sets the preferred chart diameter.
     * The chart shrinks when the assigned bounds cannot fit it together with the legend.
     *
     * @param diameter positive diameter in logical UI units
     * @throws IllegalArgumentException if the diameter is not positive or is non-finite
     */
    public final void setDiameter(float diameter) {
        if (!Float.isFinite(diameter) || diameter <= 0) {
            throw new IllegalArgumentException("Donut diameter must be finite and positive.");
        }
        this.diameter = diameter;
    }

    /**
     * Returns the inner radius as a fraction of the outer radius.
     *
     * @return the ratio in {@code [0, 1)}
     */
    public final float innerRadiusRatio() {
        return innerRadiusRatio;
    }

    /**
     * Sets the inner radius as a fraction of the outer radius.
     * Zero draws a pie chart; larger values make a thinner ring.
     *
     * @param innerRadiusRatio ratio in {@code [0, 1)}
     * @throws IllegalArgumentException if the ratio is outside the range or non-finite
     */
    public final void setInnerRadiusRatio(float innerRadiusRatio) {
        if (!Float.isFinite(innerRadiusRatio) || innerRadiusRatio < 0 || innerRadiusRatio >= 1) {
            throw new IllegalArgumentException("Inner radius ratio must be finite and in [0, 1).");
        }
        this.innerRadiusRatio = innerRadiusRatio;
    }

    /**
     * Reports whether the legend is drawn.
     *
     * @return true when the legend is enabled
     */
    public final boolean isShowLegend() {
        return showLegend;
    }

    /**
     * Shows or hides the legend.
     * The legend participates in measurement only while it is visible and a font is available.
     *
     * @param showLegend true to draw the legend
     */
    public final void setShowLegend(boolean showLegend) {
        this.showLegend = showLegend;
    }

    /**
     * Returns the label drawn in the center of the ring.
     *
     * @return the center label, never null
     */
    public final String centerLabel() {
        return centerLabel;
    }

    /**
     * Sets the label drawn in the center of the ring.
     *
     * @param centerLabel center label, or an empty string to draw nothing
     * @throws NullPointerException if {@code centerLabel} is null
     */
    public final void setCenterLabel(String centerLabel) {
        this.centerLabel = Objects.requireNonNull(centerLabel, "centerLabel");
    }

    /**
     * Returns the appearance animation duration.
     *
     * @return the duration in seconds, or zero for an instant appearance
     */
    public final float animationDuration() {
        return animationDuration;
    }

    /**
     * Sets the appearance animation duration.
     * Zero shows the complete chart immediately.
     *
     * @param animationDuration non-negative duration in seconds
     * @throws IllegalArgumentException if the duration is negative or non-finite
     */
    public final void setAnimationDuration(float animationDuration) {
        if (!Float.isFinite(animationDuration) || animationDuration < 0) {
            throw new IllegalArgumentException("Animation duration must be finite and non-negative.");
        }
        this.animationDuration = animationDuration;
        if (animationDuration == 0) {
            cancelAnimation();
            appearance = 1;
        }
    }

    /**
     * Returns the appearance animation timing curve.
     *
     * @return the timing curve
     */
    public final Easing animationEasing() {
        return animationEasing;
    }

    /**
     * Sets the appearance animation timing curve.
     *
     * @param animationEasing timing curve
     * @throws NullPointerException if {@code animationEasing} is null
     */
    public final void setAnimationEasing(Easing animationEasing) {
        this.animationEasing = Objects.requireNonNull(animationEasing, "animationEasing");
    }

    /**
     * Returns the animated reveal progress.
     *
     * @return progress in {@code [0, 1]}
     */
    public final float appearance() {
        return appearance;
    }

    /**
     * Plays the sweep-in animation again from an empty ring.
     * Without an attached context, or with a zero duration, the chart is shown immediately.
     */
    public final void replayAnimation() {
        cancelAnimation();
        UiContext owner = context();
        if (owner == null || animationDuration <= 0) {
            appearance = 1;
            return;
        }
        appearance = 0;
        animationOwner = owner.animator();
        animation = animationOwner.animate(0, 1, animationDuration, animationEasing,
                value -> appearance = Math.clamp(value, 0f, 1f));
        animation.setOnComplete(this::clearAnimation);
    }

    /**
     * Returns the segment index under the pointer.
     *
     * @return the hovered index, or -1 when no segment is hovered
     */
    public final int hoveredIndex() {
        return hoveredIndex;
    }

    /**
     * Returns the callback invoked when a segment is clicked.
     *
     * @return the click callback, or null when none is assigned
     */
    public final IntConsumer onSegmentClick() {
        return onSegmentClick;
    }

    /**
     * Assigns a callback invoked with the zero-based segment index after a completed click.
     * Clicking does nothing and does not request the pointer cursor while no callback is assigned.
     *
     * @param onSegmentClick click callback, or null to clear it
     */
    public final void setOnSegmentClick(IntConsumer onSegmentClick) {
        this.onSegmentClick = onSegmentClick;
    }

    /**
     * Measures the preferred diameter, optional legend and outer padding.
     *
     * @param constraints available and required dimensions
     * @return the natural chart size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Font font = font();
        float legendWidth = showLegend && font != null ? naturalLegendWidth(font) : 0;
        float legendHeight = showLegend && font != null ? legendHeight(font) : 0;
        float width = PADDING * 2 + diameter
                + (legendWidth > 0 ? LEGEND_GAP + legendWidth : 0);
        float height = PADDING * 2 + Math.max(diameter, legendHeight);
        return new Size(width, height);
    }

    /**
     * Starts the appearance animation when the chart attaches.
     */
    @Override
    protected void onAttached() {
        replayAnimation();
    }

    /**
     * Stops the appearance animation when the chart leaves its widget tree.
     */
    @Override
    protected void onDetached() {
        cancelAnimation();
        appearance = 1;
    }

    /**
     * Keeps the hover tooltip text in sync with live segment values.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        if (hoveredIndex >= segments.size()) {
            hoveredIndex = -1;
        }
        if (hoveredIndex >= 0) {
            syncTooltip();
        }
    }

    /**
     * Paints the ring, segments, center label and legend.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (context() == null) {
            return;
        }
        Geometry geometry = geometry();
        if (geometry == null || geometry.outerRadius <= 0) {
            return;
        }
        Theme theme = context().theme();
        drawTrack(renderer, geometry, theme);
        List<Arc> arcs = arcs();
        double revealed = Math.clamp((double) appearance, 0.0, 1.0);
        for (int pass = 0; pass < 2; pass++) {
            for (Arc arc : arcs) {
                boolean hovered = arc.index == hoveredIndex;
                if (pass == 0 && hovered) {
                    continue;
                }
                if (pass == 1 && !hovered) {
                    continue;
                }
                drawSegment(renderer, geometry, arc, revealed, hovered);
            }
        }
        Font font = font();
        drawCenterLabel(renderer, geometry, theme, font);
        if (font != null) {
            drawLegend(renderer, geometry, theme, font);
        }
    }

    /**
     * Tracks the ring segment under the pointer and updates the tooltip.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @return false so the event can still bubble
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        int index = segmentAt(x, y);
        if (index != hoveredIndex) {
            hoveredIndex = index;
            syncTooltip();
        }
        return false;
    }

    /**
     * Clears hover feedback and the tooltip when the pointer leaves.
     */
    @Override
    protected void onPointerExit() {
        if (hoveredIndex >= 0) {
            hoveredIndex = -1;
            syncTooltip();
        }
    }

    /**
     * Reports the clicked segment.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when a segment was clicked and the callback is assigned
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT || onSegmentClick == null) {
            return false;
        }
        int index = segmentAt(x, y);
        if (index < 0) {
            return false;
        }
        playSound();
        onSegmentClick.accept(index);
        return true;
    }

    /**
     * Requests the pointer cursor over clickable segments.
     *
     * @return the pointer cursor over a segment with a click callback, or the default cursor
     */
    @Override
    public Cursor cursor() {
        return onSegmentClick != null && hoveredIndex >= 0 ? Cursor.POINTER : Cursor.DEFAULT;
    }

    private void drawTrack(RenderManager renderer, Geometry geometry, Theme theme) {
        if (geometry.innerRadius <= ANIMATION_SNAP) {
            return;
        }
        drawRingSector(renderer, new Point(geometry.centerX, geometry.centerY),
                geometry.innerRadius, geometry.outerRadius, START_ANGLE, FULL_TURN,
                Paint.solid(theme.border().withAlpha(0.25f)));
    }

    private void drawSegment(RenderManager renderer, Geometry geometry, Arc arc, double revealed,
                             boolean hovered) {
        VisibleRange range = visibleRange(arc, revealed);
        if (range.sweepFraction <= 0) {
            return;
        }
        Segment segment = segments.get(arc.index);
        float start = START_ANGLE + (float) (range.startFraction * TAU);
        float visibleSweep = (float) (range.sweepFraction * TAU);
        if (visibleSweep <= ANIMATION_SNAP) {
            return;
        }
        float inner = geometry.innerRadius;
        float outer = hovered ? geometry.outerRadius + HOVER_LIFT : geometry.outerRadius;
        Color color = hovered ? Color.mix(segment.color, Color.WHITE, 0.18f) : segment.color;
        drawRingSector(renderer, new Point(geometry.centerX, geometry.centerY),
                inner, outer, start, visibleSweep, Paint.solid(color));
    }

    private VisibleRange visibleRange(Arc arc, double revealed) {
        if (arc.startFraction >= revealed) {
            return new VisibleRange(0, 0);
        }
        double visible = Math.min(arc.sweepFraction, revealed - arc.startFraction);
        if (visible <= 0) {
            return new VisibleRange(0, 0);
        }
        double gap = segments.size() > 1
                ? Math.min(SEGMENT_GAP_FRACTION, visible * 0.25) : 0;
        double sweep = visible - gap;
        if (sweep <= 0) {
            return new VisibleRange(0, 0);
        }
        return new VisibleRange(arc.startFraction + gap / 2, sweep);
    }

    private void drawRingSector(RenderManager renderer, Point center, float inner, float outer,
                                float startAngle, float sweep, Paint paint) {
        if (sweep <= ANIMATION_SNAP || outer <= 0 || outer <= inner) {
            return;
        }
        if (sweep >= FULL_TURN - 0.0001f) {
            if (inner <= ANIMATION_SNAP) {
                renderer.fillCircle(center, outer, paint);
            } else {
                renderer.strokeCircle(center, (inner + outer) / 2, outer - inner, paint);
            }
            return;
        }
        if (inner <= ANIMATION_SNAP) {
            renderer.fillArc(center, outer, outer, startAngle, sweep, paint);
            return;
        }
        float meanRadius = (inner + outer) / 2;
        if (sweep * meanRadius < 0.5f) {
            return;
        }
        int steps = Math.max(2, (int) Math.ceil(sweep / FULL_TURN * ARC_STEPS));
        List<Point> points = new ArrayList<>((steps + 1) * 2);
        for (int step = 0; step <= steps; step++) {
            float angle = startAngle + sweep * step / steps;
            points.add(pointAt(center, outer, angle));
        }
        for (int step = steps; step >= 0; step--) {
            float angle = startAngle + sweep * step / steps;
            points.add(pointAt(center, inner, angle));
        }
        renderer.fillPolygon(points, paint);
    }

    private void drawCenterLabel(RenderManager renderer, Geometry geometry, Theme theme,
                                 Font font) {
        if (font == null || centerLabel.isEmpty()) {
            return;
        }
        float available = geometry.innerRadius > ANIMATION_SNAP
                ? geometry.innerRadius * 1.6f : geometry.outerRadius * 1.2f;
        if (available <= 0) {
            return;
        }
        TextStyle style = new TextStyle(available, 0, 2, TextOverflow.ELLIPSIS,
                HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        renderer.text(font, centerLabel, geometry.centerX, geometry.centerY, theme.text(), style);
    }

    private void drawLegend(RenderManager renderer, Geometry geometry, Theme theme, Font font) {
        if (!showLegend || geometry.legendWidth <= 0 || segments.isEmpty()) {
            return;
        }
        float textWidth = geometry.legendWidth - LEGEND_SWATCH - LEGEND_TEXT_GAP;
        if (textWidth <= 0) {
            return;
        }
        TextStyle style = new TextStyle(textWidth, 0, 1, TextOverflow.ELLIPSIS,
                HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        for (int index = 0; index < segments.size(); index++) {
            Segment segment = segments.get(index);
            float centerY = geometry.legendStartY + index * geometry.legendRowHeight
                    + font.lineHeight() / 2;
            boolean hovered = index == hoveredIndex;
            Color color = hovered ? Color.mix(segment.color, Color.WHITE, 0.18f) : segment.color;
            renderer.fillRoundedRect(new Rect(geometry.legendX, centerY - LEGEND_SWATCH / 2,
                    LEGEND_SWATCH, LEGEND_SWATCH), 2, Paint.solid(color));
            renderer.text(font, segment.label, geometry.legendX + LEGEND_SWATCH + LEGEND_TEXT_GAP,
                    centerY, hovered ? theme.primary() : theme.text(), style);
        }
    }

    private Geometry geometry() {
        Rect area = localBounds();
        if (area.isEmpty()) {
            return null;
        }
        Rect content = new Rect(area.x() + PADDING, area.y() + PADDING,
                Math.max(0, area.width() - PADDING * 2),
                Math.max(0, area.height() - PADDING * 2));
        if (content.isEmpty()) {
            return null;
        }
        Font font = font();
        boolean legend = showLegend && font != null && !segments.isEmpty();
        float legendWidth = 0;
        float legendHeight = 0;
        if (legend) {
            legendWidth = Math.min(naturalLegendWidth(font), content.width() * 0.45f);
            legendHeight = legendHeight(font);
        }
        float reserved = legendWidth > 0 ? legendWidth + LEGEND_GAP : 0;
        float available = Math.max(0, content.width() - reserved);
        float side = Math.min(diameter, Math.min(content.height(), available));
        float centerX = content.x() + available / 2;
        float centerY = content.y() + content.height() / 2;
        float outerRadius = side / 2;
        float innerRadius = outerRadius * innerRadiusRatio;
        float legendX = content.x() + available + LEGEND_GAP;
        float legendStartY = Math.max(content.y(), centerY - legendHeight / 2);
        float rowHeight = legend ? font.lineHeight() + LEGEND_ROW_GAP : 0;
        return new Geometry(centerX, centerY, outerRadius, innerRadius, legendX, legendWidth,
                legendStartY, rowHeight);
    }

    private List<Arc> arcs() {
        double total = total();
        if (total <= 0) {
            return List.of();
        }
        List<Arc> arcs = new ArrayList<>(segments.size());
        double start = 0;
        for (int index = 0; index < segments.size(); index++) {
            double sweep = segments.get(index).value / total;
            arcs.add(new Arc(index, start, sweep));
            start += sweep;
        }
        return arcs;
    }

    private int segmentAt(float x, float y) {
        Geometry geometry = geometry();
        if (geometry == null || geometry.outerRadius <= 0 || total() <= 0) {
            return -1;
        }
        float dx = x - geometry.centerX;
        float dy = y - geometry.centerY;
        float distance = (float) Math.hypot(dx, dy);
        if (distance < geometry.innerRadius) {
            return -1;
        }
        double relative = normalizedFraction(Math.atan2(dy, dx) - START_ANGLE);
        double revealed = Math.clamp((double) appearance, 0.0, 1.0);
        for (Arc arc : arcs()) {
            VisibleRange range = visibleRange(arc, revealed);
            if (range.sweepFraction <= 0) {
                continue;
            }
            if (relative >= range.startFraction
                    && relative < range.startFraction + range.sweepFraction) {
                float outer = arc.index == hoveredIndex
                        ? geometry.outerRadius + HOVER_LIFT : geometry.outerRadius;
                return distance <= outer ? arc.index : -1;
            }
        }
        return -1;
    }

    private void syncTooltip() {
        if (tooltip() != null && tooltip() != segmentTooltip) {
            return;
        }
        if (hoveredIndex < 0 || hoveredIndex >= segments.size()) {
            segmentTooltip.setText("");
            if (tooltip() == segmentTooltip) {
                setTooltip(null);
            }
            return;
        }
        if (tooltip() == null) {
            setTooltip(segmentTooltip);
        }
        Segment segment = segments.get(hoveredIndex);
        double total = total();
        double percent = total <= 0 ? 0 : segment.value / total * 100;
        String label = segment.label.isEmpty() ? "" : segment.label + ": ";
        segmentTooltip.setText(label + formatValue(segment.value)
                + " (" + Math.round(percent) + "%)");
    }

    private int indexOf(Segment segment) {
        for (int index = 0; index < segments.size(); index++) {
            if (segments.get(index) == segment) {
                return index;
            }
        }
        return -1;
    }

    private float naturalLegendWidth(Font font) {
        float width = 0;
        for (Segment segment : segments) {
            width = Math.max(width, LEGEND_SWATCH + LEGEND_TEXT_GAP
                    + font.measure(segment.label).width());
        }
        return width;
    }

    private float legendHeight(Font font) {
        if (segments.isEmpty()) {
            return 0;
        }
        return segments.size() * font.lineHeight() + (segments.size() - 1) * LEGEND_ROW_GAP;
    }

    private void cancelAnimation() {
        if (animation != null) {
            animationOwner.cancel(animation);
        }
        clearAnimation();
    }

    private void clearAnimation() {
        animation = null;
        animationOwner = null;
    }

    private static Point pointAt(Point center, float radius, float angle) {
        return new Point(center.x() + (float) Math.cos(angle) * radius,
                center.y() + (float) Math.sin(angle) * radius);
    }

    private static double normalizedFraction(double angle) {
        double normalized = angle % TAU;
        if (normalized < 0) {
            normalized += TAU;
        }
        return normalized / TAU;
    }

    private static String formatValue(float value) {
        return String.format(Locale.ROOT, value == Math.rint(value) ? "%.0f" : "%.1f", value);
    }

    private static void validateValue(float value) {
        if (!Float.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Segment value must be finite and non-negative.");
        }
    }

    private record Geometry(float centerX, float centerY, float outerRadius, float innerRadius,
                            float legendX, float legendWidth, float legendStartY,
                            float legendRowHeight) {
    }

    private record Arc(int index, double startFraction, double sweepFraction) {
    }

    private record VisibleRange(double startFraction, double sweepFraction) {
    }
}
