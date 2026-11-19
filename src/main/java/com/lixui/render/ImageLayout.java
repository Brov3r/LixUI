package com.lixui.render;

import com.lixui.geometry.Rect;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;

/**
 * Resolves aspect-preserving fits and tiled repeats into a destination rectangle and UV region.
 */
final class ImageLayout {
    private ImageLayout() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated.");
    }

    static ImageQuad resolve(Rect bounds, Rect uv, int textureWidth, int textureHeight, ImageStyle style) {
        float sourceWidth = uv.width() * textureWidth;
        float sourceHeight = uv.height() * textureHeight;
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return new ImageQuad(bounds, uv);
        }

        float horizontal = horizontalAnchor(style.horizontal());
        float vertical = verticalAnchor(style.vertical());
        Rect tile;
        Rect sampled = uv;
        switch (style.fit()) {
            case FILL -> tile = bounds;
            case CONTAIN -> {
                float scale = scaleToContain(bounds, sourceWidth, sourceHeight);
                tile = anchored(bounds, sourceWidth * scale, sourceHeight * scale, horizontal, vertical);
            }
            case COVER -> {
                tile = bounds;
                float scale = Math.max(bounds.width() / sourceWidth, bounds.height() / sourceHeight);
                sampled = cropCenter(uv, bounds, sourceWidth * scale, sourceHeight * scale, horizontal, vertical);
            }
            case FIT_WIDTH -> {
                float scale = bounds.width() / sourceWidth;
                tile = anchored(bounds, bounds.width(), sourceHeight * scale, horizontal, vertical);
            }
            case FIT_HEIGHT -> {
                float scale = bounds.height() / sourceHeight;
                tile = anchored(bounds, sourceWidth * scale, bounds.height(), horizontal, vertical);
            }
            case NONE -> tile = anchored(bounds, sourceWidth, sourceHeight, horizontal, vertical);
            case SCALE_DOWN -> {
                float scale = Math.min(1, scaleToContain(bounds, sourceWidth, sourceHeight));
                tile = anchored(bounds, sourceWidth * scale, sourceHeight * scale, horizontal, vertical);
            }
            default -> throw new IllegalStateException("Unknown image fit.");
        }
        return repeat(bounds, tile, sampled, style.repeat());
    }

    static boolean repeatsHorizontally(ImageRepeat repeat) {
        return repeat == ImageRepeat.X || repeat == ImageRepeat.BOTH;
    }

    static boolean repeatsVertically(ImageRepeat repeat) {
        return repeat == ImageRepeat.Y || repeat == ImageRepeat.BOTH;
    }

    private static float scaleToContain(Rect bounds, float sourceWidth, float sourceHeight) {
        return Math.min(bounds.width() / sourceWidth, bounds.height() / sourceHeight);
    }

    private static Rect anchored(Rect bounds, float width, float height,
                                 float horizontal, float vertical) {
        return new Rect(bounds.x() + (bounds.width() - width) * horizontal,
                bounds.y() + (bounds.height() - height) * vertical, width, height);
    }

    private static Rect cropCenter(Rect uv, Rect bounds, float scaledWidth, float scaledHeight,
                                   float horizontal, float vertical) {
        float visibleWidth = bounds.width() / scaledWidth;
        float visibleHeight = bounds.height() / scaledHeight;
        float croppedWidth = uv.width() * visibleWidth;
        float croppedHeight = uv.height() * visibleHeight;
        return new Rect(uv.x() + (uv.width() - croppedWidth) * horizontal,
                uv.y() + (uv.height() - croppedHeight) * vertical, croppedWidth, croppedHeight);
    }

    private static ImageQuad repeat(Rect bounds, Rect tile, Rect uv, ImageRepeat repeat) {
        boolean repeatX = repeatsHorizontally(repeat);
        boolean repeatY = repeatsVertically(repeat);
        if (!repeatX && !repeatY) {
            return new ImageQuad(tile, uv);
        }
        float x = repeatX ? bounds.x() : tile.x();
        float y = repeatY ? bounds.y() : tile.y();
        float width = repeatX ? bounds.width() : tile.width();
        float height = repeatY ? bounds.height() : tile.height();
        float u = repeatX ? uv.x() + (bounds.x() - tile.x()) / tile.width() * uv.width() : uv.x();
        float v = repeatY ? uv.y() + (bounds.y() - tile.y()) / tile.height() * uv.height() : uv.y();
        float uWidth = repeatX ? uv.width() * bounds.width() / tile.width() : uv.width();
        float vHeight = repeatY ? uv.height() * bounds.height() / tile.height() : uv.height();
        return new ImageQuad(new Rect(x, y, width, height), new Rect(u, v, uWidth, vHeight));
    }

    private static float horizontalAnchor(HorizontalAlignment alignment) {
        return switch (alignment) {
            case LEFT, JUSTIFY -> 0;
            case CENTER -> 0.5f;
            case RIGHT -> 1;
        };
    }

    private static float verticalAnchor(VerticalAlignment alignment) {
        return switch (alignment) {
            case TOP -> 0;
            case CENTER -> 0.5f;
            case BOTTOM -> 1;
        };
    }

    record ImageQuad(Rect bounds, Rect uv) {
    }
}
