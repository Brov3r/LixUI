package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.GradientStop;
import com.lixui.graphics.Paint;
import com.lixui.render.ImageFit;
import com.lixui.render.ImageStyle;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.render.TextureOptions;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.IconSet;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;
import com.lixui.widget.image.ImageHandle;
import com.lixui.widget.image.ImageLoader;
import com.lixui.widget.image.ImageRequest;
import com.lixui.widget.image.ImageSource;

import java.util.List;
import java.util.Objects;

/**
 * A non-interactive widget that draws a texture, a texture region, a bundled icon or a remote image.
 * The image supports aspect-preserving fits, anchors, tiled repetition, a tint, a background,
 * an optional border and rounded or circular clipping. Rounded clipping follows the resolved image
 * rectangle, so it works together with every fit mode.
 * Caller-owned textures are never released by this widget. Bundled icons are acquired through the
 * shared context icon cache, and remote images are resolved through the context {@code ImageLoader}.
 * Remote loading never blocks the render thread and displays the placeholder while pixels arrive.
 */
public class Image extends Widget {
    private static final float DEFAULT_ICON_SIZE = 16;
    private static final int DEFAULT_REMOTE_SIZE = 256;
    private static final Rect FULL_UV = new Rect(0, 0, 1, 1);

    private ImageSource source;
    private ImageLoader imageLoader;
    private IconSet iconSet;
    private Texture remoteTexture;
    private ImageHandle remoteHandle;
    private ImageRequest remoteRequest;
    private long remoteGeneration;
    private boolean remoteFailed;
    private Texture placeholder;
    private Texture errorTexture;
    private Rect uv;
    private ImageStyle imageStyle = ImageStyle.CONTAIN;
    private Color tint = Color.WHITE;
    private Color background;
    private Color borderColor = Color.TRANSPARENT;
    private float cornerRadius;
    private float borderWidth;
    private boolean circular;

    /**
     * Creates an empty image.
     */
    public Image() {
    }

    /**
     * Creates an image that shows a caller-owned texture.
     *
     * @param texture texture to draw, or null for an empty image
     */
    public Image(Texture texture) {
        setTexture(texture);
    }

    /**
     * Returns the image source.
     *
     * @return the source, or null when the image is empty
     */
    public ImageSource source() {
        return source;
    }

    /**
     * Assigns the image source and releases the previous icon or remote request.
     *
     * @param source image source, or null to clear the image
     */
    public void setSource(ImageSource source) {
        if (Objects.equals(this.source, source)) {
            return;
        }
        cancelRemoteRequest();
        releaseIconSet();
        iconSet = null;
        this.source = source;
        if (source instanceof ImageSource.IconSource icon) {
            iconSet = new IconSet(icon.name());
        }
        if (context() != null) {
            ensureIcon();
        }
    }

    /**
     * Clears the image source and releases its resources.
     */
    public void clearSource() {
        setSource(null);
    }

    /**
     * Returns the loader used for remote image sources.
     *
     * @return the assigned loader, or null when remote sources are disabled
     */
    public ImageLoader imageLoader() {
        return imageLoader;
    }

    /**
     * Assigns the loader used for remote image sources.
     * The widget does not close the loader; its owner controls the loader lifetime. Multiple image
     * widgets can share one loader, which enables request deduplication and texture caching.
     *
     * @param imageLoader image loader, or null to stop resolving remote sources
     */
    public void setImageLoader(ImageLoader imageLoader) {
        if (this.imageLoader == imageLoader) {
            return;
        }
        cancelRemoteRequest();
        this.imageLoader = imageLoader;
        if (context() != null) {
            ensureRemoteImage();
        }
    }

    /**
     * Returns the directly assigned texture.
     *
     * @return the caller-owned texture, or null when another source is used
     */
    public Texture texture() {
        return source instanceof ImageSource.TextureSource textureSource
                ? textureSource.texture() : null;
    }

    /**
     * Assigns a caller-owned texture source.
     * The widget draws the texture but never releases it. Async image loaders must create or upload
     * the texture on the render thread and then hand it to this method.
     *
     * @param texture texture to draw, or null to clear the source
     */
    public void setTexture(Texture texture) {
        if (texture == null) {
            clearSource();
            return;
        }
        setSource(ImageSource.texture(texture));
    }

    /**
     * Clears the directly assigned texture source.
     */
    public void clearTexture() {
        if (source instanceof ImageSource.TextureSource) {
            clearSource();
        }
    }

