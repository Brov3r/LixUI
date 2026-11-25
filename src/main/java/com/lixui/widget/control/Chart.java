package com.lixui.widget.control;

import com.lixui.animation.Animation;
import com.lixui.animation.Animator;
import com.lixui.animation.Easing;
import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
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

/**
 * A line, bar or sparkline chart for one or more value series.
 * Line and bar modes draw axes, horizontal grid lines, category labels and an optional legend.
 * Sparkline mode hides the frame and keeps only the series lines. The chart appears from left to
 * right in line modes, and bars grow from the zero baseline. Hovering a point or a bar updates a
 * tooltip, and the appearance animation can be replayed at any time.
 */
public class Chart extends Widget {
    /**
     * Rendering style of the chart.
     */
    public enum Mode {
        /** Points connected by a stroked line. */
        LINE,
        /** Grouped vertical bars from the zero baseline. */
        BAR,
        /** A compact line without axes, labels or a legend. */
        SPARKLINE
    }

    /**
     * A named series of values drawn with one color.
     */
    public static final class Series {
        private String name;
        private List<Float> values;
        private Color color;

        private Series(String name, List<Float> values, Color color) {
            this.name = name;
            this.values = values;
            this.color = color;
        }

        /**
         * Returns the series name used by the legend and the tooltip.
         *
         * @return the name, never null
         */
        public String name() {
            return name;
        }

        /**
         * Replaces the series name.
         *
         * @param name new name
         * @throws NullPointerException if {@code name} is null
         */
        public void setName(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        /**
         * Returns the immutable value list.
         *
         * @return the values in category order
         */
        public List<Float> values() {
            return values;
        }

        /**
         * Replaces the series values.
         *
         * @param values finite values in category order
         * @throws NullPointerException if {@code values} or a value is null
         * @throws IllegalArgumentException if a value is non-finite
         */
        public void setValues(List<Float> values) {
            this.values = copyValues(values);
        }

        /**
         * Returns the series color.
         *
         * @return the color, never null
         */
        public Color color() {
            return color;
        }

        /**
         * Replaces the series color.
         *
         * @param color new color
         * @throws NullPointerException if {@code color} is null
         */
        public void setColor(Color color) {
            this.color = Objects.requireNonNull(color, "color");
        }
    }

    private static final float DEFAULT_WIDTH = 360;
    private static final float DEFAULT_HEIGHT = 220;
    private static final float SPARKLINE_HEIGHT = 72;
    private static final float PADDING = 8;
    private static final float TOP_PAD = 8;
    private static final float Y_LABEL_GAP = 6;
    private static final float X_LABEL_GAP = 4;
    private static final float LEGEND_GAP = 14;
    private static final float LEGEND_SWATCH_WIDTH = 10;
    private static final float LEGEND_SWATCH_HEIGHT = 6;
    private static final float LEGEND_TEXT_GAP = 7;
    private static final float LEGEND_ROW_GAP = 4;
    private static final float LINE_WIDTH = 2;
    private static final float POINT_RADIUS = 2.5f;
    private static final float HOVER_RADIUS = 10;
    private static final float BAR_GROUP_GAP_RATIO = 0.18f;
    private static final float DEFAULT_ANIMATION_SECONDS = 0.8f;
    private static final float ANIMATION_SNAP = 0.001f;

    private final List<Series> series = new ArrayList<>();
    private final TextTooltip pointTooltip = new TextTooltip();

    private List<String> labels = List.of();
    private Mode mode = Mode.LINE;
    private boolean showLegend = true;
    private boolean showGrid = true;
    private boolean showAxes = true;
    private float animationDuration = DEFAULT_ANIMATION_SECONDS;
    private Easing animationEasing = Easing.EASE_OUT;
    private float appearance;
    private int hoveredSeriesIndex = -1;
    private int hoveredValueIndex = -1;
    private Animator animationOwner;
    private Animation animation;

    /**
     * Creates an empty line chart.
     */
    public Chart() {
    }

    /**
     * Returns the series in drawing order.
     *
     * @return an immutable snapshot of the series
     */
    public final List<Series> series() {
        return List.copyOf(series);
    }

