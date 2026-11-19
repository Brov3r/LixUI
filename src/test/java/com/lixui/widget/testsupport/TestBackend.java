package com.lixui.widget.testsupport;

import com.lixui.render.DrawList;
import com.lixui.render.ImageData;
import com.lixui.render.RenderBackend;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;

import java.util.ArrayList;
import java.util.List;

/**
 * A render backend that records frames, textures and viewport changes for tests.
 */
public final class TestBackend implements RenderBackend {
    private final List<Texture> created = new ArrayList<>();
    private final List<Texture> destroyed = new ArrayList<>();
    private DrawList lastDrawList;
    private Viewport viewport;
    private ImageData lastImage;
    private Texture lastCreatedTexture;
    private Texture lastDestroyedTexture;
    private int drawCalls;
    private int lastCommandCount;
    private int failAfter = Integer.MAX_VALUE;
    private boolean analytic;

    /**
     * Returns the last submitted draw list.
     *
     * @return the last draw list, or null before the first frame
     */
    public DrawList lastDrawList() {
        return lastDrawList;
    }

    /**
     * Returns the last viewport passed to {@link #resize(Viewport)}.
     *
     * @return the last viewport, or null before the first resize
     */
    public Viewport viewport() {
        return viewport;
    }

    /**
     * Returns the number of submitted frames.
     *
     * @return the frame count
     */
    public int drawCalls() {
        return drawCalls;
    }

    /**
     * Returns the command count of the last submitted frame.
     *
     * @return the last command count, or zero before the first frame
     */
    public int lastCommandCount() {
        return lastCommandCount;
    }

    /**
     * Returns whether analytic shapes are advertised.
     *
     * @return true when analytic shapes are enabled
     */
    public boolean analytic() {
        return analytic;
    }

    /**
     * Enables or disables the analytic shape capability.
     *
     * @param analytic true to advertise analytic shapes
     */
    public void setAnalytic(boolean analytic) {
        this.analytic = analytic;
    }

    /**
     * Returns the texture creation limit.
     *
     * @return the maximum number of textures created before an upload failure
     */
    public int failAfter() {
        return failAfter;
    }

    /**
     * Fails texture uploads after the supplied number of successful creations.
     *
     * @param failAfter successful creations before the failure, or {@link Integer#MAX_VALUE}
     */
    public void setFailAfter(int failAfter) {
        this.failAfter = failAfter;
    }

    /**
     * Returns the source image of the last texture upload.
     *
     * @return the last uploaded image, or null when no texture was created
     */
    public ImageData lastImage() {
        return lastImage;
    }

    /**
     * Returns the most recently created texture.
     *
     * @return the last created texture, or null
     */
    public Texture lastCreatedTexture() {
        return lastCreatedTexture;
    }

    /**
     * Returns the most recently destroyed texture.
     *
     * @return the last destroyed texture, or null
     */
    public Texture lastDestroyedTexture() {
        return lastDestroyedTexture;
    }

    /**
     * Returns every texture created by this backend.
     *
     * @return an immutable texture snapshot
     */
    public List<Texture> created() {
        return List.copyOf(created);
    }

    /**
     * Returns every texture destroyed by this backend.
     *
     * @return an immutable texture snapshot
     */
    public List<Texture> destroyed() {
        return List.copyOf(destroyed);
    }

    /**
     * Reports whether analytic shapes are supported.
     *
     * @return the configured capability
     */
    @Override
    public boolean supportsAnalyticShapes() {
        return analytic;
    }

    /**
     * Records the requested viewport.
     *
     * @param viewport requested viewport
     */
    @Override
    public void resize(Viewport viewport) {
        this.viewport = viewport;
    }

    /**
     * Records a submitted frame.
     *
     * @param drawList submitted commands
     */
    @Override
    public void draw(DrawList drawList) {
        lastDrawList = drawList;
        drawCalls++;
        lastCommandCount = drawList.commands().size();
    }

    /**
     * Creates a texture handle with the source dimensions.
     *
     * @param image source pixels
     * @return a lightweight texture handle
     * @throws IllegalStateException after the configured creation limit is reached
     */
    @Override
    public Texture createTexture(ImageData image) {
        if (created.size() >= failAfter) {
            throw new IllegalStateException("Texture upload failed.");
        }
        lastImage = image;
        lastCreatedTexture = new TestTexture(image.width(), image.height());
        created.add(lastCreatedTexture);
        return lastCreatedTexture;
    }

    /**
     * Records texture disposal.
     *
     * @param texture released texture
     */
    @Override
    public void destroyTexture(Texture texture) {
        lastDestroyedTexture = texture;
        destroyed.add(texture);
    }

    /**
     * Closes this resource-free backend.
     */
    @Override
    public void close() {
    }
}
