package com.lixui.widget.container;

import com.lixui.animation.Easing;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.geometry.Transform;
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
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.IconSet;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A vertical list of collapsible sections with animated content reveal.
 * Every section has a clickable header with an expansion arrow and an optional title icon.
 * Sections can be expanded independently, or the accordion can keep only one section open at a time.
 * Disabled sections stay visible but ignore pointer input. Expanded content is clipped to its animated
 * reveal area, so opening and closing stay smooth even when the content changes size.
 */
public class Accordion extends Widget {
    private static final float DEFAULT_HEADER_HEIGHT = 32;
    private static final float DEFAULT_HEADER_PADDING = 10;
    private static final float DEFAULT_CONTENT_PADDING = 10;
    private static final float DEFAULT_ICON_SIZE = 16;
    private static final float DEFAULT_ICON_GAP = 8;
    private static final float ARROW_GAP = 8;
    private static final float DEFAULT_EXPANSION_SECONDS = 0.2f;
    private static final float HOVER_ALPHA = 0.08f;
    private static final float PRESSED_ALPHA = 0.14f;
    private static final float EXPANDED_ALPHA = 0.05f;
    private static final float ANIMATION_SNAP = 0.001f;

    private final List<Section> sections = new ArrayList<>();
    private final IconSet icons = new IconSet("arrow-right-04.svg");

    private boolean exclusive;
    private boolean showIcons = true;
    private float headerHeight = DEFAULT_HEADER_HEIGHT;
    private float headerPadding = DEFAULT_HEADER_PADDING;
    private float contentPadding = DEFAULT_CONTENT_PADDING;
    private float iconSize = DEFAULT_ICON_SIZE;
    private float iconGap = DEFAULT_ICON_GAP;
    private ImageStyle iconStyle = ImageStyle.ICON;
    private float expansionDuration = DEFAULT_EXPANSION_SECONDS;
    private Easing expansionEasing = Easing.EASE_IN_OUT;
    private Consumer<Section> onExpandedChange;
    private Section hoveredSection;
    private Section pressedSection;
    private Section lastExpanded;

    /**
     * Creates an empty accordion.
     */
    public Accordion() {
        setClipToBounds(true);
    }

    /**
     * Appends a section without an icon.
     *
     * @param title section title
     * @param content content revealed while the section is expanded, or null for an empty section
     * @return the created section
     * @throws NullPointerException if {@code title} is null
     * @throws IllegalArgumentException if the content already belongs to a widget tree
     */
    public Section addSection(String title, Widget content) {
        return addSection(title, null, content);
    }

    /**
     * Appends a section with a title icon.
     *
     * @param title section title
     * @param icon title icon, or null for a text-only title
     * @param content content revealed while the section is expanded, or null for an empty section
     * @return the created section
     * @throws NullPointerException if {@code title} is null
     * @throws IllegalArgumentException if the content already belongs to a widget tree
     */
    public Section addSection(String title, Texture icon, Widget content) {
        Section section = new Section(this, Objects.requireNonNull(title, "title"), icon, content);
        if (content != null) {
            add(content);
        }
        sections.add(section);
        updateContentVisibility(section);
        return section;
    }

    /**
     * Appends a section with a title icon and an initial expansion state.
     * An initially expanded section collapses other sections when the accordion is exclusive.
     *
     * @param title section title
     * @param icon title icon, or null for a text-only title
     * @param content content revealed while the section is expanded, or null for an empty section
     * @param expanded true to expand the section immediately
     * @return the created section
     * @throws NullPointerException if {@code title} is null
     * @throws IllegalArgumentException if the content already belongs to a widget tree
     */
    public Section addSection(String title, Texture icon, Widget content, boolean expanded) {
        Section section = addSection(title, icon, content);
        if (expanded) {
            setSectionExpanded(section, true, false, true);
        }
        return section;
    }

