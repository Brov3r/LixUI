package com.lixui.render;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.FontStyle;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.SystemFonts;
import com.lixui.text.TextGlyph;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Backend-specific drawing manager used by widgets and custom draw code.
 * It owns a {@link DrawList}, exposes raw primitives, gradients, transforms, text and stencil masks,
 * and submits the completed list to the registered {@link RenderBackend}.
 * Drawing methods are valid only between {@link #begin()} and {@link #end()}.
 * Masks require a stencil buffer in OpenGL or a depth/stencil attachment in Vulkan.
 */
public final class RenderManager {
    private static final Rect FULL_UV = new Rect(0, 0, 1, 1);
    private static final Paint MASK_PAINT = Paint.solid(Color.WHITE);
    private final RenderBackend backend;
    private final DrawList drawList = new DrawList(Rect.EMPTY);
    private final Deque<Transform> transforms = new ArrayDeque<>();
    private final Map<Font, Texture> fontAtlases = new IdentityHashMap<>();
    private Viewport viewport;
    private Rect logicalViewport = Rect.EMPTY;
    private Antialiasing antialiasing = Antialiasing.LOW;
    private boolean drawing;

    /**
     * Creates a render manager for the supplied backend.
     *
     * @param backend backend that receives completed draw lists
     * @throws NullPointerException if {@code backend} is null
     */
    public RenderManager(RenderBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
        transforms.push(Transform.IDENTITY);
    }

    /**
     * Sets the physical framebuffer and logical UI viewport.
     *
     * @param viewport target viewport
     * @throws NullPointerException if {@code viewport} is null
     */
    public void resize(Viewport viewport) {
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        logicalViewport = new Rect(0, 0, viewport.logicalWidth(), viewport.logicalHeight());
        backend.resize(viewport);
    }

    /**
     * Returns the logical viewport used by drawing commands.
     *
     * @return the current logical viewport, or an empty rectangle before the first resize
     */
    public Rect viewport() {
        return logicalViewport;
    }

    /**
     * Starts a new frame and resets the command list.
     *
     * @throws IllegalStateException if {@link #resize(Viewport)} has not been called or a frame is
     *                               already in progress
     */
    public void begin() {
        if (viewport == null) {
            throw new IllegalStateException("resize must be called before begin.");
        }
        if (drawing) {
            throw new IllegalStateException("A frame is already in progress.");
        }
        transforms.clear();
        transforms.push(Transform.IDENTITY);
        ShapeTessellator.setQuality(antialiasing.tessellationScale());
        drawList.reset(logicalViewport);
        drawing = true;
    }

    /**
     * Submits the current command list to the backend and finishes the frame.
     *
     * @throws IllegalStateException if {@link #begin()} has not been called
     */
    public void end() {
        if (!drawing) {
            throw new IllegalStateException("begin must be called before end.");
        }
        drawing = false;
        backend.draw(drawList);
    }

    /**
     * Appends a rectangle with sharp corners.
     *
     * @param bounds rectangle to fill
     * @param color fill color
     * @throws NullPointerException if {@code bounds} or {@code color} is null
     * @throws IllegalStateException if no frame is in progress
     */
    public void rect(Rect bounds, Color color) {
        fillRect(bounds, Paint.solid(color));
    }

    /**
     * Appends a rounded rectangle.
     *
     * @param bounds rectangle to fill
     * @param radius corner radius in logical UI units
     * @param color fill color
     * @throws NullPointerException if {@code bounds} or {@code color} is null
     * @throws IllegalArgumentException if the radius is negative or non-finite
     * @throws IllegalStateException if no frame is in progress
     */
    public void roundedRect(Rect bounds, float radius, Color color) {
        fillRoundedRect(bounds, radius, Paint.solid(color));
    }

    /**
     * Appends a texture with the full normalized UV range and no tint.
     * The current transform is applied to the destination rectangle.
     *
     * @param texture texture to draw
     * @param bounds destination rectangle
     * @throws NullPointerException if {@code texture} or {@code bounds} is null
     * @throws IllegalStateException if no frame is in progress
     */
    public void image(Texture texture, Rect bounds) {
        list().image(texture, bounds, 0, FULL_UV, Color.WHITE, currentTransform());
    }

    /**
     * Appends a texture region with no tint.
     * The UV region selects a normalized top-left sub-rectangle, which makes the overload suitable
     * for sampling sprites from an atlas. The current transform is applied to the destination
     * rectangle.
     *
     * @param texture texture to draw
     * @param bounds destination rectangle
     * @param uv sampled texture region
     * @throws NullPointerException if any argument is null
     * @throws IllegalStateException if no frame is in progress
     */
    public void image(Texture texture, Rect bounds, Rect uv) {
        list().image(texture, bounds, 0, uv, Color.WHITE, currentTransform());
    }

    /**
     * Appends a textured rounded rectangle.
     * The current transform is applied to the destination rectangle.
     *
     * @param texture texture to draw
     * @param bounds destination rectangle
     * @param radius corner radius in logical UI units
     * @param uv sampled texture region
     * @param tint straight-alpha tint multiplied with the sampled texel
     * @throws NullPointerException if any reference argument is null
     * @throws IllegalArgumentException if the radius is negative or non-finite
     * @throws IllegalStateException if no frame is in progress
     */
    public void image(Texture texture, Rect bounds, float radius, Rect uv, Color tint) {
        list().image(texture, bounds, radius, uv, tint, currentTransform());
    }

    /**
     * Rasterizes a TrueType font and uploads its glyph atlas to the backend.
     * The default character set covers printable ASCII, Latin-1 and Cyrillic. The returned font is
     * bound to this render manager and must be released with {@link #destroyFont(Font)} when it is
     * no longer needed, unless the backend is closed first.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @return the uploaded font
     * @throws NullPointerException if {@code data} is null
     * @throws IllegalArgumentException if the size is invalid or the data has no TrueType outlines
     */
    public Font createFont(FontData data, float pixelSize) {
        return createFont(data, pixelSize, Font.defaultCodepoints(), FontStyle.REGULAR, 0);
    }

    /**
     * Rasterizes a TrueType font with letter spacing and uploads its glyph atlas to the backend.
     * The default character set covers printable ASCII, Latin-1 and Cyrillic.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @param letterSpacing extra space between glyphs in logical UI units
     * @return the uploaded font
     * @throws NullPointerException if {@code data} is null
     * @throws IllegalArgumentException if the size or spacing is invalid, or the data has no
     *                                  TrueType outlines
     */
    public Font createFont(FontData data, float pixelSize, float letterSpacing) {
        return createFont(data, pixelSize, Font.defaultCodepoints(), FontStyle.REGULAR, letterSpacing);
    }

    /**
     * Rasterizes a styled TrueType font and uploads its glyph atlas to the backend.
     * The default character set covers printable ASCII, Latin-1 and Cyrillic. Bold and italic are
     * synthetic: coverage is thickened and glyphs are sheared while the atlas is baked.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @param style synthetic bold or italic style
     * @return the uploaded font
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the size is invalid or the data has no TrueType outlines
     */
    public Font createFont(FontData data, float pixelSize, FontStyle style) {
        return createFont(data, pixelSize, Font.defaultCodepoints(), style, 0);
    }

    /**
     * Rasterizes a styled TrueType font with letter spacing and uploads its glyph atlas.
     * The default character set covers printable ASCII, Latin-1 and Cyrillic.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @param style synthetic bold or italic style
     * @param letterSpacing extra space between glyphs in logical UI units
     * @return the uploaded font
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the size or spacing is invalid, or the data has no
     *                                  TrueType outlines
     */
    public Font createFont(FontData data, float pixelSize, FontStyle style, float letterSpacing) {
        return createFont(data, pixelSize, Font.defaultCodepoints(), style, letterSpacing);
    }

    /**
     * Rasterizes a TrueType font for an explicit set of Unicode code points and uploads its atlas.
     * Kerning pairs are baked when at most 512 code points are requested.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @param codepoints Unicode code points to bake
     * @return the uploaded font
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the size or a code point is invalid, the data has no
     *                                  TrueType outlines, or the atlas is too large
     */
    public Font createFont(FontData data, float pixelSize, int[] codepoints) {
        return createFont(data, pixelSize, codepoints, FontStyle.REGULAR, 0);
    }

    /**
     * Rasterizes a styled TrueType font for an explicit set of Unicode code points and uploads its atlas.
     * Kerning pairs are baked when at most 512 code points are requested.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @param codepoints Unicode code points to bake
     * @param style synthetic bold or italic style
     * @return the uploaded font
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the size or a code point is invalid, the data has no
     *                                  TrueType outlines, or the atlas is too large
     */
    public Font createFont(FontData data, float pixelSize, int[] codepoints, FontStyle style) {
        return createFont(data, pixelSize, codepoints, style, 0);
    }

    /**
     * Rasterizes a styled TrueType font with letter spacing for explicit code points and uploads its atlas.
     * Kerning pairs are baked when at most 512 code points are requested.
     *
     * @param data font data
     * @param pixelSize requested line size in logical UI units
     * @param codepoints Unicode code points to bake
     * @param style synthetic bold or italic style
     * @param letterSpacing extra space between glyphs in logical UI units
     * @return the uploaded font
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the size or spacing is invalid, a code point is out of
     *                                  range, the data has no TrueType outlines, or the atlas is too large
     */
    public Font createFont(FontData data, float pixelSize, int[] codepoints,
                           FontStyle style, float letterSpacing) {
        Font font = Font.load(data, pixelSize, codepoints, style, letterSpacing);
        fontAtlases.put(font, backend.createTexture(font.atlas()));
        return font;
    }

    /**
     * Loads the best installed face of a system font family and uploads its glyph atlas.
     * A real bold or italic face is used when the system provides one; otherwise the regular face is
     * rasterized with the synthetic style. The result is empty when the family is not installed.
     *
     * @param family family name such as {@code "Arial"}
     * @param pixelSize requested line size in logical UI units
     * @param style requested face style
     * @return the uploaded font, or empty when the family is not installed
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the size is invalid or a font atlas cannot be built
     */
    public Optional<Font> createFont(String family, float pixelSize, FontStyle style) {
        return createFont(family, pixelSize, style, 0);
    }

    /**
     * Loads the best installed face of a system font family with letter spacing and uploads its atlas.
     * A real bold or italic face is used when the system provides one; otherwise the regular face is
     * rasterized with the synthetic style. The result is empty when the family is not installed.
     *
     * @param family family name such as {@code "Arial"}
     * @param pixelSize requested line size in logical UI units
     * @param style requested face style
     * @param letterSpacing extra space between glyphs in logical UI units
     * @return the uploaded font, or empty when the family is not installed
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the size or spacing is invalid, or a font atlas cannot be built
     */
    public Optional<Font> createFont(String family, float pixelSize, FontStyle style,
                                     float letterSpacing) {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(style, "style");
        Optional<FontData> regular = SystemFonts.find(family);
        if (regular.isEmpty()) {
            return Optional.empty();
        }
        Optional<FontData> face = style == FontStyle.REGULAR
                ? regular : SystemFonts.find(family, style);
        Font font = face
                .map(data -> Font.load(data, pixelSize, Font.defaultCodepoints(),
                        FontStyle.REGULAR, letterSpacing))
                .orElseGet(() -> Font.load(regular.get(), pixelSize, Font.defaultCodepoints(),
                        style, letterSpacing));
        fontAtlases.put(font, backend.createTexture(font.atlas()));
        return Optional.of(font);
    }

    /**
     * Releases the atlas texture of a font created by this render manager.
     * Fonts that were already released, or that belong to another render manager, are ignored.
     *
     * @param font font to release
     * @throws NullPointerException if {@code font} is null
     */
    public void destroyFont(Font font) {
        Objects.requireNonNull(font, "font");
        Texture atlas = fontAtlases.remove(font);
        if (atlas != null) {
            backend.destroyTexture(atlas);
        }
    }

    /**
     * Draws a text block with its top-left corner at the supplied point.
     * Newlines start a new line, tabs advance by four spaces and unsupported code points are
     * skipped. The current transform and clip apply to every glyph.
     *
     * @param font font created by this render manager
     * @param text text to draw
     * @param position top-left corner of the first line
     * @param color straight-alpha text color
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the font was not created by this render manager
     * @throws IllegalStateException if no frame is in progress
     */
    public void text(Font font, String text, Point position, Color color) {
        Objects.requireNonNull(position, "position");
        text(font, text, position.x(), position.y(), Paint.solid(color));
    }

    /**
     * Draws a text block with its top-left corner at the supplied coordinates.
     * Newlines start a new line, tabs advance by four spaces and unsupported code points are
     * skipped. The current transform and clip apply to every glyph.
     *
     * @param font font created by this render manager
     * @param text text to draw
     * @param x horizontal origin in logical UI units
     * @param y vertical origin in logical UI units
     * @param color straight-alpha text color
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the font was not created by this render manager
     * @throws IllegalStateException if no frame is in progress
     */
    public void text(Font font, String text, float x, float y, Color color) {
        Objects.requireNonNull(color, "color");
        text(font, text, x, y, Paint.solid(color));
    }

    /**
     * Draws a text block filled with a paint.
     * The paint is evaluated in layout coordinates, before the current transform, and sampled at
     * each glyph center, so gradients transition between glyphs. Newlines start a new line, tabs
     * advance by four spaces and unsupported code points are skipped.
     *
     * @param font font created by this render manager
     * @param text text to draw
     * @param position top-left corner of the first line
     * @param paint text paint
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the font was not created by this render manager
     * @throws IllegalStateException if no frame is in progress
     */
    public void text(Font font, String text, Point position, Paint paint) {
        Objects.requireNonNull(position, "position");
        text(font, text, position.x(), position.y(), paint);
    }

    /**
     * Draws a text block filled with a paint.
     * The paint is evaluated in layout coordinates, before the current transform, and sampled at
     * each glyph center, so gradients transition between glyphs. Newlines start a new line, tabs
     * advance by four spaces and unsupported code points are skipped.
     *
     * @param font font created by this render manager
     * @param text text to draw
     * @param x horizontal origin in logical UI units
     * @param y vertical origin in logical UI units
     * @param paint text paint
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the font was not created by this render manager
     * @throws IllegalStateException if no frame is in progress
     */
    public void text(Font font, String text, float x, float y, Paint paint) {
        text(font, text, x, y, paint, HorizontalAlignment.LEFT, VerticalAlignment.TOP);
    }

    /**
     * Draws a color-filled text block aligned against the supplied anchor point.
     * Multiline text aligns every line horizontally; the block is aligned vertically as a whole.
     * The current transform and clip apply to every glyph.
     *
     * @param font font created by this render manager
     * @param text text to draw
     * @param position text anchor
     * @param color straight-alpha text color
     * @param horizontal horizontal alignment relative to the anchor
     * @param vertical vertical alignment relative to the anchor
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the font was not created by this render manager
     * @throws IllegalStateException if no frame is in progress
     */
    public void text(Font font, String text, Point position, Color color,
                     HorizontalAlignment horizontal, VerticalAlignment vertical) {
        Objects.requireNonNull(position, "position");
        text(font, text, position.x(), position.y(), Paint.solid(color), horizontal, vertical);
    }

    /**
     * Draws a color-filled text block aligned against the supplied anchor coordinates.
     * Multiline text aligns every line horizontally; the block is aligned vertically as a whole.
     * The current transform and clip apply to every glyph.
     *
     * @param font font created by this render manager
     * @param text text to draw
     * @param x horizontal anchor in logical UI units
     * @param y vertical anchor in logical UI units
     * @param color straight-alpha text color
     * @param horizontal horizontal alignment relative to the anchor
     * @param vertical vertical alignment relative to the anchor
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the font was not created by this render manager
     * @throws IllegalStateException if no frame is in progress
     */
    public void text(Font font, String text, float x, float y, Color color,
                     HorizontalAlignment horizontal, VerticalAlignment vertical) {
        Objects.requireNonNull(color, "color");
        text(font, text, x, y, Paint.solid(color), horizontal, vertical);
    }

    /**
     * Draws a paint-filled text block aligned against the supplied anchor point.
     * Multiline text aligns every line horizontally; the block is aligned vertically as a whole.
     * The paint is evaluated in layout coordinates, before the current transform.
     *
     * @param font font created by this render manager
     * @param text text to draw
     * @param position text anchor
     * @param paint text paint
     * @param horizontal horizontal alignment relative to the anchor
     * @param vertical vertical alignment relative to the anchor
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the font was not created by this render manager
     * @throws IllegalStateException if no frame is in progress
     */
    public void text(Font font, String text, Point position, Paint paint,
                     HorizontalAlignment horizontal, VerticalAlignment vertical) {
        Objects.requireNonNull(position, "position");
        text(font, text, position.x(), position.y(), paint, horizontal, vertical);
    }

    /**
     * Draws a paint-filled text block aligned against the supplied anchor coordinates.
     * Multiline text aligns every line horizontally; the block is aligned vertically as a whole.
     * The paint is evaluated in layout coordinates, before the current transform, and sampled at
     * each glyph center, so gradients transition between glyphs.
     *
     * @param font font created by this render manager
     * @param text text to draw
     * @param x horizontal anchor in logical UI units
     * @param y vertical anchor in logical UI units
     * @param paint text paint
     * @param horizontal horizontal alignment relative to the anchor
     * @param vertical vertical alignment relative to the anchor
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the font was not created by this render manager
     * @throws IllegalStateException if no frame is in progress
     */
    public void text(Font font, String text, float x, float y, Paint paint,
                     HorizontalAlignment horizontal, VerticalAlignment vertical) {
        Objects.requireNonNull(horizontal, "horizontal");
        Objects.requireNonNull(vertical, "vertical");
        drawText(font, text, x, y, paint, font.layoutRelative(text, horizontal, vertical));
    }

    /**
     * Draws a text block with wrapping and truncation options.
     * The anchor and alignments are taken from {@code style}. The current transform and clip apply
     * to every glyph.
     *
     * @param font font created by this render manager
     * @param text text to draw
     * @param x horizontal anchor in logical UI units
     * @param y vertical anchor in logical UI units
     * @param paint text paint
     * @param style layout options
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the font was not created by this render manager
     * @throws IllegalStateException if no frame is in progress
     */
    public void text(Font font, String text, float x, float y, Paint paint, TextStyle style) {
        Objects.requireNonNull(style, "style");
        drawText(font, text, x, y, paint, font.layoutBlockRelative(text, style).glyphs());
    }

    /**
     * Draws a text block with wrapping and truncation options at a point anchor.
     *
     * @param font font created by this render manager
     * @param text text to draw
     * @param position text anchor
     * @param paint text paint
     * @param style layout options
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the font was not created by this render manager
     * @throws IllegalStateException if no frame is in progress
     */
    public void text(Font font, String text, Point position, Paint paint, TextStyle style) {
        Objects.requireNonNull(position, "position");
        text(font, text, position.x(), position.y(), paint, style);
    }

    /**
     * Draws a color-filled text block with wrapping and truncation options.
     *
     * @param font font created by this render manager
     * @param text text to draw
     * @param x horizontal anchor in logical UI units
     * @param y vertical anchor in logical UI units
     * @param color straight-alpha text color
     * @param style layout options
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the font was not created by this render manager
     * @throws IllegalStateException if no frame is in progress
     */
    public void text(Font font, String text, float x, float y, Color color, TextStyle style) {
        Objects.requireNonNull(color, "color");
        text(font, text, x, y, Paint.solid(color), style);
    }

    /**
     * Draws a color-filled text block with wrapping and truncation options at a point anchor.
     *
     * @param font font created by this render manager
     * @param text text to draw
     * @param position text anchor
     * @param color straight-alpha text color
     * @param style layout options
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the font was not created by this render manager
     * @throws IllegalStateException if no frame is in progress
     */
    public void text(Font font, String text, Point position, Color color, TextStyle style) {
        Objects.requireNonNull(position, "position");
        text(font, text, position.x(), position.y(), color, style);
    }

    private void drawText(Font font, String text, float x, float y, Paint paint,
                          List<TextGlyph> relative) {
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(paint, "paint");
        Objects.requireNonNull(relative, "relative");
        DrawList target = list();
        Texture atlas = fontAtlases.get(font);
        if (atlas == null) {
            throw new IllegalArgumentException("Font was not created by this render manager.");
        }
        if (relative.isEmpty()) {
            return;
        }
        Transform transform = currentTransform();
        Color solid = paint instanceof Paint.Solid value ? value.color() : null;
        List<TextGlyph> glyphs = new ArrayList<>(relative.size());
        List<Color> colors = new ArrayList<>(relative.size());
        for (TextGlyph glyph : relative) {
            Rect bounds = glyph.bounds();
            Rect snapped = snapToPixel(new Rect(bounds.x() + x, bounds.y() + y,
                    bounds.width(), bounds.height()));
            glyphs.add(new TextGlyph(snapped, glyph.uv()));
            colors.add(solid != null ? solid : paint.colorAt(center(snapped)));
        }
        target.text(atlas, glyphs, colors, transform);
    }

    private static Rect snapToPixel(Rect bounds) {
        float x = Math.round(bounds.x());
        float y = Math.round(bounds.y());
        if (x == bounds.x() && y == bounds.y()) {
            return bounds;
        }
        return new Rect(x, y, bounds.width(), bounds.height());
    }

    private static Point center(Rect bounds) {
        return new Point(bounds.x() + bounds.width() / 2, bounds.y() + bounds.height() / 2);
    }

    /**
     * Measures a text block with the baked advances, kerning and line height of a font.
     * This is the same measurement used by text alignment and does not require an active frame.
     *
     * @param font font created by this render manager
     * @param text text to measure
     * @return the non-negative width and height in logical UI units
     * @throws NullPointerException if an argument is null
     */
    public Size measureText(Font font, String text) {
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(text, "text");
        return font.measure(text);
    }

    /**
     * Measures a wrapped or truncated text block.
     *
     * @param font font created by this render manager
     * @param text text to measure
     * @param style layout options
     * @return the measured width and height in logical UI units
     * @throws NullPointerException if an argument is null
     */
    public Size measureText(Font font, String text, TextStyle style) {
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(style, "style");
        return font.measure(text, style);
    }

    /**
     * Returns the current tessellation quality and sample-count hint.
     *
     * @return the current antialiasing setting
     */
    public Antialiasing antialiasing() {
        return antialiasing;
    }

    /**
     * Sets tessellation quality and the sample-count hint for future frames.
     * The sample count is not applied to an existing render target; use
     * {@link Antialiasing#samples()} when creating the window or render pass.
     *
     * @param antialiasing antialiasing setting
     * @throws NullPointerException if {@code antialiasing} is null
     */
    public void setAntialiasing(Antialiasing antialiasing) {
        this.antialiasing = Objects.requireNonNull(antialiasing, "antialiasing");
    }

    /**
     * Returns the current composed transform.
     *
     * @return the current transform, or identity before the first frame
     */
    public Transform currentTransform() {
        return transforms.peek();
    }

    /**
     * Returns the current intersection of all active clips.
     *
     * @return the active clip rectangle in logical viewport coordinates
     * @throws IllegalStateException if no frame is in progress
     */
    public Rect clip() {
        return list().clip();
    }

    /**
     * Pushes a transform that applies to subsequent shape, mesh, image and texture-mask commands.
     * Transforms compose with the current transform; the new transform is applied first.
     *
     * @param transform transform to push
     * @throws NullPointerException if {@code transform} is null
     * @throws IllegalStateException if no frame is in progress
     */
    public void pushTransform(Transform transform) {
        list();
        Objects.requireNonNull(transform, "transform");
        transforms.push(transforms.peek().multiply(transform));
    }

    /**
     * Restores the transform that was active before the matching {@link #pushTransform(Transform)}.
     *
     * @throws IllegalStateException if no frame is in progress or only the identity transform remains
     */
    public void popTransform() {
        list();
        if (transforms.size() == 1) {
            throw new IllegalStateException("Cannot pop the identity transform.");
        }
        transforms.pop();
    }

    /**
     * Runs an action with an additional transform and always restores the previous transform.
     *
     * @param transform transform to apply
     * @param action drawing action
     * @throws NullPointerException if {@code transform} or {@code action} is null
     * @throws IllegalStateException if no frame is in progress
     */
    public void withTransform(Transform transform, Runnable action) {
        Objects.requireNonNull(action, "action");
        pushTransform(transform);
        try {
            action.run();
        } finally {
            popTransform();
        }
    }

    /**
     * Appends a raw triangle mesh using per-vertex colors.
     *
     * @param vertices triangle vertices in submission order
     * @throws NullPointerException if {@code vertices} or a vertex is null
     * @throws IllegalArgumentException if the vertex count is not a non-zero multiple of three
     * @throws IllegalStateException if no frame is in progress
     */
    public void mesh(List<MeshVertex> vertices) {
        Objects.requireNonNull(vertices, "vertices");
        drawMesh(vertices);
    }

    /**
     * Fills a rectangle.
     * Supporting backends evaluate gradients with up to four stops and transformed shapes on the GPU.
     * Other backends and paints use the tessellated path, except for translated solid rectangles.
     *
     * @param bounds rectangle to fill
     * @param paint fill paint
     * @throws NullPointerException if an argument is null
     * @throws IllegalStateException if no frame is in progress
     */
    public void fillRect(Rect bounds, Paint paint) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(paint, "paint");
        DrawList drawList = list();
        Transform transform = currentTransform();
        if (paint instanceof Paint.Solid solid && isTranslation(transform)) {
            drawList.rect(translatedBounds(bounds, transform), solid.color());
        } else if (backend.supportsAnalyticShapes() && ShapeCommand.supportsPaint(paint)) {
            drawList.shape(bounds, 0, false, paint, transform);
        } else {
            drawMesh(ShapeTessellator.fillRect(bounds, paint));
        }
    }

    /**
     * Fills a rounded rectangle.
     * Supporting backends evaluate gradients with up to four stops and transformed shapes on the GPU.
     * Other backends and paints use the tessellated path, except for translated solid rectangles.
     *
     * @param bounds rectangle to fill
     * @param radius corner radius in logical UI units
     * @param paint fill paint
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the radius is negative or non-finite
     * @throws IllegalStateException if no frame is in progress
     */
    public void fillRoundedRect(Rect bounds, float radius, Paint paint) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(paint, "paint");
        ShapeTessellator.validateRadius(radius);
        DrawList drawList = list();
        Transform transform = currentTransform();
        if (paint instanceof Paint.Solid solid && isTranslation(transform)) {
            drawList.roundedRect(translatedBounds(bounds, transform), radius, solid.color());
        } else if (backend.supportsAnalyticShapes() && ShapeCommand.supportsPaint(paint)) {
            drawList.shape(bounds, radius, false, paint, transform);
        } else {
            drawMesh(ShapeTessellator.fillRoundedRect(bounds, radius, paint));
        }
    }

    /**
     * Fills a circle.
     *
     * @param center circle center
     * @param radius circle radius
     * @param paint fill paint
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the radius is not positive or is non-finite
     * @throws IllegalStateException if no frame is in progress
     */
    public void fillCircle(Point center, float radius, Paint paint) {
        fillEllipse(center, radius, radius, paint);
    }

    /**
     * Fills an ellipse.
     *
     * @param center ellipse center
     * @param radiusX horizontal radius
     * @param radiusY vertical radius
     * @param paint fill paint
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if a radius is not positive or is non-finite
     * @throws IllegalStateException if no frame is in progress
     */
    public void fillEllipse(Point center, float radiusX, float radiusY, Paint paint) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(paint, "paint");
        ShapeTessellator.validateEllipse(center, radiusX, radiusY);
        DrawList target = list();
        if (backend.supportsAnalyticShapes() && ShapeCommand.supportsPaint(paint)) {
            target.shape(new Rect(center.x() - radiusX, center.y() - radiusY, radiusX * 2, radiusY * 2),
                    0, true, paint, currentTransform());
        } else {
            drawMesh(ShapeTessellator.fillEllipse(center, radiusX, radiusY, paint));
        }
    }

    /**
     * Fills a circular or elliptical sector.
     * Angles are in radians; zero points along positive X and positive angles move toward positive Y.
     *
     * @param center arc center
     * @param radiusX horizontal radius
     * @param radiusY vertical radius
     * @param startAngle start angle in radians
     * @param sweepAngle signed sweep in radians, at most one full turn
     * @param paint fill paint
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if a radius or angle is invalid
     * @throws IllegalStateException if no frame is in progress
     */
    public void fillArc(Point center, float radiusX, float radiusY,
                        float startAngle, float sweepAngle, Paint paint) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(paint, "paint");
        ShapeTessellator.validateArc(center, radiusX, radiusY, startAngle, sweepAngle);
        drawMesh(ShapeTessellator.fillArc(
                center, radiusX, radiusY, startAngle, sweepAngle, paint));
    }

    /**
     * Fills a polygon using ear-clipping triangulation.
     *
     * @param points polygon vertices in order
     * @param paint fill paint
     * @throws NullPointerException if an argument or point is null
     * @throws IllegalArgumentException if the polygon has fewer than three distinct points or no area
     * @throws IllegalStateException if no frame is in progress
     */
    public void fillPolygon(List<Point> points, Paint paint) {
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(paint, "paint");
        drawMesh(ShapeTessellator.fillPolygon(points, paint));
    }

    /**
     * Strokes a rectangle with round joins.
     *
     * @param bounds rectangle outline
     * @param width stroke width
     * @param paint stroke paint
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the width is not positive or is non-finite
     * @throws IllegalStateException if no frame is in progress
     */
    public void strokeRect(Rect bounds, float width, Paint paint) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(paint, "paint");
        ShapeTessellator.validateWidth(width);
        drawMesh(ShapeTessellator.strokeRect(bounds, width, paint));
    }

    /**
     * Strokes a rounded rectangle with round joins.
     *
     * @param bounds rectangle outline
     * @param radius corner radius
     * @param width stroke width
     * @param paint stroke paint
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the radius or width is invalid
     * @throws IllegalStateException if no frame is in progress
     */
    public void strokeRoundedRect(Rect bounds, float radius, float width, Paint paint) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(paint, "paint");
        ShapeTessellator.validateRadius(radius);
        ShapeTessellator.validateWidth(width);
        drawMesh(ShapeTessellator.strokeRoundedRect(bounds, radius, width, paint));
    }

    /**
     * Strokes a line segment with round caps.
     *
     * @param start segment start
     * @param end segment end
     * @param width stroke width
     * @param paint stroke paint
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the width is invalid
     * @throws IllegalStateException if no frame is in progress
     */
    public void strokeLine(Point start, Point end, float width, Paint paint) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(paint, "paint");
        ShapeTessellator.validateWidth(width);
        drawMesh(ShapeTessellator.strokeLine(start, end, width, paint));
    }

    /**
     * Strokes a circle.
     *
     * @param center circle center
     * @param radius circle radius
     * @param width stroke width
     * @param paint stroke paint
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the radius or width is invalid
     * @throws IllegalStateException if no frame is in progress
     */
    public void strokeCircle(Point center, float radius, float width, Paint paint) {
        strokeEllipse(center, radius, radius, width, paint);
    }

    /**
     * Strokes an ellipse.
     *
     * @param center ellipse center
     * @param radiusX horizontal radius
     * @param radiusY vertical radius
     * @param width stroke width
     * @param paint stroke paint
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if a radius or width is invalid
     * @throws IllegalStateException if no frame is in progress
     */
    public void strokeEllipse(Point center, float radiusX, float radiusY, float width, Paint paint) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(paint, "paint");
        ShapeTessellator.validateEllipse(center, radiusX, radiusY);
        ShapeTessellator.validateWidth(width);
        drawMesh(ShapeTessellator.strokeEllipse(center, radiusX, radiusY, width, paint));
    }

    /**
     * Strokes an open circular or elliptical arc with round caps.
     * Angles are in radians; zero points along positive X and positive angles move toward positive Y.
     *
     * @param center arc center
     * @param radiusX horizontal radius
     * @param radiusY vertical radius
     * @param startAngle start angle in radians
     * @param sweepAngle signed sweep in radians, at most one full turn
     * @param width stroke width
     * @param paint stroke paint
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if a radius, angle or width is invalid
     * @throws IllegalStateException if no frame is in progress
     */
    public void strokeArc(Point center, float radiusX, float radiusY,
                          float startAngle, float sweepAngle, float width, Paint paint) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(paint, "paint");
        ShapeTessellator.validateArc(center, radiusX, radiusY, startAngle, sweepAngle);
        ShapeTessellator.validateWidth(width);
        drawMesh(ShapeTessellator.strokeArc(
                center, radiusX, radiusY, startAngle, sweepAngle, width, paint));
    }

    /**
     * Strokes an open polyline with round caps and round joins.
     *
     * @param points path vertices in order
     * @param width stroke width
     * @param paint stroke paint
     * @throws NullPointerException if an argument or point is null
     * @throws IllegalArgumentException if the path has fewer than two distinct points or the width is invalid
     * @throws IllegalStateException if no frame is in progress
     */
    public void strokePolyline(List<Point> points, float width, Paint paint) {
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(paint, "paint");
        ShapeTessellator.validateWidth(width);
        drawMesh(ShapeTessellator.strokePolyline(points, width, paint));
    }

    /**
     * Strokes a closed polygon with round joins.
     *
     * @param points polygon vertices in order
     * @param width stroke width
     * @param paint stroke paint
     * @throws NullPointerException if an argument or point is null
     * @throws IllegalArgumentException if the polygon has fewer than three distinct points or the width is invalid
     * @throws IllegalStateException if no frame is in progress
     */
    public void strokePolygon(List<Point> points, float width, Paint paint) {
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(paint, "paint");
        ShapeTessellator.validateWidth(width);
        drawMesh(ShapeTessellator.strokePolygon(points, width, paint));
    }

    /**
     * Pushes a rectangular stencil mask.
     * Content submitted until the matching {@link #popMask()} is clipped to the mask.
     *
     * @param bounds mask rectangle
     * @throws NullPointerException if {@code bounds} is null
     * @throws IllegalStateException if no frame is in progress
     */
    public void pushMaskRect(Rect bounds) {
        Objects.requireNonNull(bounds, "bounds");
        DrawList target = list();
        target.pushMask(transformVertices(ShapeTessellator.fillRect(bounds, MASK_PAINT)));
    }

    /**
     * Pushes a rounded rectangular stencil mask.
     *
     * @param bounds mask rectangle
     * @param radius corner radius
     * @throws NullPointerException if {@code bounds} is null
     * @throws IllegalArgumentException if the radius is invalid
     * @throws IllegalStateException if no frame is in progress
     */
    public void pushMaskRoundedRect(Rect bounds, float radius) {
        Objects.requireNonNull(bounds, "bounds");
        ShapeTessellator.validateRadius(radius);
        DrawList target = list();
        target.pushMask(transformVertices(ShapeTessellator.fillRoundedRect(bounds, radius, MASK_PAINT)));
    }

    /**
     * Pushes a circular or elliptical stencil mask.
     *
     * @param center mask center
     * @param radiusX horizontal radius
     * @param radiusY vertical radius
     * @throws NullPointerException if {@code center} is null
     * @throws IllegalArgumentException if a radius is invalid
     * @throws IllegalStateException if no frame is in progress
     */
    public void pushMaskEllipse(Point center, float radiusX, float radiusY) {
        Objects.requireNonNull(center, "center");
        ShapeTessellator.validateEllipse(center, radiusX, radiusY);
        DrawList target = list();
        target.pushMask(transformVertices(
                ShapeTessellator.fillEllipse(center, radiusX, radiusY, MASK_PAINT)));
    }

    /**
     * Pushes a polygon stencil mask.
     *
     * @param points polygon vertices in order
     * @throws NullPointerException if {@code points} or a point is null
     * @throws IllegalArgumentException if the polygon is invalid
     * @throws IllegalStateException if no frame is in progress
     */
    public void pushMaskPolygon(List<Point> points) {
        Objects.requireNonNull(points, "points");
        DrawList target = list();
        target.pushMask(transformVertices(ShapeTessellator.fillPolygon(points, MASK_PAINT)));
    }

    /**
     * Pushes a stencil mask driven by a texture's alpha channel.
     *
     * @param texture mask texture
     * @param bounds destination rectangle in logical UI units
     * @throws NullPointerException if an argument is null
     * @throws IllegalStateException if no frame is in progress
     */
    public void pushMaskTexture(Texture texture, Rect bounds) {
        pushMaskTexture(texture, bounds, new Rect(0, 0, 1, 1));
    }

    /**
     * Pushes a texture mask with an explicit sampled region.
     * Pixels with zero alpha are outside the mask. The mask rectangle follows the current transform.
     *
     * @param texture mask texture
     * @param bounds destination rectangle in logical UI units
     * @param uv sampled texture region
     * @throws NullPointerException if an argument is null
     * @throws IllegalStateException if no frame is in progress
     */
    public void pushMaskTexture(Texture texture, Rect bounds, Rect uv) {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(uv, "uv");
        list().pushMask(texture, bounds, uv, currentTransform());
    }

    /**
     * Ends the most recently pushed stencil mask scope.
     *
     * @throws IllegalStateException if no frame is in progress or no mask is active
     */
    public void popMask() {
        list().popMask();
    }

    /**
     * Intersects the current clip with the supplied rectangle.
     *
     * @param clip additional clip rectangle
     * @throws NullPointerException if {@code clip} is null
     * @throws IllegalStateException if no frame is in progress
     */
    public void pushClip(Rect clip) {
        list().pushClip(clip);
    }

    /**
     * Restores the clip that was active before the matching {@link #pushClip(Rect)}.
     *
     * @throws IllegalStateException if no frame is in progress or only the viewport clip remains
     */
    public void popClip() {
        list().popClip();
    }

    /**
     * Runs an action with an additional clip and always restores the previous clip.
     *
     * @param clip additional clip rectangle
     * @param action drawing action
     * @throws NullPointerException if {@code clip} or {@code action} is null
     * @throws IllegalStateException if no frame is in progress
     */
    public void withClip(Rect clip, Runnable action) {
        Objects.requireNonNull(action, "action");
        list().pushClip(clip);
        try {
            action.run();
        } finally {
            drawList.popClip();
        }
    }

    /**
     * Creates a backend texture from CPU image data.
     *
     * @param image RGBA8 image data with a top-left origin
     * @return the created texture
     * @throws NullPointerException if {@code image} is null
     */
    public Texture createTexture(ImageData image) {
        return backend.createTexture(Objects.requireNonNull(image, "image"));
    }

    /**
     * Releases a texture created by the registered backend.
     *
     * @param texture texture to release
     * @throws NullPointerException if {@code texture} is null
     */
    public void destroyTexture(Texture texture) {
        backend.destroyTexture(Objects.requireNonNull(texture, "texture"));
    }

    private static boolean isTranslation(Transform transform) {
        return transform.m00() == 1 && transform.m01() == 0
                && transform.m10() == 0 && transform.m11() == 1;
    }

    private static Rect translatedBounds(Rect bounds, Transform transform) {
        if (transform.m02() == 0 && transform.m12() == 0) {
            return bounds;
        }
        return new Rect(bounds.x() + transform.m02(), bounds.y() + transform.m12(),
                bounds.width(), bounds.height());
    }

    private void drawMesh(List<MeshVertex> vertices) {
        list().meshOwned(transformVertices(vertices));
    }

    private List<MeshVertex> transformVertices(List<MeshVertex> vertices) {
        Transform transform = currentTransform();
        if (transform.isIdentity()) {
            return vertices;
        }
        List<MeshVertex> transformed = new ArrayList<>(vertices.size());
        for (MeshVertex vertex : vertices) {
            Point point = transform.apply(new Point(vertex.x(), vertex.y()));
            transformed.add(new MeshVertex(point.x(), point.y(), vertex.color()));
        }
        return transformed;
    }

    private DrawList list() {
        if (!drawing) {
            throw new IllegalStateException("begin must be called before drawing.");
        }
        return drawList;
    }
}
