package com.lixui.widget;

import com.lixui.animation.Animation;
import com.lixui.animation.Animator;
import com.lixui.animation.Easing;
import com.lixui.geometry.Rect;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.platform.UiPlatform;
import com.lixui.render.ImageData;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.render.TextureOptions;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.FontStyle;
import com.lixui.theme.Theme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Shared services and state available to attached widgets.
 * A context is owned by a {@link WidgetManager}; it exposes the logical viewport, display scale,
 * theme, default font, clipboard, cursor, URI opening and resource factories.
 * Textures and fonts created through the context are tracked and released by {@link #close()}.
 * Resources created directly through {@link RenderManager} are owned by the caller instead.
 */
public final class UiContext implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UiContext.class);

    private final RenderManager renderer;
    private final UiPlatform platform;
    private final WidgetManager manager;
    private final Animator animator = new Animator();
    private final Set<Texture> textures = identitySet();
    private final Set<Font> fonts = identitySet();
    private final Map<IconKey, SharedIcon> icons = new HashMap<>();
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private Rect viewport = Rect.EMPTY;
    private float scaleX = 1;
    private float scaleY = 1;
    private Theme theme = Theme.dark();
    private Font defaultFont;
    private Cursor cursor = Cursor.DEFAULT;
    private boolean closed;
    private boolean iconSmoothing = true;

    UiContext(RenderManager renderer, UiPlatform platform) {
        this(renderer, platform, null);
    }

    UiContext(RenderManager renderer, UiPlatform platform, WidgetManager manager) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.manager = manager;
    }

    /**
     * Returns the logical viewport in UI units.
     *
     * @return the viewport, or an empty rectangle before the first resize
     */
    public Rect viewport() {
        return viewport;
    }

    /**
     * Returns the horizontal number of physical pixels per logical UI unit.
     *
     * @return the positive horizontal display scale
     */
    public float scaleX() {
        return scaleX;
    }

    /**
     * Returns the vertical number of physical pixels per logical UI unit.
     *
     * @return the positive vertical display scale
     */
    public float scaleY() {
        return scaleY;
    }

    /**
     * Returns the average display scale, suitable for icons and stroke sizes on uniform displays.
     *
     * @return the positive average number of physical pixels per logical UI unit
     */
    public float density() {
        return (scaleX + scaleY) / 2;
    }

    /**
     * Returns the active theme.
     *
     * @return the current theme
     */
    public Theme theme() {
        return theme;
    }

    /**
     * Returns the font used by widgets when no explicit font is supplied.
     *
     * @return the default font, or null when the host has not assigned one
     */
    public Font defaultFont() {
        return defaultFont;
    }

    /**
     * Returns the current pointer cursor requested by the UI.
     *
     * @return the active cursor shape
     */
    public Cursor cursor() {
        return cursor;
    }

    /**
     * Returns the widget currently under the pointer.
     *
     * @return the hovered widget, or null when the pointer is outside or no manager is attached
     */
    public Widget hovered() {
        return manager == null ? null : manager.hovered();
    }

    /**
     * Returns the widget that currently owns keyboard focus.
     *
     * @return the focused widget, or null when nothing is focused or no manager is attached
     */
    public Widget focused() {
        return manager == null ? null : manager.focused();
    }

    /**
     * Returns the animator updated with every widget-manager frame.
     *
     * @return the shared animator
     * @throws IllegalStateException if the context is closed
     */
    public Animator animator() {
        ensureOpen();
        return animator;
    }

    /**
     * Starts an animation on the shared animator.
     *
     * @param from start value
     * @param to end value
     * @param duration duration in seconds
     * @param easing timing curve
     * @param onUpdate callback receiving the interpolated value
     * @return the started animation
     * @throws NullPointerException if {@code easing} or {@code onUpdate} is null
     * @throws IllegalStateException if the context is closed
     */
    public Animation animate(float from, float to, float duration, Easing easing,
                             Consumer<Float> onUpdate) {
        ensureOpen();
        return animator.animate(from, to, duration, easing, onUpdate);
    }

    void updateAnimations(float deltaSeconds) {
        animator.update(deltaSeconds);
    }

    /**
     * Schedules a task to run on the render thread during the next manager update.
     * Tasks posted while the queue is being drained run in the same update.
     *
     * @param task task to run
     * @throws NullPointerException if {@code task} is null
     * @throws IllegalStateException if the context is closed
     */
    public void post(Runnable task) {
        ensureOpen();
        tasks.add(Objects.requireNonNull(task, "task"));
    }

    void runTasks() {
        if (closed) {
            return;
        }
        Runnable task;
        while ((task = tasks.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException exception) {
                LOGGER.error("Queued UI task failed.", exception);
            }
        }
    }

    /**
     * Returns the current clipboard text.
     *
     * @return the clipboard text, or an empty string when the clipboard is unavailable or empty
     * @throws IllegalStateException if the context is closed
     */
    public String clipboardText() {
        ensureOpen();
        String text = platform.clipboardText();
        return text == null ? "" : text;
    }

    /**
     * Replaces the clipboard text.
     *
     * @param text text to copy
     * @throws NullPointerException if {@code text} is null
     * @throws IllegalStateException if the context is closed
     */
    public void setClipboardText(String text) {
        ensureOpen();
        platform.setClipboardText(Objects.requireNonNull(text, "text"));
    }

    /**
     * Changes the pointer cursor.
     * Repeated requests for the same cursor do not reach the platform.
     *
     * @param cursor requested cursor
     * @throws NullPointerException if {@code cursor} is null
     * @throws IllegalStateException if the context is closed
     */
    public void setCursor(Cursor cursor) {
        ensureOpen();
        Objects.requireNonNull(cursor, "cursor");
        if (this.cursor == cursor) {
            return;
        }
        platform.setCursor(cursor);
        this.cursor = cursor;
    }

    /**
     * Opens a URI in the platform's default handler, usually a web browser.
     *
     * @param uri absolute URI to open
     * @throws NullPointerException if {@code uri} is null
     * @throws IllegalStateException if the context is closed
     * @throws RuntimeException if the platform cannot open the URI
     */
    public void openUri(String uri) {
        ensureOpen();
        platform.openUri(Objects.requireNonNull(uri, "uri"));
    }

    /**
     * Registers a widget as a top-level overlay in the owning manager.
     * The widget is attached to this context and drawn above regular widgets when it is marked
     * {@link Widget#setAlwaysOnTop(boolean) always on top}.
     *
     * @param widget widget without a parent or manager
     * @throws NullPointerException if {@code widget} is null
     * @throws IllegalArgumentException if the widget already has a parent or manager
     * @throws IllegalStateException if the context has no manager or is closed
     */
    public void addTopLevel(Widget widget) {
        ensureOpen();
        manager().add(Objects.requireNonNull(widget, "widget"));
    }

    /**
     * Removes a top-level widget from the owning manager.
     *
     * @param widget widget to remove
     * @return true when the widget was registered
     * @throws NullPointerException if {@code widget} is null
     * @throws IllegalStateException if the context has no manager or is closed
     */
    public boolean removeTopLevel(Widget widget) {
        ensureOpen();
        return manager().remove(Objects.requireNonNull(widget, "widget"));
    }

    /**
     * Gives keyboard focus to a widget.
     *
     * @param widget widget to focus
     * @throws NullPointerException if {@code widget} is null
     * @throws IllegalArgumentException if the widget is not focusable, not interactive or not attached
     * @throws IllegalStateException if the context has no manager or is closed
     */
    public void requestFocus(Widget widget) {
        ensureOpen();
        manager().focus(Objects.requireNonNull(widget, "widget"));
    }

    /**
     * Captures pointer button events for a widget until the button is released or the capture is
     * explicitly released.
     *
     * @param widget widget that receives captured pointer events
     * @param button captured button
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the widget is not attached
     * @throws IllegalStateException if the context has no manager or is closed
     */
    public void capturePointer(Widget widget, MouseButton button) {
        ensureOpen();
        manager().capturePointer(Objects.requireNonNull(widget, "widget"),
                Objects.requireNonNull(button, "button"));
    }

    /**
     * Releases a pointer capture for a button.
     *
     * @param button released button
     * @throws NullPointerException if {@code button} is null
     * @throws IllegalStateException if the context has no manager or is closed
     */
    public void releasePointerCapture(MouseButton button) {
        ensureOpen();
        manager().releasePointerCapture(Objects.requireNonNull(button, "button"));
    }

    void releasePointerCapture(Widget owner, MouseButton button) {
        ensureOpen();
        manager().releasePointerCapture(owner, button);
    }

    /**
     * Rasterizes and uploads a font with the default character set and tracks its atlas.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @return the uploaded font
     * @throws NullPointerException if {@code data} is null
     * @throws IllegalArgumentException if the size is invalid or the data has no TrueType outlines
     * @throws IllegalStateException if the context is closed
     */
    public Font createFont(FontData data, float pixelSize) {
        ensureOpen();
        return registerFont(renderer.createFont(data, pixelSize));
    }

    /**
     * Rasterizes and uploads a styled font with the default character set and tracks its atlas.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @param style synthetic bold or italic style
     * @return the uploaded font
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the size is invalid or the data has no TrueType outlines
     * @throws IllegalStateException if the context is closed
     */
    public Font createFont(FontData data, float pixelSize, FontStyle style) {
        ensureOpen();
        return registerFont(renderer.createFont(data, pixelSize, style));
    }

    /**
     * Loads the best installed face of a system font family and tracks its atlas.
     *
     * @param family family name such as {@code "Arial"}
     * @param pixelSize requested line size in logical UI units
     * @param style requested face style
     * @return the uploaded font, or empty when the family is not installed
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the size is invalid or a font atlas cannot be built
     * @throws IllegalStateException if the context is closed
     */
    public Optional<Font> createFont(String family, float pixelSize, FontStyle style) {
        ensureOpen();
        return renderer.createFont(family, pixelSize, style).map(this::registerFont);
    }

    /**
     * Releases a font created by this context and its atlas texture.
     * Fonts created elsewhere, already released fonts, and calls after {@link #close()} are ignored.
     *
     * @param font font to release
     * @throws NullPointerException if {@code font} is null
     */
    public void destroyFont(Font font) {
        Objects.requireNonNull(font, "font");
        if (closed || !fonts.remove(font)) {
            return;
        }
        if (defaultFont == font) {
            defaultFont = null;
        }
        renderer.destroyFont(font);
    }

    /**
     * Creates and tracks a backend texture from CPU image data.
     *
     * @param image RGBA8 image data with a top-left origin
     * @return the created texture
     * @throws NullPointerException if {@code image} is null
     * @throws IllegalStateException if the context is closed
     */
    public Texture createTexture(ImageData image) {
        return createTexture(image, TextureOptions.DEFAULT);
    }

    /**
     * Creates and tracks a backend texture with explicit sampling options.
     *
     * @param image RGBA8 image data with a top-left origin
     * @param options filtering, wrapping and mipmap settings applied to the texture
     * @return the created texture
     * @throws NullPointerException if an argument is null
     * @throws IllegalStateException if the context is closed
     * @throws UnsupportedOperationException if the backend does not support custom sampling options
     */
    public Texture createTexture(ImageData image, TextureOptions options) {
        ensureOpen();
        Texture texture = renderer.createTexture(Objects.requireNonNull(image, "image"),
                Objects.requireNonNull(options, "options"));
        textures.add(texture);
        return texture;
    }

    /**
     * Rasterizes a bundled monochrome vector icon at the requested physical size for tinting.
     * Uses the current icon smoothing setting for SVG edges and texture sampling.
     *
     * @param name SVG file name in the bundled icon set; legacy PNG names are also accepted
     * @param size requested edge length in physical pixels
     * @return a texture tracked by this context
     * @throws NullPointerException if the name is null
     * @throws IllegalArgumentException if the size is not positive or the icon is missing
     * @throws IllegalStateException if the context is closed
     */
    public Texture createBundledIcon(String name, int size) {
        ensureOpen();
        Objects.requireNonNull(name, "name");
        String svg = name.endsWith(".png") ? name.substring(0, name.length() - 4) + ".svg" : name;
        ImageData image = ImageData.fromResource("/assets/icons/" + svg, size, size, iconSmoothing);
        byte[] pixels = image.pixels();
        // With straight alpha, linear sampling must interpolate coverage without fading the tint.
        for (int i = 0; i < pixels.length; i += 4) {
            pixels[i] = (byte) 255;
            pixels[i + 1] = (byte) 255;
            pixels[i + 2] = (byte) 255;
        }
        try {
            return createTexture(image, iconSmoothing ? TextureOptions.DEFAULT : TextureOptions.PIXEL_ART);
        } catch (UnsupportedOperationException unsupportedSampling) {
            return createTexture(image);
        }
    }

    /**
     * Reports whether bundled icons use antialiased edges and linear sampling.
     *
     * @return true if smoothing is enabled (the default)
     */
    public boolean iconSmoothing() {
        return iconSmoothing;
    }

    void setIconSmoothing(boolean enabled) {
        iconSmoothing = enabled;
    }

    /**
     * Acquires a shared bundled icon and increments its reference count.
     * Icons with the same name, pixel size and smoothing setting reuse one texture.
     *
     * @param name bundled icon resource name
     * @param size icon edge length in physical pixels
     * @return the shared icon texture
     * @throws NullPointerException if {@code name} is null
     * @throws IllegalArgumentException if the size is not positive or the icon is missing
     * @throws IllegalStateException if the context is closed
     */
    public Texture acquireIcon(String name, int size) {
        ensureOpen();
        IconKey key = new IconKey(name, size, iconSmoothing);
        SharedIcon icon = icons.get(key);
        if (icon == null) {
            icon = new SharedIcon(createBundledIcon(name, size));
            icons.put(key, icon);
        }
        icon.references++;
        return icon.texture;
    }

    /**
     * Releases one reference to a shared bundled icon and destroys it when no references remain.
     * Textures not acquired through {@link #acquireIcon(String, int)} are ignored.
     *
     * @param texture icon texture to release
     * @throws NullPointerException if {@code texture} is null
     */
    public void releaseIcon(Texture texture) {
        Objects.requireNonNull(texture, "texture");
        Iterator<SharedIcon> iterator = icons.values().iterator();
        while (iterator.hasNext()) {
            SharedIcon icon = iterator.next();
            if (icon.texture == texture) {
                if (--icon.references == 0) {
                    iterator.remove();
                    destroyTexture(texture);
                }
                return;
            }
        }
    }

    /**
     * Releases several shared bundled icons and suppresses later cleanup failures.
     * Every texture is attempted even when one release fails.
     *
     * @param textures icon textures to release
     * @throws NullPointerException if the collection or an element is null
     * @throws RuntimeException the first cleanup failure, with later failures suppressed
     */
    public void releaseIcons(Collection<Texture> textures) {
        Objects.requireNonNull(textures, "textures");
        RuntimeException failure = null;
        for (Texture texture : textures) {
            try {
                releaseIcon(texture);
            } catch (RuntimeException exception) {
                failure = Widget.recordFailure(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Releases a texture created by this context.
     * Textures created elsewhere, already released textures, and calls after {@link #close()} are
     * ignored.
     *
     * @param texture texture to release
     * @throws NullPointerException if {@code texture} is null
     */
    public void destroyTexture(Texture texture) {
        Objects.requireNonNull(texture, "texture");
        if (closed || !textures.remove(texture)) {
            return;
        }
        renderer.destroyTexture(texture);
    }

    /**
     * Releases every font and texture created through this context.
     * The graphics context that owns the backend resources must be current. Closing is idempotent.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        animator.clear();
        tasks.clear();
        RuntimeException failure = null;
        for (Font font : List.copyOf(fonts)) {
            try {
                renderer.destroyFont(font);
            } catch (RuntimeException exception) {
                failure = Widget.recordFailure(failure, exception);
            }
        }
        for (Texture texture : List.copyOf(textures)) {
            try {
                renderer.destroyTexture(texture);
            } catch (RuntimeException exception) {
                failure = Widget.recordFailure(failure, exception);
            }
        }
        fonts.clear();
        textures.clear();
        icons.clear();
        defaultFont = null;
        if (failure != null) {
            throw failure;
        }
    }

    void setViewport(Viewport viewport) {
        Objects.requireNonNull(viewport, "viewport");
        this.viewport = new Rect(0, 0, viewport.logicalWidth(), viewport.logicalHeight());
        scaleX = viewport.scaleX();
        scaleY = viewport.scaleY();
    }

    void setTheme(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    void setDefaultFont(Font font) {
        defaultFont = Objects.requireNonNull(font, "font");
    }

    void widgetStateChanged() {
        if (manager != null) {
            manager.pruneInteraction();
        }
    }

    private WidgetManager manager() {
        if (manager == null) {
            throw new IllegalStateException("UI context is not attached to a widget manager.");
        }
        return manager;
    }

    private Font registerFont(Font font) {
        fonts.add(font);
        return font;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("UI context is closed.");
        }
    }

    static <T> Set<T> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private record IconKey(String resource, int size, boolean smooth) {
    }

    private static final class SharedIcon {
        private final Texture texture;
        private int references;

        private SharedIcon(Texture texture) {
            this.texture = texture;
        }
    }
}