    /**
     * Appends a section without an icon and with an initial expansion state.
     * An initially expanded section collapses other sections when the accordion is exclusive.
     *
     * @param title section title
     * @param content content revealed while the section is expanded, or null for an empty section
     * @param expanded true to expand the section immediately
     * @return the created section
     * @throws NullPointerException if {@code title} is null
     * @throws IllegalArgumentException if the content already belongs to a widget tree
     */
    public Section addSection(String title, Widget content, boolean expanded) {
        return addSection(title, null, content, expanded);
    }

    /**
     * Removes a section and detaches its content without invoking the change callback.
     *
     * @param section section to remove
     * @return true when the section belonged to this accordion
     * @throws NullPointerException if {@code section} is null
     */
    public boolean removeSection(Section section) {
        Objects.requireNonNull(section, "section");
        if (!sections.remove(section)) {
            return false;
        }
        section.animation.cancel();
        if (section.content != null) {
            remove(section.content);
        }
        section.owner = null;
        if (hoveredSection == section) {
            hoveredSection = null;
        }
        if (pressedSection == section) {
            pressedSection = null;
        }
        if (lastExpanded == section) {
            lastExpanded = null;
        }
        return true;
    }

    /**
     * Removes every section and detaches all content.
     */
    public void clearSections() {
        for (Section section : List.copyOf(sections)) {
            removeSection(section);
        }
    }

    /**
     * Returns the sections in display order.
     *
     * @return an immutable snapshot of the sections
     */
    public List<Section> sections() {
        return List.copyOf(sections);
    }

