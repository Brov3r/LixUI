package com.lixui.render;

/**
 * How a texture region is mapped onto its destination rectangle.
 * Every mode except {@link #FILL} preserves the source aspect ratio.
 */
public enum ImageFit {
    /**
     * Stretches the source region to fill the destination rectangle, ignoring its aspect ratio.
     */
    FILL,

    /**
     * Scales the source region to fit inside the destination rectangle, leaving empty margins.
     */
    CONTAIN,

    /**
     * Scales the source region to cover the destination rectangle and crops the overflowing axis.
     */
    COVER,

    /**
     * Matches the destination width and scales the height proportionally.
     * The result may overflow the destination vertically.
     */
    FIT_WIDTH,

    /**
     * Matches the destination height and scales the width proportionally.
     * The result may overflow the destination horizontally.
     */
    FIT_HEIGHT,

    /**
     * Draws the source region at its pixel size in logical UI units without scaling.
     */
    NONE,

    /**
     * Behaves like {@link #NONE} while the source fits and like {@link #CONTAIN} when it is larger.
     */
    SCALE_DOWN
}