    /**
     * Returns the bundled icon name.
     *
     * @return the icon resource name, or null when another source is used
     */
    public String icon() {
        return source instanceof ImageSource.IconSource iconSource ? iconSource.name() : null;
    }

    /**
     * Assigns a bundled icon source.
     * The icon is rasterized at the current widget size and display density, and reloaded when either
     * changes. The icon texture is shared through the context icon cache.
     *
     * @param icon bundled icon resource name
     * @throws NullPointerException if {@code icon} is null
     * @throws IllegalArgumentException if the name is blank
     */
    public void setIcon(String icon) {
        setSource(ImageSource.icon(icon));
    }

    /**
     * Clears the bundled icon source.
     */
    public void clearIcon() {
        if (source instanceof ImageSource.IconSource) {
            clearSource();
        }
    }

    /**
     * Returns the texture drawn behind a remote image while it loads or after a failure.
     *
     * @return the placeholder texture, or null when none is assigned
     */
    public Texture placeholder() {
        return placeholder;
    }

    /**
     * Assigns a texture shown while a remote image is loading.
     *
     * @param placeholder placeholder texture, or null to clear it
     */
    public void setPlaceholder(Texture placeholder) {
        this.placeholder = placeholder;
    }

    /**
     * Returns the texture shown when a remote image fails to load.
     *
     * @return the error texture, or null when none is assigned
     */
    public Texture errorTexture() {
        return errorTexture;
    }

    /**
     * Assigns a texture shown when a remote image fails to load.
     *
     * @param errorTexture error texture, or null to clear it
     */
    public void setErrorTexture(Texture errorTexture) {
        this.errorTexture = errorTexture;
    }

    /**
     * Returns the texture that is actually drawn.
     *
     * @return the source, placeholder or error texture, or null when nothing is available yet
     */
    public Texture displayedTexture() {
        if (source instanceof ImageSource.TextureSource textureSource) {
            return textureSource.texture();
        }
        if (source instanceof ImageSource.IconSource) {
            return iconSet == null ? null : iconSet.get(0);
        }
        if (source instanceof ImageSource.RemoteSource) {
            if (remoteTexture != null) {
                return remoteTexture;
            }
            return remoteFailed && errorTexture != null ? errorTexture : placeholder;
        }
        return null;
    }

    /**
     * Returns the sampled texture region.
     *
     * @return the normalized top-left UV region, or null when the full texture is sampled
     */
    public Rect uv() {
        return uv;
    }

    /**
     * Assigns a normalized top-left texture region for sprite atlases.
     *
     * @param uv sampled region, or null to sample the full texture
     */
    public void setUv(Rect uv) {
        this.uv = uv;
    }

    /**
     * Removes the explicit UV region and samples the full texture.
     */
    public void clearUv() {
        uv = null;
    }

    /**
     * Returns the image placement settings.
     *
     * @return the image placement style
     */
    public ImageStyle imageStyle() {
        return imageStyle;
    }

    /**
     * Assigns the image placement settings.
     *
     * @param imageStyle placement and repetition settings
     * @throws NullPointerException if {@code imageStyle} is null
     */
    public void setImageStyle(ImageStyle imageStyle) {
        this.imageStyle = Objects.requireNonNull(imageStyle, "imageStyle");
    }

    /**
     * Returns the image fit mode.
     *
     * @return the current fit mode
     */
    public ImageFit fit() {
        return imageStyle.fit();
    }

    /**
     * Changes the fit mode while preserving anchors and repetition.
     *
     * @param fit image fit mode
     * @throws NullPointerException if {@code fit} is null
     */
    public void setFit(ImageFit fit) {
        Objects.requireNonNull(fit, "fit");
        imageStyle = new ImageStyle(fit, imageStyle.horizontal(), imageStyle.vertical(),
                imageStyle.repeat());
    }

    /**
     * Returns the tint multiplied with every sampled texel.
     *
     * @return the straight-alpha tint
     */
    public Color tint() {
        return tint;
    }

    /**
     * Assigns a tint multiplied with every sampled texel.
     * Alpha in the tint fades the image; white keeps the original colors.
     *
     * @param tint straight-alpha tint
     * @throws NullPointerException if {@code tint} is null
     */
    public void setTint(Color tint) {
        this.tint = Objects.requireNonNull(tint, "tint");
    }

