package com.lixui.backend;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.graphics.GradientStop;
import com.lixui.graphics.Paint;
import com.lixui.render.RenderBackend;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises batch boundaries shared by both real GPU backends.
 */
public final class BatchScenario {
    private BatchScenario() {
    }

    /**
     * Emits commands crossing paint, texture, clip and stencil boundaries in a fixed order.
     *
     * @param renderer active renderer
     * @param texture texture belonging to the backend under test
     */
    public static void draw(RenderManager renderer, Texture texture) {
        Rect bounds = new Rect(10, 10, 40, 40);
        Paint gradient = Paint.linear(new Point(10, 0), new Point(50, 0),
                new GradientStop(0, Color.BLACK), new GradientStop(1, Color.WHITE));
        Paint equalGradient = Paint.linear(new Point(10, 0), new Point(50, 0),
                new GradientStop(0, Color.BLACK), new GradientStop(1, Color.WHITE));
        renderer.fillRect(bounds, gradient);
        renderer.fillCircle(new Point(30, 30), 10, equalGradient);
        renderer.fillRect(bounds, Paint.radial(new Point(30, 30), 20,
                new GradientStop(0, Color.WHITE), new GradientStop(1, Color.BLACK)));
        renderer.rect(bounds, Color.WHITE);
        renderer.roundedRect(bounds, 5, Color.BLACK);
        renderer.image(texture, bounds);
        renderer.pushClip(new Rect(0, 0, 64, 64));
        renderer.rect(bounds, Color.WHITE);
        renderer.pushMaskRect(new Rect(20, 20, 10, 10));
        renderer.rect(bounds, Color.BLACK);
        renderer.popMask();
        renderer.rect(new Rect(40, 40, 5, 5), Color.WHITE);
        renderer.popClip();
        renderer.fillRect(new Rect(80, 10, 20, 20), gradient);
    }

    /**
     * Checks merging, submission order, vertex ranges and mask boundaries without exposing metrics.
     *
     * @param backend backend that just rendered the scenario
     * @throws ReflectiveOperationException if the internal batch representation cannot be inspected
     */
    public static void verify(RenderBackend backend) throws ReflectiveOperationException {
        Field field = backend.getClass().getDeclaredField("batches");
        field.setAccessible(true);
        List<?> batches = (List<?>) field.get(backend);
        int[] counts = {12, 6, 12, 6, 6, 6, 6, 6, 6, 6};
        int[] stencil = {0, 0, 0, 0, 0, 0, 1, 1, 0, 0};
        assertEquals(counts.length, batches.size());
        int first = 0;
        for (int index = 0; index < counts.length; index++) {
            Object batch = batches.get(index);
            assertEquals(first, value(batch, "firstVertex"));
            assertEquals(counts[index], value(batch, "vertexCount"));
            assertEquals(stencil[index], value(batch, "stencilRef"));
            String action = index == 5 ? "PUSH" : index == 7 ? "POP" : "DRAW";
            assertEquals(action, value(batch, "action").toString());
            first += counts[index];
        }
    }

    private static Object value(Object batch, String name) throws ReflectiveOperationException {
        Method method = batch.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(batch);
    }
}
