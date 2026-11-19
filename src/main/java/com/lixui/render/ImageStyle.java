package com.lixui.render;

import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;

import java.util.Objects;

/**
 * Placement and repetition settings for an image draw command.
 * The alignment anchors a fitted image inside its destination rectangle and, together with
 * {@link ImageRepeat}, determines where the first repeated tile starts. {@link HorizontalAlignment#JUSTIFY}
 * is treated as {@link HorizontalAlignment#LEFT} because image lines cannot be justified.
 *
 * @param fit how the source region is scaled
 * @param horizontal horizontal anchor inside the destination rectangle
 * @param vertical vertical anchor inside the destination rectangle
 * @param repeat axes on which the fitted tile repeats
 */
public record ImageStyle(ImageFit fit, HorizontalAlignment horizontal, VerticalAlignment vertical,
                         ImageRepeat repeat) {
    /**
     * Fills the destination rectangle by stretching the source region.
     */
    public static final ImageStyle FILL =
            new ImageStyle(ImageFit.FILL, HorizontalAlignment.CENTER, VerticalAlignment.CENTER, ImageRepeat.NONE);

    /**
     * Fits the source region inside the destination rectangle and centers it.
     */
    public static final ImageStyle CONTAIN =
            new ImageStyle(ImageFit.CONTAIN, HorizontalAlignment.CENTER, VerticalAlignment.CENTER, ImageRepeat.NONE);

    /**
     * Covers the destination rectangle with the source region, cropping the overflow at the center.
     */
    public static final ImageStyle COVER =
            new ImageStyle(ImageFit.COVER, HorizontalAlignment.CENTER, VerticalAlignment.CENTER, ImageRepeat.NONE);

    /**
     * Matches the destination width and centers the proportional result vertically.
     */
    public static final ImageStyle FIT_WIDTH =
            new ImageStyle(ImageFit.FIT_WIDTH, HorizontalAlignment.CENTER, VerticalAlignment.CENTER, ImageRepeat.NONE);

    /**
     * Matches the destination height and centers the proportional result horizontally.
     */
    public static final ImageStyle FIT_HEIGHT =
            new ImageStyle(ImageFit.FIT_HEIGHT, HorizontalAlignment.CENTER, VerticalAlignment.CENTER, ImageRepeat.NONE);

    /**
     * Draws the source region at its pixel size, centered in the destination rectangle.
     */
    public static final ImageStyle NONE =
            new ImageStyle(ImageFit.NONE, HorizontalAlignment.CENTER, VerticalAlignment.CENTER, ImageRepeat.NONE);

    /**
     * Draws the source region at its pixel size unless it is larger than the destination rectangle.
     */
    public static final ImageStyle SCALE_DOWN =
            new ImageStyle(ImageFit.SCALE_DOWN, HorizontalAlignment.CENTER, VerticalAlignment.CENTER, ImageRepeat.NONE);

    /**
     * Preserves the aspect ratio of icons, which are commonly non-square or padded.
     */
    public static final ImageStyle ICON = CONTAIN;

    /**
     * Creates image style settings.
     *
     * @param fit how the source region is scaled
     * @param horizontal horizontal anchor inside the destination rectangle
     * @param vertical vertical anchor inside the destination rectangle
     * @param repeat axes on which the fitted tile repeats
     * @throws NullPointerException if an argument is null
     */
    public ImageStyle {
        Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(horizontal, "horizontal");
        Objects.requireNonNull(vertical, "vertical");
        Objects.requireNonNull(repeat, "repeat");
    }
}
