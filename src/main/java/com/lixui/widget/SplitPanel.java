package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.theme.Theme;
import com.lixui.widget.layout.LayoutContainer;

import java.util.Objects;

/**
 * A container that splits its content area between two panes with a draggable divider.
 * The panes are arranged along the configured orientation; a horizontal split places them side by
 * side with a vertical divider, while a vertical split stacks them with a horizontal divider.
 * The divider position is normalized to the available content length, so it follows container
 * resizes. Panes can be nested to build more complex layouts.
 */
public class SplitPanel extends LayoutContainer {
    /** The axis along which the two panes are arranged. */
    public enum Orientation {
        /** Panes are placed side by side and the divider is vertical. */
        HORIZONTAL,
        /** Panes are stacked and the divider is horizontal. */
        VERTICAL
    }

    private static final float DEFAULT_DIVIDER_THICKNESS = 6;
    private static final float DEFAULT_DIVIDER_POSITION = 0.5f;

    private final Divider divider = new Divider();
    private Orientation orientation;
    private Widget first;
    private Widget second;
    private float dividerThickness = DEFAULT_DIVIDER_THICKNESS;
    private float dividerPosition = DEFAULT_DIVIDER_POSITION;
    private float minimumPaneSize;
    private float dragStartPointer;
    private float dragStartPosition;

    /**
     * Creates a horizontal split panel.
     */
    public SplitPanel() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a split panel with the supplied orientation.
     *
     * @param orientation axis along which the panes are arranged
     * @throws NullPointerException if {@code orientation} is null
     */
    public SplitPanel(Orientation orientation) {
        this.orientation = Objects.requireNonNull(orientation, "orientation");
    }

    /**
     * Returns the axis along which the panes are arranged.
     *
     * @return the orientation
     */
    public final Orientation orientation() {
        return orientation;
    }

    /**
     * Replaces the axis along which the panes are arranged.
     *
     * @param orientation axis along which the panes are arranged
     * @throws NullPointerException if {@code orientation} is null
     */
    public final void setOrientation(Orientation orientation) {
        this.orientation = Objects.requireNonNull(orientation, "orientation");
    }

    /**
     * Returns the widget shown in the first pane.
     * For a horizontal split the first pane is on the left; for a vertical split it is on top.
     *
     * @return the first pane widget, or null when the pane is empty
     */
    public final Widget first() {
        return first;
    }

    /**
     * Places a widget in the first pane.
     *
     * @param first widget shown in the first pane
     * @throws NullPointerException if {@code first} is null
     * @throws IllegalArgumentException if the widget already has a parent or manager, or is the
     *                                  second pane widget
     */
    public final void setFirst(Widget first) {
        Objects.requireNonNull(first, "first");
        if (first == this.first) {
            return;
        }
        if (first == second) {
            throw new IllegalArgumentException("The same widget cannot fill both panes.");
        }
        add(first);
        Widget previous = this.first;
        this.first = first;
        if (previous != null) {
            remove(previous);
        }
        ensureDividerOnTop();
    }

    /**
     * Returns the widget shown in the second pane.
     * For a horizontal split the second pane is on the right; for a vertical split it is at the
     * bottom.
     *
     * @return the second pane widget, or null when the pane is empty
     */
    public final Widget second() {
        return second;
    }

    /**
     * Places a widget in the second pane.
     *
     * @param second widget shown in the second pane
     * @throws NullPointerException if {@code second} is null
     * @throws IllegalArgumentException if the widget already has a parent or manager, or is the
     *                                  first pane widget
     */
    public final void setSecond(Widget second) {
        Objects.requireNonNull(second, "second");
        if (second == this.second) {
            return;
        }
        if (second == first) {
            throw new IllegalArgumentException("The same widget cannot fill both panes.");
        }
        add(second);
        Widget previous = this.second;
        this.second = second;
        if (previous != null) {
            remove(previous);
        }
        ensureDividerOnTop();
    }

    /**
     * Returns the divider thickness.
     *
     * @return the thickness in logical UI units
     */
    public final float dividerThickness() {
        return dividerThickness;
    }

    /**
     * Replaces the divider thickness.
     *
     * @param dividerThickness positive thickness in logical UI units
     * @throws IllegalArgumentException if the thickness is not positive or is non-finite
     */
    public final void setDividerThickness(float dividerThickness) {
        if (!Float.isFinite(dividerThickness) || dividerThickness <= 0) {
            throw new IllegalArgumentException("Divider thickness must be finite and positive.");
        }
        this.dividerThickness = dividerThickness;
    }

