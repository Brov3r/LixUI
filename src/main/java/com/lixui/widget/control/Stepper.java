package com.lixui.widget.control;

import com.lixui.animation.Animation;
import com.lixui.animation.Animator;
import com.lixui.animation.Easing;
import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.ImageStyle;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.IconSet;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * A horizontal progress indicator made of numbered or icon steps and connecting lines.
 * A step is completed when it lies before the current step, current when it matches the current
 * index, and future otherwise. Individual steps can additionally be marked as failed or disabled.
 * Connector fill animates between the previous and the new current step, while the step faces switch
 * to their new states immediately. Steps are clickable only when clicking is enabled; disabled steps
 * never accept a click. Titles are measured with the inherited font and truncated to their column.
 */
public class Stepper extends Widget {
    /**
     * Visual state of a single step.
     */
    public enum StepState {
        /** The step lies before the current step. */
        COMPLETED,
        /** The step matches the current step index. */
        CURRENT,
        /** The step lies after the current step. */
        FUTURE,
        /** The step is explicitly marked as failed. */
        ERROR,
        /** The step is explicitly disabled or the whole stepper is disabled. */
        DISABLED
    }

    private static final float DEFAULT_STEP_SIZE = 32;
    private static final float DEFAULT_CONNECTOR_LENGTH = 48;
    private static final float DEFAULT_CONNECTOR_THICKNESS = 2;
    private static final float DEFAULT_TITLE_GAP = 8;
    private static final float DEFAULT_PADDING = 2;
    private static final float DEFAULT_ICON_SIZE = 15;
    private static final float DEFAULT_ANIMATION_SECONDS = 0.35f;
    private static final float TITLE_SIDE_PADDING = 3;
    private static final float ANIMATION_SNAP = 0.001f;
    private static final Color DEFAULT_ERROR_COLOR = Color.hex("#E06C75");

    private final List<Step> steps = new ArrayList<>();
    private final IconSet checkIcons = new IconSet("tick.svg");

    private float stepSize = DEFAULT_STEP_SIZE;
    private float connectorLength = DEFAULT_CONNECTOR_LENGTH;
    private float connectorThickness = DEFAULT_CONNECTOR_THICKNESS;
    private float titleGap = DEFAULT_TITLE_GAP;
    private float padding = DEFAULT_PADDING;
    private float iconSize = DEFAULT_ICON_SIZE;
    private float animationDuration = DEFAULT_ANIMATION_SECONDS;
    private Easing animationEasing = Easing.EASE_IN_OUT;
    private Color errorColor = DEFAULT_ERROR_COLOR;
    private int currentIndex;
    private float displayedPosition;
    private boolean clickable;
    private int hoveredIndex = -1;
    private IntConsumer onChange;
    private Animator animationOwner;
    private Animation animation;

    /**
     * Creates an empty stepper.
     */
    public Stepper() {
    }

    /**
     * Returns the steps in display order.
     *
     * @return an immutable snapshot of the steps
     */
    public final List<Step> steps() {
        return List.copyOf(steps);
    }

    /**
     * Appends a step with a title and no icon.
     *
     * @param title step title
     * @return the created step
     * @throws NullPointerException if {@code title} is null
     */
    public final Step addStep(String title) {
        return addStep(title, null);
    }

    /**
     * Appends a step with a title and an optional icon.
     * The icon replaces the step number in current and future states; completed steps keep the
     * check mark and failed steps keep the exclamation mark.
     *
     * @param title step title
     * @param icon step icon, or null to show the step number
     * @return the created step
     * @throws NullPointerException if {@code title} is null
     */
    public final Step addStep(String title, Texture icon) {
        Step step = new Step(Objects.requireNonNull(title, "title"), icon);
        steps.add(step);
        if (steps.size() == 1) {
            currentIndex = 0;
            displayedPosition = 0;
        }
        return step;
    }