    /**
     * Returns the background painted behind the image.
     *
     * @return the background color, or null when the background is transparent
     */
    public Color background() {
        return background;
    }

    /**
     * Assigns a background painted behind the image and visible in letterboxed areas.
     *
     * @param background background color, or null to clear it
     */
    public void setBackground(Color background) {
        this.background = background;
    }

    /**
     * Returns the uniform corner radius.
     *
     * @return the radius in logical UI units; ignored while the image is circular
     */
    public float cornerRadius() {
        return cornerRadius;
    }

    /**
     * Sets the uniform corner radius.
     *
     * @param cornerRadius non-negative radius in logical UI units
     * @throws IllegalArgumentException if the radius is negative or non-finite
     */
    public void setCornerRadius(float cornerRadius) {
        if (!Float.isFinite(cornerRadius) || cornerRadius < 0) {
            throw new IllegalArgumentException("Corner radius must be finite and non-negative.");
        }
        this.cornerRadius = cornerRadius;
    }

    /**
     * Reports whether the image is clipped to a circle.
     *
     * @return true when circular clipping is enabled
     */
    public boolean isCircular() {
        return circular;
    }

    /**
     * Enables or disables circular clipping.
     * The radius is half of the smaller widget dimension; {@link #cornerRadius()} is ignored.
     *
     * @param circular true to clip the image and background to a circle
     */
    public void setCircular(boolean circular) {
        this.circular = circular;
    }

    /**
     * Returns the border color.
     *
     * @return the border color
     */
    public Color borderColor() {
        return borderColor;
    }

    /**
     * Assigns the border color drawn inside the image bounds.
     *
     * @param borderColor border color
     * @throws NullPointerException if {@code borderColor} is null
     */
    public void setBorderColor(Color borderColor) {
        this.borderColor = Objects.requireNonNull(borderColor, "borderColor");
    }

    /**
     * Returns the border width.
     *
     * @return the border width in logical UI units
     */
    public float borderWidth() {
        return borderWidth;
    }

    /**
     * Sets the border width drawn inside the image bounds.
     *
     * @param borderWidth non-negative border width in logical UI units
     * @throws IllegalArgumentException if the width is negative or non-finite
     */
    public void setBorderWidth(float borderWidth) {
        if (!Float.isFinite(borderWidth) || borderWidth < 0) {
            throw new IllegalArgumentException("Border width must be finite and non-negative.");
        }
        this.borderWidth = borderWidth;
    }

    /**
     * Measures the intrinsic texture size unless an explicit non-auto-sized size is assigned.
     *
     * @param constraints available and required dimensions
     * @return the assigned widget size, the texture size in logical units, or an empty size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        if (!isAutoSize() && !localBounds().isEmpty()) {
            return new Size(bounds().width(), bounds().height());
        }
        Texture image = displayedTexture();
        UiContext context = context();
        if (image == null || context == null) {
            return new Size(bounds().width(), bounds().height());
        }
        return new Size(image.width() / context.scaleX(), image.height() / context.scaleY());
    }

    /**
     * Loads the bundled icon or starts the remote request when the image is attached.
     */
    @Override
    protected void onAttached() {
        ensureIcon();
        ensureRemoteImage();
    }

    /**
     * Releases the shared icon and cancels the remote request when the image leaves its manager.
     */
    @Override
    protected void onDetached() {
        cancelRemoteRequest();
        releaseIconSet();
    }

    /**
     * Reloads icons and regenerates the remote request after a display scale change.
     */
    @Override
    protected void onContextChanged() {
        releaseIconSet();
        ensureIcon();
        ensureRemoteImage();
    }

    /**
     * Refreshes the icon and remote request after the widget size has been assigned.
     */
    @Override
    protected void onLayout() {
        ensureIcon();
        ensureRemoteImage();
    }

