package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.graphics.GradientStop;
import com.lixui.graphics.Paint;
import com.lixui.input.InputState;
import com.lixui.render.MeshVertex;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.MaskBeginCommand;
import com.lixui.render.command.MaskEndCommand;
import com.lixui.render.command.MeshCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.command.ShapeCommand;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressBarTest {
    @Test
    void animatesTowardsTheTargetAndClampsValues() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            ProgressBar bar = new ProgressBar();
            bar.setBounds(new Rect(0, 0, 100, 10));
            manager.add(bar);
            manager.render();

            bar.setValue(0.8f);
            manager.update(0.05f);
            float middle = bar.animatedProgress();
            assertTrue(middle > 0 && middle < 0.8f);
            manager.update(1);
            assertEquals(0.8f, bar.animatedProgress(), 0.001f);

            bar.setValue(2);
            assertEquals(1, bar.value(), 0.001f);
            bar.setValue(-1);
            assertEquals(0, bar.value(), 0.001f);

            bar.setRange(10, 20);
            assertEquals(10, bar.value(), 0.001f);
        }
    }

    @Test
    void drawsFilledTrackAndProgress() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            ProgressBar bar = new ProgressBar(0.5f);
            bar.setBounds(new Rect(0, 0, 100, 10));
            bar.setAnimationRate(0);
            bar.setShape(ProgressBar.Shape.ROUNDED);
            bar.setCornerRadius(5);
            manager.add(bar);
            manager.render();

            List<RoundedRectCommand> commands = backend.lastDrawList().commands().stream()
                    .filter(RoundedRectCommand.class::isInstance)
                    .map(RoundedRectCommand.class::cast)
                    .toList();
            assertEquals(2, commands.size());
            assertTrue(commands.stream().anyMatch(command -> command.bounds().width() == 100));
            assertTrue(commands.stream().anyMatch(command -> command.bounds().width() == 50));
            assertEquals(5, commands.getFirst().radius(), 0.001f);
        }
    }

    @Test
    void drawsAFillGradientAcrossTheIndicator() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            ProgressBar bar = new ProgressBar(0.5f);
            bar.setBounds(new Rect(0, 0, 100, 10));
            bar.setAnimationRate(0);
            bar.setShape(ProgressBar.Shape.ROUNDED);
            bar.setCornerRadius(5);
            bar.setFillGradient(Color.rgb(255, 0, 0), Color.rgb(0, 0, 255));
            manager.add(bar);
            manager.render();

            assertEquals(Color.rgb(255, 0, 0), bar.fillGradientStart());
            assertEquals(Color.rgb(0, 0, 255), bar.fillGradientEnd());
            MeshCommand fill = backend.lastDrawList().commands().stream()
                    .filter(MeshCommand.class::isInstance)
                    .map(MeshCommand.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertTrue(fill.vertices().stream().map(MeshVertex::color).distinct().count() > 1);

            bar.clearFillGradient();
            assertNull(bar.fillGradientStart());
            assertNull(bar.fillGradientEnd());
        }
    }

    @Test
    void ringFeatherCarriesItsOwnColorInsteadOfBlack() {
        TestBackend backend = new TestBackend();
        backend.setAnalytic(true);
        try (WidgetManager manager = manager(backend)) {
            ProgressBar bar = new ProgressBar(0.61f);
            bar.setBounds(new Rect(0, 0, 64, 64));
            bar.setAnimationRate(0);
            bar.setShape(ProgressBar.Shape.CIRCLE);
            bar.setThickness(8);
            bar.setFillColor(Color.hex("#E39A5B"));
            manager.add(bar);
            manager.render();

            List<Paint.RadialGradient> gradients = backend.lastDrawList().commands().stream()
                    .filter(ShapeCommand.class::isInstance)
                    .map(ShapeCommand.class::cast)
                    .map(ShapeCommand::paint)
                    .filter(Paint.RadialGradient.class::isInstance)
                    .map(Paint.RadialGradient.class::cast)
                    .toList();

            assertFalse(gradients.isEmpty());
            for (Paint.RadialGradient gradient : gradients) {
                Color edge = gradient.stops().getLast().color();
                for (GradientStop stop : gradient.stops()) {
                    if (stop.color().a() == 0) {
                        assertEquals(edge.toRgb(), stop.color().toRgb());
                    }
                }
            }
        }
    }

    @Test
    void drawsOutlineBarWithStrokes() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            ProgressBar bar = new ProgressBar(0.5f);
            bar.setBounds(new Rect(0, 0, 100, 10));
            bar.setAnimationRate(0);
            bar.setAppearance(ProgressBar.Appearance.OUTLINE);
            bar.setThickness(2);
            manager.add(bar);
            manager.render();

            assertTrue(backend.lastDrawList().commands().stream()
                    .filter(MeshCommand.class::isInstance).count() >= 2);
        }
    }

    @Test
    void drawsPerimeterProgressAroundRectangles() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            ProgressBar bar = new ProgressBar(0.5f);
            bar.setBounds(new Rect(0, 0, 120, 60));
            bar.setAnimationRate(0);
            bar.setShape(ProgressBar.Shape.ROUNDED);
            bar.setCornerRadius(12);
            bar.setAppearance(ProgressBar.Appearance.PERIMETER);
            bar.setThickness(4);
            manager.add(bar);
            manager.render();

            assertTrue(backend.lastDrawList().commands().stream()
                    .filter(MeshCommand.class::isInstance).count() >= 2);
        }
    }

    @Test
    void strokesPerimeterProgressAsOneBand() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            ProgressBar bar = new ProgressBar(0.5f);
            bar.setBounds(new Rect(0, 0, 120, 60));
            bar.setAnimationRate(0);
            bar.setShape(ProgressBar.Shape.ROUNDED);
            bar.setCornerRadius(12);
            bar.setAppearance(ProgressBar.Appearance.PERIMETER);
            bar.setThickness(4);
            bar.setFillColor(Color.WHITE.withAlpha(0.6f));
            manager.add(bar);
            manager.render();

            List<DrawCommand> commands = backend.lastDrawList().commands();
            assertEquals(2, commands.stream()
                    .filter(MeshCommand.class::isInstance).count());
            assertTrue(commands.stream()
                    .noneMatch(RoundedRectCommand.class::isInstance));
        }
    }

    @Test
    void skipsPerimeterProgressBelowThePathTolerance() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            ProgressBar bar = new ProgressBar(1e-9f);
            bar.setBounds(new Rect(0, 0, 120, 60));
            bar.setAnimationRate(0);
            bar.setShape(ProgressBar.Shape.ROUNDED);
            bar.setAppearance(ProgressBar.Appearance.PERIMETER);
            bar.setThickness(4);
            manager.add(bar);
            manager.render();

            assertEquals(1, backend.lastDrawList().commands().stream()
                    .filter(MeshCommand.class::isInstance).count());
        }
    }

    @Test
    void drawsPolygonPerimeter() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            ProgressBar bar = new ProgressBar(0.5f);
            bar.setBounds(new Rect(0, 0, 100, 100));
            bar.setAnimationRate(0);
            bar.setShape(ProgressBar.Shape.POLYGON);
            bar.setPolygon(ProgressBar.regularPolygon(6));
            bar.setAppearance(ProgressBar.Appearance.PERIMETER);
            bar.setThickness(4);
            manager.add(bar);
            manager.render();

            assertTrue(backend.lastDrawList().commands().stream()
                    .filter(MeshCommand.class::isInstance).count() >= 2);
        }
    }

    @Test
    void drawsRadialCircleFill() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            ProgressBar bar = new ProgressBar(0.5f);
            bar.setBounds(new Rect(0, 0, 100, 100));
            bar.setAnimationRate(0);
            bar.setShape(ProgressBar.Shape.CIRCLE);
            bar.setAppearance(ProgressBar.Appearance.RADIAL);
            manager.add(bar);
            manager.render();

            assertEquals(1, backend.lastDrawList().commands().stream()
                    .filter(MaskBeginCommand.class::isInstance).count());
            assertEquals(1, backend.lastDrawList().commands().stream()
                    .filter(MaskEndCommand.class::isInstance).count());
        }
    }

    @Test
    void drawsRadialPolygonFill() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            ProgressBar bar = new ProgressBar(0.5f);
            bar.setBounds(new Rect(0, 0, 100, 100));
            bar.setAnimationRate(0);
            bar.setShape(ProgressBar.Shape.POLYGON);
            bar.setPolygon(ProgressBar.regularPolygon(6));
            bar.setAppearance(ProgressBar.Appearance.RADIAL);
            manager.add(bar);
            manager.render();

            assertEquals(2, backend.lastDrawList().commands().stream()
                    .filter(MaskBeginCommand.class::isInstance).count());
            assertEquals(2, backend.lastDrawList().commands().stream()
                    .filter(MaskEndCommand.class::isInstance).count());
        }
    }

    @Test
    void drawsCircularProgressWithAMaskedArc() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            ProgressBar bar = new ProgressBar(0.5f);
            bar.setBounds(new Rect(0, 0, 100, 100));
            bar.setAnimationRate(0);
            bar.setShape(ProgressBar.Shape.CIRCLE);
            bar.setThickness(10);
            manager.add(bar);
            manager.render();

            assertEquals(1, backend.lastDrawList().commands().stream()
                    .filter(MaskBeginCommand.class::isInstance).count());
            assertEquals(1, backend.lastDrawList().commands().stream()
                    .filter(MaskEndCommand.class::isInstance).count());
        }
    }

    @Test
    void drawsSubPixelCircularProgress() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            ProgressBar bar = new ProgressBar(0.002f);
            bar.setBounds(new Rect(0, 0, 100, 100));
            bar.setAnimationRate(0);
            bar.setShape(ProgressBar.Shape.CIRCLE);
            manager.add(bar);
            manager.render();

            bar.setAppearance(ProgressBar.Appearance.RADIAL);
            bar.setValue(0.0001f);
            manager.render();

            assertEquals(1, backend.lastDrawList().commands().stream()
                    .filter(MaskBeginCommand.class::isInstance).count());
            assertEquals(1, backend.lastDrawList().commands().stream()
                    .filter(MaskEndCommand.class::isInstance).count());
        }
    }

    @Test
    void rejectsInvalidSettings() {
        ProgressBar bar = new ProgressBar();
        assertThrows(IllegalArgumentException.class, () -> bar.setValue(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> bar.setRange(10, 10));
        assertThrows(IllegalArgumentException.class, () -> bar.setRange(Float.NaN, 10));
        assertThrows(IllegalArgumentException.class, () -> bar.setAnimationRate(-1));
        assertThrows(IllegalArgumentException.class, () -> bar.setThickness(0));
        assertThrows(IllegalArgumentException.class, () -> bar.setCornerRadius(-1));
        assertThrows(NullPointerException.class, () -> bar.setShape(null));
        assertThrows(NullPointerException.class, () -> bar.setAppearance(null));
        assertThrows(IllegalArgumentException.class,
                () -> bar.setPolygon(List.of(new Point(0, 0), new Point(1, 0))));
        assertThrows(IllegalArgumentException.class,
                () -> bar.setPolygon(List.of(new Point(0, 0), new Point(2, 0), new Point(0, 1))));
        assertThrows(NullPointerException.class, () -> bar.setPolygon(null));
        assertThrows(IllegalArgumentException.class, () -> ProgressBar.regularPolygon(2));
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(TestViewports.LOGICAL);
        return manager;
    }
}
