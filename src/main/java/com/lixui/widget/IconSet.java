package com.lixui.widget;

import com.lixui.render.Texture;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns a named set of shared bundled icons and reloads them when the pixel size or smoothing changes.
 * Acquisition is atomic: a failure releases the icons loaded so far and rethrows the original error.
 */
public final class IconSet {
    private final String[] names;
    private final List<Texture> textures = new ArrayList<>();
    private int pixelSize = -1;
    private boolean smoothed;

    /**
     * Creates an icon set for the supplied bundled resource names.
     *
     * @param names bundled icon resource names in retrieval order
     */
    public IconSet(String... names) {
        this.names = names.clone();
    }

    /**
     * Reports whether the loaded icons match the requested size and current smoothing setting.
     *
     * @param context context providing the smoothing setting
     * @param size requested icon edge length in physical pixels
     * @return true when no reload is needed
     */
    public boolean matches(UiContext context, int size) {
        return !textures.isEmpty() && pixelSize == size
                && smoothed == context.iconSmoothing();
    }

    /**
     * Acquires every icon at the requested size and releases the previous set.
     *
     * @param context context that owns the icon cache
     * @param size icon edge length in physical pixels
     */
    public void reload(UiContext context, int size) {
        List<Texture> loaded = new ArrayList<>(names.length);
        try {
            for (String name : names) {
                loaded.add(context.acquireIcon(name, size));
            }
        } catch (RuntimeException | Error exception) {
            try {
                context.releaseIcons(loaded);
            } catch (RuntimeException cleanupFailure) {
                if (cleanupFailure != exception) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw exception;
        }
        List<Texture> previous = List.copyOf(textures);
        textures.clear();
        textures.addAll(loaded);
        pixelSize = size;
        smoothed = context.iconSmoothing();
        context.releaseIcons(previous);
    }

    /**
     * Returns a loaded icon by its declaration order.
     *
     * @param index zero-based icon index
     * @return the loaded icon texture, or null when the set is not loaded
     */
    public Texture get(int index) {
        return index < textures.size() ? textures.get(index) : null;
    }

    /**
     * Releases every loaded icon and clears the cached size.
     *
     * @param context context that owns the icon cache, or null when the icons are already released
     */
    public void release(UiContext context) {
        List<Texture> previous = List.copyOf(textures);
        textures.clear();
        pixelSize = -1;
        smoothed = false;
        if (context != null) {
            context.releaseIcons(previous);
        }
    }
}
