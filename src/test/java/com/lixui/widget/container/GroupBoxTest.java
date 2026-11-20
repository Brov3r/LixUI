package com.lixui.widget.container;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.FontStyle;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.SystemFonts;
import com.lixui.text.TextGlyph;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.testsupport.FixedWidget;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroupBoxTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void drawsTheTitleOnTheUpperBorder() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Font font = font(manager, 16, FontStyle.REGULAR);
            manager.setDefaultFont(font);
            GroupBox box = new GroupBox("Options");
            box.setBounds(new Rect(0, 0, 200, 100));
            manager.add(box);

            manager.render();

            List<DrawCommand> commands = backend.lastDrawList().commands();
            RoundedRectCommand surface = commands.stream()
                    .filter(RoundedRectCommand.class::isInstance)
                    .map(RoundedRectCommand.class::cast)
                    .findFirst().orElseThrow();
            assertEquals(new Rect(0, 0, 200, 100), surface.bounds());
            assertEquals(manager.context().theme().surface(), surface.color());

            TextCommand text = commands.stream()
                    .filter(TextCommand.class::isInstance)
                    .map(TextCommand.class::cast)
                    .findFirst().orElseThrow();
            assertEquals(manager.context().theme().text(), text.colors().getFirst());

            float textWidth = Math.min(font.measure("Options").width(), 176);
            TextStyle style = new TextStyle(textWidth, 0, 1, TextOverflow.ELLIPSIS,
                    HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            TextGlyph expected = font.layout("Options", 12, 10, style).getFirst();
            assertEquals(expected.bounds().x(), text.glyphs().getFirst().bounds().x(), 1.0f);
            assertEquals(expected.bounds().y(), text.glyphs().getFirst().bounds().y(), 1.0f);
        }
    }

    @Test
    void anEmptyTitleDrawsNoText() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager, 16, FontStyle.REGULAR));
            GroupBox box = new GroupBox();
            box.setBounds(new Rect(0, 0, 200, 100));
            manager.add(box);

            manager.render();

            List<DrawCommand> commands = backend.lastDrawList().commands();
            assertEquals(2, commands.size());
            assertInstanceOf(RoundedRectCommand.class, commands.get(0));
        }
    }

    @Test
    void laysTheContentOutInsideTheFrame() {
        try (WidgetManager manager = manager(new TestBackend())) {
            GroupBox box = new GroupBox("Options");
            box.setTitleHeight(20);
            box.setContentPadding(10);
            box.setBounds(new Rect(10, 20, 200, 120));
            FixedWidget content = new FixedWidget(60, 30);
            box.setContent(content);
            manager.add(box);

            manager.layout();

            assertEquals(new Rect(10, 30, 180, 80), content.bounds());
            assertEquals(new Rect(0, 0, 200, 120), box.localBounds());
        }
    }

    @Test
    void measuresTheContentPlusTheTitleStripAndPadding() {
        GroupBox box = new GroupBox("Options");
        box.setTitleHeight(20);
        box.setContentPadding(8);
        box.setContent(new FixedWidget(100, 40));

        Size measured = box.measure(LayoutConstraints.loose(500, 500));

        assertEquals(116, measured.width(), 0.001f);
        assertEquals(76, measured.height(), 0.001f);
    }

    @Test
    void replacesAndClearsTheContent() {
        GroupBox box = new GroupBox();
        FixedWidget first = new FixedWidget(40, 20);
        FixedWidget second = new FixedWidget(60, 30);

        box.setContent(first);
        assertSame(first, box.content());
        assertSame(box, first.parent());

        box.setContent(second);
        assertSame(second, box.content());
        assertSame(box, second.parent());
        assertNull(first.parent());

        box.clearContent();
        assertNull(box.content());
        assertNull(second.parent());
    }

    @Test
    void rejectsInvalidArguments() {
        GroupBox box = new GroupBox("Title");
        assertThrows(NullPointerException.class, () -> new GroupBox(null));
        assertThrows(NullPointerException.class, () -> box.setTitle(null));
        assertThrows(IllegalArgumentException.class, () -> box.setTitleHeight(-1));
        assertThrows(IllegalArgumentException.class, () -> box.setTitleHeight(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> box.setTitlePadding(-1));
        assertThrows(IllegalArgumentException.class,
                () -> box.setTitlePadding(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> box.setTitleGap(-1));
        assertThrows(IllegalArgumentException.class, () -> box.setTitleGap(Float.NaN));
    }

    private static Font font(WidgetManager manager, float size, FontStyle style) {
        return manager.context().createFont(fontData(), size, style);
    }

    private static FontData fontData() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        return data.get();
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }
}