    /**
     * Removes a step and snaps the connector fill to the resulting current step.
     * Removing a step before the current one keeps the same current step object; its index
     * decreases and the change callback runs with the new index. Removing the current step selects
     * the next remaining step, or the previous step when the current one was last; the callback
     * runs with the new index. Removing a later step keeps the selection and the callback silent.
     * Removing the last remaining step clears the selection without invoking the callback.
     *
     * @param step step to remove
     * @return true when the step belonged to this stepper
     * @throws NullPointerException if {@code step} is null
     */
    public final boolean removeStep(Step step) {
        Objects.requireNonNull(step, "step");
        int previousIndex = currentIndex;
        int removedIndex = indexOf(step);
        if (removedIndex < 0) {
            return false;
        }
        steps.remove(removedIndex);
        cancelAnimation();
        hoveredIndex = -1;
        if (steps.isEmpty()) {
            currentIndex = 0;
            displayedPosition = 0;
            return true;
        }
        if (removedIndex < previousIndex) {
            currentIndex = previousIndex - 1;
        } else if (removedIndex == previousIndex) {
            currentIndex = Math.min(previousIndex, steps.size() - 1);
        }
        displayedPosition = currentIndex;
        if (removedIndex <= previousIndex) {
            notifyChange();
        }
        return true;
    }

    /**
     * Removes every step and resets the current index and connector fill.
     * The change callback is not invoked.
     */
    public final void clearSteps() {
        cancelAnimation();
        steps.clear();
        currentIndex = 0;
        displayedPosition = 0;
        hoveredIndex = -1;
    }

    /**
     * Returns the zero-based current step index.
     *
     * @return the current index
     */
    public final int currentIndex() {
        return currentIndex;
    }

    /**
     * Selects the current step and animates the connector fill toward it.
     * Selecting the index that is already current does nothing. The change callback runs after the
     * new index is selected.
     *
     * @param index zero-based step index
     * @throws IndexOutOfBoundsException if the index is outside the step list
     */
    public final void setCurrentIndex(int index) {
        if (index < 0 || index >= steps.size()) {
            throw new IndexOutOfBoundsException("Step index " + index + " is outside the step list.");
        }
        if (index == currentIndex) {
            return;
        }
        currentIndex = index;
        startAnimation();
        notifyChange();
    }

    /**
     * Returns the animated connector position in step units.
     * A value of two means the connectors up to the third step are filled.
     *
     * @return the animated position in the step index range
     */
    public final float displayedPosition() {
        return displayedPosition;
    }

    /**
     * Resolves the visual state of a step.
     * A disabled widget reports every step as disabled.
     *
     * @param index zero-based step index
     * @return the step state
     * @throws IndexOutOfBoundsException if the index is outside the step list
     */
    public final StepState state(int index) {
        if (index < 0 || index >= steps.size()) {
            throw new IndexOutOfBoundsException("Step index " + index + " is outside the step list.");
        }
        if (!isEnabled() || steps.get(index).disabled) {
            return StepState.DISABLED;
        }
        if (steps.get(index).error) {
            return StepState.ERROR;
        }
        if (index < currentIndex) {
            return StepState.COMPLETED;
        }
        return index == currentIndex ? StepState.CURRENT : StepState.FUTURE;
    }

    /**
     * Returns the step face diameter.
     *
     * @return the diameter in logical UI units
     */
    public final float stepSize() {
        return stepSize;
    }

    /**
     * Sets the preferred step face diameter.
     * The diameter shrinks when the assigned width cannot fit every step.
     *
     * @param stepSize positive diameter in logical UI units
     * @throws IllegalArgumentException if the size is not positive or is non-finite
     */
    public final void setStepSize(float stepSize) {
        if (!Float.isFinite(stepSize) || stepSize <= 0) {
            throw new IllegalArgumentException("Step size must be finite and positive.");
        }
        this.stepSize = stepSize;
    }

    /**
     * Returns the preferred connector length.
     *
     * @return the connector length in logical UI units
     */
    public final float connectorLength() {
        return connectorLength;
    }

    /**
     * Sets the preferred connector length used for the natural width.
     *
     * @param connectorLength non-negative length in logical UI units
     * @throws IllegalArgumentException if the length is negative or non-finite
     */
    public final void setConnectorLength(float connectorLength) {
        if (!Float.isFinite(connectorLength) || connectorLength < 0) {
            throw new IllegalArgumentException("Connector length must be finite and non-negative.");
        }
        this.connectorLength = connectorLength;
    }

    /**
     * Returns the connector stroke thickness.
     *
     * @return the thickness in logical UI units
     */
    public final float connectorThickness() {
        return connectorThickness;
    }

