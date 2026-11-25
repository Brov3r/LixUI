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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages transient notification cards shown in a viewport corner.
 * Toasts enter and leave with a short slide and fade, pause their countdown while hovered and show
 * a progress bar with the remaining time. Every toast has a semantic type that selects a default
 * accent color and icon, and can add a title, an explicit icon, wrapped text limits and explicit
 * card width bounds. The visible count is limited; extra notifications wait in a queue and appear
 * as earlier cards expire. The manager owns a top-level host that lets pointer input pass through
 * empty space, so the layer never blocks the UI outside an actual toast.
 */
public final class ToastManager implements AutoCloseable {
    /**
     * A viewport corner used as the toast stack origin.
     */
    public enum Corner {
        /** Top-left corner. */
        TOP_LEFT,
        /** Top-right corner. */
        TOP_RIGHT,
        /** Bottom-left corner. */
        BOTTOM_LEFT,
        /** Bottom-right corner. */
        BOTTOM_RIGHT
    }

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
     * Presentation options for a single toast.
     * Options are mutable so a caller can build a notification fluently, and are copied when the
     * toast is shown, so later changes do not affect a visible card. The message is the only
     * required value; every other option has a default so the plain
     * {@link ToastManager#show(String)} call keeps working unchanged.
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

        /**
         * Creates options for a notification message.
         *
         * @param message notification text
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
         * An empty title hides the title row.
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
         * {@link ImageSource}. A null source restores the default type icon.
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
         * {@link #maxCardWidth(float)}.
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
         * @return the lifetime in seconds, or zero when the manager default is used
         */
        public float duration() {
            return duration;
        }

        /**
         * Assigns an explicit lifetime.
         * When no lifetime is assigned, {@link ToastManager#defaultDuration()} is used.
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

        private void validate() {
            if (minCardWidth > maxCardWidth) {
                throw new IllegalArgumentException(
                        "Minimum toast card width must not exceed the maximum card width.");
            }
        }
    }

    private static final float MARGIN = 16;
    private static final float GAP = 8;
    private static final float SLIDE_DISTANCE = 24;
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

    private final UiContext context;
    private final ToastHost host;
    private final List<ToastCard> visible = new ArrayList<>();
    private final ArrayDeque<ToastCard> pending = new ArrayDeque<>();
    private Corner corner = Corner.TOP_RIGHT;
    private int maxVisible = 3;
    private float defaultDuration = DEFAULT_DURATION;
    private boolean closed;

    /**
     * Creates a manager, attaches its transparent host and shows an empty layer.
     *
     * @param context context that owns the widget manager
     * @throws NullPointerException if {@code context} is null
     * @throws IllegalStateException if the context is closed or has no manager
     */
    public ToastManager(UiContext context) {
        this.context = Objects.requireNonNull(context, "context");
        host = new ToastHost(this);
        context.addTopLevel(host);
    }

    /**
     * Returns the stack corner.
     *
     * @return the current corner
     */
    public Corner corner() {
        return corner;
    }

    /**
     * Moves the toast stack to a viewport corner.
     *
     * @param corner stack corner
     * @throws NullPointerException if {@code corner} is null
     */
    public void setCorner(Corner corner) {
        this.corner = Objects.requireNonNull(corner, "corner");
    }

    /**
     * Returns the maximum number of simultaneously visible toasts.
     *
     * @return the visible limit
     */
    public int maxVisible() {
        return maxVisible;
    }

    /**
     * Sets the maximum number of simultaneously visible toasts.
     * Lowering the limit immediately removes the newest excess toasts.
     *
     * @param maxVisible positive visible limit
     * @throws IllegalArgumentException if the limit is not positive
     * @throws IllegalStateException if the manager is closed
     */
    public void setMaxVisible(int maxVisible) {
        ensureOpen();
        if (maxVisible <= 0) {
            throw new IllegalArgumentException("Visible toast limit must be positive.");
        }
        this.maxVisible = maxVisible;
        while (visible.size() > maxVisible) {
            ToastCard card = visible.remove(visible.size() - 1);
            host.remove(card);
            card.abort();
        }
        promote();
    }

    /**
     * Returns the duration used by {@link #show(String)}.
     *
     * @return the default duration in seconds
     */
    public float defaultDuration() {
        return defaultDuration;
    }

    /**
     * Sets the duration used by {@link #show(String)}.
     *
     * @param defaultDuration positive duration in seconds
     * @throws IllegalArgumentException if the duration is not finite or not positive
     * @throws IllegalStateException if the manager is closed
     */
    public void setDefaultDuration(float defaultDuration) {
        ensureOpen();
        if (!Float.isFinite(defaultDuration) || defaultDuration <= 0) {
            throw new IllegalArgumentException("Default toast duration must be finite and positive.");
        }
        this.defaultDuration = defaultDuration;
    }

    /**
     * Shows a toast with the default duration.
     *
     * @param message notification text
     * @return the toast handle
     * @throws NullPointerException if {@code message} is null
     * @throws IllegalStateException if the manager is closed
     */
    public Toast show(String message) {
        return show(message, defaultDuration);
    }

    /**
     * Shows a toast with an explicit duration.
     * When the visible limit is reached the toast waits in the queue.
     *
     * @param message notification text
     * @param duration positive lifetime in seconds
     * @return the toast handle
     * @throws NullPointerException if {@code message} is null
     * @throws IllegalArgumentException if the duration is not finite or not positive
     * @throws IllegalStateException if the manager is closed
     */
    public Toast show(String message, float duration) {
        ensureOpen();
        return show(new Options(message).duration(duration));
    }

    /**
     * Shows a toast with explicit presentation options.
     * When the visible limit is reached the toast waits in the queue. A zero option duration uses
     * {@link #defaultDuration()}.
     *
     * @param options toast presentation options
     * @return the toast handle
     * @throws NullPointerException if {@code options} is null
     * @throws IllegalArgumentException if the options are inconsistent
     * @throws IllegalStateException if the manager is closed
     */
    public Toast show(Options options) {
        ensureOpen();
        Objects.requireNonNull(options, "options");
        options.validate();
        float duration = options.duration() > 0 ? options.duration() : defaultDuration;
        ToastCard card = new ToastCard(this, options, duration);
        if (visible.size() < maxVisible) {
            showCard(card);
        } else {
            pending.addLast(card);
        }
        return card.toast;
    }

    /**
     * Returns the number of visible toasts.
     *
     * @return the visible count
     */
    public int visibleCount() {
        return visible.size();
    }

    /**
     * Returns the number of queued toasts.
     *
     * @return the queued count
     */
    public int queuedCount() {
        return pending.size();
    }

    /**
     * Returns handles for the visible toasts in display order.
     *
     * @return an immutable snapshot of visible toast handles
     */
    public List<Toast> visibleToasts() {
        List<Toast> toasts = new ArrayList<>(visible.size());
        for (ToastCard card : visible) {
            toasts.add(card.toast);
        }
        return List.copyOf(toasts);
    }

    /**
     * Dismisses every queued toast and starts the exit animation for visible ones.
     *
     * @throws IllegalStateException if the manager is closed
     */
    public void dismissAll() {
        ensureOpen();
        for (ToastCard card : pending) {
            card.abort();
        }
        pending.clear();
        for (ToastCard card : List.copyOf(visible)) {
            card.startExit();
        }
    }

    /**
     * Removes the host layer and every toast.
     * Closing an already closed manager does nothing.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pending.clear();
        for (ToastCard card : List.copyOf(visible)) {
            card.abort();
            host.remove(card);
        }
        visible.clear();
        try {
            context.removeTopLevel(host);
        } catch (IllegalStateException ignored) {
            // The manager may already be closing and detach every top-level widget.
        }
    }

    private void showCard(ToastCard card) {
        visible.add(card);
        host.add(card);
        card.startAppear();
    }

    private void update(float deltaSeconds) {
        for (ToastCard card : List.copyOf(visible)) {
            if (card.finished) {
                removeVisible(card);
                continue;
            }
            card.advance(deltaSeconds);
        }
    }

    private void removeVisible(ToastCard card) {
        if (!visible.remove(card)) {
            return;
        }
        host.remove(card);
        promote();
    }

    private void promote() {
        while (visible.size() < maxVisible && !pending.isEmpty()) {
            showCard(pending.pollFirst());
        }
    }

    private void dismiss(ToastCard card) {
        if (pending.remove(card)) {
            card.abort();
            return;
        }
        if (visible.contains(card)) {
            card.startExit();
        }
    }

    private void layoutToasts() {
        float viewportWidth = host.bounds().width();
        float viewportHeight = host.bounds().height();
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }
        float maximumWidth = Math.max(1, viewportWidth - MARGIN * 2);
        float maximumHeight = Math.max(1, viewportHeight - MARGIN * 2);
        float top = MARGIN;
        float bottom = viewportHeight - MARGIN;
        for (ToastCard card : visible) {
            Size size = card.measure(LayoutConstraints.loose(maximumWidth, maximumHeight));
            float slide = card.slideProgress() * SLIDE_DISTANCE;
            float x = switch (corner) {
                case TOP_LEFT, BOTTOM_LEFT -> MARGIN - slide;
                case TOP_RIGHT, BOTTOM_RIGHT ->
                        viewportWidth - MARGIN - size.width() + slide;
            };
            float y;
            switch (corner) {
                case TOP_LEFT, TOP_RIGHT -> {
                    y = top;
                    top += size.height() + GAP;
                }
                case BOTTOM_LEFT, BOTTOM_RIGHT -> {
                    bottom -= size.height();
                    y = bottom;
                    bottom -= GAP;
                }
                default -> throw new IllegalStateException("Unknown toast corner.");
            }
            card.layout(new Rect(x, y, size.width(), size.height()));
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Toast manager is closed.");
        }
    }

    /**
     * A handle for a queued or visible toast.
     */
    public static final class Toast {
        private final ToastCard card;

        private Toast(ToastCard card) {
            this.card = card;
        }

        /**
         * Returns the notification text.
         *
         * @return the message
         */
        public String message() {
            return card.message;
        }

        /**
         * Returns the semantic type.
         *
         * @return the type
         */
        public Type type() {
            return card.type;
        }

        /**
         * Returns the title text.
         *
         * @return the title, or an empty string when the toast has no title
         */
        public String title() {
            return card.title;
        }

        /**
         * Returns the effective icon source.
         *
         * @return the explicit icon source or the default type icon
         */
        public ImageSource icon() {
            return card.iconSource;
        }

        /**
         * Returns the explicit accent color.
         *
         * @return the accent, or null when the toast uses the type color
         */
        public Color accent() {
            return card.accent;
        }

        /**
         * Returns the maximum number of message lines.
         *
         * @return the line limit, or zero when the message is not line-limited
         */
        public int maxLines() {
            return card.maxLines;
        }

        /**
         * Returns the maximum message text width.
         *
         * @return the wrap width in logical UI units
         */
        public float maxWidth() {
            return card.maxWidth;
        }

        /**
         * Returns the minimum card width.
         *
         * @return the lower bound in logical UI units
         */
        public float minCardWidth() {
            return card.minCardWidth;
        }

        /**
         * Returns the maximum card width.
         *
         * @return the upper bound in logical UI units
         */
        public float maxCardWidth() {
            return card.maxCardWidth;
        }

        /**
         * Reports whether the remaining-time bar is drawn.
         *
         * @return true when the timer indicator is shown
         */
        public boolean showTimer() {
            return card.showTimer;
        }

        /**
         * Returns the remaining auto-hide time.
         *
         * @return seconds until the toast starts hiding
         */
        public float remainingSeconds() {
            return card.remaining;
        }

        /**
         * Reports whether the toast is currently attached to the screen.
         *
         * @return true when the card is visible in the host
         */
        public boolean isVisible() {
            return card.parent() != null && !card.finished;
        }

        /**
         * Returns the toast bounds in viewport coordinates.
         *
         * @return the current or last bounds
         */
        public Rect bounds() {
            Point position = card.absolutePosition();
            return new Rect(position.x(), position.y(),
                    card.bounds().width(), card.bounds().height());
        }

        /**
         * Dismisses the toast, removing it from the queue or starting its exit animation.
         */
        public void dismiss() {
            card.manager.dismiss(card);
        }

        TextLayout messageLayout() {
            return card.messageLayout();
        }
    }

    private static final class ToastHost extends Widget {
        private final ToastManager manager;

        private ToastHost(ToastManager manager) {
            this.manager = manager;
            setFillViewport(true);
            setAlwaysOnTop(true);
            setClipToBounds(true);
        }

        /**
         * Advances toast timers and removes finished cards.
         *
         * @param frame timing information for the current frame
         */
        @Override
        protected void onUpdate(FrameInfo frame) {
            manager.update(frame.deltaSeconds());
        }

        /**
         * Stacks visible cards in the configured corner.
         */
        @Override
        protected void onLayout() {
            manager.layoutToasts();
        }

        /**
         * Passes input through empty layer space while keeping toast cards hoverable.
         *
         * @param x horizontal coordinate relative to the host
         * @param y vertical coordinate relative to the host
         * @return the toast under the point, or null when the point is outside every card
         */
        @Override
        public Widget hitTest(float x, float y) {
            if (!contains(x, y)) {
                return null;
            }
            for (int index = children().size() - 1; index >= 0; index--) {
                Widget child = children().get(index);
                if (!child.isVisible()) {
                    continue;
                }
                Widget hit = child.hitTest(x - child.bounds().x(), y - child.bounds().y());
                if (hit != null) {
                    return hit;
                }
            }
            return null;
        }
    }

    private static final class ToastCard extends Panel {
        private final ToastManager manager;
        private final Toast toast;
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
        private final Image iconView = new Image();
        private float textWidth;
        private float remaining;
        private float appear = 1;
        private float exit;
        private boolean exiting;
        private boolean finished;
        private Animation appearAnimation;
        private Animation exitAnimation;

        private ToastCard(ToastManager manager, Options options, float duration) {
            this.manager = manager;
            this.toast = new Toast(this);
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
            iconView.setAutoSize(false);
            iconView.setVisible(false);
            iconView.setSource(iconSource);
            add(iconView);
        }

        /**
         * Applies the type accent to the card surface when the toast attaches.
         */
        @Override
        protected void onAttached() {
            applyTheme();
        }

        /**
         * Reapplies the type accent after a theme or context change.
         */
        @Override
        protected void onContextChanged() {
            applyTheme();
        }

        /**
         * Measures the icon, title, wrapped message and optional timer bar.
         *
         * @param constraints available and required dimensions
         * @return the card size
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            float availableCardWidth = Math.max(1, Math.min(maxCardWidth, constraints.maxWidth()));
            float lowerBound = Math.min(minCardWidth, availableCardWidth);
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
            float height = Math.max(MIN_HEIGHT, contentHeight + CARD_PADDING * 2
                    + (showTimer ? PROGRESS_GAP + PROGRESS_HEIGHT : 0));
            return new Size(Math.max(1, width), height);
        }

        /**
         * Places the icon in the text block and refreshes the resolved wrap width.
         */
        @Override
        protected void onLayout() {
            float width = localBounds().width();
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
         * Paints the faded card, the icon, the title, the message and the remaining-time bar.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            WidgetStyle widgetStyle = style();
            if (widgetStyle == null || localBounds().isEmpty()) {
                return;
            }
            float opacity = opacity();
            if (opacity <= 0) {
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
            if (showTimer) {
                drawTimer(renderer, width, accentColor);
            }
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

        private void drawTimer(RenderManager renderer, float width, Color accentColor) {
            float barY = localBounds().height() - CARD_PADDING - PROGRESS_HEIGHT;
            float barWidth = Math.max(0, width - CARD_PADDING * 2);
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

        private float textLimit(float cardWidth) {
            return Math.max(1, Math.min(maxWidth,
                    cardWidth - CARD_PADDING * 2 - ICON_SPACE));
        }

        private static Size measure(Font font, String text, TextStyle style) {
            return font == null || text.isEmpty() ? new Size(0, 0) : font.measure(text, style);
        }

        private TextLayout messageLayout() {
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
            finished = false;
            UiContext owner = context();
            if (owner == null) {
                appear = 1;
                return;
            }
            appearAnimation = owner.animate(0, 1, APPEAR_SECONDS, Easing.EASE_OUT,
                    value -> appear = value);
        }

        private void advance(float deltaSeconds) {
            if (finished || exiting || isHovered()) {
                return;
            }
            remaining -= deltaSeconds;
            if (remaining <= 0) {
                remaining = 0;
                startExit();
            }
        }

        private void startExit() {
            if (exiting || finished) {
                return;
            }
            exiting = true;
            UiContext owner = context();
            if (owner == null) {
                finished = true;
                return;
            }
            exitAnimation = owner.animate(0, 1, EXIT_SECONDS, Easing.EASE_IN,
                    value -> exit = value);
            exitAnimation.setOnComplete(() -> finished = true);
        }

        private float opacity() {
            return Math.clamp(appear * (1 - exit), 0, 1);
        }

        private float slideProgress() {
            return Math.clamp((1 - appear) + exit, 0, 1);
        }

        private void abort() {
            finished = true;
            UiContext owner = context();
            if (owner != null) {
                if (appearAnimation != null) {
                    owner.animator().cancel(appearAnimation);
                }
                if (exitAnimation != null) {
                    owner.animator().cancel(exitAnimation);
                }
            }
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
}
