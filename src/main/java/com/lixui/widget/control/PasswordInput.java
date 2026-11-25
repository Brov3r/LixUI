package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.render.Texture;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.IconSet;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.UiContext;

/**
 * A password field with a reveal button that toggles secret rendering.
 * The field starts in secret mode. The optional eye button is drawn inside the trailing edge and
 * reserves content space, so the value and caret never overlap it. Hiding the button restores the
 * full content width. Toggling the button updates the stored secret flag and the visible icon only;
 * the value and change callback are left untouched.
 */
public class PasswordInput extends Input {
    private static final float DEFAULT_BUTTON_SIZE = 22;
    private static final float DEFAULT_ICON_SIZE = 16;
    private static final float BUTTON_GAP = 4;
    private static final float BUTTON_PADDING = 3;
    private static final float BUTTON_RADIUS = 4;

    private final Button revealButton = new Button();
    private final IconSet icons = new IconSet("visible.svg", "invisible.svg");
    private boolean revealButtonVisible = true;
    private boolean lastSecret = true;

    /**
     * Creates an empty secret field with the reveal button visible.
     */
    public PasswordInput() {
        this("");
    }

    /**
     * Creates a secret field with text and the reveal button visible.
     *
     * @param text initial value
     * @throws NullPointerException if {@code text} is null
     */
    public PasswordInput(String text) {
        super(text);
        setSecret(true);
        revealButton.setIconSize(DEFAULT_ICON_SIZE);
        revealButton.setTooltipText("Show or hide the password");
        revealButton.setOnAction(this::toggleSecret);
        add(revealButton);
    }

    /**
     * Reports whether the reveal button is shown.
     *
     * @return true when the eye button is visible
     */
    public final boolean isRevealButtonVisible() {
        return revealButtonVisible;
    }

    /**
     * Shows or hides the reveal button.
     * Hiding the button gives the text the full field content width.
     *
     * @param revealButtonVisible true to show the eye button
     */
    public final void setRevealButtonVisible(boolean revealButtonVisible) {
        this.revealButtonVisible = revealButtonVisible;
        revealButton.setVisible(revealButtonVisible);
    }

    /**
     * Returns the content rectangle with the reveal button space removed from the trailing edge.
     *
     * @param widgetStyle resolved style used for the field
     * @return the content rectangle in widget-local coordinates
     */
    @Override
    protected Rect contentRect(WidgetStyle widgetStyle) {
        Rect base = super.contentRect(widgetStyle);
        if (!revealButtonVisible || base.isEmpty()) {
            return base;
        }
        float side = Math.min(DEFAULT_BUTTON_SIZE, base.height());
        if (side <= 0) {
            return base;
        }
        return new Rect(base.x(), base.y(),
                Math.max(0, base.width() - side - BUTTON_GAP), base.height());
    }

    /**
     * Adds the reveal button width to the natural field size.
     *
     * @param constraints available and required dimensions
     * @return the natural field size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Size base = super.onMeasure(constraints);
        if (!revealButtonVisible) {
            return base;
        }
        return new Size(base.width() + DEFAULT_BUTTON_SIZE + BUTTON_GAP, base.height());
    }

    /**
     * Places the reveal button at the trailing edge of the content rectangle.
     */
    @Override
    protected void onLayout() {
        super.onLayout();
        revealButton.setEnabled(isEnabled());
        WidgetStyle style = resolvedStyle();
        if (style == null) {
            return;
        }
        Rect base = super.contentRect(style);
        float side = Math.min(DEFAULT_BUTTON_SIZE, base.height());
        if (!revealButtonVisible || base.isEmpty() || side <= 0) {
            revealButton.setVisible(false);
            return;
        }
        revealButton.setVisible(true);
        revealButton.layout(new Rect(base.x() + base.width() - side,
                base.y() + (base.height() - side) / 2, side, side));
        ensureIcons();
        if (lastSecret != isSecret()) {
            lastSecret = isSecret();
            updateRevealIcon();
        }
    }

    /**
     * Loads the eye icons when the field attaches.
     */
    @Override
    protected void onAttached() {
        applyTheme();
        ensureIcons();
        updateRevealIcon();
    }

    /**
     * Releases the shared eye icons when the field detaches.
     */
    @Override
    protected void onDetached() {
        icons.release(context());
    }

    /**
     * Reloads the icons and reapplies theme colors after a context change.
     */
    @Override
    protected void onContextChanged() {
        icons.release(context());
        applyTheme();
        ensureIcons();
        updateRevealIcon();
    }

    /**
     * Keeps the text field from moving the caret when the reveal button is pressed.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when the press belongs to the reveal button
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button == MouseButton.LEFT && revealButtonVisible
                && revealButton.bounds().contains(x, y)) {
            return true;
        }
        return super.onPointerPress(x, y, button, modifiers);
    }

    private void toggleSecret() {
        setSecret(!isSecret());
        lastSecret = isSecret();
        updateRevealIcon();
    }

    private void applyTheme() {
        UiContext uiContext = context();
        if (uiContext == null) {
            return;
        }
        Theme theme = uiContext.theme();
        WidgetStyle style = new WidgetStyle(Color.TRANSPARENT, theme.surfaceHover(),
                theme.surfacePressed(), Color.TRANSPARENT, theme.textMuted(),
                theme.textMuted().withAlpha(0.5f), Color.TRANSPARENT, Color.TRANSPARENT,
                BUTTON_RADIUS, BUTTON_PADDING, 0);
        revealButton.setStyle(style);
        revealButton.setIconTint(theme.textMuted(), theme.text(), theme.text(),
                theme.textMuted().withAlpha(0.5f));
    }

    private void ensureIcons() {
        UiContext uiContext = context();
        if (uiContext == null) {
            return;
        }
        int size = Math.max(1, Math.round(DEFAULT_ICON_SIZE * uiContext.density()));
        if (!icons.matches(uiContext, size)) {
            icons.reload(uiContext, size);
            updateRevealIcon();
        }
    }

    private void updateRevealIcon() {
        Texture icon = icons.get(isSecret() ? 0 : 1);
        if (icon != null) {
            revealButton.setIcon(icon);
        }
    }
}