    /**
     * Sets the connector stroke thickness.
     *
     * @param connectorThickness positive thickness in logical UI units
     * @throws IllegalArgumentException if the thickness is not positive or is non-finite
     */
    public final void setConnectorThickness(float connectorThickness) {
        if (!Float.isFinite(connectorThickness) || connectorThickness <= 0) {
            throw new IllegalArgumentException("Connector thickness must be finite and positive.");
        }
        this.connectorThickness = connectorThickness;
    }

    /**
     * Returns the gap between a step face and its title.
     *
     * @return the gap in logical UI units
     */
    public final float titleGap() {
        return titleGap;
    }

    /**
     * Sets the gap between a step face and its title.
     *
     * @param titleGap non-negative gap in logical UI units
     * @throws IllegalArgumentException if the gap is negative or non-finite
     */
    public final void setTitleGap(float titleGap) {
        if (!Float.isFinite(titleGap) || titleGap < 0) {
            throw new IllegalArgumentException("Title gap must be finite and non-negative.");
        }
        this.titleGap = titleGap;
    }

    /**
     * Returns the outer padding.
     *
     * @return the padding in logical UI units
     */
    public final float padding() {
        return padding;
    }

    /**
     * Sets the outer padding around the step row.
     *
     * @param padding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is negative or non-finite
     */
    public final void setPadding(float padding) {
        if (!Float.isFinite(padding) || padding < 0) {
            throw new IllegalArgumentException("Padding must be finite and non-negative.");
        }
        this.padding = padding;
    }

    /**
     * Returns the preferred icon edge length inside a step face.
     *
     * @return the icon edge in logical UI units
     */
    public final float iconSize() {
        return iconSize;
    }

    /**
     * Sets the preferred icon edge length inside a step face.
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
     * Returns the connector fill animation duration.
     *
     * @return the duration in seconds, or zero for instant updates
     */
    public final float animationDuration() {
        return animationDuration;
    }

    /**
     * Sets the connector fill animation duration.
     * Zero applies connector changes instantly.
     *
     * @param animationDuration non-negative duration in seconds
     * @throws IllegalArgumentException if the duration is negative or non-finite
     */
    public final void setAnimationDuration(float animationDuration) {
        if (!Float.isFinite(animationDuration) || animationDuration < 0) {
            throw new IllegalArgumentException("Animation duration must be finite and non-negative.");
        }
        this.animationDuration = animationDuration;
        if (animationDuration == 0) {
            cancelAnimation();
            displayedPosition = currentIndex;
        }
    }

    /**
     * Returns the connector fill animation timing curve.
     *
     * @return the timing curve
     */
    public final Easing animationEasing() {
        return animationEasing;
    }

    /**
     * Sets the connector fill animation timing curve.
     *
     * @param animationEasing timing curve
     * @throws NullPointerException if {@code animationEasing} is null
     */
    public final void setAnimationEasing(Easing animationEasing) {
        this.animationEasing = Objects.requireNonNull(animationEasing, "animationEasing");
    }

    /**
     * Returns the color used for failed steps and their incoming connector.
     *
     * @return the error color
     */
    public final Color errorColor() {
        return errorColor;
    }

    /**
     * Sets the color used for failed steps and their incoming connector.
     *
     * @param errorColor error color
     * @throws NullPointerException if {@code errorColor} is null
     */
    public final void setErrorColor(Color errorColor) {
        this.errorColor = Objects.requireNonNull(errorColor, "errorColor");
    }

    /**
     * Reports whether step selection by clicking is enabled.
     *
     * @return true when steps can be selected with the pointer
     */
    public final boolean isClickable() {
        return clickable;
    }

    /**
     * Enables or disables step selection by clicking.
     * Disabled steps never become selected even when clicking is enabled.
     *
     * @param clickable true to select steps on click
     */
    public final void setClickable(boolean clickable) {
        this.clickable = clickable;
        if (!clickable) {
            hoveredIndex = -1;
        }
    }

    /**
     * Returns the callback invoked when the current step changes.
     *
     * @return the change callback, or null when none is assigned
     */
    public final IntConsumer onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked when the current step changes.
     * The callback receives the new zero-based index for pointer and programmatic selection
     * changes. It also runs when removing a step at or before the current one changes the current
     * index or replaces the current step; removing a later step keeps it silent.
     *
     * @param onChange change callback, or null to clear it
     */
    public final void setOnChange(IntConsumer onChange) {
        this.onChange = onChange;
    }

