package com.lixui.widget.overlay;

import com.lixui.animation.Animation;
import com.lixui.animation.Easing;
import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.render.ImageStyle;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextLayout;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Image;
import com.lixui.widget.image.ImageSource;

import java.util.Objects;

/**
 * A notification card that can be shown standalone or hosted by a {@link ToastManager}.
 * A toast fades and slides in when it attaches, counts down while it is not hovered, and fades out
 * when its lifetime ends or {@link #dismiss()} is called. A standalone toast removes itself from
 * its parent or top-level manager when it finishes, while a managed toast reports completion
 * through an internal hook so the manager can promote a queued notification.
 * The default card shows a type icon, an optional title and wrapped message text; a caller can
 * replace that body with a custom widget through {@link Options#content(Widget)} or replace the
 * whole card visual through {@link Options#renderer(Renderer)}.
 */
public class Toast extends Panel {
    /**
     * Semantic notification type.
     * The type selects the default accent color and the bundled icon used when no explicit icon is
     * assigned.
     */
    public enum Type {
        /** Neutral informational message. */
        INFO("info.svg"),
        /** Successful operation or confirmation. */
        SUCCESS("tick.svg"),
        /** Warning that needs attention. */
        WARNING("exclamation.svg"),
        /** Error or failed operation. */
        ERROR("stop.svg");

        private final String iconName;

        Type(String iconName) {
            this.iconName = iconName;
        }

        /**
         * Returns the bundled icon name used by this type.
         *
         * @return the icon resource name
         */
        public String iconName() {
            return iconName;
        }
    }

    /**
     * Paints the whole toast card.
     * When a renderer is assigned, the default surface, border, body and remaining-time bar are
     * not drawn, so the renderer is responsible for every card visual. Custom content children are
     * still laid out and drawn above the renderer output.
     */
    @FunctionalInterface
    public interface Renderer {
        /**
         * Draws the complete toast card.
         *
         * @param toast toast being painted
         * @param renderer drawing manager positioned at the card's local origin
         * @param bounds full card bounds in local coordinates
         * @param frame timing information for the current frame
         */
        void render(Toast toast, RenderManager renderer, Rect bounds, FrameInfo frame);
    }

    /**
     * Presentation options for a single toast.
     * Options are mutable so a caller can build a notification fluently, and are copied when the
     * toast is constructed, so later changes do not affect a created card. The message is the only
     * required value; every other option has a default. A custom content widget replaces the
     * built-in icon, title and message body while keeping the card surface and timer. A custom
     * renderer replaces the whole card visual, including its surface and timer.
     */
    public static final class Options {
        private final String message;
        private Type type = Type.INFO;
        private String title = "";
        private ImageSource icon;
        private Color accent;
        private int maxLines = DEFAULT_MAX_LINES;
        private float maxWidth = DEFAULT_MAX_WIDTH;
        private float minCardWidth = DEFAULT_MIN_CARD_WIDTH;
        private float maxCardWidth = DEFAULT_MAX_CARD_WIDTH;
        private boolean showTimer = true;
        private float duration;
        private Widget content;
        private Renderer renderer;

        /**
         * Creates options for a notification message.
         *
         * @param message notification text, or an empty string for a content-only toast
         * @throws NullPointerException if {@code message} is null
         */
        public Options(String message) {
            this.message = Objects.requireNonNull(message, "message");
        }

        /**
         * Returns the notification text.
         *
         * @return the message
         */
        public String message() {
            return message;
        }

        /**
         * Returns the semantic type.
         *
         * @return the type
         */
        public Type type() {
            return type;
        }

        /**
         * Assigns the semantic type that selects the default color and icon.
         *
         * @param type semantic type
         * @return this options object
         * @throws NullPointerException if {@code type} is null
         */
        public Options type(Type type) {
            this.type = Objects.requireNonNull(type, "type");
            return this;
        }

        /**
         * Returns the title text.
         *
         * @return the title, or an empty string when no title is set
         */
        public String title() {
            return title;
        }

