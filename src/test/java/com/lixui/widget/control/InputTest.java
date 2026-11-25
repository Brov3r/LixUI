package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.CharTyped;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.platform.Cursor;
import com.lixui.platform.UiPlatform;
import com.lixui.render.Viewport;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.MeshCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.Glyph;
import com.lixui.text.SystemFonts;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.Panel;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;
    private static final Rect FIELD_BOUNDS = new Rect(0, 0, 240, 30);
    private static final Modifiers CTRL = new Modifiers(false, true, false, false);
    private static final Modifiers SHIFT = new Modifiers(true, false, false, false);
    private static final Modifiers CTRL_SHIFT = new Modifiers(true, true, false, false);

    @Test
    void typesAndDeletesWholeCodePoints() {
        try (WidgetManager manager = manager(new TestBackend(), new ClipboardPlatform())) {
            Input input = new Input();
            input.setBounds(FIELD_BOUNDS);
            manager.add(input);
            manager.focus(input);
            input.setText("A");

            manager.handle(new CharTyped(0x1F600));
            manager.handle(new CharTyped('B'));

            assertEquals("A\uD83D\uDE00B", input.text());
            assertEquals(4, input.caretIndex());

            manager.handle(new KeyPressed(Key.BACKSPACE, Modifiers.NONE, false));
            assertEquals("A\uD83D\uDE00", input.text());

            manager.handle(new KeyPressed(Key.BACKSPACE, Modifiers.NONE, false));
            assertEquals("A", input.text());
            assertEquals(1, input.caretIndex());

            input.setText("e\u0301x");
            manager.handle(new KeyPressed(Key.BACKSPACE, Modifiers.NONE, false));
            manager.handle(new KeyPressed(Key.BACKSPACE, Modifiers.NONE, false));
            assertEquals("", input.text());
        }
    }

    @Test
    void editsWithSelectionShortcutsAndHistory() {
        ClipboardPlatform platform = new ClipboardPlatform();
        try (WidgetManager manager = manager(new TestBackend(), platform)) {
            Input input = new Input("abc def");
            input.setBounds(FIELD_BOUNDS);
            manager.add(input);
            manager.focus(input);

            manager.handle(new KeyPressed(Key.END, Modifiers.NONE, false));
            manager.handle(new KeyPressed(Key.LEFT, CTRL_SHIFT, false));
            assertEquals(4, input.selectionStart());
            assertEquals(7, input.selectionEnd());

            manager.handle(new CharTyped('X'));
            assertEquals("abc X", input.text());

            manager.handle(new KeyPressed(Key.Z, CTRL, false));
            assertEquals("abc def", input.text());
            assertEquals(4, input.selectionStart());
            assertEquals(7, input.selectionEnd());

            manager.handle(new KeyPressed(Key.Y, CTRL, false));
            assertEquals("abc X", input.text());

            manager.handle(new KeyPressed(Key.A, CTRL, false));
            manager.handle(new KeyPressed(Key.C, CTRL, false));
            assertEquals("abc X", platform.clipboardText);

            manager.handle(new KeyPressed(Key.X, CTRL, false));
            assertEquals("", input.text());
            assertEquals("abc X", platform.clipboardText);

            manager.handle(new KeyPressed(Key.V, CTRL, false));
            assertEquals("abc X", input.text());
        }
    }

    @Test
    void limitsLengthAndRejectsControlText() {
        Input input = new Input("abc\ndef\tghi");
        assertEquals("abc def ghi", input.text());

        input.setMaxLength(5);
        assertEquals("abc d", input.text());

        try (WidgetManager manager = manager(new TestBackend(), new ClipboardPlatform())) {
            input.setBounds(FIELD_BOUNDS);
            manager.add(input);
            manager.focus(input);

            manager.handle(new CharTyped('Z'));
            assertEquals("abc d", input.text());

            input.setSelection(0, 1);
            manager.handle(new CharTyped('X'));
            assertEquals("Xbc d", input.text());
            assertFalse(input.onCharTyped('\n'));

            input.setReadOnly(true);
            manager.handle(new CharTyped('Y'));
            manager.handle(new KeyPressed(Key.BACKSPACE, Modifiers.NONE, false));
            assertEquals("Xbc d", input.text());
        }
    }

    @Test
    void pointerDragSelectsTextAndDoubleClickSelectsWord() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend, new ClipboardPlatform())) {
            Font font = manager.context().createFont(data.get(), 16);
            manager.setDefaultFont(font);
            Input input = new Input("alpha beta");
            input.setBounds(FIELD_BOUNDS);
            manager.add(input);
            manager.render();

            float padding = manager.context().theme().controlStyle().padding();
            float alphaWidth = font.measure("alpha ").width();
            manager.handle(new PointerPressed(padding + 1, 15, MouseButton.LEFT,
                    Modifiers.NONE));
            manager.handle(new PointerMoved(padding + alphaWidth - 1, 15));
            manager.handle(new PointerReleased(padding + alphaWidth - 1, 15, MouseButton.LEFT,
                    Modifiers.NONE));

            assertTrue(input.hasSelection());
            assertEquals(0, input.selectionStart());
            assertTrue(input.selectionEnd() >= 5);

            float betaX = padding + alphaWidth + font.measure("be").width() / 2;
            click(manager, betaX, 15);
            manager.update(0.01f);
            click(manager, betaX, 15);

            assertEquals("beta", input.text().substring(input.selectionStart(),
                    input.selectionEnd()));

            manager.update(0.01f);
            click(manager, betaX, 15);
            assertEquals(0, input.selectionStart());
            assertEquals(input.text().length(), input.selectionEnd());
        }
    }

    @Test
    void keepsControlSurfaceAndAbsoluteTextClipInsideAnInheritedStyle() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend, new ClipboardPlatform())) {
            Font font = manager.context().createFont(data.get(), 16);
            manager.setDefaultFont(font);
            Theme theme = manager.context().theme();
            Color red = Color.hex("#FF0000");
            WidgetStyle inherited = new WidgetStyle(red, red, red, red,
                    theme.text(), theme.textMuted(), theme.border(), theme.focusRing(), 0, 0, 0);
            Panel root = new Panel();
            root.setStyle(inherited);
            root.setBounds(new Rect(0, 0, 500, 200));
            Input input = new Input("Hi");
            input.setBounds(new Rect(40, 80, 200, 36));
            root.add(input);
            manager.add(root);
            manager.render();

            List<DrawCommand> commands = backend.lastDrawList().commands();
            boolean controlSurface = commands.stream()
                    .filter(RoundedRectCommand.class::isInstance)
                    .map(RoundedRectCommand.class::cast)
                    .anyMatch(command -> command.color().equals(
                            theme.controlStyle().background(WidgetState.NORMAL)));
            assertTrue(controlSurface);

            TextCommand text = commands.stream()
                    .filter(TextCommand.class::isInstance)
                    .map(TextCommand.class::cast)
                    .findFirst()
                    .orElseThrow();
            Point center = text.transform().apply(text.glyphs().getFirst().bounds().center());
            assertTrue(text.clip().contains(center.x(), center.y()));
        }
    }

    @Test
    void formatsCustomMasksAndFiltersMaskTokens() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend, new ClipboardPlatform())) {
            Input phone = new Input();
            phone.setMask("+7 (xxx) xxx-xx-xx");
            phone.setBounds(FIELD_BOUNDS);
            manager.add(phone);
            manager.focus(phone);

            for (char digit : "1234567890".toCharArray()) {
                manager.handle(new CharTyped(digit));
            }

            assertEquals("+7 (123) 456-78-90", phone.text());
            assertEquals("1234567890", phone.rawText());

            manager.handle(new KeyPressed(Key.BACKSPACE, Modifiers.NONE, false));
            assertEquals("+7 (123) 456-78-9", phone.text());
            assertEquals("123456789", phone.rawText());

            Input date = new Input();
            date.setMask("99.99.9999");
            date.setText("12.34.2025");
            assertEquals("12.34.2025", date.text());
            assertEquals("12342025", date.rawText());

            date.setBounds(FIELD_BOUNDS);
            manager.add(date);
            manager.focus(date);
            date.setSelection(0, date.rawText().length());
            manager.handle(new CharTyped('a'));

            assertEquals("12.34.2025", date.text());
        }
    }

    @Test
    void keepsFilledMaskContentWhenInsertingWithoutFreeSlots() {
        try (WidgetManager manager = manager(new TestBackend(), new ClipboardPlatform())) {
            Input date = new Input();
            date.setMask("99.99.9999");
            date.setText("31.12.2025");
            date.setBounds(FIELD_BOUNDS);
            manager.add(date);
            manager.focus(date);

            date.setCaretIndex(0);
            manager.handle(new CharTyped('9'));
            assertEquals("31.12.2025", date.text());
            assertEquals("31122025", date.rawText());

            date.setCaretIndex(2);
            manager.handle(new CharTyped('9'));
            assertEquals("31.12.2025", date.text());
            assertEquals("31122025", date.rawText());
            assertEquals(2, date.caretIndex());
        }
    }

    @Test
    void limitsMaskInsertionToFreeSlotsWithoutDroppingTheTail() {
        ClipboardPlatform platform = new ClipboardPlatform();
        platform.clipboardText = "999";
        try (WidgetManager manager = manager(new TestBackend(), platform)) {
            Input date = new Input();
            date.setMask("99.99.9999");
            date.setText("31.12.20");
            date.setBounds(FIELD_BOUNDS);
            manager.add(date);
            manager.focus(date);

            date.setCaretIndex(0);
            manager.handle(new KeyPressed(Key.V, CTRL, false));

            assertEquals("99.31.1220", date.text());
            assertEquals("99311220", date.rawText());
            assertEquals(2, date.caretIndex());
        }
    }

    @Test
    void restrictsAcceptedCharacterKinds() {
        Input input = new Input("abc123!@#");
        input.setAllowedCharacters(Input.CharacterKind.DIGITS);
        assertEquals("123", input.text());
        assertEquals(Set.of(Input.CharacterKind.DIGITS), input.allowedCharacters());

        try (WidgetManager manager = manager(new TestBackend(), new ClipboardPlatform())) {
            input.setBounds(FIELD_BOUNDS);
            manager.add(input);
            manager.focus(input);

            manager.handle(new CharTyped('4'));
            assertEquals("1234", input.text());
            manager.handle(new CharTyped('x'));
            assertEquals("1234", input.text());

            input.setAllowedCharacters(Input.CharacterKind.DIGITS, Input.CharacterKind.LETTERS);
            manager.handle(new CharTyped('x'));
            assertEquals("1234x", input.text());

            input.setAllowedCharacters(Input.CharacterKind.SYMBOLS);
            assertEquals("", input.text());
            manager.handle(new CharTyped('!'));
            assertEquals("!", input.text());
        }
        assertThrows(NullPointerException.class,
                () -> input.setAllowedCharacters((Input.CharacterKind[]) null));
    }

    @Test
    void hidesSecretCharactersAndBlocksClipboardCopy() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        ClipboardPlatform platform = new ClipboardPlatform();
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend, platform)) {
            Font font = manager.context().createFont(data.get(), 16);
            manager.setDefaultFont(font);
            Input input = new Input("Pass");
            input.setBounds(FIELD_BOUNDS);
            input.setSecret(true);
            manager.add(input);
            manager.focus(input);
            manager.render();

            TextCommand text = backend.lastDrawList().commands().stream()
                    .filter(TextCommand.class::isInstance)
                    .map(TextCommand.class::cast)
                    .findFirst()
                    .orElseThrow();
            Glyph mask = font.glyph(input.secretCharacter());
            assertTrue(mask != null);
            assertEquals(mask.width(), text.glyphs().getFirst().bounds().width(), 0.01f);

            input.selectAll();
            manager.handle(new KeyPressed(Key.C, CTRL, false));
            assertEquals("", platform.clipboardText);
            assertEquals("Pass", input.text());

            input.setSecret(false);
            manager.handle(new KeyPressed(Key.C, CTRL, false));
            assertEquals("Pass", platform.clipboardText);
        }
    }

    @Test
    void drawsAnalyticRoundedSurfaceWithoutTessellatedBorder() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend, new ClipboardPlatform())) {
            Input input = new Input("Hi");
            input.setBounds(FIELD_BOUNDS);
            manager.add(input);
            manager.render();

            List<DrawCommand> commands = backend.lastDrawList().commands();
            assertTrue(commands.stream().noneMatch(MeshCommand.class::isInstance));
            long rounded = commands.stream().filter(RoundedRectCommand.class::isInstance).count();
            assertEquals(2, rounded);
        }
    }

    @Test
    void disabledFieldRejectsEditsAndHidesCaretAndSelection() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend, new ClipboardPlatform())) {
            Font font = manager.context().createFont(data.get(), 16);
            manager.setDefaultFont(font);
            Input input = new Input("Hi");
            input.setBounds(FIELD_BOUNDS);
            manager.add(input);
            manager.focus(input);
            input.selectAll();

            input.setEnabled(false);

            assertFalse(input.isFocused());
            assertTrue(input.insertText("X", false));
            assertEquals("Hi", input.text());

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

    @Test
    void validatesConfigurationAndNormalizesPlaceholder() {
        Input input = new Input();
        assertThrows(NullPointerException.class, () -> new Input(null));
        assertThrows(NullPointerException.class, () -> input.setText(null));
        assertThrows(NullPointerException.class, () -> input.setPlaceholder(null));
        assertThrows(IllegalArgumentException.class, () -> input.setMaxLength(-1));

        input.setPlaceholder("type\nhere");
        assertEquals("type here", input.placeholder());
        input.setText("a\nb");
        assertEquals("a b", input.text());
        input.setMaxLength(0);
        input.setReadOnly(true);
        assertTrue(input.isReadOnly());
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static WidgetManager manager(TestBackend backend, UiPlatform platform) {
        WidgetManager manager = new WidgetManager(new InputState(), backend, platform);
        manager.resize(VIEWPORT);
        return manager;
    }

    private static final class ClipboardPlatform implements UiPlatform {
        private String clipboardText = "";

        @Override
        public String clipboardText() {
            return clipboardText;
        }

        @Override
        public void setClipboardText(String text) {
            clipboardText = text;
        }

        @Override
        public void setCursor(Cursor cursor) {
        }
    }
}
