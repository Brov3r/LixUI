package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.event.KeyPressed;
import com.lixui.render.Viewport;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputCodeTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;
    private static final Rect CODE_BOUNDS = new Rect(0, 0, 360, 200);

    @Test
    void drawsGutterAndDefaultSyntaxColors() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Font font = manager.context().createFont(data.get(), 16);
            manager.setDefaultFont(font);
            Theme theme = manager.context().theme();
            InputCode code = new InputCode("class Foo {\n"
                    + "  @Deprecated\n"
                    + "  private static final int ANSWER = 42;\n"
                    + "  int value = 42; // note\n"
                    + "  String text = \"hi\";\n"
                    + "  void run() {\n"
                    + "    System.out.println(ANSWER);\n"
                    + "  }\n"
                    + "}");
            Color numberColor = Color.hex("#FF00FF");
            code.setLineNumberColor(numberColor);
            code.setCurrentLineNumberColor(numberColor);
            code.setBounds(CODE_BOUNDS);
            manager.add(code);

            manager.render();
            Set<Color> colors = textColors(backend);
            InputCode.SyntaxColors syntax = InputCode.SyntaxColors.defaults(theme);
            assertTrue(colors.contains(numberColor));
            assertTrue(colors.contains(syntax.keyword()));
            assertTrue(colors.contains(syntax.string()));
            assertTrue(colors.contains(syntax.number()));
            assertTrue(colors.contains(syntax.comment()));
            assertTrue(colors.contains(syntax.annotation()));
            assertTrue(colors.contains(syntax.type()));
            assertTrue(colors.contains(syntax.method()));
            assertTrue(colors.contains(syntax.constant()));
            assertTrue(colors.contains(syntax.operator()));

            code.setShowLineNumbers(false);
            manager.render();
            assertFalse(textColors(backend).contains(numberColor));

            code.setSyntaxHighlighting(false);
            manager.render();
            colors = textColors(backend);
            assertFalse(colors.contains(syntax.keyword()));
            assertFalse(colors.contains(syntax.string()));
            assertFalse(colors.contains(syntax.number()));
            assertFalse(colors.contains(syntax.comment()));
        }
    }

    @Test
    void tabIndentsSelectedLinesAndShiftTabOutdentsThem() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        try (WidgetManager manager = manager(new TestBackend())) {
            Font font = manager.context().createFont(data.get(), 16);
            manager.setDefaultFont(font);
            InputCode code = new InputCode("one\ntwo");
            code.setBounds(CODE_BOUNDS);
            manager.add(code);
            manager.focus(code);

            code.setSelection(0, code.rawText().length());
            manager.handle(new KeyPressed(Key.TAB, Modifiers.NONE, false));
            assertEquals("    one\n    two", code.text());

            manager.handle(new KeyPressed(Key.TAB, new Modifiers(true, false, false, false),
                    false));
            assertEquals("one\ntwo", code.text());

            code.setCaretIndex(0);
            manager.handle(new KeyPressed(Key.TAB, Modifiers.NONE, false));
            assertEquals("    one\ntwo", code.text());
            assertEquals(4, code.caretIndex());

            code.setUseSpacesForTabs(false);
            code.setText("x");
            code.setCaretIndex(0);
            manager.handle(new KeyPressed(Key.TAB, Modifiers.NONE, false));
            assertEquals("\tx", code.text());
        }
    }

    @Test
    void customizesKeywordsAndSyntaxPalette() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Font font = manager.context().createFont(data.get(), 16);
            manager.setDefaultFont(font);
            InputCode code = new InputCode("custom 7 \"x\" // note");
            InputCode.SyntaxColors palette = new InputCode.SyntaxColors(
                    Color.hex("#010203"), Color.hex("#040506"), Color.hex("#070809"),
                    Color.hex("#0A0B0C"), Color.hex("#0D0E0F"));
            code.setKeywords(Set.of("custom"));
            code.setSyntaxColors(palette);
            code.setShowLineNumbers(false);
            code.setBounds(CODE_BOUNDS);
            manager.add(code);

            manager.render();

            assertEquals(Set.of("custom"), code.keywords());
            Set<Color> colors = textColors(backend);
            assertTrue(colors.contains(palette.keyword()));
            assertTrue(colors.contains(palette.string()));
            assertTrue(colors.contains(palette.number()));
            assertTrue(colors.contains(palette.comment()));
        }
    }

    @Test
    void currentLineHighlightFollowsFocusAndDisablesWithTheEditor() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Font font = manager.context().createFont(data.get(), 16);
            manager.setDefaultFont(font);
            InputCode code = new InputCode("one\ntwo");
            Color highlight = Color.hex("#112233");
            code.setCurrentLineColor(highlight);
            code.setBounds(CODE_BOUNDS);
            manager.add(code);
            manager.focus(code);

            manager.render();
            assertTrue(roundedColors(backend).contains(highlight));

            code.setEnabled(false);
            manager.render();
            assertFalse(roundedColors(backend).contains(highlight));
            assertTrue(textColors(backend).contains(
                    manager.context().theme().controlStyle().text(WidgetState.DISABLED)));
        }
    }

    private static Set<Color> textColors(TestBackend backend) {
        return backend.lastDrawList().commands().stream()
                .filter(TextCommand.class::isInstance)
                .map(TextCommand.class::cast)
                .flatMap(command -> command.colors().stream())
                .collect(Collectors.toSet());
    }

    private static Set<Color> roundedColors(TestBackend backend) {
        return backend.lastDrawList().commands().stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .map(RoundedRectCommand::color)
                .collect(Collectors.toSet());
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }
}
