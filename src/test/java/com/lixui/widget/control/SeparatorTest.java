package com.lixui.widget.control;

import com.lixui.widget.testsupport.TestViewports;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.LayoutConstraints;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.Viewport;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.testsupport.FixedWidget;
import com.lixui.widget.testsupport.TestBackend;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeparatorTest {
    private static final Viewport VIEWPORT = TestViewports.HIDPI;

    @Test
    void horizontalSeparatorFillsTheAvailableWidth() {
        Separator separator = new Separator();
        separator.setThickness(3);

        assertEquals(Separator.Orientation.HORIZONTAL, separator.orientation());
        assertEquals(200, separator.measure(LayoutConstraints.loose(200, 100)).width(), 0.001f);
        assertEquals(3, separator.measure(LayoutConstraints.loose(200, 100)).height(), 0.001f);
    }

    @Test
    void verticalSeparatorFillsTheAvailableHeight() {
        Separator separator = new Separator(Separator.Orientation.VERTICAL);
        separator.setThickness(2);

        assertEquals(2, separator.measure(LayoutConstraints.loose(200, 100)).width(), 0.001f);
        assertEquals(100, separator.measure(LayoutConstraints.loose(200, 100)).height(), 0.001f);

        separator.setOrientation(Separator.Orientation.HORIZONTAL);
        assertEquals(200, separator.measure(LayoutConstraints.loose(200, 100)).width(), 0.001f);
        assertEquals(2, separator.measure(LayoutConstraints.loose(200, 100)).height(), 0.001f);
    }

    @Test
    void drawsAHorizontalLineCenteredAcrossTheBounds() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        Separator separator = new Separator();
        separator.setBounds(new Rect(10, 20, 200, 5));
        manager.add(separator);

        manager.render();

        RoundedRectCommand line = assertInstanceOf(RoundedRectCommand.class, singleCommand(backend));
        assertEquals(new Rect(10, 22, 200, 1), line.bounds());
        assertEquals(0, line.radius(), 0.0001f);
        WidgetStyle style = manager.context().theme().controlStyle();
        assertEquals(style.border(WidgetState.NORMAL), line.color());
    }

    @Test
    void drawsAVerticalLineCenteredAcrossTheBounds() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        Separator separator = new Separator(Separator.Orientation.VERTICAL);
        separator.setThickness(2);
        separator.setBounds(new Rect(10, 20, 5, 100));
        manager.add(separator);

        manager.render();

        RoundedRectCommand line = assertInstanceOf(RoundedRectCommand.class, singleCommand(backend));
        assertEquals(new Rect(11.5f, 20, 2, 100), line.bounds());
    }

    @Test
    void explicitColorOverridesTheStyleBorder() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        Separator separator = new Separator();
        Color color = Color.rgb(255, 0, 0);
        separator.setColor(color);
        separator.setBounds(new Rect(0, 0, 100, 1));
        manager.add(separator);

        manager.render();

        RoundedRectCommand line = assertInstanceOf(RoundedRectCommand.class, singleCommand(backend));
        assertEquals(color, line.color());
    }

    @Test
    void clampsTheLineToTheAssignedBounds() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        Separator separator = new Separator();
        separator.setThickness(4);
        separator.setBounds(new Rect(0, 0, 100, 0.5f));
        manager.add(separator);

        manager.render();

        RoundedRectCommand line = assertInstanceOf(RoundedRectCommand.class, singleCommand(backend));
        assertEquals(new Rect(0, 0, 100, 0.5f), line.bounds());
    }

    @Test
    void boxesStretchTheSeparatorAcrossTheCrossAxis() {
        VBox vertical = new VBox();
        Separator horizontal = new Separator();
        vertical.add(new FixedWidget(100, 20)).add(horizontal).add(new FixedWidget(50, 10));
        vertical.layout(new Rect(0, 0, 200, 100));
        assertEquals(new Rect(0, 20, 200, 1), horizontal.bounds());

        HBox horizontalBox = new HBox();
        Separator verticalSeparator = new Separator(Separator.Orientation.VERTICAL);
        verticalSeparator.setThickness(2);
        horizontalBox.add(new FixedWidget(20, 30)).add(verticalSeparator)
                .add(new FixedWidget(10, 30));
        horizontalBox.layout(new Rect(0, 0, 200, 100));
        assertEquals(new Rect(20, 0, 2, 100), verticalSeparator.bounds());
    }

    @Test
    void rejectsInvalidThickness() {
        Separator separator = new Separator();
        assertThrows(IllegalArgumentException.class, () -> separator.setThickness(0));
        assertThrows(IllegalArgumentException.class, () -> separator.setThickness(-1));
        assertThrows(IllegalArgumentException.class, () -> separator.setThickness(Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> separator.setThickness(Float.POSITIVE_INFINITY));
    }

    private static DrawCommand singleCommand(TestBackend backend) {
        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertEquals(1, commands.size());
        return commands.get(0);
    }
}