        /**
         * Assigns a title drawn on its own line above the message.
         * An empty title hides the title row. The title is ignored when custom content or a
         * renderer replaces the default body.
         *
         * @param title title text
         * @return this options object
         * @throws NullPointerException if {@code title} is null
         */
        public Options title(String title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /**
         * Returns the explicit icon source.
         *
         * @return the explicit source, or null when the type icon is used
         */
        public ImageSource icon() {
            return icon;
        }

        /**
         * Assigns an explicit icon source.
         * Textures, bundled icon names and remote sources are supported through
         * {@link ImageSource}. A null source restores the default type icon. The icon is ignored
         * when custom content or a renderer replaces the default body.
         *
         * @param icon explicit image source, or null to use the type icon
         * @return this options object
         */
        public Options icon(ImageSource icon) {
            this.icon = icon;
            return this;
        }

        /**
         * Assigns a caller-owned texture as the icon.
         * The texture is not released by the toast.
         *
         * @param texture icon texture, or null to use the type icon
         * @return this options object
         */
        public Options icon(Texture texture) {
            return icon(texture == null ? null : ImageSource.texture(texture));
        }

        /**
         * Returns the explicit accent color.
         *
         * @return the accent, or null when the type color is used
         */
        public Color accent() {
            return accent;
        }

        /**
         * Assigns an explicit accent color.
         * The accent tints the icon, the title and the remaining-time bar, and slightly colors the
         * card surface and border. A null accent restores the type color.
         *
         * @param accent straight-alpha accent color, or null to use the type color
         * @return this options object
         */
        public Options accent(Color accent) {
            this.accent = accent;
            return this;
        }

        /**
         * Returns the maximum number of message lines.
         *
         * @return the line limit, or zero when the message is not line-limited
         */
        public int maxLines() {
            return maxLines;
        }

        /**
         * Assigns the maximum number of visible message lines.
         * Additional lines are truncated with an ellipsis. Zero keeps every wrapped line.
         *
         * @param maxLines maximum line count, or zero for no limit
         * @return this options object
         * @throws IllegalArgumentException if the count is negative
         */
        public Options maxLines(int maxLines) {
            if (maxLines < 0) {
                throw new IllegalArgumentException("Maximum toast line count must be non-negative.");
            }
            this.maxLines = maxLines;
            return this;
        }

        /**
         * Returns the maximum message text width.
         *
         * @return the wrap width in logical UI units
         */
        public float maxWidth() {
            return maxWidth;
        }

        /**
         * Assigns the maximum message text width before lines wrap.
         * The card width can be bounded separately through {@link #minCardWidth(float)} and
         * {@link #maxCardWidth(float)}. For a renderer-only card this value becomes the preferred
         * card width.
         *
         * @param maxWidth positive wrap width in logical UI units
         * @return this options object
         * @throws IllegalArgumentException if the width is not finite or not positive
         */
        public Options maxWidth(float maxWidth) {
            if (!Float.isFinite(maxWidth) || maxWidth <= 0) {
                throw new IllegalArgumentException("Maximum toast text width must be finite and positive.");
            }
            this.maxWidth = maxWidth;
            return this;
        }

        /**
         * Returns the minimum card width.
         *
         * @return the lower bound in logical UI units
         */
        public float minCardWidth() {
            return minCardWidth;
        }

        /**
         * Assigns the minimum card width.
         * Short content stretches the card to this width.
         *
         * @param minCardWidth non-negative width in logical UI units
         * @return this options object
         * @throws IllegalArgumentException if the width is not finite or negative
         */
        public Options minCardWidth(float minCardWidth) {
            if (!Float.isFinite(minCardWidth) || minCardWidth < 0) {
                throw new IllegalArgumentException("Minimum toast card width must be finite and non-negative.");
            }
            this.minCardWidth = minCardWidth;
            return this;
        }

        /**
         * Returns the maximum card width.
         *
         * @return the upper bound in logical UI units
         */
        public float maxCardWidth() {
            return maxCardWidth;
        }

        /**
         * Assigns the maximum card width.
         * The card never grows past this width even when the available viewport is wider.
         *
         * @param maxCardWidth positive width in logical UI units
         * @return this options object
         * @throws IllegalArgumentException if the width is not finite or not positive
         */
        public Options maxCardWidth(float maxCardWidth) {
            if (!Float.isFinite(maxCardWidth) || maxCardWidth <= 0) {
                throw new IllegalArgumentException("Maximum toast card width must be finite and positive.");
            }
            this.maxCardWidth = maxCardWidth;
            return this;
        }

        /**
         * Reports whether the remaining-time bar is shown.
         *
         * @return true when the timer indicator is drawn
         */
        public boolean showTimer() {
            return showTimer;
        }

        /**
         * Shows or hides the remaining-time bar.
         * The countdown and automatic hiding continue when the indicator is hidden.
         *
         * @param showTimer true to draw the remaining-time bar
         * @return this options object
         */
        public Options showTimer(boolean showTimer) {
            this.showTimer = showTimer;
            return this;
        }

        /**
         * Returns the explicit lifetime.
         *
         * @return the lifetime in seconds, or zero when the toast default is used
         */
        public float duration() {
            return duration;
        }

        /**
         * Assigns an explicit lifetime.
         * When no lifetime is assigned, the hosting manager default is used for managed toasts and
         * four seconds for standalone toasts.
         *
         * @param duration positive lifetime in seconds
         * @return this options object
         * @throws IllegalArgumentException if the duration is not finite or not positive
         */
        public Options duration(float duration) {
            if (!Float.isFinite(duration) || duration <= 0) {
                throw new IllegalArgumentException("Toast duration must be finite and positive.");
            }
            this.duration = duration;
            return this;
        }

        /**
         * Returns the custom content widget.
         *
         * @return the custom content, or null when the default text body is used
         */
        public Widget content() {
            return content;
        }

        /**
         * Replaces the default icon, title and message body with a custom widget.
         * The widget is stretched across the card content area, and the optional remaining-time
         * bar stays below it unless a custom renderer replaces the whole card. The content must
         * not have a parent when the toast is created.
         *
         * @param content content widget without a parent, or null to restore the default body
         * @return this options object
         */
        public Options content(Widget content) {
            this.content = content;
            return this;
        }

        /**
         * Returns the custom card renderer.
         *
         * @return the renderer, or null when the default card is drawn
         */
        public Renderer renderer() {
            return renderer;
        }

        /**
         * Replaces the whole card visual with custom drawing.
         * The default surface, border, body and timer are skipped. A content widget can be
         * combined with the renderer; it draws above the renderer output.
         *
         * @param renderer custom card renderer, or null to draw the default card
         * @return this options object
         */
        public Options renderer(Renderer renderer) {
            this.renderer = renderer;
            return this;
        }

        private void validate() {
            if (minCardWidth > maxCardWidth) {
                throw new IllegalArgumentException(
                        "Minimum toast card width must not exceed the maximum card width.");
            }
        }
    }

