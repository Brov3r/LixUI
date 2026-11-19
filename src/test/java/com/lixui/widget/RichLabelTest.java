package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.FontFamily;
import com.lixui.text.FontStyle;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.SystemFonts;
import com.lixui.text.TextDecoration;
import com.lixui.text.TextShadow;
import com.lixui.text.VerticalAlignment;
import com.lixui.widget.testsupport.TestBackend;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichLabelTest {
    private static final Viewport VIEWPORT = new Viewport(800, 600, 800, 600);

    @Test
    void measuresMixedFontRuns() {
        try (WidgetManager manager = manager(new TestBackend())) {
            Font small = font(manager, 16, FontStyle.REGULAR);
            Font large = font(manager, 24, FontStyle.REGULAR);
            RichLabel label = new RichLabel();
            label.add("Hi ", Color.WHITE, small);
            label.add("big", Color.rgb(0, 255, 0), large);

            Size measured = label.measure(LayoutConstraints.loose(1000, 1000));

            float expected = small.measure("Hi").width() + small.measure(" ").width()
                    + large.measure("big").width();
            assertEquals(expected, measured.width(), 0.01f);
            assertEquals(large.lineHeight(), measured.height(), 0.01f);
        }
    }

    @Test
    void wrapsBetweenWords() {
        try (WidgetManager manager = manager(new TestBackend())) {
            Font font = font(manager, 16, FontStyle.REGULAR);
            RichLabel label = new RichLabel();
            label.setFont(font);
            label.add("alpha beta gamma", Color.WHITE);
            float limit = Math.max(font.measure("alpha").width(),
                    Math.max(font.measure("beta").width(), font.measure("gamma").width())) + 0.5f;

            Size measured = label.measure(LayoutConstraints.loose(limit, 1000));

            assertTrue(measured.width() <= limit);
            assertEquals(3 * font.lineHeight(), measured.height(), 0.01f);
        }
    }

    @Test
    void justifiesWrappedRunsAcrossTheAvailableWidth() {
        try (WidgetManager manager = manager(new TestBackend())) {
            Font font = font(manager, 16, FontStyle.REGULAR);
            RichLabel label = new RichLabel();
            label.setFont(font);
            label.setAlignment(HorizontalAlignment.JUSTIFY);
            label.add("a b a b a b a b", Color.WHITE);
            float limit = font.measure("a b").width() + 0.5f;

            Size measured = label.measure(LayoutConstraints.loose(limit, 1000));

            assertEquals(limit, measured.width(), 0.01f);
            assertTrue(measured.height() > 2 * font.lineHeight());
        }
    }

    @Test
    void drawsShadowsDecorationsAndSpanColors() {
        TestBackend backend = new TestBackend();
        Color red = Color.rgb(255, 0, 0);
        Color black = Color.rgb(0, 0, 0);
        try (WidgetManager manager = manager(backend)) {
            Font font = font(manager, 16, FontStyle.REGULAR);
            RichLabel label = new RichLabel();
            label.setAutoSize(false);
            label.setBounds(new Rect(0, 0, 300, 100));
            label.add("A", red, font, TextDecoration.UNDERLINE);
            label.add(new RichLabel.Span("B", Color.WHITE, font, FontStyle.REGULAR,
                    TextDecoration.NONE, new TextShadow(1, 1, black)));
            manager.add(label);

            manager.render();

            List<DrawCommand> commands = backend.lastDrawList().commands();
            assertEquals(4, commands.size());
            assertEquals(black, assertInstanceOf(TextCommand.class,
                    commands.get(0)).colors().getFirst());
            assertInstanceOf(RoundedRectCommand.class, commands.get(1));
            assertEquals(red, assertInstanceOf(TextCommand.class,
                    commands.get(2)).colors().getFirst());
            assertEquals(Color.WHITE, assertInstanceOf(TextCommand.class,
                    commands.get(3)).colors().getFirst());
        }
    }

    @Test
    void honorsNewlinesAndVerticalAlignment() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Font font = font(manager, 16, FontStyle.REGULAR);
            RichLabel label = new RichLabel();
            label.setAutoSize(false);
            label.setBounds(new Rect(0, 0, 100, 200));
            label.setVerticalAlignment(VerticalAlignment.CENTER);
            label.add("A\nB", Color.WHITE, font);
            manager.add(label);

            manager.render();

            List<DrawCommand> commands = backend.lastDrawList().commands();
            assertEquals(2, commands.size());
            float top = (200 - 2 * font.lineHeight()) / 2;
            float expectedFirst = font.layout("A", 0, top,
                    HorizontalAlignment.LEFT, VerticalAlignment.TOP).getFirst().bounds().y();
            float expectedSecond = font.layout("B", 0, top + font.lineHeight(),
                    HorizontalAlignment.LEFT, VerticalAlignment.TOP).getFirst().bounds().y();
            assertEquals(expectedFirst, assertInstanceOf(TextCommand.class,
                    commands.get(0)).glyphs().getFirst().bounds().y(), 1.0f);
            assertEquals(expectedSecond, assertInstanceOf(TextCommand.class,
                    commands.get(1)).glyphs().getFirst().bounds().y(), 1.0f);
        }
    }

    @Test
    void honorsHorizontalAlignment() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Font font = font(manager, 16, FontStyle.REGULAR);
            RichLabel label = new RichLabel();
            label.setAutoSize(false);
            label.setBounds(new Rect(0, 0, 100, 100));
            label.setAlignment(HorizontalAlignment.RIGHT);
            label.add("A", Color.WHITE, font);
            manager.add(label);

            manager.render();

            float expected = 100 - font.measure("A").width();
            assertEquals(expected, assertInstanceOf(TextCommand.class,
                    backend.lastDrawList().commands().getFirst()).glyphs().getFirst().bounds().x(),
                    1.0f);
        }
    }

    @Test
    void spanStylesResolveThroughTheFontFamily() {
        try (WidgetManager manager = manager(new TestBackend())) {
            Font regular = font(manager, 16, FontStyle.REGULAR);
            Font boldLarge = font(manager, 24, FontStyle.BOLD);
            RichLabel label = new RichLabel();
            label.setFontFamily(new FontFamily(regular, boldLarge, null, null));
            label.add("bold", Color.WHITE, FontStyle.BOLD, TextDecoration.NONE);

            Size measured = label.measure(LayoutConstraints.loose(500, 500));

            assertEquals(boldLarge.lineHeight(), measured.height(), 0.01f);
        }
    }

    @Test
    void rejectsInvalidArguments() {
        RichLabel label = new RichLabel();
        assertTrue(label.isAutoSize());
        assertThrows(UnsupportedOperationException.class, () -> label.spans().clear());
        assertThrows(NullPointerException.class, () -> label.add((RichLabel.Span) null));
        assertThrows(NullPointerException.class, () -> label.add(null, null));
        assertThrows(NullPointerException.class, () -> label.setAlignment(null));
        assertThrows(NullPointerException.class, () -> label.setVerticalAlignment(null));
        assertThrows(NullPointerException.class,
                () -> new RichLabel.Span(null, Color.WHITE, null, FontStyle.REGULAR,
                        TextDecoration.NONE, null));
        assertThrows(NullPointerException.class,
                () -> new RichLabel.Span("x", Color.WHITE, null, null,
                        TextDecoration.NONE, null));
        assertThrows(NullPointerException.class,
                () -> new RichLabel.Span("x", Color.WHITE, null, FontStyle.REGULAR, null, null));

        label.add("x", Color.WHITE);
        label.clear();
        assertTrue(label.spans().isEmpty());
        assertTrue(label.measure(LayoutConstraints.loose(100, 100)).width() == 0);
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