    /**
     * Appends a series.
     *
     * @param name series name used by the legend and the tooltip
     * @param values finite values in category order
     * @param color series color
     * @return the created series
     * @throws NullPointerException if an argument or a value is null
     * @throws IllegalArgumentException if a value is non-finite
     */
    public final Series addSeries(String name, List<Float> values, Color color) {
        Series created = new Series(Objects.requireNonNull(name, "name"), copyValues(values),
                Objects.requireNonNull(color, "color"));
        series.add(created);
        clearHover();
        return created;
    }

    /**
     * Removes a series.
     *
     * @param target series to remove
     * @return true when the series belonged to this chart
     * @throws NullPointerException if {@code target} is null
     */
    public final boolean removeSeries(Series target) {
        Objects.requireNonNull(target, "target");
        int index = indexOf(target);
        if (index < 0) {
            return false;
        }
        series.remove(index);
        clearHover();
        return true;
    }

    /**
     * Removes every series.
     */
    public final void clearSeries() {
        series.clear();
        clearHover();
    }

    /**
     * Returns the category labels.
     *
     * @return an immutable snapshot of the labels
     */
    public final List<String> labels() {
        return labels;
    }

    /**
     * Replaces the category labels drawn under line and bar charts.
     *
     * @param labels labels in category order
     * @throws NullPointerException if {@code labels} or a label is null
     */
    public final void setLabels(List<String> labels) {
        Objects.requireNonNull(labels, "labels");
        List<String> copy = new ArrayList<>(labels.size());
        for (String label : labels) {
            copy.add(Objects.requireNonNull(label, "label"));
        }
        this.labels = List.copyOf(copy);
        clearHover();
    }

    /**
     * Returns the rendering style.
     *
     * @return the chart mode
     */
    public final Mode mode() {
        return mode;
    }

    /**
     * Replaces the rendering style.
     *
     * @param mode chart mode
     * @throws NullPointerException if {@code mode} is null
     */
    public final void setMode(Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
        clearHover();
    }

    /**
     * Reports whether the legend is drawn in line and bar modes.
     *
     * @return true when the legend is enabled
     */
    public final boolean isShowLegend() {
        return showLegend;
    }

    /**
     * Shows or hides the legend in line and bar modes.
     *
     * @param showLegend true to draw the legend
     */
    public final void setShowLegend(boolean showLegend) {
        this.showLegend = showLegend;
    }

    /**
     * Reports whether horizontal grid lines are drawn in line and bar modes.
     *
     * @return true when the grid is enabled
     */
    public final boolean isShowGrid() {
        return showGrid;
    }

    /**
     * Shows or hides horizontal grid lines in line and bar modes.
     *
     * @param showGrid true to draw the grid
     */
    public final void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    /**
     * Reports whether axes, value labels and category labels are drawn.
     *
     * @return true when the axes are enabled
     */
    public final boolean isShowAxes() {
        return showAxes;
    }

