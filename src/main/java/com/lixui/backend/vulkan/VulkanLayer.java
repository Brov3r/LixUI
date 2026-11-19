package com.lixui.backend.vulkan;

import com.lixui.render.DrawList;
import com.lixui.render.ImageData;
import com.lixui.render.RenderBackend;
import com.lixui.render.Texture;
import com.lixui.render.TextureOptions;
import com.lixui.render.Viewport;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;

import java.nio.LongBuffer;
import java.util.Objects;

/**
 * Owns a transparent UI color/stencil render pass and composites its result over a host render pass.
 * Record {@link #beginFrame(VkCommandBuffer)}, UI drawing, and {@link #endFrame()} outside the host
 * pass, then call {@link #composite(VkCommandBuffer)} in subpass zero of the compatible host pass.
 * Composition blends premultiplied alpha and never tests or writes host depth/stencil.
 * The caller must wait for previous submissions using this instance before updating, recording,
 * resizing, releasing textures, or closing it. Use one instance per concurrent frame slot.
 * Command-buffer state is explicit: record composition last or rebind host state afterwards.
 */
public final class VulkanLayer implements RenderBackend {
    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private VulkanBackend backend;
    private long renderPass;
    private long compositePipeline;
    private long framebuffer;
    private Attachment color;
    private Attachment stencil;
    private Texture sampledColor;
    private int stencilFormat;
    private int width;
    private int height;
    private Viewport viewport;
    private VkCommandBuffer recording;
    private boolean drawn;
    private boolean ready;
    private boolean closed;
    private int drawCalls;

    /**
     * Creates the UI pass and a composition pipeline compatible with the host's color attachment.
     * The host render pass must have one color attachment in subpass zero and preserve the scene
     * color when the UI is composed over it.
     *
     * @param physicalDevice physical device providing image memory and format support
     * @param device logical device owning the resources
     * @param queue graphics queue used for synchronous texture uploads
     * @param queueFamily family index of the graphics queue
     * @param hostRenderPass compatible host render pass for composition
     * @param hostSamples sample count of the host color attachment
     * @throws NullPointerException if a device or queue is null
     * @throws IllegalArgumentException if the host pass or sample count is invalid
     * @throws IllegalStateException if required formats or GPU resources are unavailable
     */
    public VulkanLayer(VkPhysicalDevice physicalDevice, VkDevice device, VkQueue queue,
                       int queueFamily, long hostRenderPass, int hostSamples) {
        this.physicalDevice = Objects.requireNonNull(physicalDevice, "physicalDevice");
        this.device = Objects.requireNonNull(device, "device");
        Objects.requireNonNull(queue, "queue");
        if (hostRenderPass == VK10.VK_NULL_HANDLE || hostSamples <= 0
                || hostSamples > VK10.VK_SAMPLE_COUNT_64_BIT || Integer.bitCount(hostSamples) != 1) {
            throw new IllegalArgumentException("A valid host render pass and sample count are required.");
        }
        try {
            stencilFormat = chooseStencilFormat();
            renderPass = createRenderPass();
            backend = new VulkanBackend(physicalDevice, device, queue, queueFamily, renderPass,
                    VK10.VK_SAMPLE_COUNT_1_BIT);
            compositePipeline = backend.createCompositePipeline(hostRenderPass, hostSamples);
        } catch (RuntimeException | Error exception) {
            close();
            throw exception;
        }
    }

    /**
     * Returns the low-level renderer for texture wrapping and UI batch diagnostics.
     * Frame recording through this layer remains responsible for attachment isolation.
     *
     * @return the owned low-level backend
     * @throws IllegalStateException if the layer is closed
     */
    public VulkanBackend backend() {
        ensureOpen();
        return backend;
    }

    /**
     * Enables analytic shape and gradient rendering.
     *
     * @return true
     */
    @Override
    public boolean supportsAnalyticShapes() {
        return true;
    }

    /**
     * Sets the layer dimensions; physical attachments are resized on the next frame.
     *
     * @param viewport physical and logical dimensions of the composition target
     * @throws NullPointerException if the viewport is null
     * @throws IllegalStateException if the layer is closed or a UI pass is active
     */
    @Override
    public void resize(Viewport viewport) {
        ensureInactive();
        Viewport next = Objects.requireNonNull(viewport, "viewport");
        if (!next.equals(this.viewport)) {
            ready = false;
        }
        this.viewport = next;
        backend.resize(next);
    }