    private static final int DEFAULT_MAX_LINES = 3;
    private static final float DEFAULT_MAX_WIDTH = 296;
    private static final float DEFAULT_MIN_CARD_WIDTH = 180;
    private static final float DEFAULT_MAX_CARD_WIDTH = 320;
    private static final float MIN_HEIGHT = 56;
    private static final float CARD_PADDING = 12;
    private static final float CORNER_RADIUS = 8;
    private static final float ICON_SIZE = 20;
    private static final float ICON_GAP = 10;
    private static final float ICON_SPACE = ICON_SIZE + ICON_GAP;
    private static final float TITLE_GAP = 2;
    private static final float PROGRESS_HEIGHT = 3;
    private static final float PROGRESS_GAP = 7;
    private static final float SURFACE_MIX = 0.1f;
    private static final float BORDER_MIX = 0.45f;
    private static final float APPEAR_SECONDS = 0.18f;
    private static final float EXIT_SECONDS = 0.15f;
    private static final float DEFAULT_DURATION = 4;

    private final String message;
    private final String title;
    private final Type type;
    private final ImageSource iconSource;
    private final Color accent;
    private final int maxLines;
    private final float maxWidth;
    private final float minCardWidth;
    private final float maxCardWidth;
    private final boolean showTimer;
    private final float duration;
    private final Widget content;
    private final Renderer customRenderer;
    private final Image iconView = new Image();
    private float textWidth;
    private float remaining;
    private float appear = 1;
    private float exit;
    private boolean exiting;
    private boolean finished;
    private boolean started;
    private Animation appearAnimation;
    private Animation exitAnimation;
    private Runnable dismissAction;
    private Runnable dismissListener;

    /**
     * Creates a toast with the default duration and body.
     *
     * @param message notification text
     * @throws NullPointerException if {@code message} is null
     */
    public Toast(String message) {
        this(new Options(message));
    }