    /**
     * Shows or hides axes, value labels and category labels in line and bar modes.
     *
     * @param showAxes true to draw the axes
     */
    public final void setShowAxes(boolean showAxes) {
        this.showAxes = showAxes;
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
     * Returns the animated appearance progress.
     *
     * @return progress in {@code [0, 1]}
     */
    public final float appearance() {
        return appearance;
    }

    /**
     * Plays the appearance animation again from empty.
     * Line modes reveal from left to right and bars grow from the zero baseline.
     */
    public final void replayAnimation() {
        cancelAnimation();
        clearHover();
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
     * Returns the hovered series index.
     *
     * @return the series index, or -1 when no point is hovered
     */
    public final int hoveredSeriesIndex() {
        return hoveredSeriesIndex;
    }

    /**
     * Returns the hovered value index within the series.
     *
     * @return the value index, or -1 when no point is hovered
     */
    public final int hoveredValueIndex() {
        return hoveredValueIndex;
    }

    /**
     * Measures the preferred chart area and optional legend.
     *
     * @param constraints available and required dimensions
     * @return the natural chart size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Font font = font();
        float legendWidth = mode != Mode.SPARKLINE && showLegend && font != null
                ? naturalLegendWidth(font) : 0;
        float height = mode == Mode.SPARKLINE
                ? Math.min(DEFAULT_HEIGHT, SPARKLINE_HEIGHT) : DEFAULT_HEIGHT;
        float width = PADDING * 2 + DEFAULT_WIDTH
                + (legendWidth > 0 ? LEGEND_GAP + legendWidth : 0);
        return new Size(width, PADDING * 2 + height);
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
        clearHover();
    }

    /**
     * Keeps the hover tooltip text in sync with live values.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        if (isHoverValid()) {
            syncTooltip();
        } else {
            clearHover();
        }
    }

    /**
     * Paints the frame, series, hover highlight and legend.
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
        if (geometry == null) {
            return;
        }
        Theme theme = context().theme();
        Font font = font();
        drawFrame(renderer, geometry, theme, font);
        float progress = Math.clamp(appearance, 0f, 1f);
        if (mode == Mode.BAR) {
            drawBars(renderer, geometry, progress, false);
            drawBars(renderer, geometry, progress, true);
        } else {
            drawLines(renderer, geometry, progress);
            drawHoverPoint(renderer, geometry, theme, progress);
        }
        if (font != null) {
            drawLegend(renderer, geometry, theme, font);
        }
    }

    /**
     * Tracks the point or bar under the pointer and updates the tooltip.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @return false so the event can still bubble
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        updateHover(x, y);
        return false;
    }

    /**
     * Clears hover feedback and the tooltip when the pointer leaves.
     */
    @Override
    protected void onPointerExit() {
        clearHover();
    }

    private void drawFrame(RenderManager renderer, Geometry geometry, Theme theme, Font font) {
        if (mode == Mode.SPARKLINE) {
            return;
        }
        Rect plot = geometry.plot;
        if (showGrid) {
            Color grid = theme.border().withAlpha(0.18f);
            for (double tick : geometry.ticks.values) {
                float y = valueToY(tick, geometry);
                renderer.strokeLine(new Point(plot.x(), y), new Point(plot.right(), y),
                        1, Paint.solid(grid));
            }
        }
        if (!showAxes) {
            return;
        }
        Color axis = theme.border().withAlpha(0.75f);
        renderer.strokeLine(new Point(plot.x(), plot.y()), new Point(plot.x(), plot.bottom()),
                1, Paint.solid(axis));
        float baseline = valueToY(0, geometry);
        renderer.strokeLine(new Point(plot.x(), baseline), new Point(plot.right(), baseline),
                1, Paint.solid(axis));
        if (font == null) {
            return;
        }
        for (double tick : geometry.ticks.values) {
            float y = valueToY(tick, geometry);
            renderer.text(font, formatTick(tick), plot.x() - Y_LABEL_GAP, y,
                    theme.textMuted(), HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        }
        drawCategoryLabels(renderer, geometry, theme, font);
    }

    private void drawCategoryLabels(RenderManager renderer, Geometry geometry, Theme theme,
                                    Font font) {
        if (labels.isEmpty()) {
            return;
        }
        int count = categoryCount();
        if (count == 0) {
            return;
        }
        float band = geometry.plot.width() / count;
        float maxWidth = Math.max(0, band - 4);
        if (maxWidth <= 0) {
            return;
        }
        TextStyle style = new TextStyle(maxWidth, 0, 1, TextOverflow.ELLIPSIS,
                HorizontalAlignment.CENTER, VerticalAlignment.TOP);
        for (int index = 0; index < Math.min(labels.size(), count); index++) {
            float x = geometry.plot.x() + band * (index + 0.5f);
            renderer.text(font, labels.get(index), x, geometry.plot.bottom() + X_LABEL_GAP,
                    theme.textMuted(), style);
        }
    }

    private void drawLines(RenderManager renderer, Geometry geometry, float progress) {
        if (progress <= ANIMATION_SNAP) {
            return;
        }
        boolean masked = progress < 1;
        if (masked) {
            renderer.pushMaskRect(new Rect(geometry.plot.x(), geometry.plot.y(),
                    geometry.plot.width() * progress, geometry.plot.height()));
        }
        try {
            int count = categoryCount();
            for (Series current : series) {
                List<Point> points = new ArrayList<>();
                for (int index = 0; index < current.values.size() && index < count; index++) {
                    points.add(pointFor(geometry, current, index));
                }
                if (points.size() > 1) {
                    renderer.strokePolyline(points, LINE_WIDTH, Paint.solid(current.color));
                }
                if (mode == Mode.LINE) {
                    for (Point point : points) {
                        renderer.fillCircle(point, POINT_RADIUS, Paint.solid(current.color));
                    }
                }
            }
        } finally {
            if (masked) {
                renderer.popMask();
            }
        }
    }

    private void drawBars(RenderManager renderer, Geometry geometry, float progress,
                          boolean hoveredOnly) {
        int count = categoryCount();
        if (count == 0 || series.isEmpty()) {
            return;
        }
        for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
            Series current = series.get(seriesIndex);
            for (int valueIndex = 0; valueIndex < current.values.size() && valueIndex < count;
                    valueIndex++) {
                boolean hovered = seriesIndex == hoveredSeriesIndex
                        && valueIndex == hoveredValueIndex;
                if (hovered != hoveredOnly) {
                    continue;
                }
                Rect bar = barRect(geometry, seriesIndex, valueIndex, progress);
                if (bar.isEmpty()) {
                    continue;
                }
                Color color = hovered
                        ? Color.mix(current.color, Color.WHITE, 0.22f) : current.color;
                renderer.fillRect(bar, Paint.solid(color));
            }
        }
    }

    private void drawHoverPoint(RenderManager renderer, Geometry geometry, Theme theme,
                                float progress) {
        if (!isHoverValid()) {
            return;
        }
        Series current = series.get(hoveredSeriesIndex);
        Point point = pointFor(geometry, current, hoveredValueIndex);
        if (point.x() > geometry.plot.x() + geometry.plot.width() * progress + 1) {
            return;
        }
        if (mode != Mode.SPARKLINE) {
            renderer.strokeLine(new Point(point.x(), geometry.plot.y()),
                    new Point(point.x(), geometry.plot.bottom()), 1,
                    Paint.solid(theme.border().withAlpha(0.35f)));
        }
        renderer.fillCircle(point, POINT_RADIUS + 2.5f,
                Paint.solid(Color.mix(current.color, Color.WHITE, 0.3f)));
        renderer.strokeCircle(point, POINT_RADIUS + 4.5f, 1.5f, Paint.solid(current.color));
    }

    private void drawLegend(RenderManager renderer, Geometry geometry, Theme theme, Font font) {
        if (!showLegend || mode == Mode.SPARKLINE || geometry.legendWidth <= 0
                || series.isEmpty()) {
            return;
        }
        float textWidth = geometry.legendWidth - LEGEND_SWATCH_WIDTH - LEGEND_TEXT_GAP;
        if (textWidth <= 0) {
            return;
        }
        TextStyle style = new TextStyle(textWidth, 0, 1, TextOverflow.ELLIPSIS,
                HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        for (int index = 0; index < series.size(); index++) {
            Series current = series.get(index);
            float centerY = geometry.legendStartY + index * geometry.legendRowHeight
                    + font.lineHeight() / 2;
            renderer.fillRoundedRect(new Rect(geometry.legendX,
                    centerY - LEGEND_SWATCH_HEIGHT / 2, LEGEND_SWATCH_WIDTH,
                    LEGEND_SWATCH_HEIGHT), 2, Paint.solid(current.color));
            boolean hovered = index == hoveredSeriesIndex;
            renderer.text(font, current.name,
                    geometry.legendX + LEGEND_SWATCH_WIDTH + LEGEND_TEXT_GAP, centerY,
                    hovered ? theme.primary() : theme.text(), style);
        }
    }

    private void updateHover(float x, float y) {
        Geometry geometry = geometry();
        int count = categoryCount();
        if (geometry == null || count == 0) {
            clearHover();
            return;
        }
        float progress = Math.clamp(appearance, 0f, 1f);
        if (mode == Mode.BAR) {
            int bestSeries = -1;
            int bestValue = -1;
            for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
                Series current = series.get(seriesIndex);
                for (int valueIndex = 0; valueIndex < current.values.size()
                        && valueIndex < count; valueIndex++) {
                    Rect bar = barRect(geometry, seriesIndex, valueIndex, progress);
                    if (!bar.isEmpty() && bar.contains(x, y)) {
                        bestSeries = seriesIndex;
                        bestValue = valueIndex;
                    }
                }
            }
            setHover(bestSeries, bestValue);
            return;
        }
        int bestSeries = -1;
        int bestValue = -1;
        float bestDistance = HOVER_RADIUS;
        for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
            Series current = series.get(seriesIndex);
            for (int valueIndex = 0; valueIndex < current.values.size()
                    && valueIndex < count; valueIndex++) {
                Point point = pointFor(geometry, current, valueIndex);
                if (point.x() > geometry.plot.x() + geometry.plot.width() * progress + 1) {
                    continue;
                }
                float distance = (float) Math.hypot(x - point.x(), y - point.y());
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    bestSeries = seriesIndex;
                    bestValue = valueIndex;
                }
            }
        }
        setHover(bestSeries, bestValue);
    }

