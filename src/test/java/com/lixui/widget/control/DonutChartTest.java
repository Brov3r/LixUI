package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonutChartTest {
    private static final Color RED = Color.hex("#E06C75");
    private static final Color GREEN = Color.hex("#86C46A");
    private static final Color BLUE = Color.hex("#7EA6F5");

    @Test
    void hitTestsTheRingByAngleAndRadius() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            DonutChart chart = chart();
            chart.setBounds(new Rect(0, 0, 200, 200));
            chart.setShowLegend(false);
            chart.setAnimationDuration(0);
            manager.add(chart);
            manager.render();

            manager.handle(new PointerMoved(151.6f, 48.4f));
            assertEquals(0, chart.hoveredIndex());

            manager.handle(new PointerMoved(48.4f, 151.6f));
            assertEquals(1, chart.hoveredIndex());

            manager.handle(new PointerMoved(48.4f, 48.4f));
            assertEquals(2, chart.hoveredIndex());

            manager.handle(new PointerMoved(100, 100));
            assertEquals(-1, chart.hoveredIndex());

            manager.handle(new PointerMoved(100, 5));
            assertEquals(-1, chart.hoveredIndex());
        }
    }

    @Test
    void hoverLiftMatchesOnlyTheHoveredDrawnSegment() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            DonutChart chart = chart();
            chart.setBounds(new Rect(0, 0, 200, 200));
            chart.setShowLegend(false);
            chart.setAnimationDuration(0);
            manager.add(chart);
            manager.render();

            manager.handle(new PointerMoved(165.1f, 34.9f));
            assertEquals(-1, chart.hoveredIndex());

            manager.handle(new PointerMoved(151.6f, 48.4f));
            assertEquals(0, chart.hoveredIndex());

            manager.handle(new PointerMoved(165.1f, 34.9f));
            assertEquals(0, chart.hoveredIndex());

            manager.handle(new PointerMoved(34.9f, 165.1f));
            assertEquals(-1, chart.hoveredIndex());
        }
    }

    @Test
    void handlesFloatMaxValuesAsTwoHalfCircles() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            DonutChart chart = new DonutChart();
            chart.addSegment("A", Float.MAX_VALUE, RED);
            chart.addSegment("B", Float.MAX_VALUE, GREEN);
            chart.setBounds(new Rect(0, 0, 200, 200));
            chart.setShowLegend(false);
            chart.setAnimationDuration(0);
            List<Integer> clicks = new ArrayList<>();
            chart.setOnSegmentClick(clicks::add);
            manager.add(chart);
            manager.render();

            assertEquals((double) Float.MAX_VALUE * 2, chart.total(), 0.0);

            manager.handle(new PointerMoved(151.6f, 48.4f));
            assertEquals(0, chart.hoveredIndex());

            manager.handle(new PointerMoved(48.4f, 151.6f));
            assertEquals(1, chart.hoveredIndex());
            TextTooltip tooltip = assertInstanceOf(TextTooltip.class, chart.tooltip());
            assertTrue(tooltip.text().endsWith("(50%)"));

            click(manager, 48.4f, 151.6f);
            assertEquals(List.of(1), clicks);
        }
    }

    @Test
    void clicksReportTheSegmentIndex() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            DonutChart chart = chart();
            chart.setBounds(new Rect(0, 0, 200, 200));
            chart.setShowLegend(false);
            chart.setAnimationDuration(0);
            List<Integer> clicks = new ArrayList<>();
            chart.setOnSegmentClick(clicks::add);
            manager.add(chart);
            manager.render();

            click(manager, 151.6f, 48.4f);
            assertEquals(List.of(0), clicks);

            click(manager, 100, 100);
            assertEquals(List.of(0), clicks);
        }
    }

    @Test
    void animatesTheAppearanceAndCanReplayIt() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            DonutChart chart = chart();
            chart.setBounds(new Rect(0, 0, 220, 220));
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
    void hoverUpdatesTheTooltipText() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            DonutChart chart = chart();
            chart.setBounds(new Rect(0, 0, 200, 200));
            chart.setShowLegend(false);
            chart.setAnimationDuration(0);
            manager.add(chart);
            manager.render();

            manager.handle(new PointerMoved(151.6f, 48.4f));
            TextTooltip tooltip = assertInstanceOf(TextTooltip.class, chart.tooltip());
            assertEquals("A: 1 (33%)", tooltip.text());

            manager.handle(new PointerMoved(48.4f, 151.6f));
            assertEquals("B: 1 (33%)", tooltip.text());

            manager.handle(new PointerMoved(100, 100));
            assertEquals("", tooltip.text());
            assertNull(chart.tooltip());
        }
    }

    @Test
    void reenteringASegmentBeforeTheNextUpdateReusesTheShownTooltip() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            DonutChart chart = chart();
            chart.setBounds(new Rect(0, 0, 200, 200));
            chart.setShowLegend(false);
            chart.setAnimationDuration(0);
            manager.add(chart);
            manager.render();

            manager.handle(new PointerMoved(151.6f, 48.4f));
            manager.update(0.6f);
            manager.update(0.06f);
            TextTooltip tooltip = assertInstanceOf(TextTooltip.class, chart.tooltip());
            assertEquals("A: 1 (33%)", tooltip.text());
            assertNotNull(tooltip.context());

            manager.handle(new PointerMoved(100, 100));
            assertNull(chart.tooltip());

            manager.handle(new PointerMoved(151.6f, 48.4f));
            assertSame(tooltip, chart.tooltip());
            assertEquals("A: 1 (33%)", tooltip.text());

            manager.handle(new PointerMoved(151.96f, 130));
            assertNull(chart.tooltip());
            manager.handle(new PointerMoved(48.4f, 151.6f));
            assertSame(tooltip, chart.tooltip());
            assertEquals("B: 1 (33%)", tooltip.text());

            manager.update(0.06f);
            manager.render();
            assertNotNull(tooltip.context());
            assertEquals("B: 1 (33%)", tooltip.text());
        }
    }

    @Test
    void switchingThroughTheHoleDuringFadeOutKeepsTheTooltipUsable() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            DonutChart chart = chart();
            chart.setBounds(new Rect(0, 0, 200, 200));
            chart.setShowLegend(false);
            chart.setAnimationDuration(0);
            manager.add(chart);
            manager.render();

            manager.handle(new PointerMoved(151.6f, 48.4f));
            manager.update(0.6f);
            manager.update(0.3f);
            TextTooltip tooltip = assertInstanceOf(TextTooltip.class, chart.tooltip());
            assertNotNull(tooltip.context());

            manager.handle(new PointerMoved(100, 100));
            manager.update(0.06f);
            assertNotNull(tooltip.context());

            manager.handle(new PointerMoved(151.6f, 48.4f));
            assertSame(tooltip, chart.tooltip());
            assertEquals("A: 1 (33%)", tooltip.text());

            manager.handle(new PointerMoved(151.96f, 130));
            manager.handle(new PointerMoved(48.4f, 151.6f));
            manager.update(0.06f);
            manager.render();

            assertSame(tooltip, chart.tooltip());
            assertNotNull(tooltip.context());
            assertEquals("B: 1 (33%)", tooltip.text());

            manager.handle(new PointerMoved(600, 400));
            manager.update(0.06f);
            manager.update(0.07f);
            assertNull(tooltip.context());
        }
    }

    @Test
    void userTooltipSurvivesReentryAndDetachesAfterLeaving() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            DonutChart chart = chart();
            chart.setBounds(new Rect(0, 0, 200, 200));
            chart.setShowLegend(false);
            chart.setAnimationDuration(0);
            TextTooltip custom = new TextTooltip("Custom");
            chart.setTooltip(custom);
            manager.add(chart);
            manager.render();

            manager.handle(new PointerMoved(151.6f, 48.4f));
            manager.update(0.6f);
            manager.render();
            assertSame(custom, chart.tooltip());
            assertNotNull(custom.context());

            manager.handle(new PointerMoved(100, 100));
            manager.handle(new PointerMoved(151.6f, 48.4f));
            manager.update(0.06f);
            manager.render();
            assertSame(custom, chart.tooltip());
            assertNotNull(custom.context());
            assertEquals("Custom", custom.text());

            manager.handle(new PointerMoved(600, 400));
            manager.update(0.06f);
            manager.update(0.07f);
            assertNull(custom.context());
        }
    }

    @Test
    void measuresTheLegendWithFontMetrics() {
        FontData data = fontData();
        Font font = Font.load(data, 16);
        DonutChart chart = new DonutChart();
        chart.setFont(font);
        chart.addSegment("A", 1, RED);

        Size first = chart.measure(LayoutConstraints.loose(10000, 10000));
        chart.segments().getFirst().setLabel("A much longer label");
        Size second = chart.measure(LayoutConstraints.loose(10000, 10000));

        assertEquals(font.measure("A much longer label").width() - font.measure("A").width(),
                second.width() - first.width(), 0.01f);
        assertTrue(second.height() >= 180);
    }

    @Test
    void drawsSegmentsCenterLabelAndLegend() {
        FontData data = fontData();
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(manager.context().createFont(data, 16));
            DonutChart chart = chart();
            chart.setBounds(new Rect(0, 0, 420, 220));
            chart.setCenterLabel("Total 3");
            chart.setAnimationDuration(0);
            manager.add(chart);
            manager.render();

            assertTrue(backend.lastDrawList().commands().stream()
                    .anyMatch(MeshCommand.class::isInstance));
            long textCommands = backend.lastDrawList().commands().stream()
                    .filter(TextCommand.class::isInstance)
                    .count();
            assertTrue(textCommands >= 4);
        }
    }

    @Test
    void rejectsInvalidConfiguration() {
        DonutChart chart = new DonutChart();
        assertThrows(IllegalArgumentException.class,
                () -> chart.addSegment("A", -1, RED));
        assertThrows(IllegalArgumentException.class,
                () -> chart.addSegment("A", Float.NaN, RED));
        assertThrows(NullPointerException.class,
                () -> chart.addSegment(null, 1, RED));
        assertThrows(NullPointerException.class,
                () -> chart.addSegment("A", 1, null));
        assertThrows(NullPointerException.class, () -> chart.removeSegment(null));
        assertThrows(IllegalArgumentException.class, () -> chart.setDiameter(0));
        assertThrows(IllegalArgumentException.class, () -> chart.setInnerRadiusRatio(-0.1f));
        assertThrows(IllegalArgumentException.class, () -> chart.setInnerRadiusRatio(1));
        assertThrows(IllegalArgumentException.class, () -> chart.setAnimationDuration(-1));
        assertThrows(NullPointerException.class, () -> chart.setAnimationEasing(null));
        assertThrows(NullPointerException.class, () -> chart.setCenterLabel(null));

        DonutChart.Segment segment = chart.addSegment("A", 1, RED);
        assertThrows(IllegalArgumentException.class, () -> segment.setValue(-1));
        assertThrows(IllegalArgumentException.class, () -> segment.setValue(Float.NaN));
        assertThrows(NullPointerException.class, () -> segment.setLabel(null));
        assertThrows(NullPointerException.class, () -> segment.setColor(null));
        assertEquals(1, chart.total(), 0.001f);
    }

    private static DonutChart chart() {
        DonutChart chart = new DonutChart();
        chart.addSegment("A", 1, RED);
        chart.addSegment("B", 1, GREEN);
        chart.addSegment("C", 1, BLUE);
        return chart;
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

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }
}