    /**
     * Measures the step row with the inherited font metrics.
     *
     * @param constraints available and required dimensions
     * @return the natural stepper size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        if (steps.isEmpty()) {
            return new Size(padding * 2, padding * 2);
        }
        Font font = font();
        boolean titles = font != null && hasTitles();
        float columnWidth = stepSize + connectorLength;
        if (titles) {
            float widestTitle = 0;
            for (Step step : steps) {
                widestTitle = Math.max(widestTitle, font.measure(step.title).width());
            }
            columnWidth = Math.max(columnWidth, widestTitle + TITLE_SIDE_PADDING * 2);
        }
        float height = padding * 2 + stepSize
                + (titles ? titleGap + font.lineHeight() : 0);
        return new Size(padding * 2 + steps.size() * columnWidth, height);
    }

    /**
     * Paints the connectors, step faces and titles.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (context() == null) {
            return;
        }
        Geometry geometry = geometry();
        if (geometry == null || geometry.radius <= 0) {
            return;
        }
        Theme theme = context().theme();
        drawConnectors(renderer, geometry, theme);
        Font font = font();
        for (int index = 0; index < steps.size(); index++) {
            drawStep(renderer, geometry, theme, font, index);
        }
    }

    /**
     * Tracks the step under the pointer for hover feedback.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @return false so the event can still bubble
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        hoveredIndex = clickable && isEnabled() ? stepAt(x) : -1;
        return false;
    }

    /**
     * Clears hover feedback when the pointer leaves.
     */
    @Override
    protected void onPointerExit() {
        hoveredIndex = -1;
    }

    /**
     * Selects the clicked step when clicking is enabled.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when a selectable step was clicked
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (!clickable || !isEnabled() || button != MouseButton.LEFT) {
            return false;
        }
        int index = stepAt(x);
        if (index < 0 || state(index) == StepState.DISABLED) {
            return false;
        }
        playSound();
        setCurrentIndex(index);
        return true;
    }

    /**
     * Requests the pointer cursor over selectable steps.
     *
     * @return the pointer cursor over a selectable step, or the default cursor otherwise
     */
    @Override
    public Cursor cursor() {
        return clickable && isEnabled() && hoveredIndex >= 0 && !steps.isEmpty()
                ? Cursor.POINTER : Cursor.DEFAULT;
    }

    /**
     * Releases the check icon when the stepper leaves its widget tree.
     */
    @Override
    protected void onDetached() {
        cancelAnimation();
        displayedPosition = currentIndex;
        checkIcons.release(context());
    }

    /**
     * Drops the cached check icon after a density or smoothing change.
     */
    @Override
    protected void onContextChanged() {
        checkIcons.release(context());
    }

    private void drawConnectors(RenderManager renderer, Geometry geometry, Theme theme) {
        if (steps.size() < 2) {
            return;
        }
        Color track = theme.border().withAlpha(0.6f);
        for (int index = 0; index < steps.size() - 1; index++) {
            float left = geometry.contentX + geometry.columnWidth * (index + 0.5f)
                    + geometry.radius;
            float right = geometry.contentX + geometry.columnWidth * (index + 1.5f)
                    - geometry.radius;
            if (right <= left) {
                continue;
            }
            renderer.strokeLine(new Point(left, geometry.centerY),
                    new Point(right, geometry.centerY), connectorThickness, Paint.solid(track));
            float fraction = Math.clamp(displayedPosition - index, 0f, 1f);
            if (fraction <= ANIMATION_SNAP) {
                continue;
            }
            Color fill = steps.get(index + 1).error ? errorColor : theme.primary();
            float filledRight = left + (right - left) * fraction;
            if (filledRight - left > ANIMATION_SNAP) {
                renderer.strokeLine(new Point(left, geometry.centerY),
                        new Point(filledRight, geometry.centerY), connectorThickness,
                        Paint.solid(fill));
            }
        }
    }

