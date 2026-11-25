package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.text.HorizontalAlignment;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.layout.VBox;

import java.util.Objects;

/**
 * A placeholder shown when a view has no content yet.
 * The state stacks an optional icon, a title, a description and an optional action button in a
 * centered column. The description wraps at the available width, and the whole column is centered
 * inside the assigned bounds. A parent that measures the widget receives the natural content size.
 * Empty parts are hidden, so the same widget covers a bare title as well as a full placeholder.
 */
public class EmptyState extends Widget {
    private static final float DEFAULT_GAP = 8;
    private static final float DEFAULT_ICON_SIZE = 48;

    private final VBox content = new VBox();
    private final Image icon = new Image();
    private final Label titleLabel = new Label();
    private final Label descriptionLabel = new Label();
    private final Button actionButton = new Button();
    private Color iconTint;

    /**
     * Creates an empty state without text, icon or action.
     */
    public EmptyState() {
        content.setCrossAlignment(VBox.CrossAlignment.CENTER);
        content.setGap(DEFAULT_GAP);
        icon.setAutoSize(false);
        icon.setBounds(new Rect(0, 0, DEFAULT_ICON_SIZE, DEFAULT_ICON_SIZE));
        icon.setVisible(false);
        titleLabel.setAlignment(HorizontalAlignment.CENTER);
        titleLabel.setWrap(true);
        titleLabel.setVisible(false);
        descriptionLabel.setAlignment(HorizontalAlignment.CENTER);
        descriptionLabel.setWrap(true);
        descriptionLabel.setVisible(false);
        actionButton.setVisible(false);
        content.add(icon).add(titleLabel).add(descriptionLabel).add(actionButton);
        add(content);
    }

    /**
     * Creates an empty state with a title and description.
     *
     * @param title short headline, or an empty string to hide it
     * @param description supporting text, or an empty string to hide it
     * @throws NullPointerException if an argument is null
     */
    public EmptyState(String title, String description) {
        this();
        setTitle(title);
        setDescription(description);
    }

    /**
     * Returns the title text.
     *
     * @return the title, or an empty string when the title is hidden
     */
    public final String title() {
        return titleLabel.text();
    }

    /**
     * Replaces the title text.
     * An empty string hides the title.
     *
     * @param title title text
     * @throws NullPointerException if {@code title} is null
     */
    public final void setTitle(String title) {
        titleLabel.setText(Objects.requireNonNull(title, "title"));
        titleLabel.setVisible(!titleLabel.text().isEmpty());
    }

    /**
     * Returns the description text.
     *
     * @return the description, or an empty string when the description is hidden
     */
    public final String description() {
        return descriptionLabel.text();
    }

    /**
     * Replaces the description text.
     * The text wraps at the available width, and an empty string hides the description.
     *
     * @param description description text
     * @throws NullPointerException if {@code description} is null
     */
    public final void setDescription(String description) {
        descriptionLabel.setText(Objects.requireNonNull(description, "description"));
        descriptionLabel.setVisible(!descriptionLabel.text().isEmpty());
    }

    /**
     * Returns the bundled icon name.
     *
     * @return the icon resource name, or null when no icon is shown
     */
    public final String icon() {
        return icon.icon();
    }

    /**
     * Assigns a bundled icon drawn above the title.
     * Passing null hides the icon.
     *
     * @param icon bundled icon resource name, or null to hide the icon
     */
    public final void setIcon(String icon) {
        if (icon == null) {
            clearIcon();
            return;
        }
        this.icon.setIcon(icon);
        this.icon.setVisible(true);
    }

    /**
     * Hides and releases the bundled icon.
     */
    public final void clearIcon() {
        icon.clearIcon();
        icon.setVisible(false);
    }

    /**
     * Returns the square icon size.
     *
     * @return the icon edge length in logical UI units
     */
    public final float iconSize() {
        return icon.bounds().width();
    }

