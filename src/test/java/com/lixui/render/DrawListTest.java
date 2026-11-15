package com.lixui.render;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawListTest {
    private static final Rect VIEWPORT = new Rect(0, 0, 1280, 720);
    private static final Color RED = Color.rgb(255, 0, 0);
    private static final Color GREEN = Color.rgb(0, 255, 0);
    private static final Color BLUE = Color.rgb(0, 0, 255);

    @Test
    void emitsCommandsInSubmissionOrderWithCapturedClip() {
        DrawList list = new DrawList(VIEWPORT);

        list.rect(new Rect(10, 20, 100, 50), RED);
        list.pushClip(new Rect(0, 0, 640, 360));
        list.rect(new Rect(20, 30, 200, 100), GREEN);
        list.popClip();
        list.rect(new Rect(30, 40, 300, 150), BLUE);

        List<DrawCommand> commands = list.commands();
        assertEquals(3, commands.size());
        assertEquals(RED, commands.get(0).color());
        assertSame(VIEWPORT, commands.get(0).clip());
        assertEquals(GREEN, commands.get(1).color());
        assertEquals(new Rect(0, 0, 640, 360), commands.get(1).clip());
        assertEquals(BLUE, commands.get(2).color());
        assertSame(VIEWPORT, commands.get(2).clip());
    }

    @Test
    void intersectsNestedClipsAndRestoresThemOnPop() {
        DrawList list = new DrawList(VIEWPORT);

        list.pushClip(new Rect(0, 0, 100, 100));
        list.pushClip(new Rect(50, 50, 100, 100));
        assertEquals(new Rect(50, 50, 50, 50), list.clip());

        list.popClip();
        assertEquals(new Rect(0, 0, 100, 100), list.clip());
        list.popClip();
        assertSame(VIEWPORT, list.clip());
    }

    @Test
    void omitsCommandsWhileTheClipIsEmpty() {
        DrawList list = new DrawList(VIEWPORT);

        list.pushClip(new Rect(2000, 2000, 10, 10));
        assertTrue(list.clip().isEmpty());
        list.rect(new Rect(0, 0, 100, 100), RED);
        assertTrue(list.commands().isEmpty());

        list.popClip();
        list.rect(new Rect(0, 0, 100, 100), RED);
        assertEquals(1, list.commands().size());
    }

    @Test
    void omitsCommandsWithEmptyBounds() {
        DrawList list = new DrawList(VIEWPORT);

        list.rect(new Rect(0, 0, 0, 100), RED);
        list.roundedRect(new Rect(0, 0, 100, 0), 4, RED);
        list.rect(new Rect(0, 0, 100, 100), RED);

        assertEquals(1, list.commands().size());
    }

    @Test
    void clampsTheRadiusToHalfOfTheSmallerDimension() {
        DrawList list = new DrawList(VIEWPORT);

        list.roundedRect(new Rect(0, 0, 100, 40), 1000, RED);
        list.roundedRect(new Rect(0, 0, 100, 40), 10, RED);

        assertEquals(20, list.commands().get(0).radius());
        assertEquals(10, list.commands().get(1).radius());
    }

    @Test
    void rejectsInvalidRadiusWithoutEmittingCommands() {
        DrawList list = new DrawList(VIEWPORT);
        Rect bounds = new Rect(0, 0, 10, 10);

        assertThrows(IllegalArgumentException.class, () -> list.roundedRect(bounds, -1, RED));
        assertThrows(IllegalArgumentException.class, () -> list.roundedRect(bounds, Float.NaN, RED));
        assertThrows(IllegalArgumentException.class,
                () -> list.roundedRect(bounds, Float.POSITIVE_INFINITY, RED));
        assertTrue(list.commands().isEmpty());
    }

    @Test
    void rejectsPoppingTheViewportClip() {
        DrawList list = new DrawList(VIEWPORT);
        assertThrows(IllegalStateException.class, list::popClip);
    }

    @Test
    void resetClearsCommandsAndTheClipStack() {
        DrawList list = new DrawList(VIEWPORT);
        Rect resized = new Rect(0, 0, 800, 600);
        list.pushClip(new Rect(0, 0, 100, 100));
        list.rect(new Rect(0, 0, 10, 10), RED);

        list.reset(resized);

        assertSame(resized, list.viewport());
        assertSame(resized, list.clip());
        assertTrue(list.commands().isEmpty());
        assertThrows(IllegalStateException.class, list::popClip);
    }

    @Test
    void commandsAreUnmodifiable() {
        DrawList list = new DrawList(VIEWPORT);
        list.rect(new Rect(0, 0, 10, 10), RED);
        assertThrows(UnsupportedOperationException.class, () -> list.commands().clear());
    }

    @Test
    void rejectsNullArguments() {
        DrawList list = new DrawList(VIEWPORT);

        assertThrows(NullPointerException.class, () -> new DrawList(null));
        assertThrows(NullPointerException.class, () -> list.reset(null));
        assertThrows(NullPointerException.class, () -> list.pushClip(null));
        assertThrows(NullPointerException.class, () -> list.rect(null, RED));
        assertThrows(NullPointerException.class, () -> list.rect(new Rect(0, 0, 1, 1), null));
        assertThrows(NullPointerException.class, () -> list.roundedRect(null, 0, RED));
        assertThrows(NullPointerException.class, () -> list.roundedRect(new Rect(0, 0, 1, 1), 0, null));
    }
}