    /**
     * Creates a toast with explicit presentation options.
     * A zero option duration falls back to four seconds.
     *
     * @param options toast presentation options
     * @throws NullPointerException if {@code options} is null
     * @throws IllegalArgumentException if the options are inconsistent or custom content already
     *                                  has a parent
     */
    public Toast(Options options) {
        this(requireOptions(options),
                options.duration() > 0 ? options.duration() : DEFAULT_DURATION);
    }

    Toast(Options options, float duration) {
        Objects.requireNonNull(options, "options");
        options.validate();
        this.message = options.message();
        this.title = options.title();
        this.type = options.type();
        this.iconSource = options.icon() != null
                ? options.icon() : ImageSource.icon(options.type().iconName());
        this.accent = options.accent();
        this.maxLines = options.maxLines();
        this.maxWidth = options.maxWidth();
        this.minCardWidth = options.minCardWidth();
        this.maxCardWidth = options.maxCardWidth();
        this.showTimer = options.showTimer();
        this.duration = duration;
        this.remaining = duration;
        this.content = options.content();
        this.customRenderer = options.renderer();
        setAutoSize(true);
        setAlwaysOnTop(true);
        setClipToBounds(true);
        setVisible(false);
        if (content == null && customRenderer == null) {
            iconView.setAutoSize(false);
            iconView.setVisible(false);
            iconView.setSource(iconSource);
            add(iconView);
        }
        if (content != null) {
            add(content);
        }
    }

    /**
     * Returns the notification text.
     *
     * @return the message
     */
    public final String message() {
        return message;
    }

    /**
     * Returns the semantic type.
     *
     * @return the type
     */
    public final Type type() {
        return type;
    }

    /**
     * Returns the title text.
     *
     * @return the title, or an empty string when the toast has no title
     */
    public final String title() {
        return title;
    }

    /**
     * Returns the effective icon source.
     *
     * @return the explicit icon source or the default type icon
     */
    public final ImageSource icon() {
        return iconSource;
    }

    /**
     * Returns the explicit accent color.
     *
     * @return the accent, or null when the toast uses the type color
     */
    public final Color accent() {
        return accent;
    }

    /**
     * Returns the maximum number of message lines.
     *
     * @return the line limit, or zero when the message is not line-limited
     */
    public final int maxLines() {
        return maxLines;
    }

    /**
     * Returns the maximum message text width.
     *
     * @return the wrap width in logical UI units
     */
    public final float maxWidth() {
        return maxWidth;
    }

    /**
     * Returns the minimum card width.
     *
     * @return the lower bound in logical UI units
     */
    public final float minCardWidth() {
        return minCardWidth;
    }

    /**
     * Returns the maximum card width.
     *
     * @return the upper bound in logical UI units
     */
    public final float maxCardWidth() {
        return maxCardWidth;
    }

    /**
     * Reports whether the remaining-time bar is drawn.
     *
     * @return true when the timer indicator is shown
     */
    public final boolean showTimer() {
        return showTimer;
    }

    /**
     * Returns the toast lifetime.
     *
     * @return the lifetime in seconds
     */
    public final float duration() {
        return duration;
    }

    /**
     * Returns the custom content widget.
     *
     * @return the custom content, or null when the default text body is used
     */
    public final Widget content() {
        return content;
    }

    /**
     * Returns the custom renderer.
     *
     * @return the renderer, or null when custom drawing is not used
     */
    public final Renderer renderer() {
        return customRenderer;
    }

    /**
     * Returns the remaining auto-hide time.
     *
     * @return seconds until the toast starts hiding
     */
    public final float remainingSeconds() {
        return remaining;
    }

    /**
     * Reports whether the toast has finished its exit and left its host.
     *
     * @return true when the toast will not be shown again
     */
    public final boolean isFinished() {
        return finished;
    }

    /**
     * Returns the toast bounds in viewport coordinates.
     *
     * @return the current bounds translated to the viewport origin
     */
    public final Rect viewportBounds() {
        Point position = absolutePosition();
        return new Rect(position.x(), position.y(), bounds().width(), bounds().height());
    }

    /**
     * Dismisses the toast, removing it from a queue or starting its exit animation.
     * A detached toast finishes immediately.
     */
    public final void dismiss() {
        if (finished) {
            return;
        }
        Runnable action = dismissAction;
        if (action != null) {
            action.run();
            return;
        }
        startExit();
    }