    private void setHover(int seriesIndex, int valueIndex) {
        if (seriesIndex == hoveredSeriesIndex && valueIndex == hoveredValueIndex) {
            return;
        }
        hoveredSeriesIndex = seriesIndex;
        hoveredValueIndex = valueIndex;
        syncTooltip();
    }

    private void clearHover() {
        if (hoveredSeriesIndex < 0 && hoveredValueIndex < 0) {
            return;
        }
        hoveredSeriesIndex = -1;
        hoveredValueIndex = -1;
        syncTooltip();
    }

    private boolean isHoverValid() {
        return hoveredSeriesIndex >= 0 && hoveredSeriesIndex < series.size()
                && hoveredValueIndex >= 0
                && hoveredValueIndex < series.get(hoveredSeriesIndex).values.size();
    }

    private void syncTooltip() {
        if (tooltip() != null && tooltip() != pointTooltip) {
            return;
        }
        if (!isHoverValid()) {
            pointTooltip.setText("");
            if (tooltip() == pointTooltip) {
                setTooltip(null);
            }
            return;
        }
        if (tooltip() == null) {
            setTooltip(pointTooltip);
        }
        Series current = series.get(hoveredSeriesIndex);
        String label = hoveredValueIndex < labels.size() ? labels.get(hoveredValueIndex) : "";
        StringBuilder text = new StringBuilder();
        if (!current.name.isEmpty()) {
            text.append(current.name);
        }
        if (!label.isEmpty()) {
            if (text.length() > 0) {
                text.append(" · ");
            }
            text.append(label);
        }
        if (text.length() > 0) {
            text.append(": ");
        }
        text.append(formatValue(current.values.get(hoveredValueIndex)));
        pointTooltip.setText(text.toString());
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
        Ticks ticks = ticks();
        Font font = font();
        boolean legend = mode != Mode.SPARKLINE && showLegend && font != null && !series.isEmpty();
        float legendWidth = 0;
        float legendHeight = 0;
        if (legend) {
            legendWidth = Math.min(naturalLegendWidth(font), content.width() * 0.35f);
            legendHeight = legendHeight(font);
        }
        float reserved = legendWidth > 0 ? legendWidth + LEGEND_GAP : 0;
        float yAxisWidth = mode != Mode.SPARKLINE && showAxes && font != null
                ? maxTickLabelWidth(font, ticks) + Y_LABEL_GAP : 0;
        float xLabelHeight = mode != Mode.SPARKLINE && showAxes && font != null
                && !labels.isEmpty() ? font.lineHeight() + X_LABEL_GAP : 0;
        Rect plot = new Rect(content.x() + yAxisWidth, content.y() + TOP_PAD,
                Math.max(0, content.width() - yAxisWidth - reserved),
                Math.max(0, content.height() - TOP_PAD - xLabelHeight));
        if (plot.isEmpty()) {
            return null;
        }
        float legendX = plot.right() + LEGEND_GAP;
        float legendStartY = Math.max(plot.y(),
                plot.center().y() - legendHeight / 2);
        float rowHeight = legend ? font.lineHeight() + LEGEND_ROW_GAP : 0;
        return new Geometry(plot, ticks, legendX, legendWidth, legendStartY, rowHeight);
    }

