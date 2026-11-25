package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.MeshCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BadgeTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void measuresTextWithPadding() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Font font = font(manager);
            manager.setDefaultFont(font);
            Badge badge = new Badge("12");
            badge.setPadding(5);
            manager.add(badge);

            Size measured = badge.measure(LayoutConstraints.loose(200, 100));
            Size text = font.measure("12");

            assertEquals(text.width() + 10, measured.width(), 0.001f);
            assertEquals(text.height() + 10, measured.height(), 0.001f);
        }
    }

    @Test
    void emptyTextMeasuresAsTheConfiguredDot() {
        Badge badge = new Badge();
        badge.setDotSize(10);

        Size measured = badge.measure(LayoutConstraints.loose(200, 100));

        assertEquals(10, measured.width(), 0.001f);
        assertEquals(10, measured.height(), 0.001f);
    }

    @Test
    void drawsThePillWithConfiguredColorsAndBorder() {
        TestBackend backend = new TestBackend();
        Color background = Color.rgb(30, 40, 50);
        Color text = Color.rgb(240, 240, 240);
        Color border = Color.rgb(90, 100, 110);
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            Badge badge = new Badge("Hi");
            badge.setBackgroundColor(background);
            badge.setTextColor(text);
            badge.setBorderColor(border);
            badge.setBorderWidth(2);
            badge.setAutoSize(false);
            badge.setBounds(new Rect(10, 20, 80, 40));
            manager.add(badge);

            manager.render();

            List<DrawCommand> commands = backend.lastDrawList().commands();
            assertEquals(3, commands.size());
            RoundedRectCommand fill = assertInstanceOf(RoundedRectCommand.class, commands.get(0));
            assertEquals(background, fill.color());
            assertEquals(20, fill.radius(), 0.001f);
            assertInstanceOf(MeshCommand.class, commands.get(1));
            TextCommand label = assertInstanceOf(TextCommand.class, commands.get(2));
            assertEquals(text, label.colors().getFirst());
        }
    }

    @Test
    void emptyBadgeDrawsADotAndStaysPointerTransparent() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Badge badge = new Badge();
            badge.setAutoSize(false);
            badge.setBounds(new Rect(0, 0, 12, 12));
            manager.add(badge);

            manager.render();

            assertEquals(1, backend.lastDrawList().commands().size());
            assertInstanceOf(MeshCommand.class, backend.lastDrawList().commands().getFirst());
            assertNull(badge.hitTest(6, 6));
        }
    }

    @Test
    void rejectsInvalidArguments() {
        Badge badge = new Badge();
        assertThrows(NullPointerException.class, () -> new Badge(null));
        assertThrows(NullPointerException.class, () -> badge.setText(null));
        assertThrows(IllegalArgumentException.class, () -> badge.setPadding(-1));
        assertThrows(IllegalArgumentException.class, () -> badge.setPadding(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> badge.setDotSize(0));
        assertThrows(IllegalArgumentException.class, () -> badge.setDotSize(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> badge.setBorderWidth(-1));
    }

    private static Font font(WidgetManager manager) {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        return manager.context().createFont(data.get(), 16);
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }
}
