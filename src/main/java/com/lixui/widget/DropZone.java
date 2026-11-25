package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.render.RenderManager;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetStyle;

/**
 * A panel that accepts dropped drag payloads of a configured kind.
 * While a drag session is under the pointer the zone is highlighted and reports whether it accepts
 * the payload kind. A completed drop invokes {@link #setOnDrop(DropHandler)}; the callback result
 * marks the drop as handled so the drag source can react after the gesture ends.
 * A zone without an accepted kind accepts every payload.
 */
public class DropZone extends Panel {
    private static final Color REJECT_COLOR = Color.hex("#E05D5D");
    private static final float HIGHLIGHT_ALPHA = 0.14f;
    private static final float REJECT_ALPHA = 0.09f;
    private static final float BORDER_WIDTH = 2;

    private String acceptedKind;
    private DropHandler onDrop;

    /**
     * Creates a zone that accepts every payload kind.
     */
    public DropZone() {
        this(null);
    }

    /**
     * Creates a zone that accepts a payload kind.
     *
     * @param acceptedKind accepted payload kind, or null to accept every kind
     */
    public DropZone(String acceptedKind) {
        this.acceptedKind = acceptedKind;
    }

    /**
     * Returns the accepted payload kind.
     *
     * @return the accepted kind, or null when every kind is accepted
     */
    public final String acceptedKind() {
        return acceptedKind;
    }

    /**
     * Restricts the zone to a payload kind.
     *
     * @param acceptedKind accepted payload kind, or null to accept every kind
     */
    public final void setAcceptedKind(String acceptedKind) {
        this.acceptedKind = acceptedKind;
    }

    /**
     * Reports whether the zone accepts a payload kind.
     *
     * @param kind payload kind supplied by the drag session
     * @return true when no kind restriction is set or it matches {@code kind}
     */
    public final boolean accepts(String kind) {
        return acceptedKind == null || acceptedKind.equals(kind);
    }

    /**
     * Returns the drop callback.
     *
     * @return the callback, or null when the zone handles no drops
     */
    public final DropHandler onDrop() {
        return onDrop;
    }

    /**
     * Replaces the callback invoked when an accepted payload is dropped on this zone.
     *
     * @param onDrop drop callback, or null to clear it
     */
    public final void setOnDrop(DropHandler onDrop) {
        this.onDrop = onDrop;
    }

    /**
     * Reports whether the active drag session points at this zone.
     *
     * @return true while this zone is the current drag target
     */
    public final boolean isDragTarget() {
        UiContext context = context();
        if (context == null) {
            return false;
        }
        DragSession session = context.dragSession();
        return session != null && session.isActive() && session.target() == this;
    }

    /**
     * Reports whether the active drag session points at this zone and the zone accepts its kind.
     *
     * @return true when a drop here would invoke the callback
     */
    public final boolean isDropAccepted() {
        UiContext context = context();
        if (context == null) {
            return false;
        }
        DragSession session = context.dragSession();
        return session != null && session.isActive() && session.target() == this
                && accepts(session.kind());
    }

    /**
     * Paints the panel and the drag highlight.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        super.onDraw(renderer, frame);
        if (!isDragTarget() || localBounds().isEmpty()) {
            return;
        }
        boolean accepted = isDropAccepted();
        Theme theme = context().theme();
        Color accent = accepted ? theme.primary() : REJECT_COLOR;
        float alpha = accepted ? HIGHLIGHT_ALPHA : REJECT_ALPHA;
        WidgetStyle style = style();
        float radius = style == null ? 6 : style.cornerRadius();
        renderer.roundedRect(localBounds(), radius, accent.withAlpha(alpha));
        float inset = BORDER_WIDTH / 2;
        Rect stroke = new Rect(inset, inset,
                Math.max(0, localBounds().width() - BORDER_WIDTH),
                Math.max(0, localBounds().height() - BORDER_WIDTH));
        if (!stroke.isEmpty()) {
            renderer.strokeRoundedRect(stroke, Math.max(0, radius - inset), BORDER_WIDTH,
                    Paint.solid(accent));
        }
    }

    boolean deliver(DragSession session) {
        if (!accepts(session.kind()) || onDrop == null) {
            return false;
        }
        return onDrop.onDrop(session.source(), session.kind(), session.payload());
    }

    /**
     * Handles an accepted payload dropped on this zone.
     */
    @FunctionalInterface
    public interface DropHandler {
        /**
         * Receives an accepted payload.
         *
         * @param source widget that started the drag
         * @param kind payload kind
         * @param payload dragged value, possibly null
         * @return true when the drop is handled and the source should treat it as applied
         */
        boolean onDrop(Widget source, String kind, Object payload);
    }
}