    private void drawStep(RenderManager renderer, Geometry geometry, Theme theme, Font font,
                          int index) {
        float centerX = geometry.contentX + geometry.columnWidth * (index + 0.5f);
        Point center = new Point(centerX, geometry.centerY);
        StepState state = state(index);
        if (state != StepState.DISABLED && index == hoveredIndex) {
            renderer.fillCircle(center, geometry.radius + 4,
                    Paint.solid(theme.primary().withAlpha(0.12f)));
        }
        Color contentColor = contentColor(theme, state);
        switch (state) {
            case COMPLETED -> {
                renderer.fillCircle(center, geometry.radius, Paint.solid(theme.primary()));
                drawCheck(renderer, geometry, center, contentColor);
            }
            case CURRENT -> {
                renderer.fillCircle(center, geometry.radius, Paint.solid(theme.primary()));
                renderer.strokeCircle(center, geometry.radius + 3, 1.5f,
                        Paint.solid(theme.primary().withAlpha(0.4f)));
                drawContent(renderer, geometry, font, index, center, contentColor);
            }
            case FUTURE -> {
                renderer.strokeCircle(center, geometry.radius, 1.4f,
                        Paint.solid(theme.border()));
                drawContent(renderer, geometry, font, index, center, contentColor);
            }
            case ERROR -> {
                renderer.fillCircle(center, geometry.radius, Paint.solid(errorColor));
                renderer.strokeCircle(center, geometry.radius + 3, 1.5f,
                        Paint.solid(errorColor.withAlpha(0.35f)));
                drawContent(renderer, geometry, font, index, center, contentColor);
            }
            case DISABLED -> {
                renderer.strokeCircle(center, geometry.radius, 1.2f,
                        Paint.solid(theme.border().withAlpha(0.4f)));
                drawContent(renderer, geometry, font, index, center, contentColor);
            }
            default -> throw new IllegalStateException("Unsupported step state: " + state);
        }
        drawTitle(renderer, geometry, theme, font, index, centerX, state);
    }

