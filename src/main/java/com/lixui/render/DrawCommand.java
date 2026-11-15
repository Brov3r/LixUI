package com.lixui.render;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;

/**
 * A backend-neutral drawing primitive emitted by the UI.
 * Bounds, clipping and the optional corner radius are expressed in logical UI units.
 */
public sealed interface DrawCommand permits RoundedRectCommand, ImageCommand, MeshCommand,
        MaskBeginCommand, MaskEndCommand, MaskTextureBeginCommand, MaskTextureEndCommand {
    /**
     * Returns the rectangle occupied by the primitive.
     * Transformed primitives report their local bounds, before the transform is applied.
     *
     * @return the primitive bounds
     */
    Rect bounds();

    /**
     * Returns the uniform corner radius.
     * Commands without a uniform radius return zero.
     *
     * @return the non-negative corner radius
     */
    default float radius() {
        return 0;
    }

    /**
     * Returns the uniform fill color or image tint.
     * Commands without a uniform color return white.
     *
     * @return the straight-alpha color
     */
    default Color color() {
        return Color.WHITE;
    }

    /**
     * Returns the clipping rectangle captured when the command was submitted.
     *
     * @return the active clipping rectangle
     */
    Rect clip();
}
