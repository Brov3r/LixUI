package com.lixui.render;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;

/**
 * A backend-neutral drawing primitive emitted by the UI.
 * Bounds, clipping and the optional corner radius are expressed in logical UI units.
 */
public sealed interface DrawCommand permits RoundedRectCommand, ImageCommand {
    /**
     * Returns the rectangle occupied by the primitive.
     *
     * @return the primitive bounds
     */
    Rect bounds();

    /**
     * Returns the corner radius, clamped to half of the smaller bound dimension.
     *
     * @return the non-negative corner radius
     */
    float radius();

    /**
     * Returns the fill color or image tint.
     *
     * @return the straight-alpha color
     */
    Color color();

    /**
     * Returns the clipping rectangle captured when the command was submitted.
     *
     * @return the active clipping rectangle
     */
    Rect clip();
}
