package com.lixui.backend.vulkan;

import com.lixui.render.Texture;
import com.lixui.render.TextureOptions;

/**
 * A Vulkan texture handle owned by the backend that created it.
 * It references a descriptor set and an optionally owned image.
 */
final class VulkanTexture implements Texture {
    private final VulkanBackend owner;
    private final long image;
    private final long imageView;
    private final long memory;
    private final long descriptorSet;
    private final int width;
    private final int height;
    private final boolean owned;
    private final TextureOptions options;

    VulkanTexture(VulkanBackend owner, long image, long imageView, long memory, long descriptorSet,
                  int width, int height, boolean owned, TextureOptions options) {
        this.owner = owner;
        this.image = image;
        this.imageView = imageView;
        this.memory = memory;
        this.descriptorSet = descriptorSet;
        this.width = width;
        this.height = height;
        this.owned = owned;
        this.options = options;
    }

    boolean belongsTo(VulkanBackend backend) {
        return owner == backend;
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

    @Override
    public TextureOptions options() {
        return options;
    }
}
