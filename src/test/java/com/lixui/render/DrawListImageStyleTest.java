package com.lixui.render;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.render.command.ImageCommand;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DrawListImageStyleTest {
    private static final Rect VIEWPORT = new Rect(0, 0, 1000, 1000);
    private static final Rect FULL_UV = new Rect(0, 0, 1, 1);

    @Test
    void emitsCommandsWithTheResolvedLayout() {
        DrawList list = new DrawList(VIEWPORT);
        Texture texture = new TestTexture(200, 100, TextureOptions.DEFAULT);
        Rect bounds = new Rect(0, 0, 100, 100);

        list.image(texture, bounds, FULL_UV, ImageStyle.CONTAIN);

        ImageCommand command = (ImageCommand) list.commands().getFirst();
        assertEquals(new Rect(0, 25, 100, 50), command.bounds());
        assertEquals(FULL_UV, command.uv());
        assertEquals(Color.WHITE, command.color());
        assertEquals(Transform.IDENTITY, command.transform());
    }

    @Test
    void rejectsRepeatOnClampedAxes() {
        DrawList list = new DrawList(VIEWPORT);
        Texture texture = new TestTexture(10, 10, TextureOptions.DEFAULT);
        ImageStyle both = new ImageStyle(ImageFit.NONE, HorizontalAlignment.CENTER,
                VerticalAlignment.CENTER, ImageRepeat.BOTH);

        assertThrows(IllegalArgumentException.class,
                () -> list.image(texture, new Rect(0, 0, 50, 50), FULL_UV, both));
    }

    @Test
    void repeatUsesTheTextureWrapModeAndEmitsAnExtendedUvRegion() {
        DrawList list = new DrawList(VIEWPORT);
        Texture texture = new TestTexture(10, 10, TextureOptions.REPEAT);
        ImageStyle both = new ImageStyle(ImageFit.NONE, HorizontalAlignment.CENTER,
                VerticalAlignment.CENTER, ImageRepeat.BOTH);

        list.image(texture, new Rect(0, 0, 50, 50), FULL_UV, both);

        ImageCommand command = (ImageCommand) list.commands().getFirst();
        assertEquals(new Rect(0, 0, 50, 50), command.bounds());
        assertEquals(new Rect(-2, -2, 5, 5), command.uv());
    }

    @Test
    void emptyBoundsSkipRepeatValidation() {
        DrawList list = new DrawList(VIEWPORT);
        Texture texture = new TestTexture(10, 10, TextureOptions.DEFAULT);
        ImageStyle both = new ImageStyle(ImageFit.NONE, HorizontalAlignment.CENTER,
                VerticalAlignment.CENTER, ImageRepeat.BOTH);

        list.image(texture, new Rect(0, 0, 0, 50), FULL_UV, both);

        assertEquals(0, list.commands().size());
    }

    @Test
    void rejectsNullArguments() {
        DrawList list = new DrawList(VIEWPORT);
        Texture texture = new TestTexture(10, 10, TextureOptions.DEFAULT);
        Rect bounds = new Rect(0, 0, 50, 50);

        assertThrows(NullPointerException.class, () -> list.image(null, bounds, FULL_UV, ImageStyle.FILL));
        assertThrows(NullPointerException.class, () -> list.image(texture, null, FULL_UV, ImageStyle.FILL));
        assertThrows(NullPointerException.class, () -> list.image(texture, bounds, null, ImageStyle.FILL));
        assertThrows(NullPointerException.class, () -> list.image(texture, bounds, FULL_UV, null));
    }

    private record TestTexture(int width, int height, TextureOptions options) implements Texture {
    }
}
