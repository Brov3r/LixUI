package com.lixui.widget.overlay;

import com.lixui.animation.Animation;
import com.lixui.animation.Easing;
import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages transient notification cards shown in a viewport corner.
 * Toasts enter and leave with a short slide and fade, pause their countdown while hovered and show
 * a progress bar with the remaining time. The visible count is limited; extra notifications wait in
 * a queue and appear as earlier cards expire. The manager owns a top-level host that lets pointer
 * input pass through empty space, so the layer never blocks the UI outside an actual toast.
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

    private static final float MARGIN = 16;
    private static final float GAP = 8;
    private static final float SLIDE_DISTANCE = 24;
    private static final int MAX_LINES = 3;
    private static final float MAX_WIDTH = 320;
    private static final float MIN_HEIGHT = 56;
    private static final float CARD_PADDING = 12;
    private static final float PROGRESS_HEIGHT = 3;
    private static final float PROGRESS_GAP = 7;
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
        Objects.requireNonNull(message, "message");
        if (!Float.isFinite(duration) || duration <= 0) {
            throw new IllegalArgumentException("Toast duration must be finite and positive.");
        }
        ToastCard card = new ToastCard(this, message, duration);
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
        private final float duration;
        private float remaining;
        private float appear = 1;
        private float exit;
        private boolean exiting;
        private boolean finished;
        private Animation appearAnimation;
        private Animation exitAnimation;

        private ToastCard(ToastManager manager, String message, float duration) {
            this.manager = manager;
            this.toast = new Toast(this);
            this.message = message;
            this.duration = duration;
            this.remaining = duration;
        }

        /**
         * Measures the card from the wrapped message and the progress bar.
         *
         * @param constraints available and required dimensions
         * @return the card size
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            float width = Math.max(1, Math.min(MAX_WIDTH, constraints.maxWidth()));
            float contentWidth = Math.max(0, width - CARD_PADDING * 2);
            Font font = font();
            Size text = font == null ? new Size(0, 0)
                    : font.measure(message, textStyle(contentWidth));
            float height = Math.max(MIN_HEIGHT,
                    text.height() + CARD_PADDING * 2 + PROGRESS_GAP + PROGRESS_HEIGHT);
            return new Size(width, height);
        }

        /**
         * Paints the faded card, the message and the remaining-time bar.
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
            float width = localBounds().width();
            float height = localBounds().height();
            float contentWidth = Math.max(0, width - CARD_PADDING * 2);
            Font font = font();
            if (font != null && !message.isEmpty()) {
                renderer.text(font, message, CARD_PADDING, CARD_PADDING,
                        widgetStyle.text(WidgetState.NORMAL), textStyle(contentWidth));
            }
            float barY = height - CARD_PADDING - PROGRESS_HEIGHT;
            Color track = widgetStyle.border(WidgetState.NORMAL).withAlpha(0.35f);
            renderer.rect(new Rect(CARD_PADDING, barY, contentWidth, PROGRESS_HEIGHT), track);
            float fraction = duration <= 0 ? 0 : Math.clamp(remaining / duration, 0, 1);
            if (fraction > 0) {
                renderer.rect(new Rect(CARD_PADDING, barY, contentWidth * fraction,
                        PROGRESS_HEIGHT), context().theme().primary());
            }
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

        private static TextStyle textStyle(float width) {
            return new TextStyle(width, 0, MAX_LINES, TextOverflow.ELLIPSIS,
                    HorizontalAlignment.LEFT, VerticalAlignment.TOP);
        }
    }
}