    private void drawContent(RenderManager renderer, Geometry geometry, Font font, int index,
                             Point center, Color color) {
        Texture icon = steps.get(index).icon;
        if (icon != null) {
            float edge = geometry.iconEdge;
            if (edge > 0) {
                renderer.icon(icon, new Rect(center.x() - edge / 2, center.y() - edge / 2,
                        edge, edge), ImageStyle.ICON, color);
            }
            return;
        }
        if (font == null) {
            return;
        }
        String label = state(index) == StepState.ERROR ? "!" : Integer.toString(index + 1);
        renderer.text(font, label, center.x(), center.y(), color,
                HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
    }

    private void drawCheck(RenderManager renderer, Geometry geometry, Point center, Color color) {
        Texture icon = checkIcon();
        if (icon != null && geometry.iconEdge > 0) {
            float edge = geometry.iconEdge;
            renderer.icon(icon, new Rect(center.x() - edge / 2, center.y() - edge / 2,
                    edge, edge), ImageStyle.ICON, color);
            return;
        }
        float radius = geometry.radius;
        List<Point> path = List.of(
                new Point(center.x() - radius * 0.36f, center.y()),
                new Point(center.x() - radius * 0.08f, center.y() + radius * 0.28f),
                new Point(center.x() + radius * 0.38f, center.y() - radius * 0.32f));
        renderer.strokePolyline(path, Math.max(1.5f, radius * 0.14f), Paint.solid(color));
    }

    private void drawTitle(RenderManager renderer, Geometry geometry, Theme theme, Font font,
                           int index, float centerX, StepState state) {
        if (font == null) {
            return;
        }
        String title = steps.get(index).title;
        float maxWidth = geometry.columnWidth - TITLE_SIDE_PADDING * 2;
        if (title.isEmpty() || maxWidth <= 0) {
            return;
        }
        TextStyle style = new TextStyle(maxWidth, 0, 1, TextOverflow.ELLIPSIS,
                HorizontalAlignment.CENTER, VerticalAlignment.TOP);
        renderer.text(font, title, centerX, geometry.titleY, titleColor(theme, state), style);
    }

    private Texture checkIcon() {
        UiContext owner = context();
        if (owner == null) {
            return null;
        }
        int pixels = Math.max(1, Math.round(iconSize * owner.density()));
        if (!checkIcons.matches(owner, pixels)) {
            checkIcons.reload(owner, pixels);
        }
        return checkIcons.get(0);
    }

    private Geometry geometry() {
        Rect area = localBounds();
        if (steps.isEmpty() || area.isEmpty()) {
            return null;
        }
        Rect content = new Rect(area.x() + padding, area.y() + padding,
                Math.max(0, area.width() - padding * 2),
                Math.max(0, area.height() - padding * 2));
        if (content.isEmpty()) {
            return null;
        }
        Font font = font();
        boolean titles = font != null && hasTitles();
        float titleHeight = titles ? titleGap + font.lineHeight() : 0;
        float columnWidth = content.width() / steps.size();
        float circleSpace = Math.max(0, content.height() - titleHeight);
        float radius = Math.min(stepSize / 2,
                Math.min(columnWidth * 0.42f, circleSpace / 2));
        float blockHeight = radius * 2 + titleHeight;
        float top = content.y() + Math.max(0, (content.height() - blockHeight) / 2);
        float centerY = top + radius;
        float titleY = top + radius * 2 + titleGap;
        float edge = Math.min(iconSize, radius * 1.4f);
        return new Geometry(content.x(), columnWidth, radius, centerY, titleY, edge);
    }

    private int stepAt(float x) {
        Geometry geometry = geometry();
        if (geometry == null || geometry.columnWidth <= 0) {
            return -1;
        }
        int index = (int) Math.floor((x - geometry.contentX) / geometry.columnWidth);
        return index >= 0 && index < steps.size() ? index : -1;
    }

    private int indexOf(Step step) {
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index) == step) {
                return index;
            }
        }
        return -1;
    }

    private boolean hasTitles() {
        for (Step step : steps) {
            if (!step.title.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Color contentColor(Theme theme, StepState state) {
        return switch (state) {
            case COMPLETED, CURRENT -> theme.onPrimary();
            case ERROR -> errorColor.luminance() > 0.6f
                    ? Color.BLACK.withAlpha(0.8f) : Color.WHITE;
            case FUTURE -> theme.textMuted();
            case DISABLED -> theme.textMuted().withAlpha(0.45f);
        };
    }

    private Color titleColor(Theme theme, StepState state) {
        return switch (state) {
            case COMPLETED, CURRENT -> theme.text();
            case FUTURE -> theme.textMuted();
            case ERROR -> errorColor;
            case DISABLED -> theme.textMuted().withAlpha(0.45f);
        };
    }

    private void startAnimation() {
        cancelAnimation();
        UiContext owner = context();
        if (owner == null || animationDuration <= 0
                || Math.abs(currentIndex - displayedPosition) <= ANIMATION_SNAP) {
            displayedPosition = currentIndex;
            return;
        }
        animationOwner = owner.animator();
        animation = animationOwner.animate(displayedPosition, currentIndex, animationDuration,
                animationEasing, value -> displayedPosition = Math.clamp(value, 0f,
                        Math.max(0, steps.size() - 1)));
        animation.setOnComplete(this::clearAnimation);
    }

    private void cancelAnimation() {
        if (animation != null) {
            animationOwner.cancel(animation);
        }
        clearAnimation();
    }

    private void clearAnimation() {
        animation = null;
        animationOwner = null;
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.accept(currentIndex);
        }
    }

    private record Geometry(float contentX, float columnWidth, float radius, float centerY,
                            float titleY, float iconEdge) {
    }

    /**
     * A step with a title, an optional icon and explicit failure or disabled flags.
     */
    public static final class Step {
        private String title;
        private Texture icon;
        private boolean error;
        private boolean disabled;

        private Step(String title, Texture icon) {
            this.title = title;
            this.icon = icon;
        }

        /**
         * Returns the step title.
         *
         * @return the title, never null
         */
        public String title() {
            return title;
        }

        /**
         * Replaces the step title.
         *
         * @param title new title
         * @throws NullPointerException if {@code title} is null
         */
        public void setTitle(String title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        /**
         * Returns the step icon.
         *
         * @return the icon, or null when the step shows its number
         */
        public Texture icon() {
            return icon;
        }

        /**
         * Replaces the step icon.
         *
         * @param icon icon to draw, or null to show the step number
         */
        public void setIcon(Texture icon) {
            this.icon = icon;
        }

        /**
         * Reports whether the step is marked as failed.
         *
         * @return true when the step shows the error state
         */
        public boolean isError() {
            return error;
        }

        /**
         * Marks or unmarks the step as failed.
         * The error state takes precedence over completed, current and future.
         *
         * @param error true to show the error state
         */
        public void setError(boolean error) {
            this.error = error;
        }

        /**
         * Reports whether the step is disabled.
         *
         * @return true when the step cannot be selected
         */
        public boolean isDisabled() {
            return disabled;
        }

        /**
         * Disables or enables the step.
         * A disabled step is drawn muted and never becomes selected by clicking.
         *
         * @param disabled true to disable the step
         */
        public void setDisabled(boolean disabled) {
            this.disabled = disabled;
        }
    }
}
