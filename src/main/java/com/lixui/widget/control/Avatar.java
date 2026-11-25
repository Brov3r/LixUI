package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.image.ImageLoader;
import com.lixui.widget.image.ImageSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A circular avatar that shows an image or generated initials.
 * A texture, bundled icon, remote source or any {@link ImageSource} can be assigned. While no image
 * is available the widget paints a colored circle with the initials of the name. Any number of
 * badges can mark the lower-right corner in a right-aligned row. The avatar is decorative and never
 * captures pointer input.
 */
public class Avatar extends Widget {
    /**
     * Corner that anchors a badge row.
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

    private static final float DEFAULT_DIAMETER = 40;
    private static final float INITIALS_INSET = 2;
    private static final float BADGE_GAP = 2;
    private static final Color[] PALETTE = {
            Color.hex("#7EA6F5"), Color.hex("#86C46A"), Color.hex("#E39A5B"),
            Color.hex("#C39BF2"), Color.hex("#5BC8C4")
    };

    private final Image image = new Image();
    private final Label initialsLabel = new Label();
    private final List<Badge> badges = new ArrayList<>();
    private final Map<Badge, Corner> badgeCorners = new IdentityHashMap<>();
    private float diameter = DEFAULT_DIAMETER;
    private String name = "";
    private Color backgroundColor;

    /**
     * Creates an empty avatar with the default diameter.
     */
    public Avatar() {
        image.setCircular(true);
        image.setAutoSize(false);
        initialsLabel.setAutoSize(false);
        initialsLabel.setWrap(false);
        initialsLabel.setAlignment(HorizontalAlignment.CENTER);
        initialsLabel.setVerticalAlignment(VerticalAlignment.CENTER);
        initialsLabel.setVisible(false);
        add(image).add(initialsLabel);
    }

    /**
     * Returns the displayed name used for the initials.
     *
     * @return the name, or an empty string when none is assigned
     */
    public final String name() {
        return name;
    }