    void setDismissAction(Runnable dismissAction) {
        this.dismissAction = dismissAction;
    }

    void setDismissListener(Runnable dismissListener) {
        this.dismissListener = dismissListener;
    }

    /**
     * Cancels every animation and marks the toast finished without running completion hooks.
     */
    void abort() {
        finished = true;
        exiting = true;
        setVisible(false);
        cancelAnimations();
    }

    /**
     * Applies the type accent to the card surface and starts the entrance animation.
     */
    @Override
    protected void onAttached() {
        applyTheme();
        if (finished) {
            return;
        }
        setVisible(true);
        if (!started) {
            started = true;
            startAppear();
        }
    }

    /**
     * Stops animations when the toast leaves its host.
     */
    @Override
    protected void onDetached() {
        abort();
    }

    /**
     * Reapplies the type accent after a theme or context change.
     */
    @Override
    protected void onContextChanged() {
        applyTheme();
    }

    /**
     * Advances the countdown while the toast is attached, not hovered and not exiting.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        if (!started || finished || exiting || isHoveredWithin()) {
            return;
        }
        remaining -= frame.deltaSeconds();
        if (remaining <= 0) {
            remaining = 0;
            startExit();
        }
    }

    /**
     * Measures custom content or the default icon, title, wrapped message and timer bar.
     *
     * @param constraints available and required dimensions
     * @return the card size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float availableCardWidth = Math.max(1, Math.min(maxCardWidth, constraints.maxWidth()));
        float lowerBound = Math.min(minCardWidth, availableCardWidth);
        float timerRows = timerRows();
        if (content != null) {
            float innerWidth = Math.max(1, availableCardWidth - CARD_PADDING * 2);
            float innerHeight = Math.max(1, constraints.maxHeight() - CARD_PADDING * 2 - timerRows);
            Size contentSize = content.measure(LayoutConstraints.loose(innerWidth, innerHeight));
            float width = Math.clamp(contentSize.width() + CARD_PADDING * 2,
                    lowerBound, availableCardWidth);
            float height = Math.max(MIN_HEIGHT,
                    contentSize.height() + CARD_PADDING * 2 + timerRows);
            return new Size(Math.max(1, width), height);
        }
        if (customRenderer != null) {
            float width = Math.clamp(maxWidth, lowerBound, availableCardWidth);
            return new Size(Math.max(1, width),
                    Math.max(MIN_HEIGHT, CARD_PADDING * 2 + timerRows));
        }
        textWidth = textLimit(availableCardWidth);
        Font font = font();
        Size titleSize = measure(font, title, titleStyle(textWidth));
        Size messageSize = measure(font, message, messageStyle(textWidth));
        float naturalTextWidth = Math.max(titleSize.width(), messageSize.width());
        float width = Math.clamp(naturalTextWidth + CARD_PADDING * 2 + ICON_SPACE,
                lowerBound, availableCardWidth);
        float textHeight = titleSize.height()
                + (titleSize.height() > 0 && messageSize.height() > 0 ? TITLE_GAP : 0)
                + messageSize.height();
        float contentHeight = Math.max(ICON_SIZE, textHeight);
        float height = Math.max(MIN_HEIGHT, contentHeight + CARD_PADDING * 2 + timerRows);
        return new Size(Math.max(1, width), height);
    }

    /**
     * Arranges custom content or the default icon in the card.
     */
    @Override
    protected void onLayout() {
        float width = localBounds().width();
        float height = localBounds().height();
        if (content != null) {
            float innerWidth = Math.max(1, width - CARD_PADDING * 2);
            float innerHeight = Math.max(1, height - CARD_PADDING * 2 - timerRows());
            Size contentSize = content.measure(LayoutConstraints.loose(innerWidth, innerHeight));
            content.layout(new Rect(CARD_PADDING, CARD_PADDING, innerWidth,
                    Math.min(contentSize.height(), innerHeight)));
            return;
        }
        if (customRenderer != null) {
            return;
        }
        float availableText = Math.max(1, width - CARD_PADDING * 2 - ICON_SPACE);
        textWidth = Math.max(1, Math.min(maxWidth, availableText));
        Font font = font();
        Size titleSize = measure(font, title, titleStyle(textWidth));
        Size messageSize = measure(font, message, messageStyle(textWidth));
        float textHeight = titleSize.height()
                + (titleSize.height() > 0 && messageSize.height() > 0 ? TITLE_GAP : 0)
                + messageSize.height();
        float iconY = CARD_PADDING + Math.max(0, (textHeight - ICON_SIZE) / 2);
        iconView.layout(new Rect(CARD_PADDING, iconY, ICON_SIZE, ICON_SIZE));
    }

