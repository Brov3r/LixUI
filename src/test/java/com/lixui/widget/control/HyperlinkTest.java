package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.platform.Cursor;
import com.lixui.platform.UiPlatform;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.FontStyle;
import com.lixui.text.SystemFonts;
import com.lixui.text.TextDecoration;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HyperlinkTest {
    private static final String URI = "https://example.com/docs";

    @Test
    void opensTheUriOnClickAndEnter() {
        RecordingPlatform platform = new RecordingPlatform();
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend(), platform);
        manager.resize(TestViewports.LOGICAL);
        Hyperlink link = new Hyperlink("Docs", URI);
        link.setBounds(new Rect(10, 20, 120, 30));
        manager.add(link);

        click(manager, 20, 30);

        assertEquals(List.of(URI), platform.opened);

        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
        assertEquals(List.of(URI, URI), platform.opened);

        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, true));
        assertEquals(2, platform.opened.size());

        manager.handle(new PointerPressed(20, 30, MouseButton.RIGHT, Modifiers.NONE));
        manager.handle(new PointerReleased(20, 30, MouseButton.RIGHT, Modifiers.NONE));
        assertEquals(2, platform.opened.size());
    }

    @Test
    void ignoresActivationWhenDisabledOrUnattached() {
        RecordingPlatform platform = new RecordingPlatform();
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend(), platform);
        manager.resize(TestViewports.LOGICAL);
        Hyperlink link = new Hyperlink(URI);
        link.setBounds(new Rect(0, 0, 120, 30));
        link.setEnabled(false);
        manager.add(link);

        click(manager, 10, 10);
        link.open();

        assertEquals(List.of(), platform.opened);

        Hyperlink unattached = new Hyperlink(URI);
        unattached.open();

        assertEquals(List.of(), platform.opened);
    }

    @Test
    void displaysUnderlinedPrimaryTextAndThePointerCursor() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(TestViewports.LOGICAL);
            manager.setDefaultFont(font(manager, 16));
            Hyperlink link = new Hyperlink("Docs", URI);
            link.setBounds(new Rect(0, 0, 120, 30));
            manager.add(link);

            assertEquals(Cursor.POINTER, link.cursor());
            assertEquals(TextDecoration.UNDERLINE, link.decoration());

            manager.render();
            assertEquals(manager.context().theme().primary(), textColor(backend.lastDrawList().commands()));

            Color explicit = Color.rgb(255, 0, 0);
            link.setTextColor(explicit);
            manager.render();
            assertEquals(explicit, textColor(backend.lastDrawList().commands()));
        }
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(NullPointerException.class, () -> new Hyperlink(null));
        assertThrows(NullPointerException.class, () -> new Hyperlink("Docs", null));
        Hyperlink link = new Hyperlink();
        assertThrows(NullPointerException.class, () -> link.setUri(null));
        try (WidgetManager manager = new WidgetManager(new InputState(), new TestBackend())) {
            assertThrows(NullPointerException.class, () -> manager.context().openUri(null));
        }
    }

    private static Color textColor(List<DrawCommand> commands) {
        for (DrawCommand command : commands) {
            if (command instanceof TextCommand text) {
                return text.colors().getFirst();
            }
        }
        throw new AssertionError("No text command was recorded.");
    }

    private static Font font(WidgetManager manager, float size) {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        return manager.context().createFont(data.get(), size, FontStyle.REGULAR);
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static final class RecordingPlatform implements UiPlatform {
        private final List<String> opened = new ArrayList<>();

        @Override
        public String clipboardText() {
            return "";
        }

        @Override
        public void setClipboardText(String text) {
        }

        @Override
        public void setCursor(Cursor cursor) {
        }

        @Override
        public void openUri(String uri) {
            opened.add(uri);
        }
    }
}
