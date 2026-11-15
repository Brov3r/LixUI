package com.lixui.backend.vulkan;

import com.lixui.geometry.Rect;
import com.lixui.render.DrawCommand;
import com.lixui.render.DrawList;
import com.lixui.render.RenderBackend;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkViewport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A Vulkan render backend for rounded rectangle draw lists.
 * The caller owns the render pass, framebuffers and command buffers: it must begin a render pass,
 * call {@link #beginFrame(VkCommandBuffer)}, {@link #draw(DrawList)} and {@link #endFrame()},
 * then end the render pass. Commands sharing a clip rectangle are batched into one draw call.
 * The vertex buffer is host-visible and overwritten by each draw, so the caller must not record a
 * frame while a previous submission still uses the backend.
 * SPIR-V binaries are generated from the adjacent GLSL sources, for example:
 * {@code glslc --target-env=vulkan1.0 rounded_rect.vert -o rounded_rect.vert.spv}.
 */
public final class VulkanBackend implements RenderBackend {
    private static final int FLOATS_PER_VERTEX = 11;
    private static final int VERTICES_PER_QUAD = 6;
    private static final int FLOATS_PER_QUAD = FLOATS_PER_VERTEX * VERTICES_PER_QUAD;
    private static final int VERTEX_STRIDE = FLOATS_PER_VERTEX * Float.BYTES;
    private static final int INITIAL_VERTEX_BYTES = 4096;

    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private final List<Batch> batches = new ArrayList<>();
    private long pipelineLayout;
    private long pipeline;
    private long vertexBuffer;
    private long vertexMemory;
    private ByteBuffer mappedMemory;
    private FloatBuffer mappedVertices;
    private int vertexCapacity;
    private VkCommandBuffer commandBuffer;
    private int framebufferWidth;
    private int framebufferHeight;

    /**
     * Creates the backend and its graphics pipeline.
     *
     * @param physicalDevice physical device used to select vertex buffer memory
     * @param device logical device that owns the created resources
     * @param renderPass render pass the pipeline must be compatible with
     * @param sampleCount rasterization sample count of the render pass
     * @throws NullPointerException if a device reference is null
     * @throws IllegalArgumentException if the render pass handle is null or sample count is not positive
     * @throws IllegalStateException if pipeline creation fails
     */
    public VulkanBackend(VkPhysicalDevice physicalDevice, VkDevice device, long renderPass, int sampleCount) {
        this.physicalDevice = Objects.requireNonNull(physicalDevice, "physicalDevice");
        this.device = Objects.requireNonNull(device, "device");
        if (renderPass == VK10.VK_NULL_HANDLE) {
            throw new IllegalArgumentException("Render pass must be a valid handle.");
        }
        if (sampleCount <= 0) {
            throw new IllegalArgumentException("Sample count must be positive.");
        }

        try {
            pipelineLayout = createPipelineLayout();
            pipeline = createPipeline(renderPass, sampleCount);
        } catch (RuntimeException | Error exception) {
            close();
            throw exception;
        }
    }

    /**
     * Stores the render target size in pixels for the next draw.
     *
     * @param framebufferWidth target width in pixels
     * @param framebufferHeight target height in pixels
     * @throws IllegalArgumentException if a dimension is not positive
     */
    @Override
    public void resize(int framebufferWidth, int framebufferHeight) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            throw new IllegalArgumentException("Framebuffer dimensions must be positive.");
        }
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
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
     * Records the draw list into the command buffer selected by {@link #beginFrame(VkCommandBuffer)}.
     *
     * @param drawList commands to render
     * @throws NullPointerException if {@code drawList} is null
     * @throws IllegalStateException if {@link #beginFrame(VkCommandBuffer)} or {@link #resize(int, int)}
     *         has not been called
     */
    @Override
    public void draw(DrawList drawList) {
        Objects.requireNonNull(drawList, "drawList");
        if (commandBuffer == null) {
            throw new IllegalStateException("beginFrame must be called before draw.");
        }
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            throw new IllegalStateException("resize must be called before draw.");
        }

        List<DrawCommand> commands = drawList.commands();
        if (commands.isEmpty()) {
            return;
        }

        ensureVertexCapacity(commands.size() * FLOATS_PER_QUAD);
        mappedVertices.clear();
        collect(commands);
        if (batches.isEmpty()) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1, stack)
                    .sType$Default()
                    .srcAccessMask(VK10.VK_ACCESS_HOST_WRITE_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT);
            VK10.vkCmdPipelineBarrier(commandBuffer, VK10.VK_PIPELINE_STAGE_HOST_BIT,
                    VK10.VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, 0, barrier, null, null);

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.get(0)
                    .x(0)
                    .y(0)
                    .width(framebufferWidth)
                    .height(framebufferHeight)
                    .minDepth(0)
                    .maxDepth(1);
            VK10.vkCmdSetViewport(commandBuffer, 0, viewport);

            VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            VK10.vkCmdPushConstants(commandBuffer, pipelineLayout, VK10.VK_SHADER_STAGE_VERTEX_BIT, 0,
                    stack.floats(framebufferWidth, framebufferHeight));

            LongBuffer buffers = stack.longs(vertexBuffer);
            LongBuffer offsets = stack.longs(0);
            VK10.vkCmdBindVertexBuffers(commandBuffer, 0, buffers, offsets);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            for (Batch batch : batches) {
                if (!applyScissor(scissor.get(0), batch.clip())) {
                    continue;
                }
                VK10.vkCmdSetScissor(commandBuffer, 0, scissor);
                VK10.vkCmdDraw(commandBuffer, batch.vertexCount(), 1, batch.firstVertex(), 0);
            }
        }
    }

    /**
     * Releases the pipeline, pipeline layout and vertex buffer owned by this backend.
     * The logical device must still be alive when this method is called.
     */
    @Override
    public void close() {
        commandBuffer = null;
        batches.clear();
        destroyVertexBuffer();
        if (pipeline != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyPipeline(device, pipeline, null);
            pipeline = VK10.VK_NULL_HANDLE;
        }
        if (pipelineLayout != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyPipelineLayout(device, pipelineLayout, null);
            pipelineLayout = VK10.VK_NULL_HANDLE;
        }
    }

    private long createPipelineLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(2 * Float.BYTES);
            VkPipelineLayoutCreateInfo info = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pPushConstantRanges(range);
            LongBuffer result = stack.mallocLong(1);
            check(VK10.vkCreatePipelineLayout(device, info, null, result), "Create pipeline layout");
            return result.get(0);
        }
    }

    private long createPipeline(long renderPass, int sampleCount) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long vertexModule = createShaderModule(loadShader("rounded_rect.vert.spv", stack));
            long fragmentModule;
            try {
                fragmentModule = createShaderModule(loadShader("rounded_rect.frag.spv", stack));
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
                        VkVertexInputAttributeDescription.calloc(5, stack);
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

                VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .pVertexBindingDescriptions(binding)
                        .pVertexAttributeDescriptions(attributes);

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
                blendAttachment.get(0)
                        .blendEnable(true)
                        .srcColorBlendFactor(VK10.VK_BLEND_FACTOR_ONE)
                        .dstColorBlendFactor(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .colorBlendOp(VK10.VK_BLEND_OP_ADD)
                        .srcAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .alphaBlendOp(VK10.VK_BLEND_OP_ADD)
                        .colorWriteMask(VK10.VK_COLOR_COMPONENT_R_BIT
                                | VK10.VK_COLOR_COMPONENT_G_BIT
                                | VK10.VK_COLOR_COMPONENT_B_BIT
                                | VK10.VK_COLOR_COMPONENT_A_BIT);

                VkPipelineColorBlendStateCreateInfo colorBlend =
                        VkPipelineColorBlendStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .logicOpEnable(false)
                                .attachmentCount(1)
                                .pAttachments(blendAttachment);

                IntBuffer dynamicStates = stack.ints(
                        VK10.VK_DYNAMIC_STATE_VIEWPORT,
                        VK10.VK_DYNAMIC_STATE_SCISSOR);
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

    private void collect(List<DrawCommand> commands) {
        batches.clear();
        Rect currentClip = null;
        int batchStartVertex = 0;
        int batchVertexCount = 0;
        int vertexIndex = 0;
        for (DrawCommand command : commands) {
            if (!command.clip().equals(currentClip)) {
                if (batchVertexCount > 0) {
                    batches.add(new Batch(batchStartVertex, batchVertexCount, currentClip));
                }
                currentClip = command.clip();
                batchStartVertex = vertexIndex;
                batchVertexCount = 0;
            }
            appendQuad(command);
            vertexIndex += VERTICES_PER_QUAD;
            batchVertexCount += VERTICES_PER_QUAD;
        }
        if (batchVertexCount > 0) {
            batches.add(new Batch(batchStartVertex, batchVertexCount, currentClip));
        }
    }

    private void appendQuad(DrawCommand command) {
        Rect bounds = command.bounds();
        float halfWidth = bounds.width() / 2;
        float halfHeight = bounds.height() / 2;
        float left = bounds.x();
        float top = bounds.y();
        float right = bounds.right();
        float bottom = bounds.bottom();
        float radius = command.radius();
        float red = command.color().r();
        float green = command.color().g();
        float blue = command.color().b();
        float alpha = command.color().a();

        putVertex(left, top, -halfWidth, -halfHeight, halfWidth, halfHeight, radius, red, green, blue, alpha);
        putVertex(right, top, halfWidth, -halfHeight, halfWidth, halfHeight, radius, red, green, blue, alpha);
        putVertex(right, bottom, halfWidth, halfHeight, halfWidth, halfHeight, radius, red, green, blue, alpha);
        putVertex(left, top, -halfWidth, -halfHeight, halfWidth, halfHeight, radius, red, green, blue, alpha);
        putVertex(right, bottom, halfWidth, halfHeight, halfWidth, halfHeight, radius, red, green, blue, alpha);
        putVertex(left, bottom, -halfWidth, halfHeight, halfWidth, halfHeight, radius, red, green, blue, alpha);
    }

    private void putVertex(float x, float y, float localX, float localY,
                          float halfWidth, float halfHeight, float radius,
                          float red, float green, float blue, float alpha) {
        mappedVertices.put(x).put(y)
                .put(localX).put(localY)
                .put(halfWidth).put(halfHeight)
                .put(radius)
                .put(red).put(green).put(blue).put(alpha);
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

    private int findMemoryType(int typeFilter, int properties) {
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

    private boolean applyScissor(VkRect2D scissor, Rect clip) {
        float left = Math.max(clip.x(), 0);
        float top = Math.max(clip.y(), 0);
        float right = Math.min(clip.right(), framebufferWidth);
        float bottom = Math.min(clip.bottom(), framebufferHeight);
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

    private static void check(int result, String operation) {
        if (result != VK10.VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with VkResult " + result + ".");
        }
    }

    private record Batch(int firstVertex, int vertexCount, Rect clip) {
    }
}
