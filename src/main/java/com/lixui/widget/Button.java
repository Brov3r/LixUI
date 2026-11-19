package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;

import java.util.Objects;

/**
 * A focusable text and icon button with state-aware surface colors and an activation callback.
 * The button activates on a completed pointer click, on Enter, or on Space release while focused.
 * Its default style is the context control style; use {@link #setStyle(WidgetStyle)} to assign a
 * primary or custom style.
 * An optional sound player callback is invoked before the action; the widget does not play audio
 * itself.
 */
public class Button extends Panel {
    private static final TextStyle CENTERED = TextStyle.of(
            HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
    private static final TextStyle LEADING = TextStyle.of(
            HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
    private static final Rect FULL_UV = new Rect(0, 0, 1, 1);
    private static final float DEFAULT_ICON_SIZE = 20;
    private static final float ICON_GAP = 8;

    private String text;
    private Runnable action;
    private Runnable soundPlayer;
    private Texture icon;
    private float iconSize = DEFAULT_ICON_SIZE;
    private Color iconTint;
    private Color iconHoverTint;
    private Color iconPressedTint;
    private Color iconDisabledTint;
    private boolean spacePressed;

    /**
     * Creates a button with an empty label.
     */
    public Button() {
        this("");
    }

    /**
     * Creates a button with a text label.
     *
     * @param text button label
     * @throws NullPointerException if {@code text} is null
     */
    public Button(String text) {
        this.text = Objects.requireNonNull(text, "text");
        setFocusable(true);
    }

    /**
     * Returns the button label.
     *
     * @return the current label
     */
    public final String text() {
        return text;
    }

    /**
     * Replaces the button label.
     *
     * @param text new label
     * @throws NullPointerException if {@code text} is null
     */
    public final void setText(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    /**
     * Returns the action invoked when the button activates.
     *
     * @return the action, or null when no action is assigned
     */
    public final Runnable action() {
        return action;
    }

    /**
     * Replaces the action invoked when the button activates.
     *
     * @param action action to run, or null to clear it
     */
    public final void setOnAction(Runnable action) {
        this.action = action;
    }

    /**
     * Returns the callback invoked when the button activates to play a sound.
     *
     * @return the sound callback, or null when none is assigned
     */
    public final Runnable soundPlayer() {
        return soundPlayer;
    }

    /**
     * Assigns a callback invoked before the button action.
     * The widget does not implement audio playback; hosts can call their sound system here.
     *
     * @param soundPlayer sound callback, or null to clear it
     */
    public final void setSoundPlayer(Runnable soundPlayer) {
        this.soundPlayer = soundPlayer;
    }

    /**
     * Returns the icon texture.
     *
     * @return the icon texture, or null when the button has no icon
     */
    public final Texture icon() {
        return icon;
    }

    /**
     * Assigns a caller-owned icon texture drawn at the configured icon size.
     *
     * @param icon icon texture, or null to clear it
     */
    public final void setIcon(Texture icon) {
        this.icon = icon;
    }

    /**
     * Returns the icon edge length in logical UI units.
     *
     * @return the square icon size
     */
    public final float iconSize() {
        return iconSize;
    }

    /**
     * Assigns the square icon edge length.
     *
     * @param iconSize positive edge length in logical UI units
     * @throws IllegalArgumentException if the size is not positive or is non-finite
     */
    public final void setIconSize(float iconSize) {
        if (!Float.isFinite(iconSize) || iconSize <= 0) {
            throw new IllegalArgumentException("Icon size must be finite and positive.");
        }
        this.iconSize = iconSize;
    }

    /**
     * Returns the explicit icon tint.
     *
     * @return the icon tint, or null when the state text color is used
     */
    public final Color iconTint() {
        return iconTint;
    }

    /**
     * Assigns an explicit icon tint.
     * When no tint is assigned, the icon uses the state-aware style text color.
     *
     * @param iconTint straight-alpha icon tint, or null to use the style text color
     */
    public final void setIconTint(Color iconTint) {
        this.iconTint = iconTint;
        this.iconHoverTint = null;
        this.iconPressedTint = null;
        this.iconDisabledTint = null;
    }

    /**
     * Assigns state-aware icon tints.
     * Null state tints fall back to the normal tint, then to the style text color.
     *
     * @param normal idle tint, or null to use the style text color
     * @param hovered hover tint, or null to fall back to the normal tint
     * @param pressed pressed tint, or null to fall back to the normal tint
     * @param disabled disabled tint, or null to use the style text color
     */
    public final void setIconTint(Color normal, Color hovered, Color pressed, Color disabled) {
        iconTint = normal;
        iconHoverTint = hovered;
        iconPressedTint = pressed;
        iconDisabledTint = disabled;
    }

    /**
     * Activates the button and runs its sound and action callbacks when the button is enabled.
     */
    public final void click() {
        if (!isEnabled()) {
            return;
        }
        if (soundPlayer != null) {
            soundPlayer.run();
        }
        if (action != null) {
            action.run();
        }
    }

    /**
     * Requests the pointer cursor for the button.
     *
     * @return the pointer cursor
     */
    @Override
    public Cursor cursor() {
        return Cursor.POINTER;
    }

    /**
     * Returns the control style from the current UI context.
     *
     * @return the context control style, or null when the button is not attached
     */
    @Override
    protected WidgetStyle defaultStyle() {
        return context() == null ? null : context().theme().controlStyle();
    }

    /**
     * Measures the icon and label with the active style padding.
     * An empty label still reserves the horizontal and vertical padding.
     *
     * @param constraints available and required dimensions
     * @return the natural button size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        WidgetStyle style = style();
        Font font = font();
        if (style == null) {
            return super.onMeasure(constraints);
        }
        float contentWidth = 0;
        float contentHeight = 0;
        if (font != null && !text.isEmpty()) {
            Size textSize = font.measure(text);
            contentWidth = textSize.width();
            contentHeight = textSize.height();
        }
        if (icon != null) {
            contentWidth += (font != null && !text.isEmpty() ? ICON_GAP : 0) + iconSize;
            contentHeight = Math.max(contentHeight, iconSize);
        }
        float padding = style.padding();
        return new Size(contentWidth + padding * 2, contentHeight + padding * 2);
    }

    /**
     * Paints the button surface, icon and label.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (localBounds().isEmpty()) {
            return;
        }
        super.onDraw(renderer, frame);
        WidgetStyle style = style();
        if (style == null) {
            return;
        }
        Font font = font();
        boolean hasText = font != null && !text.isEmpty();
        if (!hasText && icon == null) {
            return;
        }
        float centerX = localBounds().width() / 2;
        float centerY = localBounds().height() / 2;
        if (icon == null) {
            renderer.text(font, text, centerX, centerY, style.text(state()), CENTERED);
            return;
        }
        Size textSize = hasText ? font.measure(text) : new Size(0, 0);
        float contentWidth = iconSize + (hasText ? ICON_GAP + textSize.width() : 0);
        float contentX = centerX - contentWidth / 2;
        Color tint = iconColor(state(), style);
        renderer.image(icon, new Rect(contentX, centerY - iconSize / 2, iconSize, iconSize),
                0, FULL_UV, tint);
        if (hasText) {
            renderer.text(font, text, contentX + iconSize + ICON_GAP,
                    centerY, style.text(state()), LEADING);
        }
    }

    private Color iconColor(WidgetState state, WidgetStyle style) {
        Color tint = switch (state) {
            case HOVERED -> iconHoverTint;
            case PRESSED -> iconPressedTint;
            case DISABLED -> iconDisabledTint;
            default -> iconTint;
        };
        if (tint != null) {
            return tint;
        }
        if (state != WidgetState.DISABLED && iconTint != null) {
            return iconTint;
        }
        return style.text(state);
    }

    /**
     * Activates the button on a completed pointer click.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the click is consumed
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        click();
        return true;
    }

    /**
     * Activates the button on the second click of a double click.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the double click is consumed
     */
    @Override
    protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        click();
        return true;
    }

    /**
     * Activates the button on Enter and remembers Space until its release.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when the key is consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        if (key == Key.ENTER) {
            if (!repeat) {
                click();
            }
            return true;
        }
        if (key == Key.SPACE) {
            if (!repeat) {
                spacePressed = true;
            }
            return true;
        }
        return false;
    }

    /**
     * Activates the button when Space is released.
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
        boolean activate = spacePressed;
        spacePressed = false;
        if (activate) {
            click();
        }
        return true;
    }

    /**
     * Cancels a pending Space activation when focus is lost.
     *
     * @param focused whether this widget gained focus
     */
    @Override
    protected void onFocusChanged(boolean focused) {
        if (!focused) {
            spacePressed = false;
        }
    }
}
