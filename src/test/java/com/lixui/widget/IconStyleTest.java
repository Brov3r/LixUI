package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.render.ImageStyle;
import com.lixui.render.Texture;
import com.lixui.render.TextureOptions;
import com.lixui.render.Viewport;
import com.lixui.render.command.ImageCommand;
import com.lixui.widget.testsupport.TestBackend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IconStyleTest {
    private static final Viewport VIEWPORT = new Viewport(800, 600, 800, 600);

    @Test
    void buttonPreservesTheIconAspectRatioByDefault() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            Button button = new Button();
            button.setBounds(new Rect(0, 0, 100, 40));
            button.setIcon(new TestTexture(40, 10));
            manager.add(button);

            assertEquals(ImageStyle.ICON, button.iconStyle());
            manager.render();

            assertEquals(new Rect(40, 17.5f, 20, 5), icon(backend).bounds());
        }
    }

    @Test
    void buttonIconStyleCanStretchTheIcon() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            Button button = new Button();
            button.setBounds(new Rect(0, 0, 100, 40));
            button.setIcon(new TestTexture(40, 10));
            button.setIconStyle(ImageStyle.FILL);
            manager.add(button);
            manager.render();

            assertEquals(new Rect(40, 10, 20, 20), icon(backend).bounds());
        }
    }

    @Test
    void rejectsNullIconStyle() {
        assertThrows(NullPointerException.class, () -> new Button().setIconStyle(null));
    }

    private static ImageCommand icon(TestBackend backend) {
        return (ImageCommand) backend.lastDrawList().commands().stream()
                .filter(ImageCommand.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    private record TestTexture(int width, int height) implements Texture {
        @Override
        public TextureOptions options() {
            return TextureOptions.DEFAULT;
        }
    }
}
