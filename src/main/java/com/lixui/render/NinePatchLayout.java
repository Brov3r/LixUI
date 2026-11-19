package com.lixui.render;

import com.lixui.geometry.Insets;
import com.lixui.geometry.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a nine-patch texture region into fixed corners, stretched edges and a stretched center.
 */
final class NinePatchLayout {
    private NinePatchLayout() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated.");
    }

    static List<Patch> split(Rect bounds, Rect uv, Insets insets, int textureWidth, int textureHeight) {
        float sourceWidth = uv.width() * textureWidth;
        float sourceHeight = uv.height() * textureHeight;
        if (insets.left() + insets.right() >= sourceWidth
                || insets.top() + insets.bottom() >= sourceHeight) {
            throw new IllegalArgumentException(
                    "Nine-patch insets must leave a positive center region.");
        }

        Axis horizontal = axis(bounds.x(), bounds.width(), uv.x(), uv.width(),
                insets.left(), insets.right(), textureWidth);
        Axis vertical = axis(bounds.y(), bounds.height(), uv.y(), uv.height(),
                insets.top(), insets.bottom(), textureHeight);

        List<Patch> patches = new ArrayList<>(9);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (horizontal.destinationSizes[column] <= 0 || vertical.destinationSizes[row] <= 0
                        || horizontal.uvSizes[column] <= 0 || vertical.uvSizes[row] <= 0) {
                    continue;
                }
                patches.add(new Patch(
                        new Rect(horizontal.destinationStarts[column], vertical.destinationStarts[row],
                                horizontal.destinationSizes[column], vertical.destinationSizes[row]),
                        new Rect(horizontal.uvStarts[column], vertical.uvStarts[row],
                                horizontal.uvSizes[column], vertical.uvSizes[row])));
            }
        }
        return patches;
    }

    private static Axis axis(float destinationStart, float destinationSize,
                             float uvStart, float uvSize, float before, float after, float textureSize) {
        float sum = before + after;
        float scale = sum > destinationSize ? destinationSize / sum : 1;
        float beforeSize = before * scale;
        float afterSize = after * scale;
        float centerSize = Math.max(0, destinationSize - beforeSize - afterSize);

        float beforeUv = before / textureSize;
        float afterUv = after / textureSize;
        float centerUv = uvSize - beforeUv - afterUv;

        return new Axis(
                new float[]{destinationStart, destinationStart + beforeSize,
                        destinationStart + destinationSize - afterSize},
                new float[]{beforeSize, centerSize, afterSize},
                new float[]{uvStart, uvStart + beforeUv, uvStart + uvSize - afterUv},
                new float[]{beforeUv, centerUv, afterUv});
    }

    record Patch(Rect bounds, Rect uv) {
    }

    private record Axis(float[] destinationStarts, float[] destinationSizes,
                        float[] uvStarts, float[] uvSizes) {
    }
}