    /**
     * Returns the normalized divider position.
     *
     * @return a value in the {@code [0, 1]} range
     */
    public final float dividerPosition() {
        return dividerPosition;
    }

    /**
     * Moves the divider to a normalized position.
     * The position is measured from the start of the content area and is clamped to {@code [0, 1]}.
     * Pane minimum sizes are applied during layout and can keep the effective position farther from
     * the edges.
     *
     * @param dividerPosition normalized divider position
     * @throws IllegalArgumentException if the position is non-finite
     */
    public final void setDividerPosition(float dividerPosition) {
        if (!Float.isFinite(dividerPosition)) {
            throw new IllegalArgumentException("Divider position must be finite.");
        }
        this.dividerPosition = clamp(dividerPosition, 0, 1);
    }

    /**
     * Returns the minimum size of each pane along the split axis.
     *
     * @return the minimum pane size in logical UI units
     */
    public final float minimumPaneSize() {
        return minimumPaneSize;
    }

    /**
     * Sets the minimum size of each pane along the split axis.
     * When the container is too small to honor both minimums, the available space is divided
     * evenly.
     *
     * @param minimumPaneSize non-negative minimum size in logical UI units
     * @throws IllegalArgumentException if the size is negative or non-finite
     */
    public final void setMinimumPaneSize(float minimumPaneSize) {
        if (!Float.isFinite(minimumPaneSize) || minimumPaneSize < 0) {
            throw new IllegalArgumentException("Minimum pane size must be finite and non-negative.");
        }
        this.minimumPaneSize = minimumPaneSize;
    }

    /**
     * Requests a resize cursor while the divider is dragged.
     *
     * @return the resize cursor for the current orientation, or the inherited cursor
     */
    @Override
    public Cursor cursor() {
        return divider.isPressed() ? resizeCursor() : super.cursor();
    }

    /**
     * Measures both panes and the divider.
     * The main axis follows the sum of the panes and the divider, and the cross axis follows the
     * larger pane.
     *
     * @param constraints available and required dimensions
     * @return the natural split panel size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float availableWidth = Math.max(0, constraints.maxWidth() - padding() * 2);
        float availableHeight = Math.max(0, constraints.maxHeight() - padding() * 2);
        Size firstSize = measurePane(first, availableWidth, availableHeight);
        Size secondSize = measurePane(second, availableWidth, availableHeight);
        float dividerSize = showsDivider() ? dividerThickness : 0;
        if (orientation == Orientation.HORIZONTAL) {
            return new Size(firstSize.width() + secondSize.width() + dividerSize + padding() * 2,
                    Math.max(firstSize.height(), secondSize.height()) + padding() * 2);
        }
        return new Size(Math.max(firstSize.width(), secondSize.width()) + padding() * 2,
                firstSize.height() + secondSize.height() + dividerSize + padding() * 2);
    }

    /**
     * Lays out the panes and the divider inside the padded content area.
     */
    @Override
    protected void onLayout() {
        float inset = padding();
        float width = Math.max(0, localBounds().width() - inset * 2);
        float height = Math.max(0, localBounds().height() - inset * 2);
        Rect content = new Rect(inset, inset, width, height);
        boolean firstVisible = first != null && first.isVisible();
        boolean secondVisible = second != null && second.isVisible();
        boolean split = firstVisible && secondVisible;
        if (!split) {
            if (firstVisible) {
                first.layout(content);
            } else if (secondVisible) {
                second.layout(content);
            }
            divider.setBounds(Rect.EMPTY);
            setVisibleIfChanged(divider, false);
            return;
        }
        float length = orientation == Orientation.HORIZONTAL ? width : height;
        float dividerSize = Math.min(dividerThickness, length);
        float available = Math.max(0, length - dividerSize);
        float firstLength = available * effectivePosition(available);
        float secondLength = available - firstLength;
        if (orientation == Orientation.HORIZONTAL) {
            first.layout(new Rect(content.x(), content.y(), firstLength, height));
            second.layout(new Rect(content.x() + firstLength + dividerSize, content.y(),
                    secondLength, height));
            divider.setBounds(new Rect(content.x() + firstLength, content.y(),
                    dividerSize, height));
        } else {
            first.layout(new Rect(content.x(), content.y(), width, firstLength));
            second.layout(new Rect(content.x(), content.y() + firstLength + dividerSize,
                    width, secondLength));
            divider.setBounds(new Rect(content.x(), content.y() + firstLength,
                    width, dividerSize));
        }
        setVisibleIfChanged(divider, true);
    }