    /**
     * Paints the faded custom card or the default surface, body and remaining-time bar.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (localBounds().isEmpty()) {
            return;
        }
        float opacity = opacity();
        if (opacity <= 0) {
            return;
        }
        if (customRenderer != null) {
            renderer.withOpacity(opacity,
                    () -> customRenderer.render(this, renderer, localBounds(), frame));
            return;
        }
        WidgetStyle widgetStyle = style();
        if (widgetStyle == null) {
            return;
        }
        renderer.withOpacity(opacity, () -> drawCard(renderer, frame, widgetStyle));
    }

    private void drawCard(RenderManager renderer, FrameInfo frame, WidgetStyle widgetStyle) {
        super.onDraw(renderer, frame);
        UiContext owner = context();
        if (owner == null) {
            return;
        }
        Theme theme = owner.theme();
        Color accentColor = resolvedAccent(theme);
        if (content == null) {
            drawBody(renderer, theme, accentColor);
        }
        if (showTimer) {
            drawTimer(renderer, accentColor);
        }
    }

    private void drawBody(RenderManager renderer, Theme theme, Color accentColor) {
        float width = localBounds().width();
        float wrapWidth = Math.max(1, Math.min(maxWidth,
                width - CARD_PADDING * 2 - ICON_SPACE));
        float textX = CARD_PADDING + ICON_SPACE;
        Font font = font();
        float titleHeight = 0;
        if (font != null) {
            if (!title.isEmpty()) {
                TextStyle style = titleStyle(wrapWidth);
                titleHeight = font.measure(title, style).height();
                renderer.text(font, title, textX, CARD_PADDING, accentColor, style);
                if (!message.isEmpty()) {
                    titleHeight += TITLE_GAP;
                }
            }
            if (!message.isEmpty()) {
                renderer.text(font, message, textX, CARD_PADDING + titleHeight,
                        theme.text(), messageStyle(wrapWidth));
            }
        }
        drawIcon(renderer, font, accentColor, wrapWidth);
    }

    private void drawIcon(RenderManager renderer, Font font, Color accentColor, float wrapWidth) {
        Texture texture = iconView.displayedTexture();
        if (texture == null) {
            return;
        }
        Size titleSize = measure(font, title, titleStyle(wrapWidth));
        Size messageSize = measure(font, message, messageStyle(wrapWidth));
        float textHeight = titleSize.height()
                + (titleSize.height() > 0 && messageSize.height() > 0 ? TITLE_GAP : 0)
                + messageSize.height();
        float iconY = CARD_PADDING + Math.max(0, (textHeight - ICON_SIZE) / 2);
        renderer.icon(texture, new Rect(CARD_PADDING, iconY, ICON_SIZE, ICON_SIZE),
                ImageStyle.CONTAIN, accentColor);
    }

    private void drawTimer(RenderManager renderer, Color accentColor) {
        float barY = localBounds().height() - CARD_PADDING - PROGRESS_HEIGHT;
        float barWidth = Math.max(0, localBounds().width() - CARD_PADDING * 2);
        renderer.rect(new Rect(CARD_PADDING, barY, barWidth, PROGRESS_HEIGHT),
                accentColor.withAlpha(0.25f));
        float fraction = duration <= 0 ? 0 : Math.clamp(remaining / duration, 0, 1);
        if (fraction > 0) {
            renderer.rect(new Rect(CARD_PADDING, barY, barWidth * fraction, PROGRESS_HEIGHT),
                    accentColor);
        }
    }

    private void applyTheme() {
        UiContext owner = context();
        if (owner == null) {
            return;
        }
        Theme theme = owner.theme();
        Color accentColor = resolvedAccent(theme);
        Color background = Color.mix(theme.surface(), accentColor, SURFACE_MIX);
        Color border = Color.mix(theme.border(), accentColor, BORDER_MIX);
        setStyle(new WidgetStyle(background, background, background, background,
                theme.text(), theme.textMuted(), border, border,
                CORNER_RADIUS, CARD_PADDING, 1));
        iconView.setTint(accentColor);
    }

    private Color resolvedAccent(Theme theme) {
        if (accent != null) {
            return accent;
        }
        return switch (type) {
            case SUCCESS -> Color.hex("#4FC778");
            case WARNING -> Color.hex("#E8A23D");
            case ERROR -> Color.hex("#E05D5D");
            case INFO -> theme.primary();
        };
    }

    private boolean isHoveredWithin() {
        if (isHovered()) {
            return true;
        }
        UiContext owner = context();
        if (owner == null) {
            return false;
        }
        for (Widget widget = owner.hovered(); widget != null; widget = widget.parent()) {
            if (widget == this) {
                return true;
            }
        }
        return false;
    }

    private float timerRows() {
        return showTimer ? PROGRESS_GAP + PROGRESS_HEIGHT : 0;
    }

    private float textLimit(float cardWidth) {
        return Math.max(1, Math.min(maxWidth,
                cardWidth - CARD_PADDING * 2 - ICON_SPACE));
    }

    private static Options requireOptions(Options options) {
        return Objects.requireNonNull(options, "options");
    }

    private static Size measure(Font font, String text, TextStyle style) {
        return font == null || text.isEmpty() ? new Size(0, 0) : font.measure(text, style);
    }

    TextLayout messageLayout() {
        Font font = font();
        if (font == null) {
            return null;
        }
        return font.layoutBlockRelative(message, messageStyle(resolvedTextWidth()));
    }

    private float resolvedTextWidth() {
        if (textWidth > 0) {
            return textWidth;
        }
        float width = localBounds().width();
        if (width > 0) {
            return Math.max(1, Math.min(maxWidth, width - CARD_PADDING * 2 - ICON_SPACE));
        }
        return Math.max(1, Math.min(maxWidth,
                maxCardWidth - CARD_PADDING * 2 - ICON_SPACE));
    }

    private void startAppear() {
        appear = 0;
        exit = 0;
        exiting = false;
        UiContext owner = context();
        if (owner == null) {
            appear = 1;
            return;
        }
        appearAnimation = owner.animate(0, 1, APPEAR_SECONDS, Easing.EASE_OUT,
                value -> appear = value);
    }

    void startExit() {
        if (exiting || finished) {
            return;
        }
        exiting = true;
        UiContext owner = context();
        if (owner == null) {
            finish();
            return;
        }
        exitAnimation = owner.animate(0, 1, EXIT_SECONDS, Easing.EASE_IN,
                value -> exit = value);
        exitAnimation.setOnComplete(this::finish);
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        exiting = true;
        setVisible(false);
        Runnable listener = dismissListener;
        if (listener != null) {
            listener.run();
            return;
        }
        leaveTree();
    }

    private void leaveTree() {
        Widget parent = parent();
        if (parent != null) {
            // A hosted toast leaves through its own parent and needs no manager lookup.
            parent.remove(this);
            return;
        }
        UiContext owner = context();
        if (owner == null) {
            return;
        }
        try {
            owner.removeTopLevel(this);
        } catch (IllegalStateException ignored) {
            // The manager may already be closing and detach every top-level widget.
        }
    }

    private void cancelAnimations() {
        UiContext owner = context();
        if (owner == null) {
            return;
        }
        try {
            if (appearAnimation != null) {
                owner.animator().cancel(appearAnimation);
            }
            if (exitAnimation != null) {
                owner.animator().cancel(exitAnimation);
            }
        } catch (IllegalStateException ignored) {
            // The context may already be closed during manager shutdown.
        }
    }

    private float opacity() {
        return Math.clamp(appear * (1 - exit), 0, 1);
    }

    float slideProgress() {
        return Math.clamp((1 - appear) + exit, 0, 1);
    }

    private static TextStyle titleStyle(float width) {
        return new TextStyle(width, 0, 1, TextOverflow.ELLIPSIS,
                HorizontalAlignment.LEFT, VerticalAlignment.TOP);
    }

    private static TextStyle messageStyle(float width, int maxLines) {
        return new TextStyle(width, 0, maxLines, TextOverflow.ELLIPSIS,
                HorizontalAlignment.LEFT, VerticalAlignment.TOP);
    }

    private TextStyle messageStyle(float width) {
        return messageStyle(width, maxLines);
    }
}