    private Ticks ticks() {
        double minimum = 0;
        double maximum = 0;
        boolean any = false;
        for (Series current : series) {
            for (float value : current.values) {
                if (!any) {
                    minimum = value;
                    maximum = value;
                    any = true;
                } else {
                    minimum = Math.min(minimum, value);
                    maximum = Math.max(maximum, value);
                }
            }
        }
        if (!any) {
            maximum = 1;
        } else {
            minimum = Math.min(0, minimum);
            maximum = Math.max(0, maximum);
        }
        if (minimum == maximum) {
            maximum = minimum + 1;
        }
        double step = niceStep((maximum - minimum) / 4);
        double tickMin = Math.floor(minimum / step) * step;
        double tickMax = Math.ceil(maximum / step) * step;
        if (tickMax <= tickMin) {
            tickMax = tickMin + step;
        }
        int count = (int) Math.round((tickMax - tickMin) / step);
        count = Math.max(1, Math.min(count, 24));
        List<Double> values = new ArrayList<>(count + 1);
        for (int index = 0; index <= count; index++) {
            values.add(tickMin + index * step);
        }
        return new Ticks(tickMin, tickMax, List.copyOf(values));
    }

    private float valueToY(double value, Geometry geometry) {
        double span = geometry.ticks.maximum - geometry.ticks.minimum;
        double fraction = span <= 0 ? 0 : (value - geometry.ticks.minimum) / span;
        return geometry.plot.bottom() - (float) (fraction * geometry.plot.height());
    }

