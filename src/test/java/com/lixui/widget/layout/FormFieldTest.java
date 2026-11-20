package com.lixui.widget.layout;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.FixedWidget;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormFieldTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void measuresTheLabelControlAndMessage() {
        try (WidgetManager manager = manager(new TestBackend())) {
            Font font = font(manager);
            manager.setDefaultFont(font);
            FormField field = new FormField("Name", new FixedWidget(80, 20), "Shown to others");
            field.setFont(font);
            field.setPadding(4);
            field.setGap(3);

            Size measured = field.measure(LayoutConstraints.loose(500, 500));

            float textWidth = Math.max(font.measure("Name").width(),
                    font.measure("Shown to others").width());
            assertEquals(Math.max(80, textWidth) + 8, measured.width(), 0.01f);
            assertEquals(font.lineHeight() * 2 + 20 + 6 + 8, measured.height(), 0.01f);
        }
    }

    @Test
    void laysOutTheControlBetweenTheLabelAndTheMessage() {
        try (WidgetManager manager = manager(new TestBackend())) {
            Font font = font(manager);
            manager.setDefaultFont(font);
            FixedWidget content = new FixedWidget(80, 20);
            FormField field = new FormField("Name", content, "Shown to others");
            field.setFont(font);
            field.setPadding(4);
            field.setGap(3);

            field.layout(new Rect(0, 0, 300, 200));

            float top = 4 + font.lineHeight() + 3;
            float bottom = 200 - 4 - font.lineHeight() - 3;
            assertEquals(new Rect(4, top, 292, bottom - top), content.bounds());
        }
    }

    @Test
    void errorReplacesTheHintAndAffectsTheNaturalHeight() {
        try (WidgetManager manager = manager(new TestBackend())) {
            Font font = font(manager);
            manager.setDefaultFont(font);
            FormField field = new FormField();
            field.setFont(font);
            field.setContent(new FixedWidget(10, 10));
            field.setHint("ok");

            Size hintSize = field.measure(LayoutConstraints.loose(80, 500));
            field.setError("a much longer error message that wraps onto several lines");

            Size errorSize = field.measure(LayoutConstraints.loose(80, 500));

            assertTrue(field.isError());
            assertEquals("a much longer error message that wraps onto several lines", field.error());
            assertTrue(errorSize.height() > hintSize.height());

            field.setError("");

            assertFalse(field.isError());
            assertEquals(hintSize.height(), field.measure(
                    LayoutConstraints.loose(80, 500)).height(), 0.01f);
        }
    }

    @Test
    void autoSizedFieldWrapsItsContent() {
        try (WidgetManager manager = manager(new TestBackend())) {
            Font font = font(manager);
            manager.setDefaultFont(font);
            FormField field = new FormField("Name", new FixedWidget(80, 20), "Shown to others");
            field.setPadding(4);
            field.setGap(3);
            field.setPosition(15, 25);
            field.setAutoSize(true);
            manager.add(field);

            manager.layout();

            float textWidth = Math.max(font.measure("Name").width(),
                    font.measure("Shown to others").width());
            Size expected = new Size(Math.max(80, textWidth) + 8,
                    font.lineHeight() * 2 + 20 + 6 + 8);
            assertEquals(new Rect(15, 25, expected.width(), expected.height()), field.bounds());
        }
    }

    @Test
    void invisibleContentTakesNoSpaceAndKeepsItsBounds() {
        try (WidgetManager manager = manager(new TestBackend())) {
            Font font = font(manager);
            manager.setDefaultFont(font);
            FixedWidget hidden = new FixedWidget(50, 30);
            hidden.setBounds(new Rect(1, 2, 50, 30));
            hidden.setVisible(false);
            FormField field = new FormField("Name", hidden, "Hint");
            field.setFont(font);
            field.setPadding(4);
            field.setGap(3);

            Size measured = field.measure(LayoutConstraints.loose(500, 500));
            field.layout(new Rect(0, 0, 200, 100));

            assertEquals(font.lineHeight() * 2 + 3 + 8, measured.height(), 0.01f);
            assertEquals(new Rect(1, 2, 50, 30), hidden.bounds());
        }
    }

    @Test
    void drawsLabelAndMessageWithHintAndErrorColors() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Font font = font(manager);
            manager.setDefaultFont(font);
            FormField field = new FormField("Email", new FixedWidget(20, 10), "Hint text");
            field.setBounds(new Rect(0, 0, 200, 100));
            manager.add(field);

            manager.render();

            List<DrawCommand> commands = backend.lastDrawList().commands();
            assertEquals(2, commands.size());
            TextCommand label = (TextCommand) commands.get(0);
            TextCommand hint = (TextCommand) commands.get(1);
            assertEquals(manager.context().theme().text(), label.colors().getFirst());
            assertEquals(manager.context().theme().textMuted(), hint.colors().getFirst());

            Color errorColor = Color.rgb(200, 30, 40);
            field.setError("Invalid value");
            field.setErrorColor(errorColor);

            manager.render();

            TextCommand error = (TextCommand) backend.lastDrawList().commands().get(1);
            assertEquals(errorColor, error.colors().getFirst());
        }
    }

    @Test
    void replacesAndClearsTheContent() {
        FormField field = new FormField();
        FixedWidget first = new FixedWidget(20, 20);
        FixedWidget second = new FixedWidget(30, 30);
        field.setContent(first);

        assertSame(first, field.content());
        assertSame(field, first.parent());

        field.setContent(second);

        assertSame(second, field.content());
        assertSame(field, second.parent());
        assertNull(first.parent());

        field.clearContent();

        assertNull(field.content());
        assertNull(second.parent());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(NullPointerException.class,
                () -> new FormField(null, new FixedWidget(1, 1), ""));
        assertThrows(NullPointerException.class, () -> new FormField("Label", null, ""));
        assertThrows(NullPointerException.class,
                () -> new FormField("Label", new FixedWidget(1, 1), null));
        FormField field = new FormField();
        assertThrows(NullPointerException.class, () -> field.setLabel(null));
        assertThrows(NullPointerException.class, () -> field.setHint(null));
        assertThrows(NullPointerException.class, () -> field.setError(null));
        assertThrows(NullPointerException.class, () -> field.setErrorColor(null));
        assertThrows(NullPointerException.class, () -> field.setContent(null));
        assertThrows(IllegalArgumentException.class, () -> field.setGap(-1));
        assertThrows(IllegalArgumentException.class, () -> field.setGap(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> field.setPadding(-1));
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