    /**
     * Replaces the displayed name and regenerates the initials.
     *
     * @param name display name
     * @throws NullPointerException if {@code name} is null
     */
    public final void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
        updateVisuals();
    }

    /**
     * Returns the generated initials for the current name.
     *
     * @return up to two uppercase initials, or an empty string without a name
     */
    public final String initials() {
        return initialsLabel.text();
    }

    /**
     * Returns the explicit fallback background color.
     *
     * @return the background color, or null when a palette color is derived from the name
     */
    public final Color backgroundColor() {
        return backgroundColor;
    }

    /**
     * Assigns an explicit fallback background color.
     * The color is visible while no image is loaded and behind transparent images.
     *
     * @param backgroundColor background color, or null to derive one from the name
     */
    public final void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
        updateVisuals();
    }

    /**
     * Returns the natural avatar diameter.
     *
     * @return the diameter in logical UI units
     */
    public final float diameter() {
        return diameter;
    }

    /**
     * Sets the natural avatar diameter.
     *
     * @param diameter positive diameter in logical UI units
     * @throws IllegalArgumentException if the diameter is not positive or non-finite
     */
    public final void setDiameter(float diameter) {
        if (!Float.isFinite(diameter) || diameter <= 0) {
            throw new IllegalArgumentException("Avatar diameter must be finite and positive.");
        }
        this.diameter = diameter;
    }

    /**
     * Adds an empty badge to the lower-right corner row.
     *
     * @return the new badge
     */
    public final Badge addBadge() {
        return addBadge(Corner.BOTTOM_RIGHT);
    }

    /**
     * Adds an empty badge anchored to a corner.
     *
     * @param corner corner that anchors the badge row
     * @return the new badge
     * @throws NullPointerException if {@code corner} is null
     */
    public final Badge addBadge(Corner corner) {
        Badge badge = new Badge();
        badges.add(badge);
        badgeCorners.put(badge, Objects.requireNonNull(corner, "corner"));
        add(badge);
        return badge;
    }

    /**
     * Adds a text badge to the lower-right corner row.
     *
     * @param text badge label
     * @return the new badge
     * @throws NullPointerException if {@code text} is null
     */
    public final Badge addBadge(String text) {
        Badge badge = addBadge();
        badge.setText(Objects.requireNonNull(text, "text"));
        return badge;
    }

    /**
     * Adds a text badge anchored to a corner.
     *
     * @param corner corner that anchors the badge row
     * @param text badge label
     * @return the new badge
     * @throws NullPointerException if an argument is null
     */
    public final Badge addBadge(Corner corner, String text) {
        Badge badge = addBadge(corner);
        badge.setText(Objects.requireNonNull(text, "text"));
        return badge;
    }

    /**
     * Moves a badge to another corner.
     *
     * @param badge badge to move
     * @param corner new corner
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the badge does not belong to this avatar
     */
    public final void setBadgeCorner(Badge badge, Corner corner) {
        Objects.requireNonNull(badge, "badge");
        Objects.requireNonNull(corner, "corner");
        if (!badges.contains(badge)) {
            throw new IllegalArgumentException("Badge does not belong to this avatar.");
        }
        badgeCorners.put(badge, corner);
    }

    /**
     * Returns the corner that anchors a badge.
     *
     * @param badge badge to query
     * @return the configured corner, or bottom-right when the badge does not belong to this avatar
     * @throws NullPointerException if {@code badge} is null
     */
    public final Corner badgeCorner(Badge badge) {
        Objects.requireNonNull(badge, "badge");
        return badgeCorners.getOrDefault(badge, Corner.BOTTOM_RIGHT);
    }

    /**
     * Removes a badge from the avatar.
     *
     * @param badge badge to remove
     * @return true when the badge belonged to this avatar
     * @throws NullPointerException if {@code badge} is null
     */
    public final boolean removeBadge(Badge badge) {
        Objects.requireNonNull(badge, "badge");
        if (!badges.remove(badge)) {
            return false;
        }
        badgeCorners.remove(badge);
        remove(badge);
        return true;
    }

    /**
     * Removes every badge from the avatar.
     */
    public final void clearBadges() {
        for (Badge badge : List.copyOf(badges)) {
            remove(badge);
        }
        badges.clear();
        badgeCorners.clear();
    }

    /**
     * Returns the badges in display order.
     *
     * @return an immutable snapshot of the badges
     */
    public final List<Badge> badges() {
        return List.copyOf(badges);
    }

    /**
     * Returns the underlying image widget for advanced configuration.
     *
     * @return the image widget
     */
    public final Image image() {
        return image;
    }

    /**
     * Returns the current image source.
     *
     * @return the source, or null when initials are shown
     */
    public final ImageSource source() {
        return image.source();
    }

    /**
     * Replaces the image source.
     *
     * @param source image source, or null to show initials
     */
    public final void setSource(ImageSource source) {
        image.setSource(source);
        syncInitials();
    }

    /**
     * Assigns a caller-owned texture.
     *
     * @param texture texture to draw, or null to show initials
     */
    public final void setTexture(Texture texture) {
        image.setTexture(texture);
        syncInitials();
    }

    /**
     * Assigns a bundled icon source.
     *
     * @param icon bundled icon resource name
     * @throws NullPointerException if {@code icon} is null
     * @throws IllegalArgumentException if the name is blank
     */
    public final void setIcon(String icon) {
        image.setIcon(icon);
        syncInitials();
    }

    /**
     * Assigns the loader used for remote image sources.
     *
     * @param imageLoader image loader, or null to stop resolving remote sources
     */
    public final void setImageLoader(ImageLoader imageLoader) {
        image.setImageLoader(imageLoader);
    }

    /**
     * Clears the image source and shows initials again.
     */
    public final void clearSource() {
        image.clearSource();
        syncInitials();
    }

    /**
     * Measures the configured diameter as a square.
     *
     * @param constraints available and required dimensions
     * @return the natural square size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return new Size(diameter, diameter);
    }

    /**
     * Lays out the circular image, initials and the badge row.
     */
    @Override
    protected void onLayout() {
        float side = Math.min(bounds().width(), bounds().height());
        float x = (bounds().width() - side) / 2;
        float y = (bounds().height() - side) / 2;
        Rect square = new Rect(x, y, side, side);
        image.layout(square);
        initialsLabel.layout(new Rect(x + INITIALS_INSET, y + INITIALS_INSET,
                Math.max(0, side - INITIALS_INSET * 2), Math.max(0, side - INITIALS_INSET * 2)));
        updateVisuals();
        Map<Corner, Float> offsets = new EnumMap<>(Corner.class);
        for (Badge badge : badges) {
            Corner corner = badgeCorners.getOrDefault(badge, Corner.BOTTOM_RIGHT);
            float offset = offsets.getOrDefault(corner, 0f);
            Size badgeSize = badge.measure(LayoutConstraints.loose(side, side));
            boolean right = corner == Corner.TOP_RIGHT || corner == Corner.BOTTOM_RIGHT;
            boolean bottom = corner == Corner.BOTTOM_LEFT || corner == Corner.BOTTOM_RIGHT;
            float badgeX = right ? x + side - badgeSize.width() - offset : x + offset;
            float badgeY = bottom ? y + side - badgeSize.height() : y;
            badge.layout(new Rect(badgeX, badgeY, badgeSize.width(), badgeSize.height()));
            offsets.put(corner, offset + badgeSize.width() + BADGE_GAP);
        }
    }

    /**
     * Paints the circular fallback background before the image and initials.
     * A dedicated circle keeps the edge smooth on every backend.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (localBounds().isEmpty()) {
            return;
        }
        float side = Math.min(bounds().width(), bounds().height());
        if (side <= 0) {
            return;
        }
        float x = (bounds().width() - side) / 2;
        float y = (bounds().height() - side) / 2;
        Point center = new Point(x + side / 2, y + side / 2);
        renderer.fillCircle(center, side / 2, Paint.solid(resolveBackground()));
    }

    /**
     * Refreshes initials after a remote image finishes loading.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        syncInitials();
    }

    /**
     * Applies the theme border color when the avatar attaches.
     */
    @Override
    protected void onAttached() {
        applyTheme();
        updateVisuals();
    }

    /**
     * Reapplies theme colors after a context change.
     */
    @Override
    protected void onContextChanged() {
        applyTheme();
        updateVisuals();
    }

    /**
     * Keeps the decorative avatar transparent to pointer input.
     *
     * @param x horizontal coordinate relative to the widget's top-left corner
     * @param y vertical coordinate relative to the widget's top-left corner
     * @return always null
     */
    @Override
    public Widget hitTest(float x, float y) {
        return null;
    }

    private void applyTheme() {
        if (context() == null) {
            return;
        }
        image.setBorderColor(context().theme().border());
    }

    private void updateVisuals() {
        Color background = resolveBackground();
        initialsLabel.setText(initialsOf(name));
        initialsLabel.setTextColor(contrastColor(background));
        syncInitials();
    }

    private void syncInitials() {
        boolean show = image.displayedTexture() == null && !initialsLabel.text().isEmpty();
        initialsLabel.setVisible(show);
    }

    private Color resolveBackground() {
        if (backgroundColor != null) {
            return backgroundColor;
        }
        if (!name.isBlank()) {
            int index = Math.floorMod(name.hashCode(), PALETTE.length);
            return PALETTE[index];
        }
        Theme theme = context() == null ? null : context().theme();
        return theme == null ? PALETTE[0] : theme.primary();
    }

    private static Color contrastColor(Color background) {
        return background.luminance() > 0.55f ? Color.BLACK.withAlpha(0.78f) : Color.WHITE;
    }

    private static String initialsOf(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] words = trimmed.split("\\s+");
        StringBuilder text = new StringBuilder();
        text.append(initial(words[0]));
        if (words.length > 1) {
            text.append(initial(words[1]));
        }
        return text.toString().toUpperCase(Locale.ROOT);
    }

    private static String initial(String word) {
        if (word.isEmpty()) {
            return "";
        }
        return new String(Character.toChars(word.codePointAt(0)));
    }
}
