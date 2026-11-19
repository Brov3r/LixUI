package com.lixui.widget;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A focusable toggle control with a label and an indicator.
 * The indicator is either a square check box or a switch with a sliding thumb. Clicking the widget,
 * pressing Space while it is focused, or calling {@link #setChecked(boolean)} toggles it.
 * A {@link ToggleGroup} can limit or coordinate the selection, and tooltips use the inherited
 * widget tooltip API.
 */
public class CheckBox extends Panel {
    /**
     * Visual style of the indicator.
     */
    public enum Indicator {
        /** A square box with a check mark when selected. */
        BOX,
        /** A pill-shaped track with a thumb moved to the right when selected. */
        SWITCH
    }

    private static final float DEFAULT_BOX_SIZE = 16;
    private static final float DEFAULT_GAP = 8;
    private static final float DEFAULT_BOX_RADIUS = 3;
    private static final float DEFAULT_CHECK_THICKNESS = 2;
    private static final float DEFAULT_TRACK_WIDTH = 34;
    private static final float DEFAULT_TRACK_HEIGHT = 18;
    private static final float DEFAULT_THUMB_INSET = 2;
    private static final float DEFAULT_ANIMATION_RATE = 16;

    private String text;
    private boolean checked;
    private Indicator indicator = Indicator.BOX;
    private Consumer<Boolean> onChange;
    private ToggleGroup group;
    private float boxSize = DEFAULT_BOX_SIZE;
    private float gap = DEFAULT_GAP;
    private float boxRadius = DEFAULT_BOX_RADIUS;
    private float checkThickness = DEFAULT_CHECK_THICKNESS;
    private float trackWidth = DEFAULT_TRACK_WIDTH;
    private float trackHeight = DEFAULT_TRACK_HEIGHT;
    private float thumbInset = DEFAULT_THUMB_INSET;
    private float animationRate = DEFAULT_ANIMATION_RATE;
    private float checkedAmount;
    private boolean spacePressed;

    /**
     * Creates a check box without a label.
     */
    public CheckBox() {
        this("");
    }

    /**
     * Creates a check box with a label.
     *
     * @param text check box label
     * @throws NullPointerException if {@code text} is null
     */
    public CheckBox(String text) {
        this.text = Objects.requireNonNull(text, "text");
        setFocusable(true);
    }

    /**
     * Returns the check box label.
     *
     * @return the label text
     */
    public final String text() {
        return text;
    }

    /**
     * Replaces the check box label.
     *
     * @param text new label
     * @throws NullPointerException if {@code text} is null
     */
    public final void setText(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    /**
     * Reports whether the check box is selected.
     *
     * @return true when the check box is selected
     */
    public final boolean isChecked() {
        return checked;
    }

    /**
     * Selects or clears the check box, honoring its group limits.
     * The change callback is invoked when the state actually changes.
     *
     * @param checked true to select the check box
     */
    public final void setChecked(boolean checked) {
        if (this.checked == checked) {
            return;
        }
        if (group != null) {
            group.requestSelection(this, checked);
            return;
        }
        applyChecked(checked);
    }

    /**
     * Returns the callback invoked after the selection changes.
     *
     * @return the change callback, or null when none is assigned
     */
    public final Consumer<Boolean> onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked after the selection changes.
     *
     * @param onChange change callback, or null to clear it
     */
    public final void setOnChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
    }

    /**
     * Returns the indicator style.
     *
     * @return the indicator style
     */
    public final Indicator indicator() {
        return indicator;
    }

    /**
     * Sets the indicator style.
     *
     * @param indicator indicator style
     * @throws NullPointerException if {@code indicator} is null
     */
    public final void setIndicator(Indicator indicator) {
        this.indicator = Objects.requireNonNull(indicator, "indicator");
    }

    /**
     * Returns the group that coordinates this check box.
     *
     * @return the group, or null when the check box is independent
     */
    public final ToggleGroup group() {
        return group;
    }

    /**
     * Joins a selection group or leaves the current one.
     *
     * @param group group to join, or null to leave the current group
     */
    public final void setGroup(ToggleGroup group) {
        if (this.group == group) {
            return;
        }
        if (group == null) {
            if (this.group != null) {
                this.group.remove(this);
            }
        } else {
            group.add(this);
        }
    }

    /**
     * Returns the square indicator edge length used by the box style.
     *
     * @return the indicator size in logical UI units
     */
    public final float boxSize() {
        return boxSize;
    }

    /**
     * Sets the square indicator edge length used by the box style.
     *
     * @param boxSize positive size in logical UI units
     * @throws IllegalArgumentException if the size is not positive or non-finite
     */
    public final void setBoxSize(float boxSize) {
        this.boxSize = positive(boxSize, "Box size");
    }

    /**
     * Returns the gap between the indicator and the label.
     *
     * @return the gap in logical UI units
     */
    public final float gap() {
        return gap;
    }

    /**
     * Sets the gap between the indicator and the label.
     *
     * @param gap non-negative gap in logical UI units
     * @throws IllegalArgumentException if the gap is negative or non-finite
     */
    public final void setGap(float gap) {
        if (!Float.isFinite(gap) || gap < 0) {
            throw new IllegalArgumentException("Gap must be finite and non-negative.");
        }
        this.gap = gap;
    }

    /**
     * Returns the box corner radius.
     *
     * @return the radius in logical UI units
     */
    public final float boxRadius() {
        return boxRadius;
    }

    /**
     * Sets the box corner radius.
     *
     * @param boxRadius non-negative radius in logical UI units
     * @throws IllegalArgumentException if the radius is negative or non-finite
     */
    public final void setBoxRadius(float boxRadius) {
        if (!Float.isFinite(boxRadius) || boxRadius < 0) {
            throw new IllegalArgumentException("Box radius must be finite and non-negative.");
        }
        this.boxRadius = boxRadius;
    }

    /**
     * Returns the check mark stroke width.
     *
     * @return the stroke width in logical UI units
     */
    public final float checkThickness() {
        return checkThickness;
    }

    /**
     * Sets the check mark stroke width.
     *
     * @param checkThickness positive stroke width in logical UI units
     * @throws IllegalArgumentException if the width is not positive or non-finite
     */
    public final void setCheckThickness(float checkThickness) {
        this.checkThickness = positive(checkThickness, "Check thickness");
    }

    /**
     * Returns the switch track width.
     *
     * @return the track width in logical UI units
     */
    public final float trackWidth() {
        return trackWidth;
    }

    /**
     * Sets the switch track width.
     *
     * @param trackWidth positive track width in logical UI units
     * @throws IllegalArgumentException if the width is not positive or non-finite
     */
    public final void setTrackWidth(float trackWidth) {
        this.trackWidth = positive(trackWidth, "Track width");
    }

    /**
     * Returns the switch track height.
     *
     * @return the track height in logical UI units
     */
    public final float trackHeight() {
        return trackHeight;
    }

    /**
     * Sets the switch track height.
     *
     * @param trackHeight positive track height in logical UI units
     * @throws IllegalArgumentException if the height is not positive or non-finite
     */
    public final void setTrackHeight(float trackHeight) {
        if (!Float.isFinite(trackHeight) || trackHeight <= 0) {
            throw new IllegalArgumentException("Track height must be finite and positive.");
        }
        if (thumbInset * 2 >= trackHeight) {
            throw new IllegalArgumentException("Track height must leave room for the thumb.");
        }
        this.trackHeight = trackHeight;
    }

    /**
     * Returns the inset between the switch track and its thumb.
     *
     * @return the inset in logical UI units
     */
    public final float thumbInset() {
        return thumbInset;
    }

    /**
     * Sets the inset between the switch track and its thumb.
     *
     * @param thumbInset non-negative inset in logical UI units
     * @throws IllegalArgumentException if the inset is negative, non-finite or larger than half the track height
     */
    public final void setThumbInset(float thumbInset) {
        if (!Float.isFinite(thumbInset) || thumbInset < 0) {
            throw new IllegalArgumentException("Thumb inset must be finite and non-negative.");
        }
        if (thumbInset * 2 >= trackHeight) {
            throw new IllegalArgumentException("Thumb inset must leave room for the thumb.");
        }
        this.thumbInset = thumbInset;
    }

    /**
     * Returns the state animation speed.
     *
     * @return the exponential animation rate per second, or zero when transitions are instant
     */
    public final float animationRate() {
        return animationRate;
    }

    /**
     * Sets the state animation speed.
     * Zero disables the transition and applies state changes instantly.
     *
     * @param animationRate non-negative rate per second
     * @throws IllegalArgumentException if the rate is negative or non-finite
     */
    public final void setAnimationRate(float animationRate) {
        if (!Float.isFinite(animationRate) || animationRate < 0) {
            throw new IllegalArgumentException("Animation rate must be finite and non-negative.");
        }
        this.animationRate = animationRate;
        if (animationRate == 0) {
            checkedAmount = checked ? 1 : 0;
        }
    }

    /**
     * Advances the selection transition.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        float target = checked ? 1 : 0;
        if (animationRate == 0 || frame.deltaSeconds() <= 0) {
            if (animationRate == 0) {
                checkedAmount = target;
            }
            return;
        }
        float factor = 1 - (float) Math.exp(-animationRate * frame.deltaSeconds());
        float next = checkedAmount + (target - checkedAmount) * factor;
        checkedAmount = Math.abs(target - next) < 0.001f ? target : next;
    }

    /**
     * Toggles the check box when the left button is clicked.
     *
     * @param x horizontal pointer position
     * @param y vertical pointer position
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the click is consumed
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        requestToggle();
        return true;
    }

    /**
     * Toggles the check box on a double click.
     *
     * @param x horizontal pointer position
     * @param y vertical pointer position
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the double click is consumed
     */
    @Override
    protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
        return onClick(x, y, button, modifiers);
    }

    /**
     * Requests the pointer cursor for the check box.
     *
     * @return the pointer cursor
     */
    @Override
    public Cursor cursor() {
        return Cursor.POINTER;
    }

    /**
     * Remembers a Space press so the release can toggle the selection.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when the key is consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        if (key != Key.SPACE) {
            return false;
        }
        if (!repeat) {
            spacePressed = true;
        }
        return true;
    }

    /**
     * Toggles the check box when Space is released.
     *
     * @param key released key
     * @param modifiers active modifiers
     * @return true when the key is consumed
     */
    @Override
    protected boolean onKeyReleased(Key key, Modifiers modifiers) {
        if (key != Key.SPACE) {
            return false;
        }
        boolean toggle = spacePressed;
        spacePressed = false;
        if (toggle) {
            requestToggle();
        }
        return true;
    }

    /**
     * Requests a user-initiated selection toggle.
     * Radio buttons override this to stay selected once checked.
     */
    protected void requestToggle() {
        playSound();
        setChecked(!checked);
    }

    /**
     * Cancels a pending Space toggle when focus is lost.
     *
     * @param focused whether this widget gained focus
     */
    @Override
    protected void onFocusChanged(boolean focused) {
        if (!focused) {
            spacePressed = false;
        }
    }

    /**
     * Measures the indicator, gap and label.
     *
     * @param constraints available and required dimensions
     * @return the natural check box size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Font font = font();
        float textWidth = font == null || text.isEmpty() ? 0 : font.measure(text).width();
        float textHeight = font == null || text.isEmpty() ? 0 : font.measure(text).height();
        float width = indicatorWidth() + (textWidth > 0 ? gap + textWidth : 0);
        return new Size(width, Math.max(indicatorHeight(), textHeight));
    }

    /**
     * Paints the indicator and label.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (localBounds().isEmpty()) {
            return;
        }
        Theme theme = context().theme();
        WidgetStyle style = style();
        if (style == null) {
            return;
        }
        float indicatorHeight = indicatorHeight();
        float top = (localBounds().height() - indicatorHeight) / 2;
        drawIndicator(renderer, theme, new Rect(0, top, indicatorWidth(), indicatorHeight));
        Font font = font();
        if (font == null || text.isEmpty()) {
            return;
        }
        renderer.text(font, text, indicatorWidth() + gap, localBounds().height() / 2,
                style.text(state()), HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
    }

    /**
     * Returns the indicator width used by layout.
     *
     * @return the indicator width in logical UI units
     */
    protected float indicatorWidth() {
        return indicator == Indicator.SWITCH ? trackWidth : boxSize;
    }

    /**
     * Returns the indicator height used by layout.
     *
     * @return the indicator height in logical UI units
     */
    protected float indicatorHeight() {
        return indicator == Indicator.SWITCH ? trackHeight : boxSize;
    }

    /**
     * Returns the animated selection progress.
     *
     * @return zero for an unchecked indicator and one for a fully checked indicator
     */
    protected float checkedAmount() {
        return checkedAmount;
    }

    /**
     * Paints the indicator for the current selection and interaction state.
     *
     * @param renderer drawing manager for the current frame
     * @param theme current theme
     * @param bounds indicator rectangle in widget-local coordinates
     */
    protected void drawIndicator(RenderManager renderer, Theme theme, Rect bounds) {
        Color backdrop = backdrop();
        Color accent = indicatorAccent(theme);
        Color border = indicatorBorder(theme);
        if (indicator == Indicator.SWITCH) {
            drawSwitch(renderer, theme, bounds, backdrop, accent, border);
        } else {
            drawBox(renderer, bounds, theme, backdrop, accent, border);
        }
    }

    /**
     * Returns the accent color used by a selected indicator.
     *
     * @param theme current theme
     * @return the opaque accent color
     */
    protected Color indicatorAccent(Theme theme) {
        return isEnabled() ? theme.primary() : blend(backdrop(), theme.primary(), 0.35f);
    }

    /**
     * Returns the outline color used by an unselected indicator.
     *
     * @param theme current theme
     * @return the opaque outline color
     */
    protected Color indicatorBorder(Theme theme) {
        float amount = isEnabled() ? (isHovered() || isFocused() ? 0.75f : 0.55f) : 0.25f;
        return blend(backdrop(), theme.textMuted(), amount);
    }

    private void drawBox(RenderManager renderer, Rect bounds, Theme theme, Color backdrop,
                         Color accent, Color border) {
        float amount = checkedAmount;
        Color fill = blend(backdrop, accent, amount);
        Color ring = blend(border, accent, amount);
        drawRing(renderer, bounds, boxRadius, ring, fill);
        if (amount <= 0.01f) {
            return;
        }
        float left = bounds.x() + bounds.width() * 0.22f;
        float middleX = bounds.x() + bounds.width() * 0.43f;
        float right = bounds.x() + bounds.width() * 0.8f;
        float middleY = bounds.y() + bounds.height() * 0.72f;
        float topY = bounds.y() + bounds.height() * 0.3f;
        float centerY = bounds.y() + bounds.height() * 0.53f;
        Color mark = theme.onPrimary().withAlpha(theme.onPrimary().a() * amount);
        renderer.strokeLine(new Point(left, centerY), new Point(middleX, middleY),
                checkThickness, Paint.solid(mark));
        renderer.strokeLine(new Point(middleX, middleY), new Point(right, topY),
                checkThickness, Paint.solid(mark));
    }

    private void drawSwitch(RenderManager renderer, Theme theme, Rect bounds, Color backdrop,
                            Color accent, Color border) {
        float amount = checkedAmount;
        float radius = Math.min(bounds.height(), bounds.width()) / 2;
        Color uncheckedTrack = blend(backdrop, border, 0.9f);
        Color track = blend(uncheckedTrack, accent, amount);
        renderer.roundedRect(bounds, radius, track);
        float thumbSize = Math.max(0, bounds.height() - thumbInset * 2);
        float leftX = bounds.x() + thumbInset;
        float rightX = bounds.x() + bounds.width() - thumbInset - thumbSize;
        float thumbX = leftX + (rightX - leftX) * amount;
        Color uncheckedThumb = isEnabled() ? theme.text() : blend(backdrop, theme.text(), 0.35f);
        Color thumb = blend(uncheckedThumb, theme.onPrimary(), amount);
        renderer.roundedRect(new Rect(thumbX, bounds.y() + thumbInset, thumbSize, thumbSize),
                thumbSize / 2, thumb);
    }

    /**
     * Paints a one-pixel ring by layering a filled shape over a smaller one.
     *
     * @param renderer drawing manager for the current frame
     * @param bounds outer shape bounds
     * @param radius outer corner radius
     * @param ring ring color
     * @param interior color filling the area inside the ring
     */
    protected static void drawRing(RenderManager renderer, Rect bounds, float radius,
                                   Color ring, Color interior) {
        renderer.roundedRect(bounds, radius, ring);
        if (bounds.width() <= 2 || bounds.height() <= 2) {
            return;
        }
        renderer.roundedRect(new Rect(bounds.x() + 1, bounds.y() + 1,
                bounds.width() - 2, bounds.height() - 2), Math.max(0, radius - 1), interior);
    }

    /**
     * Returns the widget background composited with the theme surface.
     *
     * @return an opaque backdrop color
     */
    protected Color backdrop() {
        Theme theme = context().theme();
        WidgetStyle style = style();
        Color base = style == null ? theme.surface() : style.background(WidgetState.NORMAL);
        if (base.a() >= 1) {
            return base;
        }
        Color surface = theme.surface();
        float alpha = base.a();
        return new Color(surface.r() + (base.r() - surface.r()) * alpha,
                surface.g() + (base.g() - surface.g()) * alpha,
                surface.b() + (base.b() - surface.b()) * alpha, 1);
    }

    /**
     * Applies a selection change without consulting the group.
     *
     * @param checked new selection state
     */
    final void applyChecked(boolean checked) {
        if (this.checked == checked) {
            return;
        }
        this.checked = checked;
        if (animationRate == 0) {
            checkedAmount = checked ? 1 : 0;
        }
        if (onChange != null) {
            onChange.accept(checked);
        }
    }

    /**
     * Assigns the owning group without registering with it.
     *
     * @param group owning group, or null when the check box is independent
     */
    final void bindGroup(ToggleGroup group) {
        this.group = group;
    }

    /**
     * Blends two colors.
     *
     * @param from color at amount zero
     * @param to color at amount one
     * @param amount blend amount in {@code [0, 1]}
     * @return the blended color
     */
    protected static Color blend(Color from, Color to, float amount) {
        float clamped = Math.max(0, Math.min(1, amount));
        return new Color(from.r() + (to.r() - from.r()) * clamped,
                from.g() + (to.g() - from.g()) * clamped,
                from.b() + (to.b() - from.b()) * clamped,
                from.a() + (to.a() - from.a()) * clamped);
    }

    private static float positive(float value, String name) {
        if (!Float.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and positive.");
        }
        return value;
    }
}