    /**
     * Begins the private UI pass, clearing only the private transparent color and stencil images.
     * The primary command buffer must be recording outside any render pass.
     *
     * @param command command buffer on the layer's graphics queue family
     * @throws NullPointerException if the command buffer is null
     * @throws IllegalStateException if the layer is closed, not resized, already recording, or allocation fails
     */
    public void beginFrame(VkCommandBuffer command) {
        ensureInactive();
        Objects.requireNonNull(command, "command");
        if (viewport == null) {
            throw new IllegalStateException("resize must be called before beginFrame.");
        }
        ensureTarget();
        ready = false;
        drawn = false;
        drawCalls = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkClearValue.Buffer clear = VkClearValue.calloc(2, stack);
            clear.get(1).depthStencil().depth(1).stencil(0);
            VkRenderPassBeginInfo begin = VkRenderPassBeginInfo.calloc(stack).sType$Default()
                    .renderPass(renderPass).framebuffer(framebuffer).pClearValues(clear);
            begin.renderArea().extent().set(width, height);
            VK10.vkCmdBeginRenderPass(command, begin, VK10.VK_SUBPASS_CONTENTS_INLINE);
        }
        recording = command;
        backend.beginFrame(command);
    }

    /**
     * Records one complete UI draw list into the active private pass.
     *
     * @param drawList UI commands for this frame
     * @throws NullPointerException if the draw list is null
     * @throws IllegalStateException if the layer is closed, no pass is active, or a list was already recorded
     */
    @Override
    public void draw(DrawList drawList) {
        ensureOpen();
        Objects.requireNonNull(drawList, "drawList");
        if (recording == null || drawn) {
            throw new IllegalStateException("Each UI pass accepts exactly one draw list.");
        }
        drawn = true;
        backend.draw(drawList);
        drawCalls = backend.drawCallCount();
    }

    /**
     * Ends the private pass and makes its color image available for sampling by composition.
     *
     * @throws IllegalStateException if the layer is closed or no UI pass is active
     */
    public void endFrame() {
        ensureOpen();
        if (recording == null) {
            throw new IllegalStateException("No UI pass is active.");
        }
        backend.endFrame();
        VK10.vkCmdEndRenderPass(recording);
        recording = null;
        ready = true;
    }

    /**
     * Blends the completed UI over the host color attachment without touching host depth/stencil.
     * Call inside the compatible host render pass, after the UI pass in submission order.
     * A different command buffer requires the host to provide the appropriate execution dependency.
     *
     * @param command recording command buffer with the host render pass active
     * @throws NullPointerException if the command is null
     * @throws IllegalStateException if the layer is closed, recording, or has no completed UI frame
     */
    public void composite(VkCommandBuffer command) {
        ensureInactive();
        Objects.requireNonNull(command, "command");
        if (!ready) {
            throw new IllegalStateException("A completed UI frame is required before composition.");
        }
        if (drawCalls > 0) {
            backend.composite(command, compositePipeline, sampledColor, viewport);
            drawCalls++;
        }
    }

    /**
     * Returns the UI batch count plus composition draws for the current frame.
     *
     * @return the submitted draw count
     */
    @Override
    public int drawCallCount() {
        return drawCalls;
    }

    /**
     * Uploads a straight-alpha texture owned by this layer.
     *
     * @param image source RGBA8 pixels
     * @return the uploaded texture
     * @throws NullPointerException if the source is null
     * @throws IllegalStateException if the layer is closed or uploading fails
     */
    @Override
    public Texture createTexture(ImageData image) {
        return createTexture(image, TextureOptions.DEFAULT);
    }

    /**
     * Uploads a straight-alpha texture owned by this layer with explicit sampling options.
     *
     * @param image source RGBA8 pixels
     * @param options filtering, wrapping and mipmap settings applied to the texture
     * @return the uploaded texture
     * @throws NullPointerException if an argument is null
     * @throws IllegalStateException if the layer is closed or uploading fails
     */
    @Override
    public Texture createTexture(ImageData image, TextureOptions options) {
        ensureOpen();
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(options, "options");
        return backend.createTexture(image, options);
    }

    /**
     * Releases a texture after the host has finished all submissions that use it.
     *
     * @param texture texture to release
     * @throws NullPointerException if the texture is null
     * @throws IllegalArgumentException if the texture belongs to another backend type
     * @throws IllegalStateException if the layer is closed
     */
    @Override
    public void destroyTexture(Texture texture) {
        ensureOpen();
        backend.destroyTexture(texture);
    }

    /**
     * Releases the layer resources after all using submissions have completed.
     * The device must still be alive. Repeated calls are harmless.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        destroyTarget();
        if (compositePipeline != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyPipeline(device, compositePipeline, null);
        }
        if (backend != null) {
            backend.close();
        }
        if (renderPass != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyRenderPass(device, renderPass, null);
        }
    }

    private long createRenderPass() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2, stack);
            attachments.get(0).format(VK10.VK_FORMAT_R8G8B8A8_UNORM).samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            attachments.get(1).format(stencilFormat).samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            VkAttachmentReference.Buffer colorReference = VkAttachmentReference.calloc(1, stack);
            colorReference.get(0).attachment(0).layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            VkAttachmentReference stencilReference = VkAttachmentReference.calloc(stack)
                    .attachment(1).layout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.get(0).pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1).pColorAttachments(colorReference)
                    .pDepthStencilAttachment(stencilReference);
            VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(2, stack);
            dependencies.get(0).srcSubpass(VK10.VK_SUBPASS_EXTERNAL).dstSubpass(0)
                    .srcStageMask(VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                            | VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                            | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
                    .srcAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                            | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
            dependencies.get(1).srcSubpass(0).dstSubpass(VK10.VK_SUBPASS_EXTERNAL)
                    .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .srcAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);
            VkRenderPassCreateInfo info = VkRenderPassCreateInfo.calloc(stack).sType$Default()
                    .pAttachments(attachments).pSubpasses(subpass).pDependencies(dependencies);
            LongBuffer result = stack.mallocLong(1);
            check(VK10.vkCreateRenderPass(device, info, null, result), "Create UI render pass");
            return result.get(0);
        }
    }

    private int chooseStencilFormat() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties properties = VkFormatProperties.malloc(stack);
            for (int format : new int[]{VK10.VK_FORMAT_D24_UNORM_S8_UINT, VK10.VK_FORMAT_D32_SFLOAT_S8_UINT}) {
                VK10.vkGetPhysicalDeviceFormatProperties(physicalDevice, format, properties);
                if ((properties.optimalTilingFeatures() & VK10.VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0) {
                    return format;
                }
            }
        }
        throw new IllegalStateException("No supported UI stencil attachment format.");
    }

    private void ensureTarget() {
        if (framebuffer != VK10.VK_NULL_HANDLE && width == viewport.framebufferWidth()
                && height == viewport.framebufferHeight()) {
            return;
        }
        destroyTarget();
        width = viewport.framebufferWidth();
        height = viewport.framebufferHeight();
        try {
            color = createAttachment(VK10.VK_FORMAT_R8G8B8A8_UNORM,
                    VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            stencil = createAttachment(stencilFormat, VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                    VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT);
            sampledColor = backend.wrapTexture(color.view(), width, height);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkFramebufferCreateInfo info = VkFramebufferCreateInfo.calloc(stack).sType$Default()
                        .renderPass(renderPass).pAttachments(stack.longs(color.view(), stencil.view()))
                        .width(width).height(height).layers(1);
                LongBuffer result = stack.mallocLong(1);
                check(VK10.vkCreateFramebuffer(device, info, null, result), "Create UI framebuffer");
                framebuffer = result.get(0);
            }
        } catch (RuntimeException | Error exception) {
            destroyTarget();
            throw exception;
        }
    }

    private Attachment createAttachment(int format, int usage, int aspect) {
        long image = VK10.VK_NULL_HANDLE;
        long memory = VK10.VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo info = VkImageCreateInfo.calloc(stack).sType$Default()
                    .imageType(VK10.VK_IMAGE_TYPE_2D).format(format).mipLevels(1).arrayLayers(1)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT).tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                    .usage(usage).sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            info.extent().set(width, height, 1);
            LongBuffer result = stack.mallocLong(1);
            check(VK10.vkCreateImage(device, info, null, result), "Create UI image");
            image = result.get(0);
            VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
            VK10.vkGetImageMemoryRequirements(device, image, requirements);
            VkMemoryAllocateInfo allocation = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(backend.findMemoryType(requirements.memoryTypeBits(),
                            VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
            check(VK10.vkAllocateMemory(device, allocation, null, result), "Allocate UI image memory");
            memory = result.get(0);
            check(VK10.vkBindImageMemory(device, image, memory, 0), "Bind UI image memory");
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack).sType$Default()
                    .image(image).viewType(VK10.VK_IMAGE_VIEW_TYPE_2D).format(format);
            viewInfo.subresourceRange().aspectMask(aspect).levelCount(1).layerCount(1);
            check(VK10.vkCreateImageView(device, viewInfo, null, result), "Create UI image view");
            return new Attachment(image, memory, result.get(0));
        } catch (RuntimeException | Error exception) {
            if (image != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyImage(device, image, null);
            }
            if (memory != VK10.VK_NULL_HANDLE) {
                VK10.vkFreeMemory(device, memory, null);
            }
            throw exception;
        }
    }

    private void destroyTarget() {
        ready = false;
        if (framebuffer != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyFramebuffer(device, framebuffer, null);
            framebuffer = VK10.VK_NULL_HANDLE;
        }
        if (sampledColor != null) {
            backend.destroyTexture(sampledColor);
            sampledColor = null;
        }
        destroyAttachment(color);
        destroyAttachment(stencil);
        color = null;
        stencil = null;
    }

    private void destroyAttachment(Attachment attachment) {
        if (attachment != null) {
            VK10.vkDestroyImageView(device, attachment.view(), null);
            VK10.vkDestroyImage(device, attachment.image(), null);
            VK10.vkFreeMemory(device, attachment.memory(), null);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("UI layer is closed.");
        }
    }

    private void ensureInactive() {
        ensureOpen();
        if (recording != null) {
            throw new IllegalStateException("A UI pass is already active.");
        }
    }

    private static void check(int result, String operation) {
        if (result != VK10.VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with Vulkan result " + result + ".");
        }
    }

    private record Attachment(long image, long memory, long view) {
    }
}