    /**
     * Returns the index of a section.
     *
     * @param section section to look up
     * @return the zero-based index, or -1 when the section is not in this accordion
     * @throws NullPointerException if {@code section} is null
     */
    public int indexOf(Section section) {
        Objects.requireNonNull(section, "section");
        for (int index = 0; index < sections.size(); index++) {
            if (sections.get(index) == section) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Returns the first expanded section in display order.
     *
     * @return the expanded section, or null when every section is closed
     */
    public Section expanded() {
        for (Section section : sections) {
            if (section.expanded) {
                return section;
            }
        }
        return null;
    }

    /**
     * Returns every expanded section in display order.
     *
     * @return an immutable snapshot of the expanded sections
     */
    public List<Section> expandedSections() {
        List<Section> expanded = new ArrayList<>();
        for (Section section : sections) {
            if (section.expanded) {
                expanded.add(section);
            }
        }
        return List.copyOf(expanded);
    }

    /**
     * Expands a section, collapsing other open sections when the accordion is exclusive.
     *
     * @param section section to expand
     * @return true when the expansion state changed
     * @throws NullPointerException if {@code section} is null
     * @throws IllegalArgumentException if the section is not in this accordion
     */
    public boolean expand(Section section) {
        requireSection(section);
        return setSectionExpanded(section, true, true, true);
    }

    /**
     * Collapses a section.
     *
     * @param section section to collapse
     * @return true when the expansion state changed
     * @throws NullPointerException if {@code section} is null
     * @throws IllegalArgumentException if the section is not in this accordion
     */
    public boolean collapse(Section section) {
        requireSection(section);
        return setSectionExpanded(section, false, true, true);
    }

    /**
     * Toggles a section.
     *
     * @param section section to toggle
     * @return true when the expansion state changed
     * @throws NullPointerException if {@code section} is null
     * @throws IllegalArgumentException if the section is not in this accordion
     */
    public boolean toggle(Section section) {
        requireSection(section);
        return setSectionExpanded(section, !section.expanded, true, true);
    }

    /**
     * Collapses every section.
     */
    public void collapseAll() {
        for (Section section : List.copyOf(sections)) {
            setSectionExpanded(section, false, true, true);
        }
    }

    /**
     * Reports whether the accordion keeps at most one section expanded.
     *
     * @return true when only one section can be open
     */
    public boolean isExclusive() {
        return exclusive;
    }

    /**
     * Enables or disables exclusive expansion.
     * Enabling exclusivity keeps the most recently expanded section and collapses the others.
     *
     * @param exclusive true to keep at most one section open
     */
    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
        if (!exclusive) {
            return;
        }
        Section keeper = lastExpanded != null && lastExpanded.expanded
                ? lastExpanded : expanded();
        if (keeper == null) {
            return;
        }
        for (Section section : List.copyOf(sections)) {
            if (section != keeper && section.expanded) {
                setSectionExpanded(section, false, true, true);
            }
        }
    }

    /**
     * Returns the header height.
     *
     * @return the header height in logical UI units
     */
    public float headerHeight() {
        return headerHeight;
    }

    /**
     * Sets the header height.
     *
     * @param headerHeight positive header height in logical UI units
     * @throws IllegalArgumentException if the height is not positive or non-finite
     */
    public void setHeaderHeight(float headerHeight) {
        if (!Float.isFinite(headerHeight) || headerHeight <= 0) {
            throw new IllegalArgumentException("Header height must be finite and positive.");
        }
        this.headerHeight = headerHeight;
    }

    /**
     * Returns the horizontal padding inside each header.
     *
     * @return the padding in logical UI units
     */
    public float headerPadding() {
        return headerPadding;
    }

    /**
     * Sets the horizontal padding inside each header.
     *
     * @param headerPadding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is negative or non-finite
     */
    public void setHeaderPadding(float headerPadding) {
        if (!Float.isFinite(headerPadding) || headerPadding < 0) {
            throw new IllegalArgumentException("Header padding must be finite and non-negative.");
        }
        this.headerPadding = headerPadding;
    }

    /**
     * Returns the padding around expanded content.
     *
     * @return the padding in logical UI units
     */
    public float contentPadding() {
        return contentPadding;
    }

    /**
     * Sets the padding around expanded content.
     *
     * @param contentPadding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is negative or non-finite
     */
    public void setContentPadding(float contentPadding) {
        if (!Float.isFinite(contentPadding) || contentPadding < 0) {
            throw new IllegalArgumentException("Content padding must be finite and non-negative.");
        }
        this.contentPadding = contentPadding;
    }

    /**
     * Returns the title and expansion icon edge length.
     *
     * @return the icon size in logical UI units
     */
    public float iconSize() {
        return iconSize;
    }

    /**
     * Sets the title and expansion icon edge length.
     *
     * @param iconSize positive icon size in logical UI units
     * @throws IllegalArgumentException if the size is not positive or non-finite
     */
    public void setIconSize(float iconSize) {
        if (!Float.isFinite(iconSize) || iconSize <= 0) {
            throw new IllegalArgumentException("Icon size must be finite and positive.");
        }
        this.iconSize = iconSize;
    }

    /**
     * Returns the gap between a title icon and the title.
     *
     * @return the gap in logical UI units
     */
    public float iconGap() {
        return iconGap;
    }

    /**
     * Sets the gap between a title icon and the title.
     *
     * @param iconGap non-negative gap in logical UI units
     * @throws IllegalArgumentException if the gap is negative or non-finite
     */
    public void setIconGap(float iconGap) {
        if (!Float.isFinite(iconGap) || iconGap < 0) {
            throw new IllegalArgumentException("Icon gap must be finite and non-negative.");
        }
        this.iconGap = iconGap;
    }

    /**
     * Returns how title icons are fitted into their square slots.
     *
     * @return the title icon placement style
     */
    public ImageStyle iconStyle() {
        return iconStyle;
    }

    /**
     * Sets how title icons are fitted into their square slots.
     * The default preserves the icon aspect ratio and centers it.
     *
     * @param iconStyle title icon placement style
     * @throws NullPointerException if {@code iconStyle} is null
     */
    public void setIconStyle(ImageStyle iconStyle) {
        this.iconStyle = Objects.requireNonNull(iconStyle, "iconStyle");
    }

    /**
     * Reports whether section title icons are drawn.
     *
     * @return true when title icons are shown
     */
    public boolean showIcons() {
        return showIcons;
    }

    /**
     * Shows or hides every section title icon without removing it.
     * Hidden icons take no space in header measurement. The expansion arrow is always visible.
     *
     * @param showIcons true to draw section title icons
     */
    public void setShowIcons(boolean showIcons) {
        this.showIcons = showIcons;
    }

    /**
     * Returns the expansion animation duration.
     *
     * @return the duration in seconds, or zero when changes apply instantly
     */
    public float expansionDuration() {
        return expansionDuration;
    }

    /**
     * Sets the expansion animation duration.
     * Zero disables the animation and applies expansion changes instantly.
     *
     * @param expansionDuration non-negative duration in seconds
     * @throws IllegalArgumentException if the duration is negative or non-finite
     */
    public void setExpansionDuration(float expansionDuration) {
        if (!Float.isFinite(expansionDuration) || expansionDuration < 0) {
            throw new IllegalArgumentException("Expansion duration must be finite and non-negative.");
        }
        this.expansionDuration = expansionDuration;
        if (expansionDuration == 0) {
            for (Section section : sections) {
                section.animation.cancel();
                section.expandedAmount = section.expanded ? 1 : 0;
                updateContentVisibility(section);
            }
        }
    }

    /**
     * Returns the expansion animation timing curve.
     *
     * @return the timing curve
     */
    public Easing expansionEasing() {
        return expansionEasing;
    }

    /**
     * Sets the expansion animation timing curve.
     *
     * @param expansionEasing timing curve
     * @throws NullPointerException if {@code expansionEasing} is null
     */
    public void setExpansionEasing(Easing expansionEasing) {
        this.expansionEasing = Objects.requireNonNull(expansionEasing, "expansionEasing");
    }

    /**
     * Returns the callback invoked after a section expansion state changes.
     *
     * @return the change callback, or null when none is assigned
     */
    public Consumer<Section> onExpandedChange() {
        return onExpandedChange;
    }

    /**
     * Assigns a callback invoked after a section expansion state changes.
     * The callback runs for the requested section and for sections collapsed by exclusivity.
     *
     * @param onExpandedChange change callback, or null to clear it
     */
    public void setOnExpandedChange(Consumer<Section> onExpandedChange) {
        this.onExpandedChange = onExpandedChange;
    }

    /**
     * Returns the panel style from the current UI context.
     *
     * @return the context panel style, or null when the accordion is not attached
     */
    @Override
    protected WidgetStyle defaultStyle() {
        return context() == null ? null : context().theme().panelStyle();
    }

    /**
     * Measures headers, title icons and the expanded part of every content widget.
     *
     * @param constraints available and required dimensions
     * @return the natural accordion size for the current expansion progress
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Font font = font();
        float contentWidth = Math.max(0, constraints.maxWidth() - contentPadding * 2);
        float width = 0;
        float height = 0;
        for (Section section : sections) {
            height += headerHeight;
            width = Math.max(width, headerWidth(section, font));
            if (section.content == null) {
                continue;
            }
            Size contentSize = measureContent(section, contentWidth, constraints.maxHeight());
            width = Math.max(width, contentSize.width() + contentPadding * 2);
            height += section.expandedAmount * (contentSize.height() + contentPadding * 2);
        }
        return new Size(width, height);
    }

    /**
     * Places headers and content at their animated reveal offsets.
     */
    @Override
    protected void onLayout() {
        float contentWidth = Math.max(0, bounds().width() - contentPadding * 2);
        float y = 0;
        for (Section section : sections) {
            section.headerY = y;
            y += headerHeight;
            if (section.content == null) {
                section.revealHeight = 0;
                continue;
            }
            Size contentSize = measureContent(section, contentWidth, bounds().height());
            section.revealHeight = section.expandedAmount * (contentSize.height() + contentPadding * 2);
            section.content.layout(new Rect(contentPadding, y + contentPadding,
                    contentWidth, contentSize.height()));
            updateContentVisibility(section);
            y += section.revealHeight;
        }
    }

    /**
     * Loads the expansion arrows and refreshes content visibility when the accordion is attached.
     */
    @Override
    protected void onAttached() {
        ensureIcons();
        for (Section section : sections) {
            updateContentVisibility(section);
        }
    }

    /**
     * Stops expansion animations and releases the expansion arrows when the accordion leaves the tree.
     */
    @Override
    protected void onDetached() {
        for (Section section : sections) {
            boolean settling = section.animation.isActive();
            section.animation.cancel();
            if (settling) {
                section.expandedAmount = section.expanded ? 1 : 0;
            }
            updateContentVisibility(section);
        }
        icons.release(context());
    }

    /**
     * Drops cached expansion arrows so they reload at the new pixel density.
     */
    @Override
    protected void onContextChanged() {
        icons.release(context());
    }

    /**
     * Paints the accordion surface, headers, icons and separators.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        drawSurface(renderer);
        if (sections.isEmpty()) {
            return;
        }
        ensureIcons();
        Font font = font();
        for (Section section : sections) {
            drawHeader(renderer, section, font);
        }
    }

    /**
     * Clips expanded content to its animated reveal area.
     *
     * @param child direct child being drawn or hit-tested
     * @return the reveal rectangle for content, or null for an unknown child
     */
    @Override
    protected Rect childClip(Widget child) {
        for (Section section : sections) {
            if (section.content == child) {
                return new Rect(0, section.headerY + headerHeight,
                        bounds().width(), section.revealHeight);
            }
        }
        return null;
    }

    /**
     * Requests the pointer cursor over enabled section headers.
     *
     * @return the pointer cursor over a header, or the default cursor otherwise
     */
    @Override
    public Cursor cursor() {
        return hoveredSection != null && hoveredSection.enabled ? Cursor.POINTER : Cursor.DEFAULT;
    }

    /**
     * Tracks the section header under the pointer.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @return false so the event can still bubble
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        hoveredSection = headerAt(x, y);
        return false;
    }

    /**
     * Clears hover and pressed header feedback when the pointer leaves.
     */
    @Override
    protected void onPointerExit() {
        hoveredSection = null;
        pressedSection = null;
    }

    /**
     * Remembers the pressed section header for pressed feedback.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when the press landed on a header
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        pressedSection = headerAt(x, y);
        return pressedSection != null;
    }

    /**
     * Clears pressed header feedback.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button released button
     * @param modifiers active modifiers
     * @return false so the event can still bubble
     */
    @Override
    protected boolean onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
        pressedSection = null;
        return false;
    }

