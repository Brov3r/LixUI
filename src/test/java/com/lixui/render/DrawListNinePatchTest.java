package com.lixui.render;

import com.lixui.geometry.Insets;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.ImageCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DrawListNinePatchTest {
    private static final Rect VIEWPORT = new Rect(0, 0, 1000, 1000);
    private static final Rect FULL_UV = new Rect(0, 0, 1, 1);

    @Test
    void emitsNinePatchesWithSharedPaintAndTransform() {
        DrawList list = new DrawList(VIEWPORT);
        Texture texture = new TestTexture(30, 30);
        Color tint = new Color(1, 0, 0, 0.5f);
        Transform transform = Transform.translation(5, 7);

        list.ninePatch(texture, new Rect(0, 0, 100, 50), Insets.uniform(10),
                FULL_UV, tint, transform);

        assertEquals(9, list.commands().size());
        ImageCommand topLeft = (ImageCommand) list.commands().getFirst();
        ImageCommand center = (ImageCommand) list.commands().get(4);
        ImageCommand bottomRight = (ImageCommand) list.commands().get(8);
        assertEquals(new Rect(0, 0, 10, 10), topLeft.bounds());
        assertEquals(new Rect(10, 10, 80, 30), center.bounds());
        assertEquals(new Rect(90, 40, 10, 10), bottomRight.bounds());
        for (DrawCommand command : list.commands()) {
            ImageCommand image = (ImageCommand) command;
            assertSame(texture, image.texture());
            assertSame(tint, image.color());
            assertSame(transform, image.transform());
            assertSame(VIEWPORT, image.clip());
            assertEquals(0, image.radius());
        }
    }

    @Test
    void emptyBoundsEmitNoPatches() {
        DrawList list = new DrawList(VIEWPORT);

        list.ninePatch(new TestTexture(30, 30), new Rect(0, 0, 0, 50), Insets.uniform(10));

        assertEquals(0, list.commands().size());
    }

    @Test
    void rejectsInsetsWithoutAPositiveCenter() {
        DrawList list = new DrawList(VIEWPORT);

        assertThrows(IllegalArgumentException.class, () -> list.ninePatch(
                new TestTexture(30, 30), new Rect(0, 0, 100, 100), Insets.uniform(15)));
    }

    @Test
    void rejectsNullArguments() {
        DrawList list = new DrawList(VIEWPORT);
        Texture texture = new TestTexture(30, 30);
        Rect bounds = new Rect(0, 0, 100, 100);

        assertThrows(NullPointerException.class, () -> list.ninePatch(null, bounds, Insets.NONE));
        assertThrows(NullPointerException.class, () -> list.ninePatch(texture, null, Insets.NONE));
        assertThrows(NullPointerException.class, () -> list.ninePatch(texture, bounds, null));
        assertThrows(NullPointerException.class, () -> list.ninePatch(
                texture, bounds, Insets.NONE, null, Color.WHITE, Transform.IDENTITY));
        assertThrows(NullPointerException.class, () -> list.ninePatch(
                texture, bounds, Insets.NONE, FULL_UV, null, Transform.IDENTITY));
        assertThrows(NullPointerException.class, () -> list.ninePatch(
                texture, bounds, Insets.NONE, FULL_UV, Color.WHITE, null));
    }

    private record TestTexture(int width, int height) implements Texture {
        @Override
        public TextureOptions options() {
            return TextureOptions.DEFAULT;
        }
    }
}
