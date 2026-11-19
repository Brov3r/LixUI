package com.lixui.render;

/**
 * Axes on which a texture region repeats beyond its fitted tile.
 * Repeating an axis requires a texture created with a non-clamped wrap mode on that axis.
 */
public enum ImageRepeat {
    /**
     * Draws a single tile.
     */
    NONE,

    /**
     * Repeats the tile horizontally.
     */
    X,

    /**
     * Repeats the tile vertically.
     */
    Y,

    /**
     * Repeats the tile on both axes.
     */
    BOTH
}
