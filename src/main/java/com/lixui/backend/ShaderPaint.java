package com.lixui.backend;

import com.lixui.graphics.Color;
import com.lixui.graphics.GradientStop;
import com.lixui.graphics.Paint;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.ShapeCommand;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Objects;

/**
 * Packs the shared OpenGL and Vulkan gradient parameters into seven four-float vectors.
 */
public final class ShaderPaint {
    /** Number of floats in the portable gradient parameter block. */
    public static final int FLOAT_COUNT = 28;

    private ShaderPaint() {
    }

    /**
     * Returns the gradient that must be shared by commands in a batch.
     *
     * @param command command being batched
     * @return the analytic gradient, or null for texture and vertex-color rendering
     * @throws NullPointerException if the command is null
     */
    public static Paint gradient(DrawCommand command) {
        Objects.requireNonNull(command, "command");
        return command instanceof ShapeCommand shape && !(shape.paint() instanceof Paint.Solid)
                ? shape.paint() : null;
    }

    /**
     * Writes geometry, four colors, stop offsets, and paint type/count at the buffer position.
     * The buffer position advances by {@link #FLOAT_COUNT}.
     *
     * @param paint supported analytic paint, or null for texture and vertex colors
     * @param target buffer with space for the parameter block
     * @throws NullPointerException if the buffer is null
     * @throws IllegalArgumentException if the gradient has more than four stops
     */
    public static void write(Paint paint, FloatBuffer target) {
        Objects.requireNonNull(target, "target");
        if (paint != null && !ShapeCommand.supportsPaint(paint)) {
            throw new IllegalArgumentException("Analytic gradients support at most four stops.");
        }
        int start = target.position();
        for (int index = 0; index < FLOAT_COUNT; index++) {
            target.put(0);
        }
        List<GradientStop> stops;
        if (paint instanceof Paint.LinearGradient gradient) {
            target.put(start, gradient.start().x()).put(start + 1, gradient.start().y());
            target.put(start + 2, gradient.end().x()).put(start + 3, gradient.end().y());
            target.put(start + 24, 1);
            stops = gradient.stops();
        } else if (paint instanceof Paint.RadialGradient gradient) {
            target.put(start, gradient.center().x()).put(start + 1, gradient.center().y());
            target.put(start + 2, gradient.radius());
            target.put(start + 24, 2);
            stops = gradient.stops();
        } else {
            return;
        }
        target.put(start + 25, stops.size());
        for (int index = 0; index < stops.size(); index++) {
            Color color = stops.get(index).color();
            int offset = start + 4 + index * 4;
            target.put(offset, color.r()).put(offset + 1, color.g());
            target.put(offset + 2, color.b()).put(offset + 3, color.a());
            target.put(start + 20 + index, stops.get(index).offset());
        }
    }
}