    /**
     * Paints the background, image, tint and border.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (localBounds().isEmpty()) {
            return;
        }
        ensureIcon();
        Rect area = imageBounds();
        float radius = effectiveCornerRadius(area);
        if (background != null && background.a() > 0) {
            renderer.roundedRect(area, radius, background);
        }
        Texture image = displayedTexture();
        if (image != null) {
            renderer.image(image, area, uv == null ? FULL_UV : uv,
                    imageStyle, radius, tint);
        }
        drawBorder(renderer, radius, area);
    }

    private void drawBorder(RenderManager renderer, float radius, Rect area) {
        if (borderWidth <= 0 || borderColor.a() <= 0) {
            return;
        }
        if (circular) {
            drawCircularBorder(renderer, area);
            return;
        }
        float inset = borderWidth / 2;
        Rect stroke = new Rect(inset, inset,
                Math.max(0, area.width() - borderWidth),
                Math.max(0, area.height() - borderWidth));
        if (stroke.isEmpty()) {
            return;
        }
        renderer.strokeRoundedRect(stroke, Math.max(0, radius - inset), borderWidth,
                Paint.solid(borderColor));
    }

    private void drawCircularBorder(RenderManager renderer, Rect area) {
        float radius = area.width() / 2;
        Point center = new Point(area.x() + radius, area.y() + radius);
        float innerRadius = radius - borderWidth;
        if (innerRadius <= 0) {
            renderer.fillCircle(center, radius, Paint.solid(borderColor));
            return;
        }
        float density = Math.max(0.01f, context().density());
        float feather = 1f / (radius * density);
        float inner = innerRadius / radius;
        float start = Math.max(0, inner - feather / 2);
        float end = Math.min(1, inner + feather / 2);
        // A transparent black stop would darken the feather band once the color is premultiplied.
        Color transparent = borderColor.withAlpha(0);
        List<GradientStop> stops = List.of(
                new GradientStop(0, transparent),
                new GradientStop(start, transparent),
                new GradientStop(end, borderColor),
                new GradientStop(1, borderColor));
        renderer.fillEllipse(center, radius, radius, Paint.radial(center, radius, stops));
    }

    private Rect imageBounds() {
        if (!circular) {
            return localBounds();
        }
        float side = Math.min(bounds().width(), bounds().height());
        return new Rect((bounds().width() - side) / 2, (bounds().height() - side) / 2,
                side, side);
    }

    private float effectiveCornerRadius(Rect area) {
        return circular ? area.width() / 2 : cornerRadius;
    }

    private void ensureIcon() {
        UiContext context = context();
        if (context == null || iconSet == null) {
            return;
        }
        float logicalSize = Math.max(localBounds().width(), localBounds().height());
        if (logicalSize <= 0) {
            logicalSize = DEFAULT_ICON_SIZE;
        }
        int size = Math.max(1, Math.round(logicalSize * context.density()));
        if (!iconSet.matches(context, size)) {
            iconSet.reload(context, size);
        }
    }

    private void ensureRemoteImage() {
        if (!(source instanceof ImageSource.RemoteSource remote)) {
            cancelRemoteRequest();
            return;
        }
        UiContext context = context();
        if (context == null || imageLoader == null) {
            return;
        }
        int targetWidth;
        int targetHeight;
        if (localBounds().isEmpty()) {
            targetWidth = DEFAULT_REMOTE_SIZE;
            targetHeight = DEFAULT_REMOTE_SIZE;
        } else {
            targetWidth = pixelSize(bounds().width(), context.scaleX());
            targetHeight = pixelSize(bounds().height(), context.scaleY());
        }
        if (remoteHandle != null && remoteRequest != null
                && remoteRequest.uri().equals(remote.uri())
                && remoteRequest.targetWidth() == targetWidth
                && remoteRequest.targetHeight() == targetHeight) {
            return;
        }
        ImageRequest request = new ImageRequest(remote.uri(), targetWidth, targetHeight,
                TextureOptions.DEFAULT);
        cancelRemoteRequest();
        remoteRequest = request;
        long generation = remoteGeneration;
        remoteHandle = imageLoader.load(request,
                texture -> {
                    if (generation == remoteGeneration) {
                        remoteTexture = texture;
                        remoteFailed = false;
                    }
                },
                error -> {
                    if (generation == remoteGeneration) {
                        remoteTexture = null;
                        remoteFailed = true;
                    }
                });
    }

    private static int pixelSize(float logical, float scale) {
        long pixels = Math.round(logical * scale);
        return (int) Math.clamp(pixels, 1, ImageRequest.MAX_DIMENSION);
    }

    private void cancelRemoteRequest() {
        remoteGeneration++;
        if (remoteHandle != null) {
            remoteHandle.close();
            remoteHandle = null;
        }
        remoteTexture = null;
        remoteFailed = false;
        remoteRequest = null;
    }

    private void releaseIconSet() {
        if (iconSet != null) {
            iconSet.release(context());
        }
    }
}
