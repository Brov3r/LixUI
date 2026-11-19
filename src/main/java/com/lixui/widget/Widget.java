package com.lixui.widget;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.geometry.Transform;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.theme.WidgetStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A retained UI node with bounds, children, drawing, logic and input hooks.
 * Bounds are local to the parent: the top-left corner is relative to the parent's top-left corner,
 * and top-level widgets are positioned relative to the viewport. Drawing and hit testing use the
 * widget's own local coordinate space whose origin is its top-left corner.
 * The default layout keeps child bounds unchanged, which provides free positioning. Subclasses can
 * override measurement and layout to arrange children automatically.
 * Attached widgets have access to a {@link UiContext} through {@link #context()}. Attachment and
 * removal invoke lifecycle hooks on the whole subtree.
 * Children are drawn after their parent and hit-tested in reverse insertion order, so the last
 * child is visually on top.
 */
public abstract class Widget {
    private final List<Widget> children = new ArrayList<>();
    private final List<Widget> childView = Collections.unmodifiableList(children);
    private List<Widget> childSnapshot = List.of();
    private Widget parent;
    private UiContext context;
    private Font font;
    private WidgetStyle style;
    private boolean detaching;
    private Rect bounds = Rect.EMPTY;
    private float minimumWidth;
    private float minimumHeight;
    private float maximumWidth = Float.POSITIVE_INFINITY;
    private float maximumHeight = Float.POSITIVE_INFINITY;
    private boolean clipToBounds;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean focusable;
    private boolean focusScope;
    private boolean focusOutlineEnabled = true;
    private boolean hovered;
    private boolean pressed;
    private boolean focused;

    /**
     * Creates a widget with empty bounds and no children.
     */
    protected Widget() {
    }

    /**
     * Returns the bounds assigned to this widget in the parent coordinate space.
     * For a top-level widget the parent coordinate space is the viewport.
     *
     * @return the bounds in logical UI units
     */
    public final Rect bounds() {
        return bounds;
    }

    /**
     * Returns the bounds in this widget's own coordinate space.
     * The result always starts at the origin and is convenient for drawing.
     *
     * @return a rectangle from the origin with the current widget size
     */
    public final Rect localBounds() {
        return new Rect(0, 0, bounds.width(), bounds.height());
    }

    /**
     * Returns the top-left corner in the parent coordinate space.
     *
     * @return the widget position in logical UI units
     */
    public final Point position() {
        return new Point(bounds.x(), bounds.y());
    }

    /**
     * Returns the widget size.
     *
     * @return the widget size in logical UI units
     */
    public final Size size() {
        return new Size(bounds.width(), bounds.height());
    }

    /**
     * Returns the top-left corner in viewport coordinates by accumulating parent positions.
     *
     * @return the absolute widget position in logical UI units
     */
    public final Point absolutePosition() {
        float x = 0;
        float y = 0;
        for (Widget widget = this; widget != null; widget = widget.parent) {
            x += widget.bounds.x();
            y += widget.bounds.y();
        }
        return new Point(x, y);
    }

    /**
     * Replaces the widget bounds in the parent coordinate space.
     *
     * @param bounds new bounds in logical UI units
     * @throws NullPointerException if {@code bounds} is null
     */
    public final void setBounds(Rect bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
    }

    /**
     * Replaces the top-left corner while preserving the size.
     *
     * @param x horizontal position relative to the parent
     * @param y vertical position relative to the parent
     * @throws IllegalArgumentException if a coordinate is non-finite
     */
    public final void setPosition(float x, float y) {
        setBounds(new Rect(x, y, bounds.width(), bounds.height()));
    }

    /**
     * Replaces the size while preserving the position.
     *
     * @param width new width in logical UI units
     * @param height new height in logical UI units
     * @throws IllegalArgumentException if a dimension is negative or non-finite
     */
    public final void setSize(float width, float height) {
        setBounds(new Rect(bounds.x(), bounds.y(), width, height));
    }

    /**
     * Returns the minimum width used by widgets that resize themselves.
     *
     * @return the minimum width in logical UI units
     */
    public final float minimumWidth() {
        return minimumWidth;
    }

    /**
     * Returns the minimum height used by widgets that resize themselves.
     *
     * @return the minimum height in logical UI units
     */
    public final float minimumHeight() {
        return minimumHeight;
    }

    /**
     * Sets the minimum size used by widgets that resize themselves.
     * Bounds assigned explicitly through {@link #setBounds(Rect)} are not clamped.
     *
     * @param width non-negative minimum width
     * @param height non-negative minimum height
     * @throws IllegalArgumentException if a dimension is negative or non-finite, or exceeds the
     *                                  current maximum
     */
    public final void setMinimumSize(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width < 0 || height < 0) {
            throw new IllegalArgumentException("Minimum size must be finite and non-negative.");
        }
        if (width > maximumWidth || height > maximumHeight) {
            throw new IllegalArgumentException("Minimum size cannot exceed the maximum size.");
        }
        minimumWidth = width;
        minimumHeight = height;
    }

    /**
     * Returns the maximum width used by widgets that resize themselves.
     *
     * @return the maximum width, or positive infinity when unlimited
     */
    public final float maximumWidth() {
        return maximumWidth;
    }

    /**
     * Returns the maximum height used by widgets that resize themselves.
     *
     * @return the maximum height, or positive infinity when unlimited
     */
    public final float maximumHeight() {
        return maximumHeight;
    }

    /**
     * Sets the maximum size used by widgets that resize themselves.
     * Bounds assigned explicitly through {@link #setBounds(Rect)} are not clamped.
     *
     * @param width positive maximum width or positive infinity
     * @param height positive maximum height or positive infinity
     * @throws IllegalArgumentException if a dimension is not positive, is NaN, or is below the
     *                                  current minimum
     */
    public final void setMaximumSize(float width, float height) {
        if (!isPositiveDimension(width) || !isPositiveDimension(height)) {
            throw new IllegalArgumentException("Maximum size must be positive.");
        }
        if (width < minimumWidth || height < minimumHeight) {
            throw new IllegalArgumentException("Maximum size cannot be below the minimum size.");
        }
        maximumWidth = width;
        maximumHeight = height;
    }

    /**
     * Reports whether this widget and its subtree are clipped to the widget bounds.
     *
     * @return true when drawing is clipped to the widget bounds
     */
    public final boolean isClipToBounds() {
        return clipToBounds;
    }

    /**
     * Sets whether this widget and its subtree are clipped to the widget bounds.
     * Clipping affects drawing only. Hit testing already stops at every widget bounds, so a clipped
     * subtree cannot receive pointer events outside this widget.
     *
     * @param clipToBounds true to clip the widget and its children to the widget bounds
     */
    public final void setClipToBounds(boolean clipToBounds) {
        this.clipToBounds = clipToBounds;
    }

    /**
     * Measures this widget against the supplied constraints.
     * The result is the value returned by {@link #onMeasure(LayoutConstraints)} clamped to the
     * constraint range.
     *
     * @param constraints available and required dimensions
     * @return the measured size within the constraint range
     * @throws NullPointerException if {@code constraints} is null
     * @throws NullPointerException if {@link #onMeasure(LayoutConstraints)} returns null
     */
    public final Size measure(LayoutConstraints constraints) {
        Objects.requireNonNull(constraints, "constraints");
        Size measured = Objects.requireNonNull(onMeasure(constraints), "onMeasure result");
        return constraints.constrain(measured);
    }

    /**
     * Assigns bounds in the parent coordinate space and lays out this widget.
     * The widget itself keeps the supplied size; children are arranged by
     * {@link #onLayout()} if the subclass overrides it.
     *
     * @param bounds new bounds in logical UI units
     * @throws NullPointerException if {@code bounds} is null
     */
    public final void layout(Rect bounds) {
        setBounds(bounds);
        onLayout();
    }

    /**
     * Returns the parent widget.
     *
     * @return the parent, or null for a top-level widget
     */
    public final Widget parent() {
        return parent;
    }

    /**
     * Returns the UI context shared by the attached widget tree.
     *
     * @return the context, or null when the widget is not attached to a manager
     */
    public final UiContext context() {
        return context;
    }

    /**
     * Returns the children in drawing order, with later children drawn on top.
     *
     * @return an unmodifiable view of the children
     */
    public final List<Widget> children() {
        return childView;
    }

    /**
     * Appends a child widget.
     *
     * @param child widget to append
     * @return this widget
     * @throws NullPointerException if {@code child} is null
     * @throws IllegalArgumentException if the child already has a parent or manager, is this widget,
     *                                  or is an ancestor
     */
    public final Widget add(Widget child) {
        Objects.requireNonNull(child, "child");
        if (child == this) {
            throw new IllegalArgumentException("A widget cannot contain itself.");
        }
        if (child.parent != null) {
            throw new IllegalArgumentException("Widget already has a parent.");
        }
        if (child.context != null) {
            throw new IllegalArgumentException("Widget is already attached to a manager.");
        }
        for (Widget ancestor = this; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor == child) {
                throw new IllegalArgumentException("A widget cannot contain its ancestor.");
            }
        }
        children.add(child);
        childSnapshot = null;
        child.setParent(this);
        try {
            if (context != null && !detaching) {
                child.attach(context);
            }
        } catch (RuntimeException | Error exception) {
            children.removeIf(candidate -> candidate == child);
            childSnapshot = null;
            child.setParent(null);
            notifyStateChanged();
            throw exception;
        }
        return this;
    }

    /**
     * Removes a direct child widget and detaches it.
     *
     * @param child widget to remove
     * @return true when the child was present
     * @throws NullPointerException if {@code child} is null
     */
    public final boolean remove(Widget child) {
        Objects.requireNonNull(child, "child");
        for (int index = 0; index < children.size(); index++) {
            if (children.get(index) == child) {
                children.remove(index);
                childSnapshot = null;
                try {
                    child.detach();
                } finally {
                    child.setParent(null);
                    notifyStateChanged();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Removes every child and detaches all of them.
     */
    public final void removeAll() {
        for (Widget child : List.copyOf(children)) {
            remove(child);
        }
    }

    /**
     * Reports whether this widget is drawn and hit-tested.
     *
     * @return true when the widget is visible
     */
    public final boolean isVisible() {
        return visible;
    }

    /**
     * Sets whether this widget is drawn and hit-tested.
     *
     * @param visible true to show the widget
     */
    public final void setVisible(boolean visible) {
        if (this.visible == visible) {
            return;
        }
        this.visible = visible;
        notifyStateChanged();
    }

    /**
     * Reports whether this widget accepts pointer and keyboard input.
     * Disabled widgets are still drawn, so subclasses can dim them.
     *
     * @return true when input is accepted
     */
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether this widget accepts pointer and keyboard input.
     *
     * @param enabled true to accept input
     */
    public final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        notifyStateChanged();
    }

    /**
     * Reports whether this widget can receive keyboard focus.
     *
     * @return true when the widget can be focused
     */
    public final boolean isFocusable() {
        return focusable;
    }

    /**
     * Sets whether this widget can receive keyboard focus.
     *
     * @param focusable true to allow focus
     */
    public final void setFocusable(boolean focusable) {
        this.focusable = focusable;
        notifyStateChanged();
    }

    /**
     * Reports whether this widget starts a focus scope.
     * Tab traversal from a focused descendant wraps inside the nearest enclosing focus scope.
     *
     * @return true when this widget is a focus scope root
     */
    public final boolean isFocusScope() {
        return focusScope;
    }

    /**
     * Sets whether this widget starts a focus scope for Tab traversal.
     *
     * @param focusScope true to confine Tab traversal to this subtree
     */
    public final void setFocusScope(boolean focusScope) {
        this.focusScope = focusScope;
        notifyStateChanged();
    }

    /**
     * Reports whether a focused widget should draw its focus border.
     * Focused widgets without a focus outline still use their normal border color.
     *
     * @return true when the focus outline is enabled
     */
    public final boolean isFocusOutlineEnabled() {
        return focusOutlineEnabled;
    }

    /**
     * Sets whether a focused widget should draw its focus border.
     *
     * @param focusOutlineEnabled true to use the focus border color while focused
     */
    public final void setFocusOutlineEnabled(boolean focusOutlineEnabled) {
        this.focusOutlineEnabled = focusOutlineEnabled;
        notifyStateChanged();
    }

    /**
     * Reports whether the pointer is currently over this widget.
     *
     * @return true when the widget is hovered
     */
    public final boolean isHovered() {
        return hovered;
    }

    /**
     * Reports whether a pointer button is currently held on this widget.
     *
     * @return true while the widget is pressed
     */
    public final boolean isPressed() {
        return pressed;
    }

    /**
     * Reports whether this widget currently has keyboard focus.
     *
     * @return true when the widget is focused
     */
    public final boolean isFocused() {
        return focused;
    }

    /**
     * Returns the pointer cursor requested while this widget or its subtree is hovered.
     * The nearest ancestor with a non-default cursor wins.
     *
     * @return the requested cursor shape
     */
    public Cursor cursor() {
        return Cursor.DEFAULT;
    }

    /**
     * Resolves the inherited font for this subtree.
     * The nearest local font wins, then the context default font.
     *
     * @return the resolved font, or null when neither the widget tree nor the context defines one
     */
    public final Font font() {
        for (Widget widget = this; widget != null; widget = widget.parent) {
            if (widget.font != null) {
                return widget.font;
            }
        }
        return context == null ? null : context.defaultFont();
    }

    /**
     * Assigns a local font that is inherited by descendants.
     *
     * @param font font to use for this subtree
     * @throws NullPointerException if {@code font} is null
     */
    public final void setFont(Font font) {
        this.font = Objects.requireNonNull(font, "font");
        notifyStateChanged();
    }

    /**
     * Removes the local font and restores inheritance from the parent or context.
     */
    public final void clearFont() {
        font = null;
        notifyStateChanged();
    }

    /**
     * Resolves the inherited visual style for this subtree.
     * The nearest local style wins, then the widget's default style.
     *
     * @return the resolved style, or null when neither the widget tree nor the context defines one
     */
    public final WidgetStyle style() {
        for (Widget widget = this; widget != null; widget = widget.parent) {
            if (widget.style != null) {
                return widget.style;
            }
        }
        return defaultStyle();
    }

    /**
     * Returns the context style used when neither this widget nor its ancestors define a local style.
     * Interactive controls use the control style by default; subclasses can select another preset.
     *
     * @return the default style, or null when the widget is not attached
     */
    protected WidgetStyle defaultStyle() {
        return context == null ? null : context.theme().controlStyle();
    }

    /**
     * Assigns a local style that is inherited by descendants.
     *
     * @param style style to use for this subtree
     * @throws NullPointerException if {@code style} is null
     */
    public final void setStyle(WidgetStyle style) {
        this.style = Objects.requireNonNull(style, "style");
        notifyStateChanged();
    }

    /**
     * Removes the local style and restores inheritance from the parent or context.
     */
    public final void clearStyle() {
        style = null;
        notifyStateChanged();
    }

    /**
     * Reports whether a point in this widget's local coordinate space can interact with it.
     * Invisible and disabled widgets never contain a point.
     *
     * @param x horizontal coordinate relative to the widget's top-left corner
     * @param y vertical coordinate relative to the widget's top-left corner
     * @return true when the widget is visible, enabled and contains the point
     */
    public final boolean contains(float x, float y) {
        return visible && enabled && localBounds().contains(x, y);
    }

    /**
     * Returns the deepest visible and enabled widget containing the supplied point.
     * The point is expressed in this widget's local coordinate space. Children are tested in
     * reverse drawing order against their parent-relative bounds.
     *
     * @param x horizontal coordinate relative to the widget's top-left corner
     * @param y vertical coordinate relative to the widget's top-left corner
     * @return the hit widget, or null when the point is outside
     */
    public Widget hitTest(float x, float y) {
        if (!contains(x, y)) {
            return null;
        }
        for (int index = children.size() - 1; index >= 0; index--) {
            Widget child = children.get(index);
            Rect childClip = childClip(child);
            if (childClip != null && !childClip.contains(x, y)) {
                continue;
            }
            Widget hit = child.hitTest(x - child.bounds.x(), y - child.bounds.y());
            if (hit != null) {
                return hit;
            }
        }
        return this;
    }

    /**
     * Updates this widget and its children.
     * Invisible widgets still receive updates, so animations and timers can continue.
     *
     * @param frame timing information for the current frame
     * @throws NullPointerException if {@code frame} is null
     */
    public final void update(FrameInfo frame) {
        Objects.requireNonNull(frame, "frame");
        UiContext owner = context;
        onUpdate(frame);
        for (Widget child : childSnapshot()) {
            if (context != owner) {
                break;
            }
            if (child.parent == this) {
                child.update(frame);
            }
        }
    }

    /**
     * Draws this widget and its children into the supplied renderer.
     * The renderer is translated by this widget's parent-relative position, so drawing code works
     * in the widget's local coordinate space. Invisible widgets and their subtrees are skipped.
     * When {@link #isClipToBounds()} is enabled, the widget and its children are clipped to the
     * widget bounds intersected with the active clip.
     * Sized widgets outside the active clip skip their draw callback. Unclipped children are still
     * considered independently; empty bounds keep supporting custom unbounded drawing nodes.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     * @throws NullPointerException if {@code renderer} or {@code frame} is null
     */
    public final void draw(RenderManager renderer, FrameInfo frame) {
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(frame, "frame");
        if (!visible) {
            return;
        }
        Point origin = absolutePosition();
        drawSubtree(renderer, frame, renderer.clip(), origin.x(), origin.y());
    }

    private void drawSubtree(RenderManager renderer, FrameInfo frame, Rect clip,
                             float absoluteX, float absoluteY) {
        if (!visible) {
            return;
        }
        Rect effectiveClip = clipToBounds
                ? clip.intersect(new Rect(absoluteX, absoluteY, bounds.width(), bounds.height()))
                : clip;
        if (effectiveClip.isEmpty()) {
            return;
        }
        if (effectiveClip.equals(clip)) {
            drawPositioned(renderer, frame, effectiveClip, absoluteX, absoluteY);
        } else {
            renderer.withClip(effectiveClip,
                    () -> drawPositioned(renderer, frame, effectiveClip, absoluteX, absoluteY));
        }
    }

    private void drawPositioned(RenderManager renderer, FrameInfo frame, Rect clip,
                                float absoluteX, float absoluteY) {
        if (bounds.x() == 0 && bounds.y() == 0) {
            drawLocal(renderer, frame, clip, absoluteX, absoluteY);
        } else {
            renderer.withTransform(Transform.translation(bounds.x(), bounds.y()),
                    () -> drawLocal(renderer, frame, clip, absoluteX, absoluteY));
        }
    }

    private void drawLocal(RenderManager renderer, FrameInfo frame, Rect clip,
                           float absoluteX, float absoluteY) {
        UiContext owner = context;
        if (intersectsClip(clip, absoluteX, absoluteY)) {
            onDraw(renderer, frame);
        }
        if (context != owner || !visible) {
            return;
        }
        boolean masked = pushChildMask(renderer);
        try {
            for (Widget child : childSnapshot()) {
                if (context != owner || !visible) {
                    break;
                }
                if (child.parent != this) {
                    continue;
                }
                Rect childClip = childClip(child);
                if (childClip == null) {
                    child.drawSubtree(renderer, frame, clip,
                            absoluteX + child.bounds.x(), absoluteY + child.bounds.y());
                } else {
                    Rect effectiveClip = clip.intersect(new Rect(
                            absoluteX + childClip.x(), absoluteY + childClip.y(),
                            childClip.width(), childClip.height()));
                    if (!effectiveClip.isEmpty()) {
                        renderer.withClip(effectiveClip, () -> child.drawSubtree(renderer, frame,
                                effectiveClip, absoluteX + child.bounds.x(), absoluteY + child.bounds.y()));
                    }
                }
            }
        } finally {
            if (masked) {
                popChildMask(renderer);
            }
        }
    }

    /**
     * Called when this widget becomes part of a managed tree and receives a context.
     * {@link #context()} is available during the callback. Resources created here can be released
     * in {@link #onDetached()}.
     */
    protected void onAttached() {
    }

    /**
     * Called when this widget leaves its managed tree.
     * Children are detached first, and the context is still available through {@link #context()}
     * while this callback runs.
     */
    protected void onDetached() {
    }

    /**
     * Called after the context viewport, display scale, theme or default font changes.
     * Parents receive the callback before their children.
     */
    protected void onContextChanged() {
    }

    /**
     * Computes the natural size of this widget for the supplied constraints.
     * The default returns the current widget size, which keeps manually assigned dimensions.
     * Container widgets override this method to measure their children.
     *
     * @param constraints available and required dimensions
     * @return the natural size before constraint clamping
     */
    protected Size onMeasure(LayoutConstraints constraints) {
        return new Size(bounds.width(), bounds.height());
    }

    /**
     * Arranges this widget after its bounds have been assigned.
     * The default lays out children with their current bounds, so they stay freely positioned
     * relative to this widget. Container widgets override this method to measure and lay out their
     * children with new bounds.
     */
    protected void onLayout() {
        for (Widget child : childSnapshot()) {
            if (child.parent == this) {
                child.layout(child.bounds());
            }
        }
    }

    /**
     * Called once per frame before the children are updated.
     *
     * @param frame timing information for the current frame
     */
    protected void onUpdate(FrameInfo frame) {
    }

    /**
     * Paints this widget before its children are drawn.
     * Drawing coordinates are local to this widget, so the top-left corner is {@code (0, 0)}.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
    }

    /**
     * Returns an optional clip for a direct child's subtree and pointer hit testing.
     *
     * @param child direct child being drawn or hit-tested
     * @return a rectangle in this widget's local coordinates, or null for no additional clip
     */
    protected Rect childClip(Widget child) {
        return null;
    }

    /**
     * Pushes an optional stencil mask around this widget's children.
     * Called after {@link #onDraw(RenderManager, FrameInfo)} and before child drawing. Returning
     * true requires the matching {@link #popChildMask(RenderManager)} call.
     *
     * @param renderer drawing manager for the current frame
     * @return true when a mask was pushed
     */
    protected boolean pushChildMask(RenderManager renderer) {
        return false;
    }

    /**
     * Pops the stencil mask pushed by {@link #pushChildMask(RenderManager)}.
     *
     * @param renderer drawing manager for the current frame
     */
    protected void popChildMask(RenderManager renderer) {
    }

    /**
     * Called when the pointer enters this widget.
     */
    protected void onPointerEnter() {
    }

    /**
     * Called when the pointer leaves this widget.
     */
    protected void onPointerExit() {
    }

    /**
     * Called for pointer movement over this widget or while it captures a pressed button.
     * The event bubbles to ancestors until a callback returns true.
     *
     * @param x horizontal pointer position relative to the widget's top-left corner
     * @param y vertical pointer position relative to the widget's top-left corner
     * @return true when the event is consumed
     */
    protected boolean onPointerMove(float x, float y) {
        return false;
    }

    /**
     * Called when a pointer button is pressed on this widget.
     * The event bubbles to ancestors until a callback returns true.
     *
     * @param x horizontal pointer position relative to the widget's top-left corner
     * @param y vertical pointer position relative to the widget's top-left corner
     * @param button pressed button
     * @param modifiers active keyboard modifiers
     * @return true when the event is consumed
     */
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        return false;
    }

    /**
     * Called when a captured pointer button is released.
     * The event bubbles to ancestors until a callback returns true.
     *
     * @param x horizontal pointer position relative to the widget's top-left corner
     * @param y vertical pointer position relative to the widget's top-left corner
     * @param button released button
     * @param modifiers active keyboard modifiers
     * @return true when the event is consumed
     */
    protected boolean onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
        return false;
    }

    /**
     * Called when the pointer scrolls while this widget is hovered.
     * The event bubbles to ancestors until a callback returns true.
     *
     * @param x horizontal pointer position relative to the widget's top-left corner
     * @param y vertical pointer position relative to the widget's top-left corner
     * @param deltaX horizontal scroll amount in wheel steps
     * @param deltaY vertical scroll amount in wheel steps
     * @return true when the event is consumed
     */
    protected boolean onPointerScroll(float x, float y, float deltaX, float deltaY) {
        return false;
    }

    /**
     * Called when a primary click is completed on this widget or one of its descendants.
     * The event bubbles to ancestors until a callback returns true.
     *
     * @param x horizontal pointer position relative to the widget's top-left corner
     * @param y vertical pointer position relative to the widget's top-left corner
     * @param button clicked button
     * @param modifiers active keyboard modifiers
     * @return true when the event is consumed
     */
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        return false;
    }

    /**
     * Called when a second click is completed within the double-click interval.
     * The event bubbles to ancestors until a callback returns true.
     *
     * @param x horizontal pointer position relative to the widget's top-left corner
     * @param y vertical pointer position relative to the widget's top-left corner
     * @param button clicked button
     * @param modifiers active keyboard modifiers
     * @return true when the event is consumed
     */
    protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
        return false;
    }

    /**
     * Called when a pressed pointer moves beyond the drag threshold.
     * The event bubbles to ancestors until a callback returns true.
     *
     * @param x horizontal pointer position relative to the widget's top-left corner
     * @param y vertical pointer position relative to the widget's top-left corner
     * @param button pressed button
     * @param modifiers modifiers captured at press time
     * @return true when the event is consumed
     */
    protected boolean onDragStart(float x, float y, MouseButton button, Modifiers modifiers) {
        return false;
    }

    /**
     * Called for pointer movement while a drag is active.
     * The event bubbles to ancestors until a callback returns true.
     *
     * @param x horizontal pointer position relative to the widget's top-left corner
     * @param y vertical pointer position relative to the widget's top-left corner
     * @param button pressed button
     * @param modifiers modifiers captured at press time
     * @return true when the event is consumed
     */
    protected boolean onDrag(float x, float y, MouseButton button, Modifiers modifiers) {
        return false;
    }

    /**
     * Called when a dragged pointer button is released.
     * The event bubbles to ancestors until a callback returns true.
     *
     * @param x horizontal pointer position relative to the widget's top-left corner
     * @param y vertical pointer position relative to the widget's top-left corner
     * @param button released button
     * @param modifiers modifiers captured at press time
     * @return true when the event is consumed
     */
    protected boolean onDragEnd(float x, float y, MouseButton button, Modifiers modifiers) {
        return false;
    }

    /**
     * Called when a pressed pointer stays within the long-press threshold.
     * The event bubbles to ancestors until a callback returns true.
     *
     * @param x horizontal pointer position relative to the widget's top-left corner
     * @param y vertical pointer position relative to the widget's top-left corner
     * @param button pressed button
     * @param modifiers modifiers captured at press time
     * @return true when the event is consumed
     */
    protected boolean onLongPress(float x, float y, MouseButton button, Modifiers modifiers) {
        return false;
    }

    /**
     * Called when a key is pressed while this widget is focused.
     * The event bubbles to ancestors until a callback returns true.
     *
     * @param key pressed key
     * @param modifiers active keyboard modifiers
     * @param repeat true for a repeated key press
     * @return true when the event is consumed
     */
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        return false;
    }

    /**
     * Called when a key is released while this widget is focused.
     * The event bubbles to ancestors until a callback returns true.
     *
     * @param key released key
     * @param modifiers active keyboard modifiers
     * @return true when the event is consumed
     */
    protected boolean onKeyReleased(Key key, Modifiers modifiers) {
        return false;
    }

    /**
     * Called when a Unicode code point is typed while this widget is focused.
     * The event bubbles to ancestors until a callback returns true.
     *
     * @param codepoint typed code point
     * @return true when the event is consumed
     */
    protected boolean onCharTyped(int codepoint) {
        return false;
    }

    /**
     * Called when keyboard focus is gained or lost.
     *
     * @param focused true when this widget gained focus
     */
    protected void onFocusChanged(boolean focused) {
    }

    /**
     * Requests keyboard focus for this widget.
     *
     * @throws IllegalStateException if the widget is not attached to a manager
     */
    protected final void requestFocus() {
        requireContext().requestFocus(this);
    }

    /**
     * Captures pointer button events for this widget until the button is released.
     *
     * @param button button to capture
     * @throws NullPointerException if {@code button} is null
     * @throws IllegalStateException if the widget is not attached to a manager
     */
    protected final void capturePointer(MouseButton button) {
        requireContext().capturePointer(this, button);
    }

    /**
     * Releases a pointer capture owned by this widget.
     *
     * @param button captured button
     * @throws NullPointerException if {@code button} is null
     * @throws IllegalStateException if the widget is not attached to a manager
     */
    protected final void releasePointerCapture(MouseButton button) {
        requireContext().releasePointerCapture(this, button);
    }

    final void attach(UiContext newContext) {
        Objects.requireNonNull(newContext, "context");
        if (context == newContext) {
            return;
        }
        if (context != null) {
            detach();
        }
        context = newContext;
        try {
            onAttached();
            for (Widget child : childSnapshot()) {
                if (child.parent == this && context == newContext) {
                    child.attach(newContext);
                }
            }
        } catch (RuntimeException | Error exception) {
            try {
                detach();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != exception) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw exception;
        }
    }

    final void detach() {
        if (context == null || detaching) {
            return;
        }
        detaching = true;
        RuntimeException failure = null;
        try {
            for (Widget child : childSnapshot()) {
                if (child.parent == this) {
                    try {
                        child.detach();
                    } catch (RuntimeException exception) {
                        failure = recordFailure(failure, exception);
                    }
                }
            }
            try {
                onDetached();
            } catch (RuntimeException exception) {
                failure = recordFailure(failure, exception);
            }
        } finally {
            context = null;
            detaching = false;
        }
        if (failure != null) {
            throw failure;
        }
    }

    final void contextChanged() {
        if (context == null) {
            return;
        }
        UiContext owner = context;
        onContextChanged();
        for (Widget child : childSnapshot()) {
            if (context != owner) {
                break;
            }
            if (child.parent == this) {
                child.contextChanged();
            }
        }
    }

    private List<Widget> childSnapshot() {
        if (childSnapshot == null) {
            childSnapshot = List.copyOf(children);
        }
        return childSnapshot;
    }

    private boolean intersectsClip(Rect clip, float x, float y) {
        if (bounds.isEmpty()) {
            return true;
        }
        WidgetStyle resolvedStyle = style();
        float padding = resolvedStyle == null ? 1 : resolvedStyle.borderWidth() / 2 + 1;
        return x - padding < clip.right() && y - padding < clip.bottom()
                && (double) x + bounds.width() + padding > clip.x()
                && (double) y + bounds.height() + padding > clip.y();
    }

    static RuntimeException recordFailure(RuntimeException failure, RuntimeException exception) {
        if (failure == null) {
            return exception;
        }
        if (failure != exception) {
            failure.addSuppressed(exception);
        }
        return failure;
    }

    private void notifyStateChanged() {
        if (context != null) {
            context.widgetStateChanged();
        }
    }

    private UiContext requireContext() {
        if (context == null) {
            throw new IllegalStateException("Widget is not attached to a widget manager.");
        }
        return context;
    }

    private static boolean isPositiveDimension(float value) {
        return value > 0 && (Float.isFinite(value) || value == Float.POSITIVE_INFINITY);
    }

    final void setParent(Widget parent) {
        this.parent = parent;
    }

    final void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    final void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    final void setFocused(boolean focused) {
        this.focused = focused;
    }
}
