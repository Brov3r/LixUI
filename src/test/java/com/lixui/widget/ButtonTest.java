package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.KeyReleased;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.DrawList;
import com.lixui.render.command.ImageCommand;
import com.lixui.render.ImageData;
import com.lixui.render.command.MeshCommand;
import com.lixui.render.RenderBackend;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonTest {
    private static final Viewport VIEWPORT = new Viewport(1600, 1200, 800, 600);
    private static final Rect BUTTON_BOUNDS = new Rect(10, 20, 160, 44);

    @Test
    void activatesFromPointerKeyboardAndDoubleClick() {
        WidgetManager manager = new WidgetManager(new InputState(), new RecordingBackend());
        Button button = new Button("Save");
        button.setBounds(BUTTON_BOUNDS);
        int[] actions = {0};
        int[] sounds = {0};
        button.setOnAction(() -> actions[0]++);
        button.setSoundPlayer(() -> sounds[0]++);
        manager.add(button);

        click(manager, 20, 30);
        assertEquals(1, actions[0]);
        assertEquals(1, sounds[0]);
        assertTrue(button.isFocused());

        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
        assertEquals(2, actions[0]);
        assertEquals(2, sounds[0]);

        manager.handle(new KeyPressed(Key.SPACE, Modifiers.NONE, false));
        assertEquals(2, actions[0]);
        manager.handle(new KeyReleased(Key.SPACE, Modifiers.NONE));
        assertEquals(3, actions[0]);
        assertEquals(3, sounds[0]);

        click(manager, 20, 30);
        assertEquals(4, actions[0]);
        assertEquals(4, sounds[0]);

        manager.handle(new PointerPressed(20, 30, MouseButton.RIGHT, Modifiers.NONE));
        manager.handle(new PointerReleased(20, 30, MouseButton.RIGHT, Modifiers.NONE));
        assertEquals(4, actions[0]);
        assertEquals(4, sounds[0]);

        button.setEnabled(false);
        button.click();
        assertEquals(4, actions[0]);
    }

    @Test
    void drawsIconAndLabelAndMeasuresBoth() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        RecordingBackend backend = new RecordingBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        Font font = manager.context().createFont(data.get(), 16);
        Button button = new Button("Go");
        button.setFont(font);
        button.setBounds(BUTTON_BOUNDS);
        manager.add(button);
        Size textOnly = button.measure(LayoutConstraints.loose(1000, 1000));

        button.setIcon(new TestTexture(128, 128));
        Size withIcon = button.measure(LayoutConstraints.loose(1000, 1000));

        assertTrue(withIcon.width() > textOnly.width());
        assertTrue(withIcon.height() >= textOnly.height());
        manager.render();
        List<DrawCommand> commands = backend.lastDrawList.commands();
        assertEquals(4, commands.size());
        RoundedRectCommand background = assertInstanceOf(RoundedRectCommand.class, commands.get(0));
        assertEquals(manager.context().theme().controlStyle().cornerRadius(), background.radius(),
                0.0001f);
        assertInstanceOf(MeshCommand.class, commands.get(1));
        assertInstanceOf(ImageCommand.class, commands.get(2));
        assertInstanceOf(TextCommand.class, commands.get(3));
    }

    @Test
    void keyRepeatAfterFocusTransferDoesNotArmSpaceActivation() {
        WidgetManager manager = new WidgetManager(new InputState(), new RecordingBackend());
        Button first = new Button();
        Button second = new Button();
        int[] actions = {0};
        second.setOnAction(() -> actions[0]++);
        manager.add(first).add(second);
        manager.focus(first);
        manager.handle(new KeyPressed(Key.SPACE, Modifiers.NONE, false));
        manager.focus(second);
        manager.handle(new KeyPressed(Key.SPACE, Modifiers.NONE, true));
        manager.handle(new KeyReleased(Key.SPACE, Modifiers.NONE));

        assertEquals(0, actions[0]);
    }

    @Test
    void emptyButtonReservesPaddingWithoutAFont() {
        WidgetManager manager = new WidgetManager(new InputState(), new RecordingBackend());
        Button button = new Button();
        manager.add(button);

        Size size = button.measure(LayoutConstraints.loose(1000, 1000));

        float padding = manager.context().theme().controlStyle().padding();
        assertEquals(padding * 2, size.width());
        assertEquals(padding * 2, size.height());
    }

    @Test
    void rejectsInvalidIconArguments() {
        Button button = new Button("Go");

        assertThrows(IllegalArgumentException.class, () -> button.setIconSize(0));
        assertThrows(IllegalArgumentException.class, () -> button.setIconSize(Float.NaN));
        button.setIconTint(null);
        assertThrows(NullPointerException.class, () -> new Button(null));
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private record TestTexture(int width, int height) implements Texture {
        /**
         * Creates a standalone texture handle for draw-command tests.
         *
         * @param width texture width
         * @param height texture height
         */
        private TestTexture {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Texture dimensions must be positive.");
            }
        }
    }

    private static final class RecordingBackend implements RenderBackend {
        private DrawList lastDrawList;

        @Override
        public void resize(Viewport viewport) {
        }

        @Override
        public void draw(DrawList drawList) {
            lastDrawList = drawList;
        }

        @Override
        public Texture createTexture(ImageData image) {
            return new TestTexture(image.width(), image.height());
        }

        @Override
        public void destroyTexture(Texture texture) {
        }

        @Override
        public void close() {
        }
    }
}
