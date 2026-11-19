package com.lixui.backend.vulkan;

import com.lixui.backend.CommandClassifier;
import com.lixui.backend.MaskAction;
import com.lixui.backend.ShaderPaint;
import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.DrawList;
import com.lixui.render.command.ImageCommand;
import com.lixui.render.ImageData;
import com.lixui.render.command.MaskBeginCommand;
import com.lixui.render.command.MaskEndCommand;
import com.lixui.render.command.MaskTextureBeginCommand;
import com.lixui.render.command.MaskTextureEndCommand;
import com.lixui.render.command.MeshCommand;
import com.lixui.render.MeshVertex;
import com.lixui.render.RenderBackend;
import com.lixui.render.command.ShapeCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.render.Texture;
import com.lixui.render.TextureFilter;
import com.lixui.render.TextureOptions;
import com.lixui.render.TextureWrap;
import com.lixui.render.Viewport;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A Vulkan render backend for rounded rectangles and textured quads.
 * The caller owns the render pass, framebuffers and command buffers: it must begin a render pass,
 * call {@link #beginFrame(VkCommandBuffer)}, {@link #draw(DrawList)} and {@link #endFrame()},
 * then end the render pass. Commands sharing a clip rectangle and texture are batched into one draw call.
 * The vertex buffer is host-visible and overwritten by each draw, so the caller must not record a
 * frame while a previous submission still uses the backend.
 * Use {@link VulkanLayer} for a private UI color/stencil pass followed by host-frame composition.
 * SPIR-V binaries are generated from the adjacent GLSL sources, for example:
 * {@code glslc --target-env=vulkan1.0 rounded_rect.vert -o rounded_rect.vert.spv}.
 */
public final class VulkanBackend implements RenderBackend {
    private static final Rect FULL_UV = new Rect(0, 0, 1, 1);
    private static final int FLOATS_PER_VERTEX = 13;
    private static final float MESH_HALF_SIZE = 1_000_000f;
    private static final int VERTEX_STRIDE = FLOATS_PER_VERTEX * Float.BYTES;
    private static final int INITIAL_VERTEX_BYTES = 4096;
    private static final int MAX_TEXTURES = 1024;
    private static final int PUSH_CONSTANT_FLOATS = 4 + ShaderPaint.FLOAT_COUNT;

    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private final VkQueue queue;
    private final List<Batch> batches = new ArrayList<>();
    private final List<VulkanTexture> textures = new ArrayList<>();
    private final Map<TextureOptions, Long> samplers = new LinkedHashMap<>();
    private long descriptorSetLayout;
    private long descriptorPool;
    private long pipelineLayout;
    private long pipeline;
    private long maskIncrementPipeline;
    private long maskDecrementPipeline;
    private long uploadCommandPool;
    private VulkanTexture whiteTexture;
    private long vertexBuffer;
    private long vertexMemory;
    private ByteBuffer mappedMemory;
    private FloatBuffer mappedVertices;
    private int vertexCapacity;
    private VkCommandBuffer commandBuffer;
    private Viewport viewport;
    private int drawCallCount;

    /**
     * Creates the backend, its graphics pipelines and the default texture sampler.
     * The render pass must provide a depth/stencil attachment for mask support.
     *
     * @param physicalDevice physical device used to select buffer and image memory
     * @param device logical device that owns the created resources
     * @param queue queue used for one-time texture uploads
     * @param queueFamily family index of {@code queue}
     * @param renderPass render pass the pipeline must be compatible with
     * @param sampleCount rasterization sample count of the render pass
     * @throws NullPointerException if a device reference is null
     * @throws IllegalArgumentException if the render pass handle is null or sample count is not positive
     * @throws IllegalStateException if resource creation fails
     */
    public VulkanBackend(VkPhysicalDevice physicalDevice, VkDevice device, VkQueue queue,
                         int queueFamily, long renderPass, int sampleCount) {
        this.physicalDevice = Objects.requireNonNull(physicalDevice, "physicalDevice");
        this.device = Objects.requireNonNull(device, "device");
        this.queue = Objects.requireNonNull(queue, "queue");
        if (renderPass == VK10.VK_NULL_HANDLE) {
            throw new IllegalArgumentException("Render pass must be a valid handle.");
        }
        if (sampleCount <= 0) {
            throw new IllegalArgumentException("Sample count must be positive.");
        }

        try {
            descriptorSetLayout = createDescriptorSetLayout();
            pipelineLayout = createPipelineLayout();
            pipeline = createPipeline(renderPass, sampleCount, MaskAction.DRAW);
            maskIncrementPipeline = createPipeline(renderPass, sampleCount, MaskAction.PUSH);
            maskDecrementPipeline = createPipeline(renderPass, sampleCount, MaskAction.POP);
            uploadCommandPool = createUploadCommandPool(queueFamily);
            descriptorPool = createDescriptorPool();
            whiteTexture = (VulkanTexture) createTexture(new ImageData(1, 1, new byte[] {
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255
            }));
        } catch (RuntimeException | Error exception) {
            close();
            throw exception;
        }
    }

    /**
     * Enables fragment-shader evaluation of shapes and gradients.
     *
     * @return true because analytic shapes are supported
     */
    @Override
    public boolean supportsAnalyticShapes() {
        return true;
    }

    /**
     * Stores the logical and physical viewport for the next draw.
     *
     * @param viewport target viewport
     * @throws NullPointerException if {@code viewport} is null
     */
    @Override
    public void resize(Viewport viewport) {
        this.viewport = Objects.requireNonNull(viewport, "viewport");
    }

    /**
     * Selects the command buffer that receives drawing commands.
     * The render pass must already be active and the command buffer must be recording.
     *
     * @param commandBuffer command buffer used by the next {@link #draw(DrawList)}
     * @throws NullPointerException if {@code commandBuffer} is null
     */
    public void beginFrame(VkCommandBuffer commandBuffer) {
        this.commandBuffer = Objects.requireNonNull(commandBuffer, "commandBuffer");
    }

    /**
     * Clears the command buffer selected by {@link #beginFrame(VkCommandBuffer)}.
     */
    public void endFrame() {
        commandBuffer = null;
    }

    /**
     * Creates a device-local texture from RGBA8 image data with default sampling options and uploads
     * it synchronously.
     *
     * @param image image data with a top-left origin
     * @return the created texture
     * @throws NullPointerException if {@code image} is null
     * @throws IllegalStateException if the texture or its descriptor cannot be created
     */
    @Override
    public Texture createTexture(ImageData image) {
        return createTexture(image, TextureOptions.DEFAULT);
    }

    /**
     * Creates a device-local texture from RGBA8 image data with explicit sampling options and uploads
     * it synchronously. A mipmap chain is generated on the GPU when requested.
     *
     * @param image image data with a top-left origin
     * @param options filtering, wrapping and mipmap settings applied to the texture
     * @return the created texture
     * @throws NullPointerException if an argument is null
     * @throws IllegalStateException if the texture or its descriptor cannot be created
     */
    @Override
    public Texture createTexture(ImageData image, TextureOptions options) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(options, "options");
        int width = image.width();
        int height = image.height();
        int mipLevels = options.mipmaps() ? mipLevelCount(width, height) : 1;
        int imageUsage = VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
        if (mipLevels > 1) {
            imageUsage |= VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
        }
        long imageSize = (long) width * height * 4;

        BufferResource staging = createBuffer(imageSize,
                VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        long imageHandle = VK10.VK_NULL_HANDLE;
        long imageMemory = VK10.VK_NULL_HANDLE;
        long imageView = VK10.VK_NULL_HANDLE;
        long descriptorSet = VK10.VK_NULL_HANDLE;
        try {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer mapped = stack.mallocPointer(1);
                check(VK10.vkMapMemory(device, staging.memory(), 0, imageSize, 0, mapped), "Map staging buffer");
                ByteBuffer data = MemoryUtil.memByteBuffer(mapped.get(0), (int) imageSize);
                data.put(image.pixels());
                VK10.vkUnmapMemory(device, staging.memory());

                LongBuffer result = stack.mallocLong(1);
                VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                        .sType$Default()
                        .imageType(VK10.VK_IMAGE_TYPE_2D)
                        .format(VK10.VK_FORMAT_R8G8B8A8_UNORM)
                        .mipLevels(mipLevels)
                        .arrayLayers(1)
                        .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                        .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                        .usage(imageUsage)
                        .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                        .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
                imageInfo.extent().set(width, height, 1);
                check(VK10.vkCreateImage(device, imageInfo, null, result), "Create texture image");
                imageHandle = result.get(0);

                VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
                VK10.vkGetImageMemoryRequirements(device, imageHandle, requirements);
                VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack)
                        .sType$Default()
                        .allocationSize(requirements.size())
                        .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(),
                                VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
                check(VK10.vkAllocateMemory(device, allocateInfo, null, result), "Allocate texture memory");
                imageMemory = result.get(0);
                check(VK10.vkBindImageMemory(device, imageHandle, imageMemory, 0), "Bind texture memory");

                VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType$Default()
                        .image(imageHandle)
                        .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                        .format(VK10.VK_FORMAT_R8G8B8A8_UNORM);
                viewInfo.subresourceRange()
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .levelCount(mipLevels)
                        .layerCount(1);
                check(VK10.vkCreateImageView(device, viewInfo, null, result), "Create texture view");
                imageView = result.get(0);

                VkCommandBuffer upload = beginOneTime();
                transitionImage(upload, imageHandle,
                        VK10.VK_IMAGE_LAYOUT_UNDEFINED, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        0, VK10.VK_ACCESS_TRANSFER_WRITE_BIT,
                        VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0, mipLevels);

                VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
                region.get(0)
                        .bufferOffset(0)
                        .bufferRowLength(0)
                        .bufferImageHeight(0);
                region.get(0).imageSubresource()
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(0)
                        .baseArrayLayer(0)
                        .layerCount(1);
                region.get(0).imageOffset().set(0, 0, 0);
                region.get(0).imageExtent().set(width, height, 1);
                VK10.vkCmdCopyBufferToImage(upload, staging.buffer(), imageHandle,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

                if (mipLevels > 1) {
                    generateMipmaps(upload, imageHandle, width, height, mipLevels);
                } else {
                    transitionImage(upload, imageHandle,
                            VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                            VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                            VK10.VK_ACCESS_TRANSFER_WRITE_BIT, VK10.VK_ACCESS_SHADER_READ_BIT,
                            VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
                }
                endOneTime(upload);

                descriptorSet = allocateDescriptorSet();
                updateDescriptorSet(descriptorSet, imageView, sampler(options),
                        VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }
        } catch (RuntimeException | Error exception) {
            if (descriptorSet != VK10.VK_NULL_HANDLE) {
                freeDescriptorSet(descriptorSet);
            }
            destroyTextureResources(imageHandle, imageView, imageMemory);
            destroyBuffer(staging);
            throw exception;
        }

        destroyBuffer(staging);
        VulkanTexture texture = new VulkanTexture(imageHandle, imageView, imageMemory,
                descriptorSet, width, height, true, options);
        textures.add(texture);
        return texture;
    }

    /**
     * Wraps an existing Vulkan image view owned by the host application using the backend sampler.
     * The image must stay alive and remain in the supplied layout while it is used. The backend
     * never deletes the host image view or sampler.
     *
     * @param imageView host image view in {@code VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL}
     * @param width texture width in pixels
     * @param height texture height in pixels
     * @return a non-owning texture handle
     * @throws IllegalArgumentException if the image view or a dimension is not positive
     */
    public Texture wrapTexture(long imageView, int width, int height) {
        return wrapTexture(imageView, sampler(TextureOptions.DEFAULT),
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, width, height);
    }

    /**
     * Wraps an existing Vulkan image view and sampler owned by the host application.
     * The image must stay alive and remain in {@code imageLayout} while it is used. The backend
     * never deletes the host image view or sampler.
     *
     * @param imageView host image view
     * @param imageSampler sampler used to sample the image
     * @param imageLayout current layout of the image
     * @param width texture width in pixels
     * @param height texture height in pixels
     * @return a non-owning texture handle
     * @throws IllegalArgumentException if a handle or a dimension is not positive
     */
    public Texture wrapTexture(long imageView, long imageSampler, int imageLayout, int width, int height) {
        return wrapTexture(imageView, imageSampler, imageLayout, width, height, TextureOptions.DEFAULT);
    }

    /**
     * Wraps an existing Vulkan image view and sampler and records the sampling options associated
     * with them. The backend never deletes the host image view or sampler.
     *
     * @param imageView host image view
     * @param imageSampler sampler used to sample the image
     * @param imageLayout current layout of the image
     * @param width texture width in pixels
     * @param height texture height in pixels
     * @param options sampling options that describe the host sampler
     * @return a non-owning texture handle
     * @throws NullPointerException if {@code options} is null
     * @throws IllegalArgumentException if a handle or a dimension is not positive
     */
    public Texture wrapTexture(long imageView, long imageSampler, int imageLayout, int width, int height,
                               TextureOptions options) {
        if (imageView == VK10.VK_NULL_HANDLE || imageSampler == VK10.VK_NULL_HANDLE) {
            throw new IllegalArgumentException("Image view and sampler must be valid handles.");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Texture dimensions must be positive.");
        }
        Objects.requireNonNull(options, "options");
        long descriptorSet = allocateDescriptorSet();
        updateDescriptorSet(descriptorSet, imageView, imageSampler, imageLayout);
        VulkanTexture texture = new VulkanTexture(VK10.VK_NULL_HANDLE, imageView, VK10.VK_NULL_HANDLE,
                descriptorSet, width, height, false, options);
        textures.add(texture);
        return texture;
    }

    /**
     * Releases a texture created by this backend. Wrapped host textures only release their descriptor set.
     *
     * @param texture texture to release
     * @throws NullPointerException if {@code texture} is null
     * @throws IllegalArgumentException if the texture belongs to another backend
     */
    @Override
    public void destroyTexture(Texture texture) {
        VulkanTexture vulkanTexture = asVulkanTexture(texture);
        if (!textures.remove(vulkanTexture)) {
            return;
        }
        if (whiteTexture == vulkanTexture) {
            whiteTexture = null;
        }
        freeDescriptorSet(vulkanTexture.descriptorSet());
        if (vulkanTexture.owned()) {
            destroyTextureResources(vulkanTexture.image(), vulkanTexture.imageView(), vulkanTexture.memory());
        }
    }

    /**
     * Records the draw list into the command buffer selected by {@link #beginFrame(VkCommandBuffer)}.
     *
     * @param drawList commands to render
     * @throws NullPointerException if {@code drawList} is null
     * @throws IllegalStateException if {@link #beginFrame(VkCommandBuffer)} or {@link #resize(Viewport)}
     *         has not been called
     */
    @Override
    public void draw(DrawList drawList) {
        Objects.requireNonNull(drawList, "drawList");
        if (commandBuffer == null) {
            throw new IllegalStateException("beginFrame must be called before draw.");
        }
        if (viewport == null) {
            throw new IllegalStateException("resize must be called before draw.");
        }

        drawCallCount = 0;
        List<DrawCommand> commands = drawList.commands();
        if (commands.isEmpty()) {
            return;
        }

        ensureVertexCapacity(totalVertexFloats(commands));
        mappedVertices.clear();
        collect(commands);
        if (batches.isEmpty()) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Queue submission makes coherent host writes available; HOST barriers are invalid inside this pass.
            VkViewport.Buffer commandViewport = VkViewport.calloc(1, stack);
            commandViewport.get(0)
                    .x(0)
                    .y(0)
                    .width(viewport.framebufferWidth())
                    .height(viewport.framebufferHeight())
                    .minDepth(0)
                    .maxDepth(1);
            VK10.vkCmdSetViewport(commandBuffer, 0, commandViewport);

            FloatBuffer constants = stack.mallocFloat(PUSH_CONSTANT_FLOATS);

            LongBuffer buffers = stack.longs(vertexBuffer);
            LongBuffer offsets = stack.longs(0);
            VK10.vkCmdBindVertexBuffers(commandBuffer, 0, buffers, offsets);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            LongBuffer descriptorSet = stack.mallocLong(1);
            Paint previousPaint = null;
            boolean paintSet = false;
            for (Batch batch : batches) {
                if (!applyScissor(scissor.get(0), batch.clip())) {
                    continue;
                }
                if (!paintSet || !Objects.equals(previousPaint, batch.paint())) {
                    constants.clear();
                    constants.put(viewport.logicalWidth()).put(viewport.logicalHeight()).put(0).put(0);
                    ShaderPaint.write(batch.paint(), constants);
                    constants.flip();
                    VK10.vkCmdPushConstants(commandBuffer, pipelineLayout,
                            VK10.VK_SHADER_STAGE_VERTEX_BIT | VK10.VK_SHADER_STAGE_FRAGMENT_BIT, 0, constants);
                    previousPaint = batch.paint();
                    paintSet = true;
                }
                long batchPipeline = switch (batch.action()) {
                    case DRAW -> pipeline;
                    case PUSH -> maskIncrementPipeline;
                    case POP -> maskDecrementPipeline;
                };
                VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, batchPipeline);
                VK10.vkCmdSetStencilCompareMask(commandBuffer,
                        VK10.VK_STENCIL_FACE_FRONT_AND_BACK, 0xFF);
                VK10.vkCmdSetStencilWriteMask(commandBuffer,
                        VK10.VK_STENCIL_FACE_FRONT_AND_BACK, 0xFF);
                VK10.vkCmdSetStencilReference(commandBuffer,
                        VK10.VK_STENCIL_FACE_FRONT_AND_BACK, batch.stencilRef());
                descriptorSet.put(0, batch.texture().descriptorSet());
                VK10.vkCmdBindDescriptorSets(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipelineLayout, 0, descriptorSet, null);
                VK10.vkCmdSetScissor(commandBuffer, 0, scissor);
                VK10.vkCmdDraw(commandBuffer, batch.vertexCount(), 1, batch.firstVertex(), 0);
                drawCallCount++;
            }
        }
    }

    /**
     * Returns the number of draw calls recorded by the most recent {@link #draw(DrawList)}.
     *
     * @return the draw call count of the last frame
     */
    @Override
    public int drawCallCount() {
        return drawCallCount;
    }

    /**
     * Releases owned textures, the descriptor pool, sampler, pipeline and vertex buffer.
     * The logical device must be idle and still alive when this method is called.
     */
    @Override
    public void close() {
        commandBuffer = null;
        viewport = null;
        batches.clear();
        List<VulkanTexture> ownedTextures = new ArrayList<>(textures);
        textures.clear();
        for (VulkanTexture texture : ownedTextures) {
            freeDescriptorSet(texture.descriptorSet());
            if (texture.owned()) {
                destroyTextureResources(texture.image(), texture.imageView(), texture.memory());
            }
        }
        whiteTexture = null;
        destroyVertexBuffer();
        for (long cachedSampler : samplers.values()) {
            VK10.vkDestroySampler(device, cachedSampler, null);
        }
        samplers.clear();
        if (descriptorPool != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyDescriptorPool(device, descriptorPool, null);
            descriptorPool = VK10.VK_NULL_HANDLE;
        }
        if (uploadCommandPool != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyCommandPool(device, uploadCommandPool, null);
            uploadCommandPool = VK10.VK_NULL_HANDLE;
        }
        if (pipeline != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyPipeline(device, pipeline, null);
            pipeline = VK10.VK_NULL_HANDLE;
        }
        if (maskIncrementPipeline != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyPipeline(device, maskIncrementPipeline, null);
            maskIncrementPipeline = VK10.VK_NULL_HANDLE;
        }
        if (maskDecrementPipeline != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyPipeline(device, maskDecrementPipeline, null);
            maskDecrementPipeline = VK10.VK_NULL_HANDLE;
        }
        if (pipelineLayout != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyPipelineLayout(device, pipelineLayout, null);
            pipelineLayout = VK10.VK_NULL_HANDLE;
        }
        if (descriptorSetLayout != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
            descriptorSetLayout = VK10.VK_NULL_HANDLE;
        }
    }

    private long createDescriptorSetLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer binding = VkDescriptorSetLayoutBinding.calloc(1, stack);
            binding.get(0)
                    .binding(0)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
            VkDescriptorSetLayoutCreateInfo info = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pBindings(binding);
            LongBuffer result = stack.mallocLong(1);
            check(VK10.vkCreateDescriptorSetLayout(device, info, null, result),
                    "Create descriptor set layout");
            return result.get(0);
        }
    }

    private long createPipelineLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT | VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0)
                    .size(PUSH_CONSTANT_FLOATS * Float.BYTES);
            VkPipelineLayoutCreateInfo info = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(stack.longs(descriptorSetLayout))
                    .pPushConstantRanges(range);
            LongBuffer result = stack.mallocLong(1);
            check(VK10.vkCreatePipelineLayout(device, info, null, result), "Create pipeline layout");
            return result.get(0);
        }
    }

    private long createPipeline(long renderPass, int sampleCount, MaskAction action) {
        return createPipeline(renderPass, sampleCount, action, false);
    }

    long createCompositePipeline(long renderPass, int sampleCount) {
        return createPipeline(renderPass, sampleCount, MaskAction.DRAW, true);
    }

    void composite(VkCommandBuffer command, long compositePipeline, Texture texture, Viewport target) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkViewport.Buffer commandViewport = VkViewport.calloc(1, stack);
            commandViewport.get(0).width(target.framebufferWidth()).height(target.framebufferHeight())
                    .minDepth(0).maxDepth(1);
            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.get(0).extent().set(target.framebufferWidth(), target.framebufferHeight());
            VK10.vkCmdSetViewport(command, 0, commandViewport);
            VK10.vkCmdSetScissor(command, 0, scissor);
            VK10.vkCmdBindPipeline(command, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, compositePipeline);
            VK10.vkCmdBindDescriptorSets(command, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout,
                    0, stack.longs(asVulkanTexture(texture).descriptorSet()), null);
            VK10.vkCmdDraw(command, 3, 1, 0, 0);
        }
    }

    private long createPipeline(long renderPass, int sampleCount, MaskAction action, boolean composite) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String shader = composite ? "layer" : "rounded_rect";
            long vertexModule = createShaderModule(loadShader(shader + ".vert.spv", stack));
            long fragmentModule;
            try {
                fragmentModule = createShaderModule(loadShader(shader + ".frag.spv", stack));
            } catch (RuntimeException | Error exception) {
                VK10.vkDestroyShaderModule(device, vertexModule, null);
                throw exception;
            }

            try {
                VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
                stages.get(0)
                        .sType$Default()
                        .stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                        .module(vertexModule)
                        .pName(stack.UTF8("main"));
                stages.get(1)
                        .sType$Default()
                        .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                        .module(fragmentModule)
                        .pName(stack.UTF8("main"));

                VkVertexInputBindingDescription.Buffer binding = VkVertexInputBindingDescription.calloc(1, stack);
                binding.get(0)
                        .binding(0)
                        .stride(VERTEX_STRIDE)
                        .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);

                VkVertexInputAttributeDescription.Buffer attributes =
                        VkVertexInputAttributeDescription.calloc(6, stack);
                attributes.get(0)
                        .location(0)
                        .binding(0)
                        .format(VK10.VK_FORMAT_R32G32_SFLOAT)
                        .offset(0);
                attributes.get(1)
                        .location(1)
                        .binding(0)
                        .format(VK10.VK_FORMAT_R32G32_SFLOAT)
                        .offset(2 * Float.BYTES);
                attributes.get(2)
                        .location(2)
                        .binding(0)
                        .format(VK10.VK_FORMAT_R32G32_SFLOAT)
                        .offset(4 * Float.BYTES);
                attributes.get(3)
                        .location(3)
                        .binding(0)
                        .format(VK10.VK_FORMAT_R32_SFLOAT)
                        .offset(6 * Float.BYTES);
                attributes.get(4)
                        .location(4)
                        .binding(0)
                        .format(VK10.VK_FORMAT_R32G32B32A32_SFLOAT)
                        .offset(7 * Float.BYTES);
                attributes.get(5)
                        .location(5)
                        .binding(0)
                        .format(VK10.VK_FORMAT_R32G32_SFLOAT)
                        .offset(11 * Float.BYTES);

                VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .pVertexBindingDescriptions(composite ? null : binding)
                        .pVertexAttributeDescriptions(composite ? null : attributes);

                VkPipelineInputAssemblyStateCreateInfo inputAssembly =
                        VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                                .primitiveRestartEnable(false);

                VkPipelineViewportStateCreateInfo viewportState =
                        VkPipelineViewportStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .viewportCount(1)
                                .scissorCount(1);

                VkPipelineRasterizationStateCreateInfo rasterization =
                        VkPipelineRasterizationStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .depthClampEnable(false)
                                .rasterizerDiscardEnable(false)
                                .polygonMode(VK10.VK_POLYGON_MODE_FILL)
                                .cullMode(VK10.VK_CULL_MODE_NONE)
                                .frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE)
                                .depthBiasEnable(false)
                                .lineWidth(1.0f);

                VkPipelineMultisampleStateCreateInfo multisample =
                        VkPipelineMultisampleStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .rasterizationSamples(sampleCount)
                                .sampleShadingEnable(false);

                VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                        VkPipelineColorBlendAttachmentState.calloc(1, stack);
                int colorWriteMask = action == MaskAction.DRAW
                        ? VK10.VK_COLOR_COMPONENT_R_BIT | VK10.VK_COLOR_COMPONENT_G_BIT
                        | VK10.VK_COLOR_COMPONENT_B_BIT | VK10.VK_COLOR_COMPONENT_A_BIT
                        : 0;
                blendAttachment.get(0)
                        .blendEnable(action == MaskAction.DRAW)
                        .srcColorBlendFactor(VK10.VK_BLEND_FACTOR_ONE)
                        .dstColorBlendFactor(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .colorBlendOp(VK10.VK_BLEND_OP_ADD)
                        .srcAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .alphaBlendOp(VK10.VK_BLEND_OP_ADD)
                        .colorWriteMask(colorWriteMask);

                VkPipelineColorBlendStateCreateInfo colorBlend =
                        VkPipelineColorBlendStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .logicOpEnable(false)
                                .attachmentCount(1)
                                .pAttachments(blendAttachment);

                int stencilPass = switch (action) {
                    case DRAW -> VK10.VK_STENCIL_OP_KEEP;
                    case PUSH -> VK10.VK_STENCIL_OP_INCREMENT_AND_CLAMP;
                    case POP -> VK10.VK_STENCIL_OP_DECREMENT_AND_CLAMP;
                    default -> throw new IllegalStateException("Unknown mask action.");
                };
                VkPipelineDepthStencilStateCreateInfo depthStencil =
                        VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .depthTestEnable(false)
                                .depthWriteEnable(false)
                                .depthCompareOp(VK10.VK_COMPARE_OP_ALWAYS)
                                .depthBoundsTestEnable(false)
                                .stencilTestEnable(!composite);
                depthStencil.front()
                        .failOp(VK10.VK_STENCIL_OP_KEEP)
                        .passOp(stencilPass)
                        .depthFailOp(VK10.VK_STENCIL_OP_KEEP)
                        .compareOp(VK10.VK_COMPARE_OP_EQUAL)
                        .compareMask(0xFF)
                        .writeMask(0xFF)
                        .reference(0);
                depthStencil.back()
                        .failOp(VK10.VK_STENCIL_OP_KEEP)
                        .passOp(stencilPass)
                        .depthFailOp(VK10.VK_STENCIL_OP_KEEP)
                        .compareOp(VK10.VK_COMPARE_OP_EQUAL)
                        .compareMask(0xFF)
                        .writeMask(0xFF)
                        .reference(0);

                IntBuffer dynamicStates = composite
                        ? stack.ints(VK10.VK_DYNAMIC_STATE_VIEWPORT, VK10.VK_DYNAMIC_STATE_SCISSOR)
                        : stack.ints(
                        VK10.VK_DYNAMIC_STATE_VIEWPORT,
                        VK10.VK_DYNAMIC_STATE_SCISSOR,
                        VK10.VK_DYNAMIC_STATE_STENCIL_COMPARE_MASK,
                        VK10.VK_DYNAMIC_STATE_STENCIL_WRITE_MASK,
                        VK10.VK_DYNAMIC_STATE_STENCIL_REFERENCE);
                VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .pDynamicStates(dynamicStates);

                VkGraphicsPipelineCreateInfo.Buffer info = VkGraphicsPipelineCreateInfo.calloc(1, stack);
                info.get(0)
                        .sType$Default()
                        .pStages(stages)
                        .pVertexInputState(vertexInput)
                        .pInputAssemblyState(inputAssembly)
                        .pViewportState(viewportState)
                        .pRasterizationState(rasterization)
                        .pMultisampleState(multisample)
                        .pColorBlendState(colorBlend)
                        .pDepthStencilState(depthStencil)
                        .pDynamicState(dynamicState)
                        .layout(pipelineLayout)
                        .renderPass(renderPass)
                        .subpass(0)
                        .basePipelineHandle(VK10.VK_NULL_HANDLE)
                        .basePipelineIndex(-1);

                LongBuffer result = stack.mallocLong(1);
                check(VK10.vkCreateGraphicsPipelines(device, VK10.VK_NULL_HANDLE, info, null, result),
                        "Create graphics pipeline");
                return result.get(0);
            } finally {
                VK10.vkDestroyShaderModule(device, vertexModule, null);
                VK10.vkDestroyShaderModule(device, fragmentModule, null);
            }
        }
    }

    private long createUploadCommandPool(int queueFamily) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo info = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .queueFamilyIndex(queueFamily)
                    .flags(VK10.VK_COMMAND_POOL_CREATE_TRANSIENT_BIT);
            LongBuffer result = stack.mallocLong(1);
            check(VK10.vkCreateCommandPool(device, info, null, result), "Create upload command pool");
            return result.get(0);
        }
    }

    private long createDescriptorPool() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(1, stack);
            sizes.get(0)
                    .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(MAX_TEXTURES);
            VkDescriptorPoolCreateInfo info = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .maxSets(MAX_TEXTURES)
                    .pPoolSizes(sizes);
            LongBuffer result = stack.mallocLong(1);
            check(VK10.vkCreateDescriptorPool(device, info, null, result), "Create descriptor pool");
            return result.get(0);
        }
    }

    private long sampler(TextureOptions options) {
        Long cached = samplers.get(options);
        if (cached != null) {
            return cached;
        }
        long created = createSampler(options);
        samplers.put(options, created);
        return created;
    }

    private long createSampler(TextureOptions options) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo info = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(filter(options.filter()))
                    .minFilter(filter(options.filter()))
                    .mipmapMode(options.mipmaps() && options.filter() == TextureFilter.LINEAR
                            ? VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR
                            : VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(addressMode(options.wrapU()))
                    .addressModeV(addressMode(options.wrapV()))
                    .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .mipLodBias(0)
                    .anisotropyEnable(false)
                    .maxAnisotropy(1)
                    .compareEnable(false)
                    .compareOp(VK10.VK_COMPARE_OP_ALWAYS)
                    .minLod(0)
                    .maxLod(options.mipmaps() ? 1000.0f : 0)
                    .borderColor(VK10.VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK)
                    .unnormalizedCoordinates(false);
            LongBuffer result = stack.mallocLong(1);
            check(VK10.vkCreateSampler(device, info, null, result), "Create texture sampler");
            return result.get(0);
        }
    }

    private static int filter(TextureFilter filter) {
        return filter == TextureFilter.NEAREST ? VK10.VK_FILTER_NEAREST : VK10.VK_FILTER_LINEAR;
    }

    private static int addressMode(TextureWrap wrap) {
        return switch (wrap) {
            case CLAMP -> VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            case REPEAT -> VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT;
            case MIRROR -> VK10.VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
        };
    }

    private long createShaderModule(ByteBuffer code) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(code);
            LongBuffer result = stack.mallocLong(1);
            check(VK10.vkCreateShaderModule(device, info, null, result), "Create shader module");
            return result.get(0);
        }
    }

    private static ByteBuffer loadShader(String name, MemoryStack stack) {
        try (InputStream input = VulkanBackend.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing shader resource: " + name);
            }
            byte[] bytes = input.readAllBytes();
            ByteBuffer buffer = stack.malloc(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load shader resource: " + name, exception);
        }
    }

    private VkCommandBuffer beginOneTime() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(uploadCommandPool)
                    .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            PointerBuffer result = stack.mallocPointer(1);
            check(VK10.vkAllocateCommandBuffers(device, allocate, result), "Allocate upload commands");
            VkCommandBuffer upload = new VkCommandBuffer(result.get(0), device);
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack).sType$Default();
            check(VK10.vkBeginCommandBuffer(upload, begin), "Begin upload commands");
            return upload;
        }
    }

    private void endOneTime(VkCommandBuffer upload) {
        check(VK10.vkEndCommandBuffer(upload), "End upload commands");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pCommandBuffers(stack.pointers(upload.address()));
            check(VK10.vkQueueSubmit(queue, submit, VK10.VK_NULL_HANDLE), "Submit upload");
            check(VK10.vkQueueWaitIdle(queue), "Wait for upload");
            VK10.vkFreeCommandBuffers(device, uploadCommandPool, upload);
        }
    }

    private void generateMipmaps(VkCommandBuffer command, long image, int width, int height, int mipLevels) {
        for (int level = 1; level < mipLevels; level++) {
            if (level == 1) {
                transitionImage(command, image,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        VK10.VK_ACCESS_TRANSFER_WRITE_BIT, VK10.VK_ACCESS_TRANSFER_READ_BIT,
                        VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                        level - 1, 1);
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
                blit.get(0).srcSubresource()
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(level - 1)
                        .baseArrayLayer(0)
                        .layerCount(1);
                blit.get(0).dstSubresource()
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(level)
                        .baseArrayLayer(0)
                        .layerCount(1);
                blit.get(0).srcOffsets(0).set(0, 0, 0);
                blit.get(0).srcOffsets(1).set(
                        Math.max(1, width >> (level - 1)), Math.max(1, height >> (level - 1)), 1);
                blit.get(0).dstOffsets(0).set(0, 0, 0);
                blit.get(0).dstOffsets(1).set(
                        Math.max(1, width >> level), Math.max(1, height >> level), 1);
                VK10.vkCmdBlitImage(command, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, blit, VK10.VK_FILTER_LINEAR);
            }
            transitionImage(command, image,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    VK10.VK_ACCESS_TRANSFER_WRITE_BIT, VK10.VK_ACCESS_TRANSFER_READ_BIT,
                    VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    level, 1);
        }
        transitionImage(command, image,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK10.VK_ACCESS_TRANSFER_READ_BIT, VK10.VK_ACCESS_SHADER_READ_BIT,
                VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                0, mipLevels);
    }

    private static int mipLevelCount(int width, int height) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(Math.max(width, height));
    }

    private void transitionImage(VkCommandBuffer command, long image,
                                 int oldLayout, int newLayout, int srcAccess, int dstAccess,
                                 int srcStage, int dstStage) {
        transitionImage(command, image, oldLayout, newLayout, srcAccess, dstAccess, srcStage, dstStage, 0, 1);
    }

    private void transitionImage(VkCommandBuffer command, long image,
                                 int oldLayout, int newLayout, int srcAccess, int dstAccess,
                                 int srcStage, int dstStage, int baseMipLevel, int levelCount) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0)
                    .sType$Default()
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcAccessMask(srcAccess)
                    .dstAccessMask(dstAccess)
                    .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange()
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(baseMipLevel)
                    .levelCount(levelCount)
                    .layerCount(1);
            VK10.vkCmdPipelineBarrier(command, srcStage, dstStage, 0, null, null, barrier);
        }
    }

    private long allocateDescriptorSet() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetAllocateInfo info = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(stack.longs(descriptorSetLayout));
            LongBuffer result = stack.mallocLong(1);
            check(VK10.vkAllocateDescriptorSets(device, info, result), "Allocate descriptor set");
            return result.get(0);
        }
    }

    private void updateDescriptorSet(long descriptorSet, long imageView, long imageSampler, int imageLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
            imageInfo.get(0)
                    .imageView(imageView)
                    .imageLayout(imageLayout)
                    .sampler(imageSampler);
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
            write.get(0)
                    .sType$Default()
                    .dstSet(descriptorSet)
                    .dstBinding(0)
                    .descriptorCount(1)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(imageInfo);
            VK10.vkUpdateDescriptorSets(device, write, null);
        }
    }

    private void freeDescriptorSet(long descriptorSet) {
        if (descriptorSet != VK10.VK_NULL_HANDLE && descriptorPool != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeDescriptorSets(device, descriptorPool, descriptorSet);
        }
    }

    private BufferResource createBuffer(long size, int usage, int properties) {
        long buffer = VK10.VK_NULL_HANDLE;
        long memory = VK10.VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo info = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer result = stack.mallocLong(1);
            check(VK10.vkCreateBuffer(device, info, null, result), "Create buffer");
            buffer = result.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
            VK10.vkGetBufferMemoryRequirements(device, buffer, requirements);
            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(), properties));
            check(VK10.vkAllocateMemory(device, allocateInfo, null, result), "Allocate buffer memory");
            memory = result.get(0);
            check(VK10.vkBindBufferMemory(device, buffer, memory, 0), "Bind buffer memory");
            return new BufferResource(buffer, memory);
        } catch (RuntimeException | Error exception) {
            if (buffer != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyBuffer(device, buffer, null);
            }
            if (memory != VK10.VK_NULL_HANDLE) {
                VK10.vkFreeMemory(device, memory, null);
            }
            throw exception;
        }
    }

    private void destroyBuffer(BufferResource buffer) {
        if (buffer.buffer() != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyBuffer(device, buffer.buffer(), null);
        }
        if (buffer.memory() != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeMemory(device, buffer.memory(), null);
        }
    }

    private void destroyTextureResources(long image, long imageView, long memory) {
        if (imageView != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImageView(device, imageView, null);
        }
        if (image != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device, image, null);
        }
        if (memory != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeMemory(device, memory, null);
        }
    }

    int findMemoryType(int typeFilter, int properties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties memoryProperties =
                    VkPhysicalDeviceMemoryProperties.malloc(stack);
            VK10.vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
            for (int index = 0; index < memoryProperties.memoryTypeCount(); index++) {
                if ((typeFilter & (1 << index)) != 0
                        && (memoryProperties.memoryTypes(index).propertyFlags() & properties) == properties) {
                    return index;
                }
            }
        }
        throw new IllegalStateException("No suitable Vulkan memory type.");
    }

    private void collect(List<DrawCommand> commands) {
        batches.clear();
        Rect currentClip = null;
        VulkanTexture currentTexture = null;
        Paint currentPaint = null;
        int currentStencilRef = 0;
        MaskAction currentAction = MaskAction.DRAW;
        int stencilDepth = 0;
        int batchStartVertex = 0;
        int batchVertexCount = 0;
        int vertexIndex = 0;
        for (DrawCommand command : commands) {
            MaskAction action = CommandClassifier.maskAction(command);
            int stencilRef = stencilDepth;
            VulkanTexture texture = asVulkanTexture(CommandClassifier.textureOf(command, whiteTexture));
            Paint paint = ShaderPaint.gradient(command);
            int commandVertices = CommandClassifier.vertexCount(command);
            if (!command.clip().equals(currentClip) || texture != currentTexture
                    || stencilRef != currentStencilRef || action != currentAction
                    || !Objects.equals(paint, currentPaint)) {
                if (batchVertexCount > 0) {
                    batches.add(new Batch(batchStartVertex, batchVertexCount,
                            currentClip, currentTexture, currentStencilRef, currentAction, currentPaint));
                }
                currentClip = command.clip();
                currentTexture = texture;
                currentPaint = paint;
                currentStencilRef = stencilRef;
                currentAction = action;
                batchStartVertex = vertexIndex;
                batchVertexCount = 0;
            }
            if (command instanceof MaskBeginCommand mask) {
                appendMask(mask.vertices());
            } else if (command instanceof MaskEndCommand mask) {
                appendMask(mask.vertices());
            } else if (command instanceof MaskTextureBeginCommand mask) {
                appendTexturedQuad(mask.bounds(), mask.uv(), mask.transform());
            } else if (command instanceof MaskTextureEndCommand mask) {
                appendTexturedQuad(mask.bounds(), mask.uv(), mask.transform());
            } else if (command instanceof TextCommand text) {
                appendText(text);
            } else if (command instanceof MeshCommand mesh) {
                appendMesh(mesh);
            } else {
                appendQuad(command);
            }
            vertexIndex += commandVertices;
            batchVertexCount += commandVertices;
            if (action == MaskAction.PUSH) {
                stencilDepth++;
            } else if (action == MaskAction.POP) {
                stencilDepth--;
            }
        }
        if (batchVertexCount > 0) {
            batches.add(new Batch(batchStartVertex, batchVertexCount,
                    currentClip, currentTexture, currentStencilRef, currentAction, currentPaint));
        }
    }



    private void appendQuad(DrawCommand command) {
        if (command instanceof ImageCommand image) {
            appendQuad(image.bounds(), image.radius(), image.color(), image.uv(), image.transform());
        } else if (command instanceof ShapeCommand shape) {
            appendQuad(shape.bounds(), shape.ellipse() ? -1 : shape.radius(), shape.color(),
                    shape.bounds(), shape.transform(), true);
        } else {
            appendQuad(command.bounds(), command.radius(), command.color(), FULL_UV, Transform.IDENTITY, true);
        }
    }

    private void appendQuad(Rect bounds, float radius, Color color, Rect uv, Transform transform) {
        appendQuad(bounds, radius, color, uv, transform, false);
    }

    private void appendQuad(Rect bounds, float radius, Color color, Rect uv, Transform transform, boolean feather) {
        float halfWidth = bounds.width() / 2;
        float halfHeight = bounds.height() / 2;
        float padX = 0;
        float padY = 0;
        float determinant = Math.abs(transform.m00() * transform.m11() - transform.m01() * transform.m10());
        if (feather && determinant > 0) {
            // Reserve a physical pixel outside the contour so the shader can cover both sides of an edge.
            padX = (Math.abs(transform.m11()) / viewport.scaleX()
                    + Math.abs(transform.m01()) / viewport.scaleY()) / determinant;
            padY = (Math.abs(transform.m10()) / viewport.scaleX()
                    + Math.abs(transform.m00()) / viewport.scaleY()) / determinant;
        }
        float localWidth = halfWidth + padX;
        float localHeight = halfHeight + padY;
        float red = color.r();
        float green = color.g();
        float blue = color.b();
        float alpha = color.a();
        float x0 = bounds.x() - padX;
        float y0 = bounds.y() - padY;
        float x1 = bounds.right() + padX;
        float y1 = y0;
        float x2 = x1;
        float y2 = bounds.bottom() + padY;
        float x3 = x0;
        float y3 = y2;
        if (!transform.isIdentity()) {
            Point topLeft = transform.apply(new Point(x0, y0));
            Point topRight = transform.apply(new Point(x1, y1));
            Point bottomRight = transform.apply(new Point(x2, y2));
            Point bottomLeft = transform.apply(new Point(x3, y3));
            x0 = topLeft.x();
            y0 = topLeft.y();
            x1 = topRight.x();
            y1 = topRight.y();
            x2 = bottomRight.x();
            y2 = bottomRight.y();
            x3 = bottomLeft.x();
            y3 = bottomLeft.y();
        }

        float padU = bounds.width() > 0 ? padX * uv.width() / bounds.width() : 0;
        float padV = bounds.height() > 0 ? padY * uv.height() / bounds.height() : 0;
        float u0 = uv.x() - padU;
        float v0 = uv.y() - padV;
        float u1 = uv.right() + padU;
        float v1 = uv.bottom() + padV;

        putVertex(x0, y0, -localWidth, -localHeight, halfWidth, halfHeight, radius, red, green, blue, alpha, u0, v0);
        putVertex(x1, y1, localWidth, -localHeight, halfWidth, halfHeight, radius, red, green, blue, alpha, u1, v0);
        putVertex(x2, y2, localWidth, localHeight, halfWidth, halfHeight, radius, red, green, blue, alpha, u1, v1);
        putVertex(x0, y0, -localWidth, -localHeight, halfWidth, halfHeight, radius, red, green, blue, alpha, u0, v0);
        putVertex(x2, y2, localWidth, localHeight, halfWidth, halfHeight, radius, red, green, blue, alpha, u1, v1);
        putVertex(x3, y3, -localWidth, localHeight, halfWidth, halfHeight, radius, red, green, blue, alpha, u0, v1);
    }

    private void appendTexturedQuad(Rect bounds, Rect uv, Transform transform) {
        appendQuad(bounds, 0, Color.WHITE, uv, transform);
    }

    private void appendMesh(MeshCommand command) {
        appendMask(command.vertices());
    }

    private void appendText(TextCommand command) {
        for (int index = 0; index < command.glyphs().size(); index++) {
            appendQuad(command.glyphs().get(index).bounds(), 0, command.colors().get(index),
                    command.glyphs().get(index).uv(), command.transform());
        }
    }

    private void appendMask(List<MeshVertex> vertices) {
        for (MeshVertex vertex : vertices) {
            putVertex(vertex.x(), vertex.y(), 0, 0, MESH_HALF_SIZE, MESH_HALF_SIZE, 0,
                    vertex.color().r(), vertex.color().g(), vertex.color().b(), vertex.color().a(),
                    0.5f, 0.5f);
        }
    }

    private static int totalVertexFloats(List<DrawCommand> commands) {
        int total = 0;
        for (DrawCommand command : commands) {
            total += CommandClassifier.vertexCount(command) * FLOATS_PER_VERTEX;
        }
        return total;
    }


    private void putVertex(float x, float y, float localX, float localY,
                          float halfWidth, float halfHeight, float radius,
                          float red, float green, float blue, float alpha, float u, float v) {
        mappedVertices.put(x).put(y)
                .put(localX).put(localY)
                .put(halfWidth).put(halfHeight)
                .put(radius)
                .put(red).put(green).put(blue).put(alpha)
                .put(u).put(v);
    }

    private void ensureVertexCapacity(int floats) {
        int bytes = floats * Float.BYTES;
        if (bytes <= vertexCapacity) {
            return;
        }

        destroyVertexBuffer();
        int size = Math.max(bytes, INITIAL_VERTEX_BYTES);
        try {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                        .sType$Default()
                        .size(size)
                        .usage(VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
                        .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
                LongBuffer result = stack.mallocLong(1);
                check(VK10.vkCreateBuffer(device, bufferInfo, null, result), "Create vertex buffer");
                vertexBuffer = result.get(0);

                VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
                VK10.vkGetBufferMemoryRequirements(device, vertexBuffer, requirements);
                VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack)
                        .sType$Default()
                        .allocationSize(requirements.size())
                        .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(),
                                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                                        | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));
                check(VK10.vkAllocateMemory(device, allocateInfo, null, result), "Allocate vertex memory");
                vertexMemory = result.get(0);
                check(VK10.vkBindBufferMemory(device, vertexBuffer, vertexMemory, 0), "Bind vertex memory");

                PointerBuffer mapped = stack.mallocPointer(1);
                check(VK10.vkMapMemory(device, vertexMemory, 0, VK10.VK_WHOLE_SIZE, 0, mapped),
                        "Map vertex memory");
                mappedMemory = MemoryUtil.memByteBuffer(mapped.get(0), size);
                mappedVertices = mappedMemory.asFloatBuffer();
                vertexCapacity = size;
            }
        } catch (RuntimeException | Error exception) {
            destroyVertexBuffer();
            throw exception;
        }
    }

    private boolean applyScissor(VkRect2D scissor, Rect clip) {
        float scaleX = viewport.scaleX();
        float scaleY = viewport.scaleY();
        float left = Math.max(clip.x() * scaleX, 0);
        float top = Math.max(clip.y() * scaleY, 0);
        float right = Math.min(clip.right() * scaleX, viewport.framebufferWidth());
        float bottom = Math.min(clip.bottom() * scaleY, viewport.framebufferHeight());
        if (right <= left || bottom <= top) {
            return false;
        }

        int scissorX = (int) Math.floor(left);
        int scissorY = (int) Math.floor(top);
        int scissorWidth = (int) Math.ceil(right) - scissorX;
        int scissorHeight = (int) Math.ceil(bottom) - scissorY;
        if (scissorWidth <= 0 || scissorHeight <= 0) {
            return false;
        }
        scissor.offset().set(scissorX, scissorY);
        scissor.extent().set(scissorWidth, scissorHeight);
        return true;
    }

    private void destroyVertexBuffer() {
        if (mappedMemory != null) {
            VK10.vkUnmapMemory(device, vertexMemory);
            mappedMemory = null;
            mappedVertices = null;
        }
        if (vertexBuffer != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyBuffer(device, vertexBuffer, null);
            vertexBuffer = VK10.VK_NULL_HANDLE;
        }
        if (vertexMemory != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeMemory(device, vertexMemory, null);
            vertexMemory = VK10.VK_NULL_HANDLE;
        }
        vertexCapacity = 0;
    }

    private static VulkanTexture asVulkanTexture(Texture texture) {
        Objects.requireNonNull(texture, "texture");
        if (!(texture instanceof VulkanTexture vulkanTexture)) {
            throw new IllegalArgumentException("Texture belongs to a different render backend.");
        }
        return vulkanTexture;
    }

    private static void check(int result, String operation) {
        if (result != VK10.VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with VkResult " + result + ".");
        }
    }

    private record Batch(int firstVertex, int vertexCount, Rect clip, VulkanTexture texture,
                         int stencilRef, MaskAction action, Paint paint) {
    }


    private record BufferResource(long buffer, long memory) {
    }
}
