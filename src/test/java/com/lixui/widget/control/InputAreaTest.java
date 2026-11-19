package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.event.CharTyped;
import com.lixui.input.event.KeyPressed;
import com.lixui.render.Viewport;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.text.TextGlyph;
import com.lixui.theme.WidgetState;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputAreaTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;
    private static final Rect AREA_BOUNDS = new Rect(0, 0, 240, 160);
    private static final Modifiers CTRL = new Modifiers(false, true, false, false);
    private static final Modifiers SHIFT = new Modifiers(true, false, false, false);

    @Test
    void insertsLineBreaksAndNavigatesBetweenLines() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        try (WidgetManager manager = manager(new TestBackend())) {
            Font font = manager.context().createFont(data.get(), 16);
            manager.setDefaultFont(font);
            InputArea area = new InputArea();
            area.setBounds(AREA_BOUNDS);
            manager.add(area);
            manager.focus(area);

            type(manager, 'a');
            type(manager, 'b');
            manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
            type(manager, 'c');
            type(manager, 'd');

            assertEquals("ab\ncd", area.text());
            assertEquals(5, area.caretIndex());

            manager.handle(new KeyPressed(Key.UP, Modifiers.NONE, false));
            assertEquals(2, area.caretIndex());
            manager.handle(new KeyPressed(Key.HOME, Modifiers.NONE, false));
            assertEquals(0, area.caretIndex());
            manager.handle(new KeyPressed(Key.END, Modifiers.NONE, false));
            assertEquals(2, area.caretIndex());
            manager.handle(new KeyPressed(Key.DOWN, Modifiers.NONE, false));
            assertTrue(area.caretIndex() >= 3);
            manager.handle(new KeyPressed(Key.HOME, CTRL, false));
            assertEquals(0, area.caretIndex());
            manager.handle(new KeyPressed(Key.END, CTRL, false));
            assertEquals(5, area.caretIndex());

            manager.handle(new KeyPressed(Key.UP, SHIFT, false));
            assertTrue(area.hasSelection());
        }
    }

    @Test
    void wrapsLongLinesAcrossSeveralRows() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Font font = manager.context().createFont(data.get(), 16);
            manager.setDefaultFont(font);
            InputArea area = new InputArea(
                    "one two three four five six seven eight nine ten eleven twelve");
            area.setBounds(new Rect(0, 0, 120, 120));
            manager.add(area);

            manager.render();

            TextCommand text = backend.lastDrawList().commands().stream()
                    .filter(TextCommand.class::isInstance)
                    .map(TextCommand.class::cast)
                    .findFirst()
                    .orElseThrow();
            float top = Float.POSITIVE_INFINITY;
            float bottom = Float.NEGATIVE_INFINITY;
            for (TextGlyph glyph : text.glyphs()) {
                top = Math.min(top, glyph.bounds().y());
                bottom = Math.max(bottom, glyph.bounds().bottom());
            }
            assertTrue(bottom - top > font.lineHeight() * 0.75f);
        }
    }

    @Test
    void disabledAreaRejectsEditsAndDrawsWithoutSelectionOrCaret() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Font font = manager.context().createFont(data.get(), 16);
            manager.setDefaultFont(font);
            InputArea area = new InputArea("a\nb");
            area.setBounds(AREA_BOUNDS);
            manager.add(area);
            manager.focus(area);
            area.selectAll();

            area.setEnabled(false);

            assertFalse(area.isFocused());
            assertTrue(area.insertText("x", false));
            assertEquals("a\nb", area.text());

            manager.render();
            List<DrawCommand> commands = backend.lastDrawList().commands();
            assertEquals(2, commands.stream()
                    .filter(RoundedRectCommand.class::isInstance).count());
            TextCommand text = commands.stream()
                    .filter(TextCommand.class::isInstance)
                    .map(TextCommand.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertEquals(manager.context().theme().controlStyle().text(WidgetState.DISABLED),
                    text.colors().getFirst());
        }
    }

    private static void type(WidgetManager manager, char character) {
        manager.handle(new CharTyped(character));
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }
}
