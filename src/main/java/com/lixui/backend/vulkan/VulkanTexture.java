package com.lixui.backend.vulkan;

import com.lixui.render.Texture;

/**
 * A Vulkan texture handle that references a descriptor set and an optionally
 * owned image.
 */
final class VulkanTexture implements Texture {
    private final long image;
    private final long imageView;
    private final long memory;
    private final long descriptorSet;
    private final int width;
    private final int height;
    private final boolean owned;

    VulkanTexture(long image, long imageView, long memory, long descriptorSet, int width, int height, boolean owned) {
        this.image = image;
        this.imageView = imageView;
        this.memory = memory;
        this.descriptorSet = descriptorSet;
        this.width = width;
        this.height = height;
        this.owned = owned;
    }

    long image() {
        return image;
    }

    long imageView() {
        return imageView;
    }

    long memory() {
        return memory;
    }

    long descriptorSet() {
        return descriptorSet;
    }

    boolean owned() {
        return owned;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }
}