    /**
     * Toggles the clicked header when its section is enabled.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the click landed on a header
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        Section section = headerAt(x, y);
        if (section == null) {
            return false;
        }
        if (section.enabled) {
            playSound();
            toggle(section);
        }
        return true;
    }

    /**
     * Routes a double click through the same header activation as a single click.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the double click landed on a header
     */
    @Override
    protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
        return onClick(x, y, button, modifiers);
    }

    private boolean setSectionExpanded(Section section, boolean expanded, boolean animate,
                                       boolean notify) {
        if (section.expanded == expanded) {
            return false;
        }
        if (expanded && exclusive) {
            for (Section other : List.copyOf(sections)) {
                if (other != section && other.expanded) {
                    setSectionExpanded(other, false, animate, notify);
                }
            }
        }
        section.expanded = expanded;
        if (expanded) {
            lastExpanded = section;
        }
        animateExpansion(section, animate);
        updateContentVisibility(section);
        if (notify && onExpandedChange != null) {
            onExpandedChange.accept(section);
        }
        return true;
    }

    private void animateExpansion(Section section, boolean animate) {
        section.animation.cancel();
        float target = section.expanded ? 1 : 0;
        UiContext context = context();
        if (!animate || expansionDuration <= 0 || context == null
                || Math.abs(target - section.expandedAmount) < ANIMATION_SNAP) {
            section.expandedAmount = target;
            return;
        }
        section.animation.start(context.animator(), section.expandedAmount, target,
                expansionDuration, expansionEasing,
                value -> {
                    section.expandedAmount = Math.clamp(value, 0f, 1f);
                    updateContentVisibility(section);
                },
                () -> {
                    section.expandedAmount = target;
                    updateContentVisibility(section);
                });
    }

    private void updateContentVisibility(Section section) {
        if (section.content != null) {
            setVisibleIfChanged(section.content,
                    section.expanded || section.expandedAmount > ANIMATION_SNAP);
        }
    }

    private Size measureContent(Section section, float width, float height) {
        if (section.content == null) {
            return new Size(0, 0);
        }
        return section.content.measure(LayoutConstraints.loose(width, Math.max(0, height)));
    }

    private float headerWidth(Section section, Font font) {
        float width = headerPadding * 2 + iconSize + ARROW_GAP;
        if (showIcons && section.icon != null) {
            width += iconSize + iconGap;
        }
        if (font != null && !section.title.isEmpty()) {
            width += font.measure(section.title).width();
        }
        return width;
    }

    private void drawSurface(RenderManager renderer) {
        WidgetStyle style = style();
        if (style == null || localBounds().isEmpty()) {
            return;
        }
        WidgetState state = isEnabled() ? WidgetState.NORMAL : WidgetState.DISABLED;
        float radius = style.cornerRadius();
        renderer.roundedRect(localBounds(), radius, style.background(state));
        float borderWidth = style.borderWidth();
        if (borderWidth <= 0) {
            return;
        }
        Rect stroke = inset(localBounds(), borderWidth / 2);
        if (!stroke.isEmpty()) {
            renderer.strokeRoundedRect(stroke, Math.max(0, radius - borderWidth / 2),
                    borderWidth, Paint.solid(style.border(WidgetState.NORMAL)));
        }
    }

    private void drawHeader(RenderManager renderer, Section section, Font font) {
        Theme theme = context().theme();
        Color textColor = headerTextColor(theme, section);
        float startY = section.headerY;
        float centerY = startY + headerHeight / 2;
        float iconY = startY + (headerHeight - iconSize) / 2;
        float x = headerPadding;

        drawHeaderHighlight(renderer, theme, section);
        drawExpansionIcon(renderer, section,
                new Rect(x, iconY, iconSize, iconSize), textColor);
        x += iconSize + ARROW_GAP;
        if (showIcons && section.icon != null) {
            renderer.icon(section.icon, new Rect(x, iconY, iconSize, iconSize),
                    iconStyle, textColor);
            x += iconSize + iconGap;
        }
        float available = Math.max(0, bounds().width() - x - headerPadding);
        if (font != null && available > 0 && !section.title.isEmpty()) {
            renderer.text(font, section.title, x, centerY, textColor,
                    new TextStyle(available, 0, 1, TextOverflow.ELLIPSIS,
                            HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
        }
        renderer.rect(new Rect(0, startY + headerHeight - 1, bounds().width(), 1),
                theme.border().withAlpha(0.75f));
    }

    private void drawHeaderHighlight(RenderManager renderer, Theme theme, Section section) {
        float alpha = EXPANDED_ALPHA * section.expandedAmount;
        if (section.enabled && isEnabled()) {
            if (section == hoveredSection) {
                alpha += HOVER_ALPHA;
            }
            if (section == pressedSection && isPressed()) {
                alpha += PRESSED_ALPHA;
            }
        }
        if (alpha > ANIMATION_SNAP) {
            renderer.rect(new Rect(0, section.headerY, bounds().width(), headerHeight),
                    theme.primary().withAlpha(Math.min(1, alpha)));
        }
    }

    private Color headerTextColor(Theme theme, Section section) {
        if (!isEnabled() || !section.enabled) {
            return theme.textMuted().withAlpha(0.45f);
        }
        if (section == hoveredSection || section == pressedSection) {
            return theme.text();
        }
        return Color.mix(theme.textMuted(), theme.text(),
                0.55f + 0.45f * section.expandedAmount);
    }

    private void drawExpansionIcon(RenderManager renderer, Section section, Rect bounds,
                                   Color color) {
        Texture arrow = icons.get(0);
        if (arrow == null) {
            return;
        }
        float amount = Math.clamp(section.expandedAmount, 0f, 1f);
        float angle = (float) (Math.PI / 2 * amount);
        float centerX = bounds.x() + bounds.width() / 2;
        float centerY = bounds.y() + bounds.height() / 2;
        Transform transform = Transform.translation(centerX, centerY)
                .multiply(Transform.rotation(angle));
        Rect local = new Rect(-bounds.width() / 2, -bounds.height() / 2,
                bounds.width(), bounds.height());
        renderer.withTransform(transform,
                () -> renderer.icon(arrow, local, ImageStyle.FILL, color));
    }

    private void ensureIcons() {
        UiContext context = context();
        if (context == null || sections.isEmpty()) {
            return;
        }
        int size = Math.max(1, Math.round(iconSize * context.density()));
        if (icons.matches(context, size)) {
            return;
        }
        icons.reload(context, size);
    }

    Rect headerBounds(Section section) {
        requireSection(section);
        return new Rect(0, section.headerY, bounds().width(), headerHeight);
    }

    private Section headerAt(float x, float y) {
        if (x < 0 || x >= bounds().width()) {
            return null;
        }
        for (Section section : sections) {
            if (y >= section.headerY && y < section.headerY + headerHeight) {
                return section;
            }
        }
        return null;
    }

    private void requireSection(Section section) {
        Objects.requireNonNull(section, "section");
        if (section.owner != this) {
            throw new IllegalArgumentException("Section does not belong to this accordion.");
        }
    }

    private static Rect inset(Rect bounds, float amount) {
        return new Rect(bounds.x() + amount, bounds.y() + amount,
                Math.max(0, bounds.width() - amount * 2),
                Math.max(0, bounds.height() - amount * 2));
    }

    /**
     * A single collapsible entry with a title, an optional title icon and a content widget.
     */
    public static final class Section {
        private Accordion owner;
        private String title;
        private Texture icon;
        private final Widget content;
        private boolean expanded;
        private boolean enabled = true;
        private float expandedAmount;
        private final AnimationSlot animation = new AnimationSlot();
        private float headerY;
        private float revealHeight;

        private Section(Accordion owner, String title, Texture icon, Widget content) {
            this.owner = owner;
            this.title = title;
            this.icon = icon;
            this.content = content;
        }

        /**
         * Returns the section title.
         *
         * @return the title
         */
        public String title() {
            return title;
        }

        /**
         * Replaces the section title.
         *
         * @param title section title
         * @throws NullPointerException if {@code title} is null
         */
        public void setTitle(String title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        /**
         * Returns the title icon.
         *
         * @return the icon, or null when the section has no title icon
         */
        public Texture icon() {
            return icon;
        }

        /**
         * Assigns a title icon. The texture is owned by the caller.
         *
         * @param icon title icon, or null to remove it
         */
        public void setIcon(Texture icon) {
            this.icon = icon;
        }

        /**
         * Returns the content revealed while the section is expanded.
         *
         * @return the content widget, or null for an empty section
         */
        public Widget content() {
            return content;
        }

        /**
         * Reports whether the section is expanded.
         *
         * @return true when the section is open
         */
        public boolean isExpanded() {
            return expanded;
        }

        /**
         * Expands or collapses the section with the accordion animation.
         * An accordion in exclusive mode collapses the other open sections.
         *
         * @param expanded true to open the section
         */
        public void setExpanded(boolean expanded) {
            if (owner == null) {
                if (this.expanded != expanded) {
                    this.expanded = expanded;
                    expandedAmount = expanded ? 1 : 0;
                }
                return;
            }
            owner.setSectionExpanded(this, expanded, true, true);
        }

        /**
         * Returns the current expansion progress.
         *
         * @return zero for a closed section and one for a fully open section
         */
        public float expandedAmount() {
            return expandedAmount;
        }

        /**
         * Reports whether the section header reacts to pointer input.
         *
         * @return true when the section is enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables the section header.
         * Disabled sections are drawn muted and ignore clicks; their state can still be changed
         * programmatically.
         *
         * @param enabled true to allow pointer interaction
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
