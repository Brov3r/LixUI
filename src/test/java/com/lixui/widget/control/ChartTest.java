package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.event.PointerMoved;
import com.lixui.render.command.MeshCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import com.lixui.widget.tooltip.TextTooltip;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartTest {
    private static final Color BLUE = Color.hex("#7EA6F5");
    private static final Color ORANGE = Color.hex("#E39A5B");
    private static final Color GREEN = Color.hex("#86C46A");

    @Test
    void lineHoverReportsTheNearestPoint() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Chart chart = new Chart();
            chart.setLabels(List.of("Mon", "Tue", "Wed"));
            chart.addSeries("Trend", List.of(0f, 50f, 100f), BLUE);
            chart.setBounds(new Rect(0, 0, 400, 240));
            chart.setAnimationDuration(0);
            manager.add(chart);
            manager.render();

            manager.handle(new PointerMoved(72, 232));
            assertEquals(0, chart.hoveredSeriesIndex());
            assertEquals(0, chart.hoveredValueIndex());
            TextTooltip tooltip = assertInstanceOf(TextTooltip.class, chart.tooltip());
            assertEquals("Trend · Mon: 0", tooltip.text());

            manager.handle(new PointerMoved(200, 124));
            assertEquals(1, chart.hoveredValueIndex());
            assertEquals("Trend · Tue: 50", tooltip.text());

            manager.handle(new PointerMoved(200, 232));
            assertEquals(-1, chart.hoveredValueIndex());
            assertNull(chart.tooltip());
        }
    }

    @Test
    void barHoverReportsTheBarUnderThePointer() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Chart chart = new Chart();
            chart.setMode(Chart.Mode.BAR);
            chart.setLabels(List.of("A", "B", "C"));
            chart.addSeries("Sales", List.of(100f, 50f, 25f), ORANGE);
            chart.setBounds(new Rect(0, 0, 400, 240));
            chart.setAnimationDuration(0);
            manager.add(chart);
            manager.render();

            manager.handle(new PointerMoved(70, 100));
            assertEquals(0, chart.hoveredSeriesIndex());
            assertEquals(0, chart.hoveredValueIndex());
            TextTooltip tooltip = assertInstanceOf(TextTooltip.class, chart.tooltip());
            assertEquals("Sales · A: 100", tooltip.text());

            manager.handle(new PointerMoved(200, 180));
            assertEquals(1, chart.hoveredValueIndex());
            assertEquals("Sales · B: 50", tooltip.text());

            manager.handle(new PointerMoved(135, 180));
            assertEquals(-1, chart.hoveredValueIndex());
            assertNull(chart.tooltip());
        }
    }

    @Test
    void animatesTheAppearanceAndCanReplayIt() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Chart chart = new Chart();
            chart.addSeries("Trend", List.of(10f, 20f, 15f), BLUE);
            chart.setBounds(new Rect(0, 0, 400, 240));
            chart.setAnimationDuration(0.3f);
            manager.add(chart);
            manager.render();
            assertEquals(0, chart.appearance(), 0.001f);

            manager.update(0.05f);
            float partial = chart.appearance();
            assertTrue(partial > 0 && partial < 1);

            manager.update(1);
            assertEquals(1, chart.appearance(), 0.001f);

            chart.replayAnimation();
            assertEquals(0, chart.appearance(), 0.001f);
            manager.update(1);
            assertEquals(1, chart.appearance(), 0.001f);

            chart.setAnimationDuration(0);
            chart.replayAnimation();
            assertEquals(1, chart.appearance(), 0.001f);
        }
    }

    @Test
    void measuresTheLegendWithFontMetrics() {
        FontData data = fontData();
        Font font = Font.load(data, 16);
        Chart chart = new Chart();
        chart.setFont(font);
        chart.addSeries("A", List.of(1f), BLUE);

        Size first = chart.measure(LayoutConstraints.loose(10000, 10000));
        chart.series().getFirst().setName("A much longer name");
        Size second = chart.measure(LayoutConstraints.loose(10000, 10000));

        assertEquals(font.measure("A much longer name").width() - font.measure("A").width(),
                second.width() - first.width(), 0.01f);
        assertEquals(236, second.height(), 0.01f);

        chart.setShowLegend(false);
        Size withoutLegend = chart.measure(LayoutConstraints.loose(10000, 10000));
        assertTrue(withoutLegend.width() < second.width());
    }

    @Test
    void sparklineDrawsOnlySeriesLines() {
        FontData data = fontData();
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(manager.context().createFont(data, 16));
            Chart chart = new Chart();
            chart.setMode(Chart.Mode.SPARKLINE);
            chart.setLabels(List.of("A", "B", "C"));
            chart.addSeries("Latency", List.of(12f, 18f, 9f), GREEN);
            chart.setBounds(new Rect(0, 0, 300, 90));
            chart.setAnimationDuration(0);
            manager.add(chart);
            manager.render();

            assertTrue(backend.lastDrawList().commands().stream()
                    .anyMatch(MeshCommand.class::isInstance));
            assertEquals(0, backend.lastDrawList().commands().stream()
                    .filter(TextCommand.class::isInstance)
                    .count());
        }
    }

    @Test
    void rejectsInvalidConfiguration() {
        Chart chart = new Chart();
        assertThrows(NullPointerException.class,
                () -> chart.addSeries(null, List.of(1f), BLUE));
        assertThrows(NullPointerException.class,
                () -> chart.addSeries("A", null, BLUE));
        assertThrows(NullPointerException.class,
                () -> chart.addSeries("A", List.of(1f), null));
        assertThrows(IllegalArgumentException.class,
                () -> chart.addSeries("A", List.of(1f, Float.NaN), BLUE));
        assertThrows(NullPointerException.class,
                () -> chart.addSeries("A", List.of(1f, null), BLUE));
        assertThrows(NullPointerException.class, () -> chart.removeSeries(null));
        assertThrows(NullPointerException.class, () -> chart.setLabels(null));
        assertThrows(NullPointerException.class, () -> chart.setLabels(List.of("A", null)));
        assertThrows(NullPointerException.class, () -> chart.setMode(null));
        assertThrows(IllegalArgumentException.class, () -> chart.setAnimationDuration(-1));
        assertThrows(NullPointerException.class, () -> chart.setAnimationEasing(null));

        Chart.Series series = chart.addSeries("A", List.of(1f), BLUE);
        assertThrows(NullPointerException.class, () -> series.setName(null));
        assertThrows(NullPointerException.class, () -> series.setColor(null));
        assertThrows(IllegalArgumentException.class,
                () -> series.setValues(List.of(Float.POSITIVE_INFINITY)));
    }

    private static FontData fontData() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        return data.get();
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(TestViewports.LOGICAL);
        return manager;
    }
}