    private Point pointFor(Geometry geometry, Series current, int index) {
        int count = categoryCount();
        float x = geometry.plot.x() + geometry.plot.width() * (index + 0.5f) / count;
        return new Point(x, valueToY(current.values.get(index), geometry));
    }

    private Rect barRect(Geometry geometry, int seriesIndex, int valueIndex, float progress) {
        int count = categoryCount();
        if (count == 0 || series.isEmpty()) {
            return Rect.EMPTY;
        }
        Series current = series.get(seriesIndex);
        float band = geometry.plot.width() / count;
        float groupGap = band * BAR_GROUP_GAP_RATIO;
        float groupWidth = Math.max(0, band - groupGap);
        float barWidth = groupWidth / Math.max(1, series.size());
        float gap = Math.min(2, barWidth * 0.15f);
        float x = geometry.plot.x() + band * valueIndex + groupGap / 2
                + barWidth * seriesIndex + gap / 2;
        float width = Math.max(0, barWidth - gap);
        float baseline = valueToY(0, geometry);
        float valueY = valueToY(current.values.get(valueIndex), geometry);
        float animatedY = baseline + (valueY - baseline) * progress;
        float top = Math.min(baseline, animatedY);
        float height = Math.abs(baseline - animatedY);
        if (height < 0.5f) {
            return Rect.EMPTY;
        }
        return new Rect(x, top, width, height);
    }

    private int categoryCount() {
        int count = labels.size();
        for (Series current : series) {
            count = Math.max(count, current.values.size());
        }
        return count;
    }

    private int indexOf(Series target) {
        for (int index = 0; index < series.size(); index++) {
            if (series.get(index) == target) {
                return index;
            }
        }
        return -1;
    }

    private float naturalLegendWidth(Font font) {
        float width = 0;
        for (Series current : series) {
            width = Math.max(width, LEGEND_SWATCH_WIDTH + LEGEND_TEXT_GAP
                    + font.measure(current.name).width());
        }
        return width;
    }

    private float legendHeight(Font font) {
        if (series.isEmpty()) {
            return 0;
        }
        return series.size() * font.lineHeight() + (series.size() - 1) * LEGEND_ROW_GAP;
    }

    private float maxTickLabelWidth(Font font, Ticks ticks) {
        float width = 0;
        for (double value : ticks.values) {
            width = Math.max(width, font.measure(formatTick(value)).width());
        }
        return width;
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

    private static List<Float> copyValues(List<Float> values) {
        Objects.requireNonNull(values, "values");
        List<Float> copy = new ArrayList<>(values.size());
        for (Float value : values) {
            Objects.requireNonNull(value, "value");
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Chart values must be finite.");
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }

    private static double niceStep(double raw) {
        if (!Double.isFinite(raw) || raw <= 0) {
            return 1;
        }
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double normalized = raw / magnitude;
        double nice;
        if (normalized <= 1) {
            nice = 1;
        } else if (normalized <= 2) {
            nice = 2;
        } else if (normalized <= 5) {
            nice = 5;
        } else {
            nice = 10;
        }
        return nice * magnitude;
    }

    private static String formatTick(double value) {
        if (value == 0) {
            return "0";
        }
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000 || absolute < 0.01) {
            return String.format(Locale.ROOT, "%.1e", value);
        }
        if (value == Math.rint(value)) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatValue(float value) {
        return String.format(Locale.ROOT, value == Math.rint(value) ? "%.0f" : "%.1f", value);
    }

    private record Ticks(double minimum, double maximum, List<Double> values) {
    }

    private record Geometry(Rect plot, Ticks ticks, float legendX, float legendWidth,
                            float legendStartY, float legendRowHeight) {
    }
}