    /**
     * Sets the square icon size.
     *
     * @param iconSize positive edge length in logical UI units
     * @throws IllegalArgumentException if the size is not positive or non-finite
     */
    public final void setIconSize(float iconSize) {
        if (!Float.isFinite(iconSize) || iconSize <= 0) {
            throw new IllegalArgumentException("Icon size must be finite and positive.");
        }
        icon.setBounds(new Rect(0, 0, iconSize, iconSize));
    }

    /**
     * Returns the explicit icon tint.
     *
     * @return the icon tint, or null when the muted theme text color is used
     */
    public final Color iconTint() {
        return iconTint;
    }

    /**
     * Assigns an explicit icon tint.
     * Passing null restores the muted theme text color.
     *
     * @param iconTint icon tint, or null to follow the theme
     */
    public final void setIconTint(Color iconTint) {
        this.iconTint = iconTint;
        applyTheme();
    }

    /**
     * Returns the action button label.
     *
     * @return the action label, or an empty string when the action is hidden
     */
    public final String actionText() {
        return actionButton.text();
    }

    /**
     * Replaces the action button label.
     * An empty string hides the action button.
     *
     * @param actionText action label
     * @throws NullPointerException if {@code actionText} is null
     */
    public final void setActionText(String actionText) {
        actionButton.setText(Objects.requireNonNull(actionText, "actionText"));
        actionButton.setVisible(!actionButton.text().isEmpty());
    }

    /**
     * Returns the callback invoked when the action button activates.
     *
     * @return the action callback, or null when no callback is assigned
     */
    public final Runnable action() {
        return actionButton.action();
    }

    /**
     * Assigns the callback invoked when the action button activates.
     *
     * @param action action callback, or null to clear it
     */
    public final void setOnAction(Runnable action) {
        actionButton.setOnAction(action);
    }

    /**
     * Returns the space between stacked parts.
     *
     * @return the gap in logical UI units
     */
    public final float gap() {
        return content.gap();
    }

    /**
     * Sets the space between stacked parts.
     *
     * @param gap non-negative gap in logical UI units
     * @throws IllegalArgumentException if the gap is negative or non-finite
     */
    public final void setGap(float gap) {
        content.setGap(gap);
    }

    /**
     * Returns the padding around the stacked content.
     *
     * @return the padding in logical UI units
     */
    public final float contentPadding() {
        return content.padding();
    }

    /**
     * Sets the padding around the stacked content.
     *
     * @param padding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is negative or non-finite
     */
    public final void setContentPadding(float padding) {
        content.setPadding(padding);
    }

    /**
     * Applies theme-dependent colors when the widget attaches.
     */
    @Override
    protected void onAttached() {
        applyTheme();
    }

    /**
     * Applies theme-dependent colors again after a theme or density change.
     */
    @Override
    protected void onContextChanged() {
        applyTheme();
    }

    /**
     * Measures the natural size of the stacked content.
     *
     * @param constraints available and required dimensions
     * @return the natural empty state size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return content.measure(constraints);
    }

    /**
     * Centers the content column inside the assigned bounds.
     * The column is limited to the available size, so its wrapped text never exceeds the widget.
     */
    @Override
    protected void onLayout() {
        float maxWidth = Math.max(0, bounds().width());
        float maxHeight = Math.max(0, bounds().height());
        Size natural = content.measure(LayoutConstraints.loose(maxWidth, maxHeight));
        float width = Math.min(natural.width(), maxWidth);
        float height = Math.min(natural.height(), maxHeight);
        content.layout(new Rect((maxWidth - width) / 2, (maxHeight - height) / 2, width, height));
    }

    /**
     * Keeps the empty space around the content transparent to pointer input.
     *
     * @param x horizontal coordinate relative to the widget's top-left corner
     * @param y vertical coordinate relative to the widget's top-left corner
     * @return the hit child, or null when the point is outside the content
     */
    @Override
    public Widget hitTest(float x, float y) {
        Widget hit = super.hitTest(x, y);
        return hit == this ? null : hit;
    }

    private void applyTheme() {
        if (context() == null) {
            return;
        }
        descriptionLabel.setTextColor(context().theme().textMuted());
        icon.setTint(iconTint != null ? iconTint : context().theme().textMuted());
    }
}
