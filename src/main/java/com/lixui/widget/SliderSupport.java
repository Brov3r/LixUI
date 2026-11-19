package com.lixui.widget;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;

import java.util.Locale;

/**
 * Shared range math, colors and drawing helpers for slider widgets.
 */
final class SliderSupport {
    private static final float KEYBOARD_STEP_FRACTION = 0.01f;
    private static final float PAGE_STEP_FRACTION = 0.1f;
    private static final float LABEL_GAP = 4;
    private static final float TICK_OVERSHOOT = 6;
    private static final float TOOLTIP_PADDING_X = 6;
    private static final float TOOLTIP_PADDING_Y = 4;
    private static final float TOOLTIP_GAP = 4;
    private static final float TOOLTIP_RADIUS = 4;

    /**
     * Prevents instantiation of the utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private SliderSupport() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    /**
     * Validates a finite range with a minimum below the maximum.
     *
     * @param minimum range start
     * @param maximum range end
     * @throws IllegalArgumentException if a bound is non-finite or the minimum is not below the
     *                                  maximum
     */
    static void validateRange(float minimum, float maximum) {
        if (!Float.isFinite(minimum) || !Float.isFinite(maximum) || minimum >= maximum) {
            throw new IllegalArgumentException(
                    "Slider range must be finite with a minimum below the maximum.");
        }
    }

