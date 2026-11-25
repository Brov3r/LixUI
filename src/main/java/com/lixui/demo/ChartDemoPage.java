package com.lixui.demo;

import com.lixui.graphics.Color;
import com.lixui.text.FontFamily;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.Chart;
import com.lixui.widget.control.CheckBox;
import com.lixui.widget.control.Label;
import com.lixui.widget.layout.Flow;
import com.lixui.widget.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds the demo page for the line, bar and sparkline chart modes.
 * The page shows two full charts with live toggles and a compact sparkline strip.
 */
final class ChartDemoPage {
    private static final float CONTENT_PADDING = 10;
    private static final float CONTENT_GAP = 10;
    private static final float ANIMATION_SECONDS = 0.9f;
    private static final Color BLUE = Color.hex("#7EA6F5");
    private static final Color GREEN = Color.hex("#86C46A");
    private static final Color ORANGE = Color.hex("#E39A5B");
    private static final Color PURPLE = Color.hex("#C39BF2");

    private final FontFamily fontFamily;

    /**
     * Creates the demo page builder.
     *
     * @param fontFamily font family used by page labels, or null for the inherited font
     */
    ChartDemoPage(FontFamily fontFamily) {
        this.fontFamily = fontFamily;
    }

    /**
     * Builds the demo page.
     *
     * @return the scrollable page content
     */
    Widget build() {
        Label status = caption("Hover a point or a bar to read its value.");
        Chart line = createLineChart();
        Chart bars = createBarChart();
        Chart sparkline = createSparkline();

        CheckBox legend = new CheckBox("Legend");
        legend.setChecked(true);
        legend.setOnChange(checked -> {
            line.setShowLegend(checked);
            bars.setShowLegend(checked);
        });

        CheckBox grid = new CheckBox("Grid");
        grid.setChecked(true);
        grid.setOnChange(checked -> {
            line.setShowGrid(checked);
            bars.setShowGrid(checked);
        });

        CheckBox axes = new CheckBox("Axes");
        axes.setChecked(true);
        axes.setOnChange(checked -> {
            line.setShowAxes(checked);
            bars.setShowAxes(checked);
        });

        CheckBox animate = new CheckBox("Animate");
        animate.setChecked(true);
        animate.setOnChange(checked -> {
            for (Chart chart : List.of(line, bars, sparkline)) {
                chart.setAnimationDuration(checked ? ANIMATION_SECONDS : 0);
                chart.replayAnimation();
            }
        });

        Button replay = new Button("Replay");
        replay.setOnAction(() -> {
            for (Chart chart : List.of(line, bars, sparkline)) {
                chart.replayAnimation();
            }
            status.setText("Appearance animation replayed.");
        });

        Button randomize = new Button("Randomize");
        randomize.setOnAction(() -> {
            for (Chart chart : List.of(line, bars, sparkline)) {
                randomize(chart);
                chart.replayAnimation();
            }
            status.setText("Values randomized.");
        });

        Flow controls = new Flow();
        controls.setGap(10);
        controls.add(legend).add(grid).add(axes).add(animate).add(replay).add(randomize);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(CONTENT_GAP);
        page.add(caption("Line: connected points reveal from left to right and show a tooltip "
                        + "on the nearest point."))
                .add(line)
                .add(caption("Bar: grouped columns grow from the zero baseline."))
                .add(bars)
                .add(caption("Sparkline: a compact line without axes, labels or a legend."))
                .add(sparkline)
                .add(controls)
                .add(status);

        return new DemoScrollPage(page);
    }

    private Chart createLineChart() {
        Chart chart = new Chart();
        chart.setMode(Chart.Mode.LINE);
        chart.setLabels(List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"));
        chart.addSeries("Revenue", List.of(42f, 58f, 51f, 73f, 68f, 81f, 76f), BLUE);
        chart.addSeries("Costs", List.of(31f, 36f, 34f, 39f, 42f, 44f, 41f), ORANGE);
        chart.setAnimationDuration(ANIMATION_SECONDS);
        return chart;
    }

    private Chart createBarChart() {
        Chart chart = new Chart();
        chart.setMode(Chart.Mode.BAR);
        chart.setLabels(List.of("Q1", "Q2", "Q3", "Q4", "Q5", "Q6"));
        chart.addSeries("North", List.of(64f, 72f, 58f, 81f, 69f, 90f), GREEN);
        chart.addSeries("South", List.of(48f, 55f, 61f, 52f, 70f, 66f), PURPLE);
        chart.setAnimationDuration(ANIMATION_SECONDS);
        return chart;
    }

    private Chart createSparkline() {
        Chart chart = new Chart();
        chart.setMode(Chart.Mode.SPARKLINE);
        chart.addSeries("Latency", List.of(18f, 22f, 16f, 25f, 19f, 14f, 17f, 12f, 15f, 11f,
                13f, 10f), GREEN);
        chart.setAnimationDuration(1.1f);
        return chart;
    }

    private void randomize(Chart chart) {
        for (Chart.Series series : chart.series()) {
            List<Float> values = new ArrayList<>(series.values().size());
            for (int index = 0; index < series.values().size(); index++) {
                values.add(ThreadLocalRandom.current().nextFloat(10, 100));
            }
            series.setValues(values);
        }
    }

    private Label caption(String text) {
        Label label = new Label(text);
        label.setFontFamily(fontFamily);
        label.setAutoSize(false);
        label.setWrap(true);
        return label;
    }
}
