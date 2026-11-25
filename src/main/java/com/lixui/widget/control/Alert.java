package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.render.Texture;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.IconSet;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.UiContext;

import java.util.Objects;

/**
 * An inline banner that communicates a status or a short message.
 * The tone selects the icon and the tinted surface and border colors. A title and a message can be
 * combined, the message wraps at the available width, and the optional close button hides the
 * banner and invokes a callback. Empty text parts are hidden.
 */
public class Alert extends Panel {
    /**
     * Semantic tone of an alert.
     */
    public enum Tone {
        /** Neutral informational message. */
        INFO("info.svg"),
        /** Successful operation or confirmation. */
        SUCCESS("tick.svg"),
        /** Warning that needs attention. */
        WARNING("exclamation.svg"),
        /** Error or destructive result. */
        DANGER("stop.svg"),
        /** Quiet neutral note. */
        NEUTRAL("info-02.svg");

        private final String iconName;

        Tone(String iconName) {
            this.iconName = iconName;
        }
    }

    private static final float ICON_SIZE = 20;
    private static final float CLOSE_SIZE = 20;
    private static final float CLOSE_ICON_SIZE = 14;
    private static final float GAP = 10;
    private static final float TEXT_GAP = 2;
    private static final float CORNER_RADIUS = 8;
    private static final float PADDING = 12;
    private static final float TONE_MIX = 0.14f;
    private static final float BORDER_MIX = 0.45f;

    private final Image toneIcon = new Image();
    private final Label titleLabel = new Label();
    private final Label messageLabel = new Label();
    private final Button closeButton = new Button();
    private final IconSet closeIcons = new IconSet("cross.svg");
    private Tone tone;
    private boolean closable;
    private Runnable onClose;

    /**
     * Creates an informational alert without text.
     */
    public Alert() {
        this(Tone.INFO);
    }

    /**
     * Creates an alert with the supplied tone and without text.
     *
     * @param tone semantic tone
     * @throws NullPointerException if {@code tone} is null
     */
    public Alert(Tone tone) {
        this.tone = Objects.requireNonNull(tone, "tone");
        toneIcon.setAutoSize(false);
        titleLabel.setWrap(true);
        titleLabel.setVisible(false);
        messageLabel.setWrap(true);
        messageLabel.setVisible(false);
        closeButton.setVisible(false);
        closeButton.setIconSize(CLOSE_ICON_SIZE);
        closeButton.setTooltipText("Dismiss the alert");
        closeButton.setOnAction(this::close);
        add(toneIcon).add(titleLabel).add(messageLabel).add(closeButton);
        updateTone();
    }

    /**
     * Returns the alert tone.
     *
     * @return the tone
     */
    public final Tone tone() {
        return tone;
    }

