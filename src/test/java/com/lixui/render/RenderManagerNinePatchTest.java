package com.lixui.render;

import com.lixui.geometry.Insets;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.ImageCommand;
import com.lixui.widget.testsupport.TestBackend;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderManagerNinePatchTest {
    @Test
    void appliesOpacityAndTransformToEveryPatch() {
        TestBackend backend = new TestBackend();
        RenderManager renderer = new RenderManager(backend);
        renderer.resize(new Viewport(800, 600, 800, 600));

        renderer.begin();
        renderer.withTransform(Transform.translation(5, 7), () -> renderer.withOpacity(0.5f,
                () -> renderer.ninePatch(new TestTexture(30, 30),
                        new Rect(0, 0, 100, 50), Insets.uniform(10))));
        renderer.end();

        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertEquals(9, commands.size());
        for (DrawCommand command : commands) {
            ImageCommand image = (ImageCommand) command;
            assertEquals(Transform.translation(5, 7), image.transform());
            assertEquals(0.5f, image.color().a(), 0.0001f);
        }
    }

    private record TestTexture(int width, int height) implements Texture {
        @Override
        public TextureOptions options() {
            return TextureOptions.DEFAULT;
        }
    }
}
