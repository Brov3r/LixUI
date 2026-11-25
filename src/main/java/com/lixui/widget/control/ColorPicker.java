package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.GradientStop;
import com.lixui.graphics.Paint;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A color field that opens a popup with a saturation/value square, hue and alpha strips and
 * hexadecimal and RGB entry.
 * The swatch and the hexadecimal text in the field follow the current color, and the popup preview
 * and alpha strip paint the color over a checkerboard so transparency stays visible. Dragging inside
 * the square or on either strip updates the color immediately, and the format callbacks report every
 * change through {@link #setOnChange(Consumer)}. The hexadecimal field applies on Enter and keeps the
 * current alpha when the text has no alpha digits, while the RGB fields always keep it. Escape closes
 * the popup without reverting already applied changes.
 */
public class ColorPicker extends PickerField {
    private static final float CONTENT_WIDTH = 220;
    private static final float SQUARE_HEIGHT = 150;
    private static final float STRIP_HEIGHT = 16;
    private static final float CONTENT_GAP = 8;
    private static final float SWATCH_SIZE = 18;
    private static final float SWATCH_GAP = 8;
    private static final float RGB_WIDTH = 68;
    private static final float RGB_GAP = 6;
    private static final float HANDLE_RADIUS = 6;
    private static final float CHECKER_SIZE = 8;

    private static final List<GradientStop> HUE_STOPS = List.of(
            new GradientStop(0, new Color(1, 0, 0, 1)),
            new GradientStop(1f / 6, new Color(1, 1, 0, 1)),
            new GradientStop(2f / 6, new Color(0, 1, 0, 1)),
            new GradientStop(3f / 6, new Color(0, 1, 1, 1)),
            new GradientStop(4f / 6, new Color(0, 0, 1, 1)),
            new GradientStop(5f / 6, new Color(1, 0, 1, 1)),
            new GradientStop(1, new Color(1, 0, 0, 1)));

    private final VBox popupContent = new VBox();
    private final SaturationValueBox square = new SaturationValueBox();
    private final ColorStrip hueStrip = new ColorStrip(false);
    private final ColorStrip alphaStrip = new ColorStrip(true);
    private final PreviewSwatch preview = new PreviewSwatch();
    private final Input hexInput = new Input();
    private final Input redInput = new Input();
    private final Input greenInput = new Input();
    private final Input blueInput = new Input();

    private float hue;
    private float saturation;
    private float value = 1;
    private float alpha = 1;
    private boolean syncing;
    private Consumer<Color> onChange;

    /**
     * Creates a picker showing white.
     */
    public ColorPicker() {
        this(Color.WHITE);
    }

    /**
     * Creates a picker showing the supplied color.
     *
     * @param color initial color
     * @throws NullPointerException if {@code color} is null
     */
    public ColorPicker(Color color) {
        super("palette.svg");
        applyColorState(Objects.requireNonNull(color, "color"));
        hexInput.setPlaceholder("#RRGGBB");
        hexInput.setMaxLength(9);
        hexInput.setOnSubmit(this::applyHexInput);
        configureChannel(redInput, "R", 0);
        configureChannel(greenInput, "G", 1);
        configureChannel(blueInput, "B", 2);
        Label hexLabel = new Label("Hex");
        hexLabel.setVerticalAlignment(VerticalAlignment.CENTER);
        hexInput.setGrow(1);
        HBox hexRow = new HBox();
        hexRow.setGap(RGB_GAP);
        hexRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        hexRow.add(hexLabel).add(hexInput);
        HBox rgbRow = new HBox();
        rgbRow.setGap(RGB_GAP);
        rgbRow.add(new WidthSlot(redInput)).add(new WidthSlot(greenInput))
                .add(new WidthSlot(blueInput));
        popupContent.setGap(CONTENT_GAP);
        popupContent.add(square).add(hueStrip).add(alphaStrip).add(preview)
                .add(hexRow).add(rgbRow);
        popup().setPadding(10);
        popup().setContent(popupContent);
        syncFields();
    }

    /**
     * Returns the selected color.
     *
     * @return the selected color
     */
    public final Color color() {
        return hsvColor(hue, saturation, value, alpha);
    }

    /**
     * Replaces the selected color.
     * The change callback runs only when the packed color actually changes.
     *
     * @param color new color
     * @throws NullPointerException if {@code color} is null
     */
    public final void setColor(Color color) {
        Color next = Objects.requireNonNull(color, "color");
        int before = color().toRgba();
        applyColorState(next);
        int after = color().toRgba();
        syncFields();
        notifyChange(before, after);
    }

    /**
     * Returns the color change callback.
     *
     * @return the callback, or null when none is assigned
     */
    public final Consumer<Color> onChange() {
        return onChange;
    }

    /**
     * Assigns the callback invoked when the selected color changes.
     *
     * @param onChange change callback, or null to clear it
     */
    public final void setOnChange(Consumer<Color> onChange) {
        this.onChange = onChange;
    }

    /**
     * Returns the hexadecimal text painted inside the field.
     *
     * @return the field text
     */
    @Override
    protected String fieldText() {
        return hexText();
    }

    /**
     * Reserves space for the leading swatch.
     *
     * @return the swatch width and gap
     */
    @Override
    protected float leadingWidth() {
        return SWATCH_SIZE + SWATCH_GAP;
    }

    /**
     * Paints the current color swatch before the hexadecimal text.
     *
     * @param renderer drawing manager for the current frame
     * @param bounds leading slot in field-local coordinates
     * @param state current widget state
     * @param style resolved field style
     */
    @Override
    protected void drawLeading(RenderManager renderer, Rect bounds, WidgetState state,
                               WidgetStyle style) {
        float size = Math.min(SWATCH_SIZE, Math.min(bounds.width(), bounds.height()));
        if (size <= 0) {
            return;
        }
        Rect swatch = new Rect(bounds.x(), bounds.y() + (bounds.height() - size) / 2, size, size);
        drawCheckerboard(renderer, swatch);
        renderer.roundedRect(swatch, 4, color());
        renderer.strokeRoundedRect(swatch, 4, 1, Paint.solid(style.border(state)));
    }

    /**
     * Opens the popup with the current color state.
     */
    @Override
    protected void openPopup() {
        if (context() == null || popup().isOpen()) {
            return;
        }
        syncFields();
        Rect anchor = new Rect(absolutePosition().x(), absolutePosition().y(),
                bounds().width(), bounds().height());
        popup().open(context(), anchor);
    }

    private void configureChannel(Input input, String placeholder, int channel) {
        input.setPlaceholder(placeholder);
        input.setAllowedCharacters(Input.CharacterKind.DIGITS);
        input.setMaxLength(3);
        input.setOnChange(text -> applyChannel(channel, text));
    }

    private void applyChannel(int channel, String text) {
        if (syncing) {
            return;
        }
        Integer parsed = parseChannel(text);
        if (parsed == null) {
            return;
        }
        Color current = color();
        float component = parsed / 255f;
        Color next = switch (channel) {
            case 0 -> new Color(component, current.g(), current.b(), current.a());
            case 1 -> new Color(current.r(), component, current.b(), current.a());
            default -> new Color(current.r(), current.g(), component, current.a());
        };
        int before = current.toRgba();
        applyColorState(next);
        int after = color().toRgba();
        syncFields();
        notifyChange(before, after);
    }

    private void applyHexInput() {
        if (syncing) {
            return;
        }
        String text = hexInput.text().trim();
        try {
            Color parsed = Color.hex(text);
            int digits = text.startsWith("#") ? text.length() - 1 : text.length();
            if (digits == 3 || digits == 6) {
                parsed = new Color(parsed.r(), parsed.g(), parsed.b(), alpha);
            }
            setColor(parsed);
        } catch (IllegalArgumentException invalid) {
            syncFields();
        }
    }

    private void updateHsv(float nextHue, float nextSaturation, float nextValue) {
        int before = color().toRgba();
        hue = normalizeHue(nextHue);
        saturation = clamp01(nextSaturation);
        value = clamp01(nextValue);
        int after = color().toRgba();
        syncFields();
        notifyChange(before, after);
    }

    private void updateAlpha(float nextAlpha) {
        int before = color().toRgba();
        alpha = clamp01(nextAlpha);
        int after = color().toRgba();
        syncFields();
        notifyChange(before, after);
    }

    private void applyColorState(Color color) {
        float red = color.r();
        float green = color.g();
        float blue = color.b();
        float maximum = Math.max(red, Math.max(green, blue));
        float minimum = Math.min(red, Math.min(green, blue));
        float delta = maximum - minimum;
        value = maximum;
        saturation = maximum == 0 ? 0 : delta / maximum;
        alpha = color.a();
        if (delta > 0) {
            if (maximum == red) {
                hue = 60 * (((green - blue) / delta) % 6);
            } else if (maximum == green) {
                hue = 60 * ((blue - red) / delta + 2);
            } else {
                hue = 60 * ((red - green) / delta + 4);
            }
            hue = normalizeHue(hue);
        }
    }

    private void syncFields() {
        syncing = true;
        try {
            hexInput.setText(hexText());
            redInput.setText(channelText(color().r()));
            greenInput.setText(channelText(color().g()));
            blueInput.setText(channelText(color().b()));
        } finally {
            syncing = false;
        }
    }

    private String hexText() {
        Color current = color();
        String hex = current.toHex();
        return current.a() >= 1 ? hex.substring(0, 7) : hex;
    }

    private static String channelText(float component) {
        return Integer.toString(Math.round(component * 255));
    }

    private static Integer parseChannel(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            return parsed >= 0 && parsed <= 255 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void notifyChange(int before, int after) {
        if (before != after && onChange != null) {
            onChange.accept(color());
        }
    }

    private static Color hsvColor(float hue, float saturation, float value, float alpha) {
        float position = normalizeHue(hue) / 60;
        int sector = (int) Math.floor(position);
        float fraction = position - sector;
        float saturationValue = clamp01(saturation);
        float brightness = clamp01(value);
        float p = brightness * (1 - saturationValue);
        float q = brightness * (1 - saturationValue * fraction);
        float t = brightness * (1 - saturationValue * (1 - fraction));
        float red;
        float green;
        float blue;
        switch (sector % 6) {
            case 0 -> {
                red = brightness;
                green = t;
                blue = p;
            }
            case 1 -> {
                red = q;
                green = brightness;
                blue = p;
            }
            case 2 -> {
                red = p;
                green = brightness;
                blue = t;
            }
            case 3 -> {
                red = p;
                green = q;
                blue = brightness;
            }
            case 4 -> {
                red = t;
                green = p;
                blue = brightness;
            }
            default -> {
                red = brightness;
                green = p;
                blue = q;
            }
        }
        return new Color(red, green, blue, clamp01(alpha));
    }

    private static float normalizeHue(float hue) {
        float normalized = hue % 360;
        return normalized < 0 ? normalized + 360 : normalized;
    }

    private static float clamp01(float value) {
        return Math.clamp(value, 0, 1);
    }

    private void drawCheckerboard(RenderManager renderer, Rect bounds) {
        if (bounds.isEmpty()) {
            return;
        }
        Theme theme = context().theme();
        for (float y = bounds.y(); y < bounds.bottom(); y += CHECKER_SIZE) {
            for (float x = bounds.x(); x < bounds.right(); x += CHECKER_SIZE) {
                boolean light = ((int) ((x - bounds.x()) / CHECKER_SIZE)
                        + (int) ((y - bounds.y()) / CHECKER_SIZE)) % 2 == 0;
                Rect cell = new Rect(x, y, Math.min(CHECKER_SIZE, bounds.right() - x),
                        Math.min(CHECKER_SIZE, bounds.bottom() - y));
                renderer.rect(cell, light ? theme.surface() : theme.border());
            }
        }
    }

    /**
     * Paints the current color over a checkerboard to make alpha visible.
     */
    private final class PreviewSwatch extends Widget {
        /**
         * Measures the preview bar.
         *
         * @param constraints available and required dimensions
         * @return the preview size
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            return new Size(Math.min(CONTENT_WIDTH, constraints.maxWidth()),
                    Math.min(26, constraints.maxHeight()));
        }

        /**
         * Paints the checkerboard, color overlay and border.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            if (localBounds().isEmpty()) {
                return;
            }
            drawCheckerboard(renderer, localBounds());
            renderer.roundedRect(localBounds(), 4, color());
            renderer.strokeRoundedRect(localBounds(), 4, 1,
                    Paint.solid(context().theme().border()));
        }
    }

    /**
     * The saturation/value square with a draggable selection handle.
     */
    private final class SaturationValueBox extends Widget {
        private SaturationValueBox() {
            setClipToBounds(true);
        }

        /**
         * Measures the configured square.
         *
         * @param constraints available and required dimensions
         * @return the natural square size
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            return new Size(Math.min(CONTENT_WIDTH, constraints.maxWidth()),
                    Math.min(SQUARE_HEIGHT, constraints.maxHeight()));
        }

        /**
         * Paints the saturation and value gradients with the selection handle.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            float width = localBounds().width();
            float height = localBounds().height();
            if (width <= 0 || height <= 0) {
                return;
            }
            Color hueColor = hsvColor(hue, 1, 1, 1);
            renderer.fillRoundedRect(localBounds(), 6, Paint.linear(
                    new Point(0, 0), new Point(width, 0),
                    new GradientStop(0, Color.WHITE), new GradientStop(1, hueColor)));
            renderer.fillRoundedRect(localBounds(), 6, Paint.linear(
                    new Point(0, 0), new Point(0, height),
                    new GradientStop(0, new Color(0, 0, 0, 0)),
                    new GradientStop(1, Color.BLACK)));
            Point handle = new Point(saturation * width, (1 - value) * height);
            renderer.fillCircle(handle, HANDLE_RADIUS, Paint.solid(Color.WHITE));
            renderer.strokeCircle(handle, HANDLE_RADIUS, 1, Paint.solid(Color.BLACK));
        }

        /**
         * Returns the crosshair cursor for precise color selection.
         *
         * @return the crosshair cursor
         */
        @Override
        public Cursor cursor() {
            return Cursor.CROSSHAIR;
        }

        /**
         * Starts dragging the selection handle.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button pressed button
         * @param modifiers active modifiers
         * @return true when the press was consumed
         */
        @Override
        protected boolean onPointerPress(float x, float y, MouseButton button,
                                         Modifiers modifiers) {
            if (button != MouseButton.LEFT) {
                return false;
            }
            capturePointer(button);
            update(x, y);
            return true;
        }

        /**
         * Updates the selection while the pointer moves with a pressed button.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @return false so the movement can bubble
         */
        @Override
        protected boolean onPointerMove(float x, float y) {
            if (isPressed()) {
                update(x, y);
            }
            return false;
        }

        /**
         * Updates the selection while dragging.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button pressed button
         * @param modifiers modifiers captured at press time
         * @return true when the drag was consumed
         */
        @Override
        protected boolean onDrag(float x, float y, MouseButton button, Modifiers modifiers) {
            update(x, y);
            return true;
        }

        /**
         * Releases the pointer capture.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button released button
         * @param modifiers active modifiers
         * @return true when the release was consumed
         */
        @Override
        protected boolean onPointerRelease(float x, float y, MouseButton button,
                                           Modifiers modifiers) {
            releasePointerCapture(button);
            return true;
        }

        private void update(float x, float y) {
            float width = localBounds().width();
            float height = localBounds().height();
            if (width <= 0 || height <= 0) {
                return;
            }
            updateHsv(hue, clamp01(x / width), 1 - clamp01(y / height));
        }
    }

    /**
     * A horizontal hue or alpha gradient strip with a draggable position marker.
     */
    private final class ColorStrip extends Widget {
        private final boolean alphaStrip;

        private ColorStrip(boolean alphaStrip) {
            this.alphaStrip = alphaStrip;
            setClipToBounds(true);
        }

        /**
         * Measures the configured strip.
         *
         * @param constraints available and required dimensions
         * @return the natural strip size
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            return new Size(Math.min(CONTENT_WIDTH, constraints.maxWidth()),
                    Math.min(STRIP_HEIGHT, constraints.maxHeight()));
        }

        /**
         * Paints the gradient and the position marker.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            float width = localBounds().width();
            float height = localBounds().height();
            if (width <= 0 || height <= 0) {
                return;
            }
            Paint gradient;
            float position;
            if (alphaStrip) {
                drawCheckerboard(renderer, localBounds());
                Color current = color();
                gradient = Paint.linear(new Point(0, 0), new Point(width, 0),
                        new GradientStop(0, new Color(current.r(), current.g(), current.b(), 0)),
                        new GradientStop(1, new Color(current.r(), current.g(), current.b(), 1)));
                position = alpha;
            } else {
                gradient = Paint.linear(new Point(0, 0), new Point(width, 0), HUE_STOPS);
                position = hue / 360;
            }
            renderer.fillRoundedRect(localBounds(), 4, gradient);
            float x = Math.clamp(position * width, 0, width);
            renderer.rect(new Rect(x - 2, 0, 4, height), Color.BLACK);
            renderer.rect(new Rect(x - 1, 0, 2, height), Color.WHITE);
        }

        /**
         * Returns the horizontal resize cursor for the strip.
         *
         * @return the horizontal resize cursor
         */
        @Override
        public Cursor cursor() {
            return Cursor.RESIZE_HORIZONTAL;
        }

        /**
         * Starts dragging the strip marker.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button pressed button
         * @param modifiers active modifiers
         * @return true when the press was consumed
         */
        @Override
        protected boolean onPointerPress(float x, float y, MouseButton button,
                                         Modifiers modifiers) {
            if (button != MouseButton.LEFT) {
                return false;
            }
            capturePointer(button);
            update(x);
            return true;
        }

        /**
         * Updates the strip while the pointer moves with a pressed button.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @return false so the movement can bubble
         */
        @Override
        protected boolean onPointerMove(float x, float y) {
            if (isPressed()) {
                update(x);
            }
            return false;
        }

        /**
         * Updates the strip while dragging.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button pressed button
         * @param modifiers modifiers captured at press time
         * @return true when the drag was consumed
         */
        @Override
        protected boolean onDrag(float x, float y, MouseButton button, Modifiers modifiers) {
            update(x);
            return true;
        }

        /**
         * Releases the pointer capture.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button released button
         * @param modifiers active modifiers
         * @return true when the release was consumed
         */
        @Override
        protected boolean onPointerRelease(float x, float y, MouseButton button,
                                           Modifiers modifiers) {
            releasePointerCapture(button);
            return true;
        }

        private void update(float x) {
            float width = localBounds().width();
            if (width <= 0) {
                return;
            }
            float fraction = clamp01(x / width);
            if (alphaStrip) {
                updateAlpha(fraction);
            } else {
                updateHsv(Math.min(359.999f, fraction * 360), saturation, value);
            }
        }
    }

    /**
     * Constrains an RGB field to an equal share of the row.
     */
    private static final class WidthSlot extends Widget {
        private final Input input;

        private WidthSlot(Input input) {
            this.input = input;
            add(input);
        }

        /**
         * Measures the input inside the fixed slot width.
         *
         * @param constraints available and required dimensions
         * @return the slot size
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            Size natural = input.measure(LayoutConstraints.loose(RGB_WIDTH,
                    constraints.maxHeight()));
            return new Size(Math.min(RGB_WIDTH, constraints.maxWidth()),
                    Math.min(natural.height(), constraints.maxHeight()));
        }

        /**
         * Stretches the input over the slot bounds.
         */
        @Override
        protected void onLayout() {
            input.layout(new Rect(0, 0, bounds().width(), bounds().height()));
        }
    }
}
