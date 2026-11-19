package com.lixui.widget.container;

import com.lixui.widget.testsupport.TestViewports;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.LayoutConstraints;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.platform.Cursor;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.Viewport;
import com.lixui.widget.testsupport.FixedWidget;
import com.lixui.widget.testsupport.TestBackend;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SplitPanelTest {
    private static final Viewport VIEWPORT = TestViewports.HIDPI;

    @Test
    void measuresPanesAndDivider() {
        SplitPanel panel = new SplitPanel();
        panel.setPadding(2);
        panel.setDividerThickness(6);
        panel.setFirst(new FixedWidget(40, 20));
        panel.setSecond(new FixedWidget(60, 30));

        Size horizontal = panel.measure(LayoutConstraints.loose(500, 500));
        assertEquals(110, horizontal.width(), 0.001f);
        assertEquals(34, horizontal.height(), 0.001f);

        panel.setOrientation(SplitPanel.Orientation.VERTICAL);
        Size vertical = panel.measure(LayoutConstraints.loose(500, 500));
        assertEquals(64, vertical.width(), 0.001f);
        assertEquals(60, vertical.height(), 0.001f);
    }

    @Test
    void laysOutPanesForHorizontalOrientation() {
        SplitPanel panel = new SplitPanel();
        panel.setPadding(4);
        panel.setDividerThickness(4);
        panel.setDividerPosition(0.25f);
        FixedWidget first = new FixedWidget(30, 20);
        FixedWidget second = new FixedWidget(50, 40);
        panel.setFirst(first);
        panel.setSecond(second);

        panel.layout(new Rect(0, 0, 304, 100));

        assertEquals(new Rect(4, 4, 73, 92), first.bounds());
        assertEquals(new Rect(81, 4, 219, 92), second.bounds());
        assertEquals(new Rect(77, 4, 4, 92), divider(panel).bounds());
    }

    @Test
    void laysOutPanesForVerticalOrientation() {
        SplitPanel panel = new SplitPanel(SplitPanel.Orientation.VERTICAL);
        panel.setPadding(4);
        panel.setDividerThickness(4);
        panel.setDividerPosition(0.5f);
        FixedWidget first = new FixedWidget(30, 20);
        FixedWidget second = new FixedWidget(50, 40);
        panel.setFirst(first);
        panel.setSecond(second);

        panel.layout(new Rect(0, 0, 100, 304));

        assertEquals(new Rect(4, 4, 92, 146), first.bounds());
        assertEquals(new Rect(4, 154, 92, 146), second.bounds());
        assertEquals(new Rect(4, 150, 92, 4), divider(panel).bounds());
    }

    @Test
    void dragsTheDividerHorizontally() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        SplitPanel panel = new SplitPanel();
        panel.setDividerThickness(8);
        panel.setBounds(new Rect(0, 0, 400, 200));
        FixedWidget first = new FixedWidget(10, 10);
        FixedWidget second = new FixedWidget(10, 10);
        panel.setFirst(first);
        panel.setSecond(second);
        manager.add(panel);
        manager.render();
        assertEquals(196, first.bounds().width(), 0.001f);

        manager.handle(new PointerMoved(200, 100));
        manager.layout();
        assertEquals(Cursor.RESIZE_HORIZONTAL, manager.context().cursor());

        manager.handle(new PointerPressed(200, 100, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(250, 100));
        manager.layout();

        assertEquals(246, first.bounds().width(), 0.001f);
        assertEquals(new Rect(254, 0, 146, 200), second.bounds());
        assertEquals(new Rect(246, 0, 8, 200), divider(panel).bounds());

        manager.handle(new PointerReleased(250, 100, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void dragsTheDividerVertically() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        SplitPanel panel = new SplitPanel(SplitPanel.Orientation.VERTICAL);
        panel.setDividerThickness(10);
        panel.setBounds(new Rect(0, 0, 300, 400));
        FixedWidget first = new FixedWidget(10, 10);
        FixedWidget second = new FixedWidget(10, 10);
        panel.setFirst(first);
        panel.setSecond(second);
        manager.add(panel);
        manager.render();
        assertEquals(195, first.bounds().height(), 0.001f);

        manager.handle(new PointerMoved(150, 200));
        manager.layout();
        assertEquals(Cursor.RESIZE_VERTICAL, manager.context().cursor());

        manager.handle(new PointerPressed(150, 200, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(150, 240));
        manager.layout();

        assertEquals(235, first.bounds().height(), 0.001f);
        assertEquals(new Rect(0, 245, 300, 155), second.bounds());
        assertEquals(new Rect(0, 235, 300, 10), divider(panel).bounds());

        manager.handle(new PointerReleased(150, 240, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void respectsTheMinimumPaneSize() {
        SplitPanel panel = new SplitPanel();
        panel.setDividerThickness(4);
        panel.setDividerPosition(0.1f);
        panel.setMinimumPaneSize(60);
        FixedWidget first = new FixedWidget(10, 10);
        FixedWidget second = new FixedWidget(10, 10);
        panel.setFirst(first);
        panel.setSecond(second);

        panel.layout(new Rect(0, 0, 300, 100));
        assertEquals(60, first.bounds().width(), 0.001f);
        assertEquals(236, second.bounds().width(), 0.001f);

        panel.setDividerPosition(0.95f);
        panel.layout(new Rect(0, 0, 300, 100));
        assertEquals(236, first.bounds().width(), 0.001f);
        assertEquals(60, second.bounds().width(), 0.001f);
    }

    @Test
    void aSingleVisiblePaneFillsTheContentArea() {
        SplitPanel panel = new SplitPanel();
        panel.setPadding(5);
        panel.setDividerThickness(10);
        FixedWidget first = new FixedWidget(10, 10);
        FixedWidget second = new FixedWidget(10, 10);
        panel.setFirst(first);
        panel.setSecond(second);
        second.setVisible(false);

        panel.layout(new Rect(0, 0, 200, 100));

        assertEquals(new Rect(5, 5, 190, 90), first.bounds());
        assertFalse(divider(panel).isVisible());
    }

    @Test
    void drawsTheDividerAndHighlightsItOnHover() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        SplitPanel panel = new SplitPanel();
        panel.setDividerThickness(6);
        panel.setBounds(new Rect(0, 0, 200, 100));
        panel.setFirst(new FixedWidget(10, 10));
        panel.setSecond(new FixedWidget(10, 10));
        manager.add(panel);

        manager.render();
        RoundedRectCommand line = assertInstanceOf(RoundedRectCommand.class, singleCommand(backend));
        assertEquals(new Rect(99.5f, 0, 1, 100), line.bounds());
        assertEquals(manager.context().theme().border(), line.color());

        manager.handle(new PointerMoved(100, 50));
        manager.render();
        line = assertInstanceOf(RoundedRectCommand.class, singleCommand(backend));
        assertEquals(manager.context().theme().textMuted(), line.color());
    }

    @Test
    void rejectsInvalidConfiguration() {
        SplitPanel panel = new SplitPanel();
        Widget widget = new FixedWidget(10, 10);
        assertThrows(NullPointerException.class, () -> panel.setFirst(null));
        assertThrows(NullPointerException.class, () -> panel.setSecond(null));
        assertThrows(NullPointerException.class, () -> new SplitPanel(null));
        assertThrows(NullPointerException.class, () -> panel.setOrientation(null));
        panel.setFirst(widget);
        assertThrows(IllegalArgumentException.class, () -> panel.setSecond(widget));
        assertThrows(IllegalArgumentException.class, () -> panel.setDividerThickness(0));
        assertThrows(IllegalArgumentException.class, () -> panel.setDividerThickness(-1));
        assertThrows(IllegalArgumentException.class,
                () -> panel.setDividerThickness(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> panel.setMinimumPaneSize(-1));
        assertThrows(IllegalArgumentException.class, () -> panel.setMinimumPaneSize(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> panel.setDividerPosition(Float.NaN));
    }

    private static Widget divider(SplitPanel panel) {
        return panel.children().get(panel.children().size() - 1);
    }

    private static DrawCommand singleCommand(TestBackend backend) {
        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertEquals(1, commands.size());
        return commands.get(0);
    }
}