    /**
     * Validates that a value is finite.
     *
     * @param value value to validate
     * @param name value name used in the error message
     * @return the validated value
     * @throws IllegalArgumentException if the value is non-finite
     */
    static float finite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite.");
        }
        return value;
    }

    /**
     * Clamps a value into the supplied range.
     *
     * @param value value to clamp
     * @param minimum range start
     * @param maximum range end
     * @return the clamped value
     */
    static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * Clamps a value and snaps it to the step grid. A zero step disables snapping.
     *
     * @param value value to quantize
     * @param minimum range start
     * @param maximum range end
     * @param step step size, or zero for a continuous range
     * @return the clamped and quantized value
     */
    static float quantize(float value, float minimum, float maximum, float step) {
        float clamped = clamp(value, minimum, maximum);
        if (step <= 0) {
            return clamped;
        }
        return clamp(minimum + Math.round((clamped - minimum) / step) * step,
                minimum, maximum);
    }

    /**
     * Returns the normalized position of a value within a range.
     *
     * @param value value to normalize
     * @param minimum range start
     * @param maximum range end
     * @return a value in the {@code [0, 1]} range
     */
    static float fraction(float value, float minimum, float maximum) {
        if (maximum <= minimum) {
            return 0;
        }
        return clamp((value - minimum) / (maximum - minimum), 0, 1);
    }

    /**
     * Converts a normalized position into a value.
     *
     * @param fraction normalized position
     * @param minimum range start
     * @param maximum range end
     * @return the value at the position
     */
    static float valueAt(float fraction, float minimum, float maximum) {
        return minimum + clamp(fraction, 0, 1) * (maximum - minimum);
    }

    /**
     * Returns the value change applied by an arrow key.
     *
     * @param minimum range start
     * @param maximum range end
     * @param step configured step, or zero for a continuous range
     * @return the keyboard increment
     */
    static float keyboardStep(float minimum, float maximum, float step) {
        return step > 0 ? step : (maximum - minimum) * KEYBOARD_STEP_FRACTION;
    }

    /**
     * Returns the value change applied by a page key.
     *
     * @param minimum range start
     * @param maximum range end
     * @return the page increment
     */
    static float pageStep(float minimum, float maximum) {
        return (maximum - minimum) * PAGE_STEP_FRACTION;
    }

    /**
     * Returns the inactive track color.
     *
     * @param theme current theme
     * @param enabled whether the control is enabled
     * @return the track color
     */
    static Color trackColor(Theme theme, boolean enabled) {
        return enabled ? theme.border() : theme.border().withAlpha(0.35f);
    }

    /**
     * Returns the filled accent color.
     *
     * @param theme current theme
     * @param enabled whether the control is enabled
     * @return the accent color
     */
    static Color accentColor(Theme theme, boolean enabled) {
        return enabled ? theme.primary() : theme.textMuted().withAlpha(0.22f);
    }

    /**
     * Returns the thumb color.
     *
     * @param theme current theme
     * @param enabled whether the control is enabled
     * @param highlighted true while hovered, pressed or focused
     * @return the thumb color
     */
    static Color thumbColor(Theme theme, boolean enabled, boolean highlighted) {
        if (!enabled) {
            return theme.textMuted().withAlpha(0.45f);
        }
        return highlighted ? theme.primary() : theme.text();
    }

    /**
     * Returns the vertical space reserved for one row of range labels.
     *
     * @param font label font, or null when text cannot be measured
     * @param labels whether labels are shown
     * @return the row height in logical UI units
     */
    static float labelRow(Font font, boolean labels) {
        return labels && font != null ? font.lineHeight() + LABEL_GAP : 0;
    }

    /**
     * Returns the bounds of the minimum range label.
     *
     * @param bounds widget-local bounds
     * @param font label font
     * @param vertical true for a vertical slider
     * @param text formatted label text
     * @return the label bounds in widget-local coordinates
     */
    static Rect minLabelBounds(Rect bounds, Font font, boolean vertical, String text) {
        float width = font.measure(text).width();
        float height = font.lineHeight();
        return vertical
                ? new Rect((bounds.width() - width) / 2, bounds.height() - height, width, height)
                : new Rect(0, bounds.height() - height, width, height);
    }

    /**
     * Returns the bounds of the maximum range label.
     *
     * @param bounds widget-local bounds
     * @param font label font
     * @param vertical true for a vertical slider
     * @param text formatted label text
     * @return the label bounds in widget-local coordinates
     */
    static Rect maxLabelBounds(Rect bounds, Font font, boolean vertical, String text) {
        float width = font.measure(text).width();
        float height = font.lineHeight();
        return vertical
                ? new Rect((bounds.width() - width) / 2, 0, width, height)
                : new Rect(bounds.width() - width, bounds.height() - height, width, height);
    }

    /**
     * Draws the minimum and maximum range labels.
     *
     * @param renderer drawing manager for the current frame
     * @param font label font
     * @param theme current theme
     * @param vertical true for a vertical slider
     * @param enabled whether the control is enabled
     * @param bounds widget-local bounds
     * @param minText formatted minimum text
     * @param maxText formatted maximum text
     */
    static void drawLabels(RenderManager renderer, Font font, Theme theme, boolean vertical,
                           boolean enabled, Rect bounds, String minText, String maxText) {
        Color color = enabled ? theme.textMuted() : theme.textMuted().withAlpha(0.35f);
        float y = bounds.height() - font.lineHeight();
        if (vertical) {
            renderer.text(font, maxText, bounds.width() / 2, 0, color,
                    HorizontalAlignment.CENTER, VerticalAlignment.TOP);
            renderer.text(font, minText, bounds.width() / 2, y, color,
                    HorizontalAlignment.CENTER, VerticalAlignment.TOP);
        } else {
            renderer.text(font, minText, 0, y, color,
                    HorizontalAlignment.LEFT, VerticalAlignment.TOP);
            renderer.text(font, maxText, bounds.width(), y, color,
                    HorizontalAlignment.RIGHT, VerticalAlignment.TOP);
        }
    }

    /**
     * Draws evenly spaced tick marks across the track.
     *
     * @param renderer drawing manager for the current frame
     * @param theme current theme
     * @param vertical true for a vertical slider
     * @param enabled whether the control is enabled
     * @param center track center across the value axis
     * @param start track start along the value axis
     * @param length track length along the value axis
     * @param thickness track thickness
     * @param count number of equal intervals
     */
    static void drawTicks(RenderManager renderer, Theme theme, boolean vertical, boolean enabled,
                          float center, float start, float length, float thickness, int count) {
        if (count <= 0 || length <= 0) {
            return;
        }
        Color color = trackColor(theme, enabled);
        float tick = thickness + TICK_OVERSHOOT;
        for (int index = 0; index <= count; index++) {
            float fraction = (float) index / count;
            if (vertical) {
                float y = start + length * (1 - fraction);
                renderer.strokeLine(new Point(center - tick / 2, y),
                        new Point(center + tick / 2, y), 1, Paint.solid(color));
            } else {
                float x = start + length * fraction;
                renderer.strokeLine(new Point(x, center - tick / 2),
                        new Point(x, center + tick / 2), 1, Paint.solid(color));
            }
        }
    }

    /**
     * Draws a round thumb with a hollow outline when disabled.
     *
     * @param renderer drawing manager for the current frame
     * @param theme current theme
     * @param enabled whether the control is enabled
     * @param highlighted true while hovered, pressed or focused
     * @param centerX thumb center X
     * @param centerY thumb center Y
     * @param diameter thumb diameter
     */
    static void drawThumb(RenderManager renderer, Theme theme, boolean enabled, boolean highlighted,
                          float centerX, float centerY, float diameter) {
        float radius = diameter / 2;
        if (radius <= 0) {
            return;
        }
        Rect bounds = new Rect(centerX - radius, centerY - radius, diameter, diameter);
        if (enabled) {
            renderer.roundedRect(bounds, radius, thumbColor(theme, true, highlighted));
            return;
        }
        renderer.roundedRect(bounds, radius, theme.textMuted().withAlpha(0.4f));
        float inner = radius - 1;
        if (inner > 0) {
            renderer.roundedRect(new Rect(centerX - inner, centerY - inner, inner * 2, inner * 2),
                    inner, theme.surface());
        }
    }

    /**
     * Draws a floating value tooltip above or below its anchor.
     *
     * @param renderer drawing manager for the current frame
     * @param font tooltip font
     * @param text tooltip text
     * @param anchorX horizontal anchor position
     * @param anchorTop top of the element the tooltip must not overlap
     * @param anchorBottom bottom of the element the tooltip must not overlap
     * @param alpha tooltip opacity in {@code [0, 1]}
     * @param theme current theme
     * @param bounds widget-local bounds used to keep the tooltip visible
     */
    static void drawTooltip(RenderManager renderer, Font font, String text, float anchorX,
                            float anchorTop, float anchorBottom, float alpha, Theme theme,
                            Rect bounds) {
        Size textSize = font.measure(text);
        float width = textSize.width() + TOOLTIP_PADDING_X * 2;
        float height = textSize.height() + TOOLTIP_PADDING_Y * 2;
        float x = clamp(anchorX - width / 2, 0, Math.max(0, bounds.width() - width));
        float above = anchorTop - height - TOOLTIP_GAP;
        float below = anchorBottom + TOOLTIP_GAP;
        float y;
        if (above >= 0) {
            y = above;
        } else if (below + height <= bounds.height()) {
            y = below;
        } else {
            float roomAbove = anchorTop;
            float roomBelow = bounds.height() - anchorBottom;
            y = roomAbove >= roomBelow ? above : below;
        }
        Rect tooltip = new Rect(x, y, width, height);
        renderer.roundedRect(tooltip, TOOLTIP_RADIUS,
                theme.surface().withAlpha(0.95f * alpha));
        renderer.strokeRoundedRect(tooltip, TOOLTIP_RADIUS, 1,
                Paint.solid(theme.border().withAlpha(alpha)));
        renderer.text(font, text, x + width / 2, y + height / 2,
                theme.text().withAlpha(alpha),
                HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
    }

    /**
     * Formats a range bound with a compact suffix for large magnitudes.
     *
     * @param value value to format
     * @return grouped text, or a value shortened with a {@code k}, {@code m} or {@code b} suffix
     */
    static String formatCompact(float value) {
        double absolute = Math.abs((double) value);
        String sign = value < 0 ? "-" : "";
        if (absolute >= 1_000_000_000d) {
            return sign + compactNumber(absolute / 1_000_000_000d) + "b";
        }
        if (absolute >= 1_000_000d) {
            return sign + compactNumber(absolute / 1_000_000d) + "m";
        }
        if (absolute >= 10_000d) {
            return sign + compactNumber(absolute / 1_000d) + "k";
        }
        return sign + groupedNumber(absolute);
    }

    /**
     * Formats a value with space-separated thousands groups and up to three decimals.
     *
     * @param value value to format
     * @return the grouped value
     */
    static String formatFull(float value) {
        String sign = value < 0 ? "-" : "";
        return sign + groupedNumber(Math.abs((double) value));
    }

    private static String compactNumber(double value) {
        double rounded = Math.round(value * 10) / 10d;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        return Double.toString(rounded);
    }

    private static String groupedNumber(double value) {
        long whole = (long) Math.floor(value);
        int fraction = (int) Math.round((value - whole) * 1000d);
        if (fraction >= 1000) {
            whole++;
            fraction -= 1000;
        }
        String text = groupDigits(whole);
        if (fraction == 0) {
            return text;
        }
        String padded = String.format(Locale.ROOT, "%03d", fraction);
        int end = padded.length();
        while (end > 0 && padded.charAt(end - 1) == '0') {
            end--;
        }
        return text + "." + padded.substring(0, end);
    }

    private static String groupDigits(long value) {
        String digits = Long.toString(value);
        if (digits.length() <= 3) {
            return digits;
        }
        StringBuilder text = new StringBuilder(digits.length() + digits.length() / 3);
        int head = digits.length() % 3;
        if (head == 0) {
            head = 3;
        }
        text.append(digits, 0, head);
        for (int index = head; index < digits.length(); index += 3) {
            text.append(' ').append(digits, index, index + 3);
        }
        return text.toString();
    }
}
