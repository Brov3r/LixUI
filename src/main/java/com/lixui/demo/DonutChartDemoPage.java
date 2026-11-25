package com.lixui.demo;

import com.lixui.graphics.Color;
import com.lixui.text.FontFamily;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.CheckBox;
import com.lixui.widget.control.DonutChart;
import com.lixui.widget.control.Label;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds the demo page for the donut chart.
 * The page compares a segmented spending chart with a compact goal ring and provides live controls.
 */
final class DonutChartDemoPage {
    private static final float CONTENT_PADDING = 10;
    private static final float CONTENT_GAP = 10;
    private static final float ANIMATION_SECONDS = 0.8f;
    private static final Color BLUE = Color.hex("#7EA6F5");
    private static final Color GREEN = Color.hex("#86C46A");
    private static final Color ORANGE = Color.hex("#E39A5B");
    private static final Color PURPLE = Color.hex("#C39BF2");
    private static final Color NEUTRAL = Color.hex("#B9B9B9");

    private final FontFamily fontFamily;

    /**
     * Creates the demo page builder.
     *
     * @param fontFamily font family used by page labels, or null for the inherited font
     */
    DonutChartDemoPage(FontFamily fontFamily) {
        this.fontFamily = fontFamily;
    }

    /**
     * Builds the demo page.
     *
     * @return the page content
     */
    Widget build() {
        Label status = caption("Hover a ring for details; click a segment to report it.");
        DonutChart spending = createSpending(status);
        DonutChart goal = createGoal(status);

        HBox charts = new HBox();
        charts.setGap(12);
        charts.setCrossAlignment(HBox.CrossAlignment.CENTER);
        spending.setGrow(1);
        goal.setGrow(1);
        charts.add(spending).add(goal);

        CheckBox legend = new CheckBox("Spending legend");
        legend.setChecked(true);
        legend.setOnChange(spending::setShowLegend);

        CheckBox animate = new CheckBox("Animate appearance");
        animate.setChecked(true);
        animate.setOnChange(checked -> {
            for (DonutChart chart : List.of(spending, goal)) {
                chart.setAnimationDuration(checked ? ANIMATION_SECONDS : 0);
                chart.replayAnimation();
            }
        });

        Button replay = new Button("Replay");
        replay.setOnAction(() -> {
            for (DonutChart chart : List.of(spending, goal)) {
                chart.replayAnimation();
            }
            status.setText("Appearance animation replayed.");
        });

        Button randomize = new Button("Randomize");
        randomize.setOnAction(() -> {
            randomize(spending);
            randomize(goal);
            for (DonutChart chart : List.of(spending, goal)) {
                chart.replayAnimation();
            }
            status.setText("Values randomized.");
        });

        HBox controls = new HBox();
        controls.setGap(10);
        controls.setCrossAlignment(HBox.CrossAlignment.CENTER);
        controls.add(legend).add(animate).add(replay).add(randomize);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(CONTENT_GAP);
        page.add(caption("Donut segments follow their values, start at the top and sweep in when "
                        + "the chart appears. Hovering lifts a segment and shows its share."))
                .add(charts)
                .add(controls)
                .add(status);
        return page;
    }

    private DonutChart createSpending(Label status) {
        DonutChart chart = new DonutChart();
        chart.setDiameter(180);
        chart.setInnerRadiusRatio(0.6f);
        chart.setAnimationDuration(ANIMATION_SECONDS);
        chart.setCenterLabel("$4.2k");
        chart.setShowLegend(true);
        chart.addSegment("Housing", 1600, BLUE);
        chart.addSegment("Food", 900, GREEN);
        chart.addSegment("Transport", 700, ORANGE);
        chart.addSegment("Leisure", 1000, PURPLE);
        chart.setOnSegmentClick(index -> report(status, "Spending", chart, index));
        return chart;
    }

    private DonutChart createGoal(Label status) {
        DonutChart chart = new DonutChart();
        chart.setDiameter(170);
        chart.setInnerRadiusRatio(0.76f);
        chart.setAnimationDuration(ANIMATION_SECONDS);
        chart.setCenterLabel("72%");
        chart.setShowLegend(false);
        chart.addSegment("Done", 72, GREEN);
        chart.addSegment("Remaining", 28, NEUTRAL);
        chart.setOnSegmentClick(index -> report(status, "Goal", chart, index));
        return chart;
    }

    private void report(Label status, String name, DonutChart chart, int index) {
        DonutChart.Segment segment = chart.segments().get(index);
        double total = chart.total();
        double percent = total <= 0 ? 0 : segment.value() / total * 100;
        status.setText(name + ": \"" + segment.label() + "\" = " + formatValue(segment.value())
                + " (" + Math.round(percent) + "%).");
    }

    private void randomize(DonutChart chart) {
        List<DonutChart.Segment> segments = chart.segments();
        for (DonutChart.Segment segment : segments) {
            segment.setValue(ThreadLocalRandom.current().nextFloat(20, 200));
        }
    }

    private static String formatValue(float value) {
        return value == Math.rint(value)
                ? Long.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private Label caption(String text) {
        Label label = new Label(text);
        label.setFontFamily(fontFamily);
        label.setAutoSize(false);
        label.setWrap(true);
        return label;
    }
}
