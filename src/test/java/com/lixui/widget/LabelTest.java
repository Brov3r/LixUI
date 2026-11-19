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
import com.lixui.text.TextGlyph;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextShadow;
import com.lixui.text.VerticalAlignment;
import com.lixui.widget.testsupport.TestBackend;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelTest {
    private static final Viewport VIEWPORT = new Viewport(800, 600, 800, 600);

    @Test
    void autoSizesToItsTextThroughTheManager() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Font font = font(manager, 16, FontStyle.REGULAR);
            manager.setDefaultFont(font);
            Label label = new Label("Hello");
            manager.add(label);

            manager.layout();

            Size expected = font.measure("Hello");
            assertEquals(expected.width(), label.bounds().width(), 0.001f);
            assertEquals(expected.height(), label.bounds().height(), 0.001f);
            assertTrue(label.isAutoSize());
        }
    }

    @Test
    void wrapsAndTruncatesAtTheAvailableWidth() {
        try (WidgetManager manager = manager(new TestBackend())) {
            Font font = font(manager, 16, FontStyle.REGULAR);
            Label label = new Label("alpha beta gamma");
            label.setFont(font);
            float limit = Math.max(font.measure("alpha").width(),
                    Math.max(font.measure("beta").width(), font.measure("gamma").width())) + 1;

            Size wrapped = label.measure(LayoutConstraints.loose(limit, 1000));
            assertTrue(wrapped.width() <= limit + 0.001f);
            assertEquals(3 * font.lineHeight(), wrapped.height(), 0.001f);

            label.setMaxLines(1);
            label.setOverflow(TextOverflow.ELLIPSIS);

            Size truncated = label.measure(LayoutConstraints.loose(limit, 1000));
            assertTrue(truncated.width() <= limit + 0.001f);
            assertEquals(font.lineHeight(), truncated.height(), 0.001f);
        }
    }

    @Test
    void justifiesWrappedTextAcrossTheAvailableWidth() {
        try (WidgetManager manager = manager(new TestBackend())) {
            Font font = font(manager, 16, FontStyle.REGULAR);
            Label label = new Label("a b a b a b a b");
            label.setFont(font);
            label.setAlignment(HorizontalAlignment.JUSTIFY);
            float limit = font.measure("a b").width() + 1;

            Size measured = label.measure(LayoutConstraints.loose(limit, 1000));

            assertEquals(limit, measured.width(), 0.001f);
            assertTrue(measured.height() > 2 * font.lineHeight());
        }
    }

    @Test
    void drawsWithExplicitColorAndAlignment() {
        TestBackend backend = new TestBackend();
        Color red = Color.rgb(255, 0, 0);
        try (WidgetManager manager = manager(backend)) {
            Font font = font(manager, 16, FontStyle.REGULAR);
            manager.setDefaultFont(font);
            Label label = new Label("Hi");
            label.setAutoSize(false);
            label.setBounds(new Rect(0, 0, 200, 100));
            label.setAlignment(HorizontalAlignment.CENTER);
            label.setVerticalAlignment(VerticalAlignment.CENTER);
            label.setTextColor(red);
            manager.add(label);

            manager.render();

            List<DrawCommand> commands = backend.lastDrawList().commands();
            assertEquals(1, commands.size());
            TextCommand text = assertInstanceOf(TextCommand.class, commands.getFirst());
            assertEquals(red, text.colors().getFirst());

            TextGlyph expected = font.layout("Hi", 100, 50,
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER).getFirst();
            assertEquals(expected.bounds().x(), text.glyphs().getFirst().bounds().x(), 1.0f);
            assertEquals(expected.bounds().y(), text.glyphs().getFirst().bounds().y(), 1.0f);
        }
    }

    @Test
    void usesTheStyleTextColorWhenNoExplicitColorIsSet() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager, 16, FontStyle.REGULAR));
            Label label = new Label("Hi");
            label.setBounds(new Rect(0, 0, 100, 40));
            manager.add(label);

            manager.render();

            TextCommand text = assertInstanceOf(TextCommand.class,
                    backend.lastDrawList().commands().getFirst());
            assertEquals(manager.context().theme().text(), text.colors().getFirst());
        }
    }

    @Test
    void appliesFamilyStyleDecorationsAndShadow() {
        TestBackend backend = new TestBackend();
        Color shadowColor = Color.rgb(0, 0, 0);
        try (WidgetManager manager = manager(backend)) {
            Font regular = font(manager, 16, FontStyle.REGULAR);
            Font boldLarge = font(manager, 24, FontStyle.BOLD);
            Label label = new Label("Hi");
            label.setFontFamily(new FontFamily(regular, boldLarge, null, null));
            label.setFontStyle(FontStyle.BOLD);
            label.setTextColor(Color.WHITE);
            label.setDecoration(TextDecoration.UNDERLINE_AND_STRIKETHROUGH);
            label.setShadow(new TextShadow(2, 3, shadowColor));
            label.setBounds(new Rect(0, 0, 200, 100));
            manager.add(label);

            manager.render();

            List<DrawCommand> commands = backend.lastDrawList().commands();
            assertEquals(4, commands.size());
            assertEquals(shadowColor, assertInstanceOf(TextCommand.class,
                    commands.get(0)).colors().getFirst());
            assertInstanceOf(RoundedRectCommand.class, commands.get(1));
            assertInstanceOf(RoundedRectCommand.class, commands.get(2));
            assertEquals(Color.WHITE, assertInstanceOf(TextCommand.class,
                    commands.get(3)).colors().getFirst());

            Size measured = label.measure(LayoutConstraints.loose(500, 500));
            assertEquals(boldLarge.lineHeight(), measured.height(), 0.001f);
        }
    }

    @Test
    void rejectsInvalidArguments() {
        Label label = new Label();
        assertThrows(NullPointerException.class, () -> new Label(null));
        assertThrows(NullPointerException.class, () -> label.setText(null));
        assertThrows(NullPointerException.class, () -> label.setAlignment(null));
        assertThrows(NullPointerException.class, () -> label.setVerticalAlignment(null));
        assertThrows(NullPointerException.class, () -> label.setOverflow(null));
        assertThrows(NullPointerException.class, () -> label.setFontStyle(null));
        assertThrows(NullPointerException.class, () -> label.setDecoration(null));
        assertThrows(IllegalArgumentException.class, () -> label.setMaxLines(-1));
        assertThrows(IllegalArgumentException.class, () -> label.setLineHeight(-1));
        assertThrows(IllegalArgumentException.class, () -> label.setLineHeight(Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new TextShadow(Float.NaN, 0, Color.WHITE));
        assertThrows(NullPointerException.class, () -> new TextShadow(0, 0, null));

        label.setTextColor(Color.WHITE);
        label.setTextColor(null);
        label.setShadow(new TextShadow(1, 1, Color.BLACK));
        label.setShadow(null);
        label.setFontFamily(null);
        assertNull(label.textColor());
        assertNull(label.shadow());
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