    /**
     * Replaces the alert tone and its icon.
     *
     * @param tone semantic tone
     * @throws NullPointerException if {@code tone} is null
     */
    public final void setTone(Tone tone) {
        Tone next = Objects.requireNonNull(tone, "tone");
        if (this.tone == next) {
            return;
        }
        this.tone = next;
        updateTone();
        applyTheme();
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
     * Returns the message text.
     *
     * @return the message, or an empty string when the message is hidden
     */
    public final String message() {
        return messageLabel.text();
    }

    /**
     * Replaces the message text.
     * The message wraps at the available width, and an empty string hides it.
     *
     * @param message message text
     * @throws NullPointerException if {@code message} is null
     */
    public final void setMessage(String message) {
        messageLabel.setText(Objects.requireNonNull(message, "message"));
        messageLabel.setVisible(!messageLabel.text().isEmpty());
    }

    /**
     * Reports whether the close button is shown.
     *
     * @return true when the alert is closable
     */
    public final boolean isClosable() {
        return closable;
    }

    /**
     * Shows or hides the close button.
     * Closing hides the alert and never removes it from its parent.
     *
     * @param closable true to show the close button
     */
    public final void setClosable(boolean closable) {
        this.closable = closable;
        closeButton.setVisible(closable);
    }

    /**
     * Returns the callback invoked after the alert closes.
     *
     * @return the close callback, or null when none is assigned
     */
    public final Runnable onClose() {
        return onClose;
    }

    /**
     * Assigns a callback invoked after the alert hides itself.
     *
     * @param onClose close callback, or null to clear it
     */
    public final void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    /**
     * Hides the alert and invokes the close callback.
     */
    public final void close() {
        setVisible(false);
        if (onClose != null) {
            onClose.run();
        }
    }

    /**
     * Applies theme colors and loads the close icon when the alert attaches.
     */
    @Override
    protected void onAttached() {
        applyTheme();
        if (closable) {
            ensureCloseIcon();
        }
    }

    /**
     * Releases the shared close icon when the alert detaches.
     */
    @Override
    protected void onDetached() {
        closeIcons.release(context());
    }

    /**
     * Reloads the close icon and reapplies tone colors after a context change.
     */
    @Override
    protected void onContextChanged() {
        closeIcons.release(context());
        applyTheme();
        if (closable) {
            ensureCloseIcon();
        }
    }

    /**
     * Measures the icon, wrapped text and close button plus padding.
     *
     * @param constraints available and required dimensions
     * @return the natural banner size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float padding = resolvePadding();
        float available = Math.max(0, constraints.maxWidth() - padding * 2);
        float closeWidth = closable ? CLOSE_SIZE + GAP : 0;
        float textWidth = Math.max(0, available - ICON_SIZE - GAP - closeWidth);
        Size titleSize = titleLabel.isVisible()
                ? titleLabel.measure(LayoutConstraints.loose(textWidth, constraints.maxHeight()))
                : new Size(0, 0);
        Size messageSize = messageLabel.isVisible()
                ? messageLabel.measure(LayoutConstraints.loose(textWidth, constraints.maxHeight()))
                : new Size(0, 0);
        float contentWidth = ICON_SIZE + GAP + Math.max(titleSize.width(), messageSize.width())
                + closeWidth;
        float textHeight = textHeight(titleSize, messageSize);
        float contentHeight = Math.max(ICON_SIZE, Math.max(textHeight,
                closable ? CLOSE_SIZE : 0));
        return new Size(Math.min(constraints.maxWidth(), contentWidth + padding * 2),
                contentHeight + padding * 2);
    }

    /**
     * Places the icon, text column and close button inside the padded surface.
     */
    @Override
    protected void onLayout() {
        WidgetStyle style = style();
        if (style == null) {
            return;
        }
        float padding = style.padding();
        float textX = padding + ICON_SIZE + GAP;
        float right = bounds().width() - padding - (closable ? CLOSE_SIZE + GAP : 0);
        float textWidth = Math.max(0, right - textX);
        Size titleSize = titleLabel.isVisible()
                ? titleLabel.measure(LayoutConstraints.loose(textWidth, bounds().height()))
                : new Size(0, 0);
        Size messageSize = messageLabel.isVisible()
                ? messageLabel.measure(LayoutConstraints.loose(textWidth, bounds().height()))
                : new Size(0, 0);
        float textHeight = textHeight(titleSize, messageSize);
        float contentHeight = Math.max(ICON_SIZE, Math.max(textHeight,
                closable ? CLOSE_SIZE : 0));
        float iconY = padding + (contentHeight - ICON_SIZE) / 2;
        toneIcon.layout(new Rect(padding, iconY, ICON_SIZE, ICON_SIZE));
        float y = padding;
        if (titleLabel.isVisible()) {
            titleLabel.layout(new Rect(textX, y, textWidth, titleSize.height()));
            y += titleSize.height() + (messageLabel.isVisible() ? TEXT_GAP : 0);
        }
        if (messageLabel.isVisible()) {
            messageLabel.layout(new Rect(textX, y, textWidth, messageSize.height()));
        }
        if (closable) {
            closeButton.layout(new Rect(bounds().width() - padding - CLOSE_SIZE,
                    padding + (contentHeight - CLOSE_SIZE) / 2, CLOSE_SIZE, CLOSE_SIZE));
            ensureCloseIcon();
        }
    }

    private float textHeight(Size titleSize, Size messageSize) {
        float title = titleLabel.isVisible() ? titleSize.height() : 0;
        float message = messageLabel.isVisible() ? messageSize.height() : 0;
        float gap = titleLabel.isVisible() && messageLabel.isVisible() ? TEXT_GAP : 0;
        return title + gap + message;
    }

    private float resolvePadding() {
        WidgetStyle style = style();
        return style == null ? PADDING : style.padding();
    }

    private void updateTone() {
        toneIcon.setIcon(tone.iconName);
    }

    private void applyTheme() {
        UiContext uiContext = context();
        if (uiContext == null) {
            return;
        }
        Theme theme = uiContext.theme();
        Color toneColor = toneColor(theme);
        Color background = Color.mix(theme.surface(), toneColor, TONE_MIX);
        Color border = Color.mix(theme.border(), toneColor, BORDER_MIX);
        setStyle(new WidgetStyle(background, background, background, background,
                theme.text(), theme.textMuted(), border, border, CORNER_RADIUS, PADDING, 1));
        titleLabel.setTextColor(theme.text());
        messageLabel.setTextColor(theme.textMuted());
        toneIcon.setTint(toneColor);
        WidgetStyle closeStyle = new WidgetStyle(Color.TRANSPARENT, theme.surfaceHover(),
                theme.surfacePressed(), Color.TRANSPARENT, theme.textMuted(),
                theme.textMuted().withAlpha(0.5f), Color.TRANSPARENT, Color.TRANSPARENT,
                CORNER_RADIUS, 2, 0);
        closeButton.setStyle(closeStyle);
        closeButton.setIconTint(theme.textMuted(), theme.text(), theme.text(),
                theme.textMuted().withAlpha(0.5f));
    }

    private Color toneColor(Theme theme) {
        return switch (tone) {
            case SUCCESS -> Color.hex("#4FC778");
            case WARNING -> Color.hex("#E8A23D");
            case DANGER -> Color.hex("#E05D5D");
            case NEUTRAL -> theme.textMuted();
            case INFO -> theme.primary();
        };
    }

    private void ensureCloseIcon() {
        UiContext uiContext = context();
        if (uiContext == null) {
            return;
        }
        int size = Math.max(1, Math.round(CLOSE_ICON_SIZE * uiContext.density()));
        if (!closeIcons.matches(uiContext, size)) {
            closeIcons.reload(uiContext, size);
        }
        Texture icon = closeIcons.get(0);
        if (icon != null) {
            closeButton.setIcon(icon);
        }
    }
}
