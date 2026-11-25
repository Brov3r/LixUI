package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Measures and paints the horizontal strip of selected-value chips shared by multi-select fields.
 * Whole chips are shown first; a trailing pill reports how many chips do not fit, so a narrow
 * field never truncates an individual chip to make room for the count.
 */
final class ChipStrip {
    static final float CHIP_HEIGHT = 20;
    static final float CHIP_PADDING = 9;
    static final float CHIP_GAP = 4;
    private static final float CHIP_FILL_AMOUNT = 0.18f;

    private final List<String> labels;

    private ChipStrip(List<String> labels) {
        this.labels = labels;
    }

    /**
     * Creates a strip from selected options, ignoring missing and disabled ones.
     *
     * @param options selected options in display order
     * @return the chip strip
     * @throws NullPointerException if {@code options} is null
     */
    static ChipStrip of(List<SelectOption> options) {
        Objects.requireNonNull(options, "options");
        List<String> labels = new ArrayList<>(options.size());
        for (SelectOption option : options) {
            if (option != null && !option.isDisabled()) {
                labels.add(option.label());
            }
        }
        return new ChipStrip(List.copyOf(labels));
    }

    /**
     * Reports whether the strip contains no visible chips.
     *
     * @return true when every option was missing or disabled
     */
    boolean isEmpty() {
        return labels.isEmpty();
    }

    /**
     * Measures the natural strip width and the chip height.
     *
     * @param font font used for labels, or null when no font is available
     * @return the full strip size including gaps
     */
    Size measure(Font font) {
        if (labels.isEmpty()) {
            return new Size(0, CHIP_HEIGHT);
        }
        float width = 0;
        float textHeight = 0;
        for (int index = 0; index < labels.size(); index++) {
            Size text = textSize(font, labels.get(index));
            if (index > 0) {
                width += CHIP_GAP;
            }
            width += text.width() + CHIP_PADDING * 2;
            textHeight = Math.max(textHeight, text.height());
        }
        return new Size(width, Math.max(CHIP_HEIGHT, textHeight));
    }

    /**
     * Measures the strip using the overflow policy for the supplied width.
     * Whole chips that fit are kept, and the remaining count collapses into a trailing pill.
     * When even one whole chip does not fit, the result is the width of the pill alone, clamped to
     * the available width.
     *
     * @param font font used for labels, or null when no font is available
     * @param availableWidth width available for the strip
     * @return the measured strip size within the available width
     */
    Size measure(Font font, float availableWidth) {
        Size natural = measure(font);
        if (availableWidth >= natural.width()) {
            return natural;
        }
        Plan plan = plan(font, availableWidth);
        return new Size(Math.max(0, Math.min(plan.width(), availableWidth)), natural.height());
    }

    /**
     * Chooses the whole chips that fit and counts the rest for the trailing pill.
     *
     * @param font font used for labels, or null when no font is available
     * @param availableWidth width available for the strip
     * @return the visible chips, the hidden count and the painted width
     */
    Plan plan(Font font, float availableWidth) {
        List<Float> widths = chipWidths(font);
        float natural = totalWidth(widths);
        if (natural <= availableWidth || labels.isEmpty()) {
            return new Plan(labels, 0, natural);
        }
        int visible = 0;
        for (int candidate = 1; candidate < labels.size(); candidate++) {
            int hidden = labels.size() - candidate;
            float width = prefixWidth(widths, candidate)
                    + CHIP_GAP + chipWidth(font, "+" + hidden);
            if (width > availableWidth) {
                break;
            }
            visible = candidate;
        }
        int hidden = labels.size() - visible;
        float width = prefixWidth(widths, visible)
                + (visible > 0 ? CHIP_GAP : 0) + chipWidth(font, "+" + hidden);
        return new Plan(labels.subList(0, visible), hidden, width);
    }

    /**
     * Paints the strip inside the content rectangle and clips it to the supplied absolute clip.
     *
     * @param renderer drawing manager for the current frame
     * @param content content rectangle in widget-local coordinates
     * @param clip absolute content clip
     * @param font font used for labels, or null when no font is available
     * @param theme theme providing the chip colors
     * @param disabled true to paint the labels in the muted disabled color
     * @throws NullPointerException if {@code renderer}, {@code content}, {@code clip} or {@code theme}
     *                              is null
     */
    void paint(RenderManager renderer, Rect content, Rect clip, Font font, Theme theme,
               boolean disabled) {
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(clip, "clip");
        Objects.requireNonNull(theme, "theme");
        if (labels.isEmpty() || content.isEmpty() || clip.isEmpty()) {
            return;
        }
        Color fill = Color.mix(theme.surface(), theme.primary(), CHIP_FILL_AMOUNT);
        Color text = disabled ? theme.textMuted() : theme.primary();
        renderer.pushClip(clip);
        try {
            paintClipped(renderer, content, font, fill, text);
        } finally {
            renderer.popClip();
        }
    }

    private void paintClipped(RenderManager renderer, Rect content, Font font,
                              Color fill, Color text) {
        float chipHeight = Math.min(CHIP_HEIGHT, content.height());
        float chipY = content.y() + (content.height() - chipHeight) / 2;
        Plan plan = plan(font, content.width());
        float chipX = content.x();
        for (String label : plan.labels()) {
            float width = chipWidth(font, label);
            drawChip(renderer, chipX, chipY, width, chipHeight, label, font, fill, text);
            chipX += width + CHIP_GAP;
        }
        if (plan.hidden() > 0) {
            String pill = "+" + plan.hidden();
            drawChip(renderer, chipX, chipY, chipWidth(font, pill), chipHeight, pill,
                    font, fill, text);
        }
    }

    private List<Float> chipWidths(Font font) {
        List<Float> widths = new ArrayList<>(labels.size());
        for (String label : labels) {
            widths.add(chipWidth(font, label));
        }
        return widths;
    }

    private static float prefixWidth(List<Float> widths, int count) {
        float total = 0;
        for (int index = 0; index < count; index++) {
            total += widths.get(index);
            if (index > 0) {
                total += CHIP_GAP;
            }
        }
        return total;
    }

    private static float chipWidth(Font font, String label) {
        return textSize(font, label).width() + CHIP_PADDING * 2;
    }

    private static Size textSize(Font font, String label) {
        return font == null ? new Size(0, 0) : font.measure(label);
    }

    private static float totalWidth(List<Float> widths) {
        return prefixWidth(widths, widths.size());
    }

    private static void drawChip(RenderManager renderer, float x, float y, float width,
                                 float height, String label, Font font, Color fill,
                                 Color textColor) {
        if (width <= 0 || height <= 0) {
            return;
        }
        renderer.roundedRect(new Rect(x, y, width, height), height / 2, fill);
        if (font != null) {
            renderer.text(font, label, x + CHIP_PADDING, y + height / 2, textColor,
                    clip(Math.max(0, width - CHIP_PADDING * 2)));
        }
    }

    private static TextStyle clip(float width) {
        return new TextStyle(width, 0, 1, TextOverflow.CLIP,
                HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
    }

    record Plan(List<String> labels, int hidden, float width) {
    }
}