    private Size measurePane(Widget pane, float availableWidth, float availableHeight) {
        if (pane == null || !pane.isVisible()) {
            return new Size(0, 0);
        }
        return pane.measure(LayoutConstraints.loose(availableWidth, availableHeight));
    }

    private boolean showsDivider() {
        return first != null && second != null && first.isVisible() && second.isVisible();
    }

    private float effectivePosition(float available) {
        if (available <= 0) {
            return DEFAULT_DIVIDER_POSITION;
        }
        float minimum = Math.min(0.5f, minimumPaneSize / available);
        return clamp(dividerPosition, minimum, 1 - minimum);
    }

    private float availableLength() {
        float length = orientation == Orientation.HORIZONTAL
                ? Math.max(0, localBounds().width() - padding() * 2)
                : Math.max(0, localBounds().height() - padding() * 2);
        return showsDivider() ? Math.max(0, length - Math.min(dividerThickness, length)) : length;
    }

    private float pointerPosition(float x, float y) {
        return orientation == Orientation.HORIZONTAL
                ? divider.bounds().x() + x
                : divider.bounds().y() + y;
    }

    private Cursor resizeCursor() {
        return orientation == Orientation.HORIZONTAL
                ? Cursor.RESIZE_HORIZONTAL
                : Cursor.RESIZE_VERTICAL;
    }

    private void ensureDividerOnTop() {
        if (!children().isEmpty() && children().get(children().size() - 1) == divider) {
            return;
        }
        remove(divider);
        add(divider);
    }

    private static void setVisibleIfChanged(Widget widget, boolean visible) {
        if (widget.isVisible() != visible) {
            widget.setVisible(visible);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * The draggable divider between the two panes.
     */
    private final class Divider extends Widget {
        /**
         * Requests a resize cursor over the divider.
         *
         * @return the resize cursor for the current orientation
         */
        @Override
        public Cursor cursor() {
            return resizeCursor();
        }

        /**
         * Remembers the drag start position.
         *
         * @param x horizontal pointer position relative to the divider
         * @param y vertical pointer position relative to the divider
         * @param button pressed button
         * @param modifiers active modifiers
         * @return true when the press is consumed
         */
        @Override
        protected boolean onPointerPress(float x, float y, MouseButton button,
                                         Modifiers modifiers) {
            if (button != MouseButton.LEFT) {
                return false;
            }
            dragStartPointer = pointerPosition(x, y);
            dragStartPosition = effectivePosition(availableLength());
            return true;
        }

        /**
         * Moves the divider with the pointer.
         *
         * @param x horizontal pointer position relative to the divider
         * @param y vertical pointer position relative to the divider
         * @param button dragged button
         * @param modifiers modifiers captured at press time
         * @return true when the drag is consumed
         */
        @Override
        protected boolean onDrag(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button != MouseButton.LEFT) {
                return false;
            }
            float available = availableLength();
            if (available <= 0) {
                return true;
            }
            float delta = pointerPosition(x, y) - dragStartPointer;
            setDividerPosition(dragStartPosition + delta / available);
            return true;
        }

        /**
         * Consumes the primary release that ends a divider drag.
         *
         * @param x horizontal pointer position relative to the divider
         * @param y vertical pointer position relative to the divider
         * @param button released button
         * @param modifiers active modifiers
         * @return true for the primary button
         */
        @Override
        protected boolean onPointerRelease(float x, float y, MouseButton button,
                                           Modifiers modifiers) {
            return button == MouseButton.LEFT;
        }

        /**
         * Paints the divider line and highlights it while hovered or dragged.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            if (context() == null || localBounds().isEmpty()) {
                return;
            }
            Theme theme = context().theme();
            Color color = isPressed() ? theme.primary()
                    : isHovered() ? theme.textMuted() : theme.border();
            Rect bounds = localBounds();
            if (orientation == Orientation.HORIZONTAL) {
                float lineWidth = Math.min(1, bounds.width());
                if (lineWidth > 0) {
                    renderer.rect(new Rect((bounds.width() - lineWidth) / 2, 0,
                            lineWidth, bounds.height()), color);
                }
            } else {
                float lineHeight = Math.min(1, bounds.height());
                if (lineHeight > 0) {
                    renderer.rect(new Rect(0, (bounds.height() - lineHeight) / 2,
                            bounds.width(), lineHeight), color);
                }
            }
        }
    }
}
