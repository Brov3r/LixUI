package com.lixui.backend;

import com.lixui.geometry.Point;
import com.lixui.graphics.Color;
import com.lixui.graphics.GradientStop;
import com.lixui.graphics.Paint;
import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShaderPaintTest {
    @Test
    void packsFourStopsAfterTheViewportWithoutOverwritingIt() {
        FloatBuffer buffer = FloatBuffer.allocate(32);
        buffer.put(800).put(600).put(0).put(0);
        Paint paint = Paint.linear(new Point(10, 20), new Point(110, 20),
                new GradientStop(0, Color.BLACK), new GradientStop(0.5f, Color.WHITE),
                new GradientStop(0.5f, Color.BLACK), new GradientStop(1, Color.WHITE));

        ShaderPaint.write(paint, buffer);

        assertEquals(32, buffer.position());
        assertEquals(800, buffer.get(0));
        assertEquals(600, buffer.get(1));
        assertEquals(10, buffer.get(4));
        assertEquals(110, buffer.get(6));
        assertEquals(0.5f, buffer.get(25));
        assertEquals(0.5f, buffer.get(26));
        assertEquals(1, buffer.get(28));
        assertEquals(4, buffer.get(29));
    }

    @Test
    void resetsGradientStateForSubsequentSolidAndTextureBatches() {
        FloatBuffer buffer = FloatBuffer.allocate(ShaderPaint.FLOAT_COUNT);
        ShaderPaint.write(Paint.radial(new Point(5, 6), 20,
                new GradientStop(0, Color.BLACK), new GradientStop(1, Color.WHITE)), buffer);
        assertEquals(2, buffer.get(24));
        assertEquals(20, buffer.get(2));

        buffer.clear();
        ShaderPaint.write(null, buffer);

        for (int index = 0; index < buffer.capacity(); index++) {
            assertEquals(0, buffer.get(index));
        }
    }
}
