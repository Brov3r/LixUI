package com.lixui.backend.vulkan;

import com.lixui.backend.MipmapScenario;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.render.TextureFilter;
import com.lixui.render.Viewport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VulkanLayerTest {
    private static final int SIZE = 64;
    private static final int PIXELS = SIZE * SIZE;

    @Test
    void samplesGeneratedMipLevelsDuringMinification() {
        try (Host host = new Host();
                VulkanLayer layer = new VulkanLayer(host.physical, host.device, host.queue, host.family,
                        host.loadPass, VK10.VK_SAMPLE_COUNT_1_BIT)) {
            RenderManager renderer = new RenderManager(layer);
            renderer.resize(new Viewport(SIZE, SIZE, SIZE, SIZE));
            Texture nearest = MipmapScenario.stripes(renderer, TextureFilter.NEAREST);
            Texture linear = MipmapScenario.stripes(renderer, TextureFilter.LINEAR);
            host.begin();
            layer.beginFrame(host.command);
            renderer.begin();
            MipmapScenario.draw(renderer, nearest, linear);
            renderer.end();
            layer.endFrame();
            host.beginPass(host.clearPass);
            layer.composite(host.command);
            VK10.vkCmdEndRenderPass(host.command);
            host.readBack();
            host.assertPixel(4, 4, 191, 191, 191);
            host.assertPixel(2, 14, 128, 128, 128);
        }
    }

    @Test
    void isolatesUiAttachmentsAndCompositesOverExistingSceneColor() {
        try (Host host = new Host();
                VulkanLayer layer = new VulkanLayer(host.physical, host.device, host.queue, host.family,
                        host.loadPass, VK10.VK_SAMPLE_COUNT_1_BIT)) {
            RenderManager renderer = new RenderManager(layer);
            renderer.resize(new Viewport(SIZE, SIZE, SIZE, SIZE));
            assertThrows(IllegalStateException.class, layer::endFrame);
            assertThrows(IllegalStateException.class, () -> layer.composite(host.command));
            host.begin();
            host.beginPass(host.clearPass);
            VK10.vkCmdEndRenderPass(host.command);

            layer.beginFrame(host.command);
            assertThrows(IllegalStateException.class, () -> layer.beginFrame(host.command));
            renderer.begin();
            renderer.pushMaskRect(new Rect(20, 20, 20, 20));
            renderer.rect(new Rect(0, 0, SIZE, SIZE), new Color(1, 0, 0, 0.5f));
            renderer.popMask();
            renderer.end();
            layer.endFrame();

            host.beginPass(host.loadPass);
            layer.composite(host.command);
            VK10.vkCmdEndRenderPass(host.command);
            host.readBack();
            assertEquals(4, layer.drawCallCount());
            host.assertPixel(25, 25, 128, 0, 127);
            host.assertPixel(10, 10, 0, 0, 255);
            host.assertDepthStencilPreserved();

            // A new physical size must recreate only the private UI target after the prior fence.
            renderer.resize(new Viewport(32, 32, 32, 32));
            host.begin();
            layer.beginFrame(host.command);
            renderer.begin();
            renderer.rect(new Rect(0, 0, 16, 16), Color.WHITE);
            renderer.end();
            layer.endFrame();
            host.beginPass(host.clearPass);
            layer.composite(host.command);
            VK10.vkCmdEndRenderPass(host.command);
            host.readBack();
            host.assertPixel(5, 5, 255, 255, 255);
            host.assertPixel(50, 50, 0, 0, 255);
            host.assertDepthStencilPreserved();
        }
    }

    private static final class Host implements AutoCloseable {
        private VkInstance instance;
        private VkPhysicalDevice physical;
        private VkDevice device;
        private VkQueue queue;
        private int family;
        private long clearPass;
        private long loadPass;
        private long framebuffer;
        private Image color;
        private Image depth;
        private long pool;
        private VkCommandBuffer command;
        private long buffer;
        private long memory;
        private ByteBuffer pixels;

        private Host() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkApplicationInfo app = VkApplicationInfo.calloc(stack).sType$Default()
                        .apiVersion(VK10.VK_API_VERSION_1_0);
                VkInstanceCreateInfo info = VkInstanceCreateInfo.calloc(stack).sType$Default().pApplicationInfo(app);
                PointerBuffer pointer = stack.mallocPointer(1);
                Assumptions.assumeTrue(VK10.vkCreateInstance(info, null, pointer) == VK10.VK_SUCCESS);
                instance = new VkInstance(pointer.get(0), info);
                IntBuffer count = stack.mallocInt(1);
                check(VK10.vkEnumeratePhysicalDevices(instance, count, null));
                Assumptions.assumeTrue(count.get(0) > 0);
                PointerBuffer devices = stack.mallocPointer(count.get(0));
                check(VK10.vkEnumeratePhysicalDevices(instance, count, devices));
                physical = new VkPhysicalDevice(devices.get(0), instance);
                VK10.vkGetPhysicalDeviceQueueFamilyProperties(physical, count, null);
                VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.malloc(count.get(0), stack);
                VK10.vkGetPhysicalDeviceQueueFamilyProperties(physical, count, families);
                family = -1;
                for (int index = 0; index < families.capacity(); index++) {
                    if ((families.get(index).queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0) {
                        family = index;
                        break;
                    }
                }
                Assumptions.assumeTrue(family >= 0);
                VkFormatProperties format = VkFormatProperties.malloc(stack);
                VK10.vkGetPhysicalDeviceFormatProperties(physical, VK10.VK_FORMAT_D32_SFLOAT_S8_UINT, format);
                Assumptions.assumeTrue((format.optimalTilingFeatures()
                        & VK10.VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0);
                VkDeviceQueueCreateInfo.Buffer queues = VkDeviceQueueCreateInfo.calloc(1, stack);
                queues.get(0).sType$Default().queueFamilyIndex(family).pQueuePriorities(stack.floats(1));
                VkDeviceCreateInfo deviceInfo = VkDeviceCreateInfo.calloc(stack).sType$Default()
                        .pQueueCreateInfos(queues);
                check(VK10.vkCreateDevice(physical, deviceInfo, null, pointer));
                device = new VkDevice(pointer.get(0), physical, deviceInfo);
                VK10.vkGetDeviceQueue(device, family, 0, pointer);
                queue = new VkQueue(pointer.get(0), device);
                clearPass = createPass(false);
                loadPass = createPass(true);
                color = createImage(VK10.VK_FORMAT_R8G8B8A8_UNORM, VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
                        VK10.VK_IMAGE_ASPECT_COLOR_BIT);
                depth = createImage(VK10.VK_FORMAT_D32_SFLOAT_S8_UINT,
                        VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                        VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT);
                LongBuffer result = stack.mallocLong(1);
                VkFramebufferCreateInfo fb = VkFramebufferCreateInfo.calloc(stack).sType$Default()
                        .renderPass(clearPass).pAttachments(stack.longs(color.view(), depth.view()))
                        .width(SIZE).height(SIZE).layers(1);
                check(VK10.vkCreateFramebuffer(device, fb, null, result));
                framebuffer = result.get(0);
                VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack).sType$Default()
                        .queueFamilyIndex(family).flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
                check(VK10.vkCreateCommandPool(device, poolInfo, null, result));
                pool = result.get(0);
                VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                        .commandPool(pool).commandBufferCount(1).level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
                check(VK10.vkAllocateCommandBuffers(device, allocate, pointer));
                command = new VkCommandBuffer(pointer.get(0), device);
                VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack).sType$Default()
                        .size(PIXELS * 9L).usage(VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT);
                check(VK10.vkCreateBuffer(device, bufferInfo, null, result));
                buffer = result.get(0);
                VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
                VK10.vkGetBufferMemoryRequirements(device, buffer, requirements);
                memory = allocateMemory(requirements,
                        VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
                check(VK10.vkBindBufferMemory(device, buffer, memory, 0));
                check(VK10.vkMapMemory(device, memory, 0, PIXELS * 9L, 0, pointer));
                pixels = MemoryUtil.memByteBuffer(pointer.get(0), PIXELS * 9);
            } catch (RuntimeException | Error exception) {
                close();
                throw exception;
            }
        }

        private void begin() {
            check(VK10.vkResetCommandBuffer(command, 0));
            try (MemoryStack stack = MemoryStack.stackPush()) {
                check(VK10.vkBeginCommandBuffer(command, VkCommandBufferBeginInfo.calloc(stack).sType$Default()));
            }
        }

        private void beginPass(long pass) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkClearValue.Buffer clear = VkClearValue.calloc(2, stack);
                clear.get(0).color().float32(2, 1).float32(3, 1);
                clear.get(1).depthStencil().depth(0.25f).stencil(7);
                VkRenderPassBeginInfo info = VkRenderPassBeginInfo.calloc(stack).sType$Default()
                        .renderPass(pass).framebuffer(framebuffer).pClearValues(clear);
                info.renderArea().extent().set(SIZE, SIZE);
                VK10.vkCmdBeginRenderPass(command, info, VK10.VK_SUBPASS_CONTENTS_INLINE);
            }
        }

        private void readBack() {
            copy(color.image(), VK10.VK_IMAGE_ASPECT_COLOR_BIT, 0);
            copy(depth.image(), VK10.VK_IMAGE_ASPECT_STENCIL_BIT, PIXELS * 4L);
            copy(depth.image(), VK10.VK_IMAGE_ASPECT_DEPTH_BIT, PIXELS * 5L);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1, stack).sType$Default()
                        .srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK10.VK_ACCESS_HOST_READ_BIT);
                VK10.vkCmdPipelineBarrier(command, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK10.VK_PIPELINE_STAGE_HOST_BIT, 0, barrier, null, null);
                check(VK10.vkEndCommandBuffer(command));
                VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
                        .pCommandBuffers(stack.pointers(command.address()));
                check(VK10.vkQueueSubmit(queue, submit, VK10.VK_NULL_HANDLE));
                check(VK10.vkQueueWaitIdle(queue));
            }
        }

        private void copy(long image, int aspect, long offset) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
                region.get(0).bufferOffset(offset);
                region.get(0).imageSubresource().aspectMask(aspect).layerCount(1);
                region.get(0).imageExtent().set(SIZE, SIZE, 1);
                VK10.vkCmdCopyImageToBuffer(command, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, region);
            }
        }

        private void assertPixel(int x, int y, int red, int green, int blue) {
            int offset = (y * SIZE + x) * 4;
            assertEquals(red, Byte.toUnsignedInt(pixels.get(offset)), 1);
            assertEquals(green, Byte.toUnsignedInt(pixels.get(offset + 1)), 1);
            assertEquals(blue, Byte.toUnsignedInt(pixels.get(offset + 2)), 1);
            assertEquals(255, Byte.toUnsignedInt(pixels.get(offset + 3)));
        }

        private void assertDepthStencilPreserved() {
            for (int index = 0; index < PIXELS; index++) {
                assertEquals(7, Byte.toUnsignedInt(pixels.get(PIXELS * 4 + index)));
                assertEquals(0.25f, pixels.getFloat(PIXELS * 5 + index * 4));
            }
        }

        private long createPass(boolean load) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                int loadOp = load ? VK10.VK_ATTACHMENT_LOAD_OP_LOAD : VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
                int initialLayout = load ? VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL : VK10.VK_IMAGE_LAYOUT_UNDEFINED;
                VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2, stack);
                attachments.get(0).format(VK10.VK_FORMAT_R8G8B8A8_UNORM).samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                        .loadOp(loadOp).storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                        .initialLayout(initialLayout).finalLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
                attachments.get(1).format(VK10.VK_FORMAT_D32_SFLOAT_S8_UINT).samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                        .loadOp(loadOp).storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                        .stencilLoadOp(loadOp).stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                        .initialLayout(initialLayout).finalLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
                VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(1, stack);
                colorRef.get(0).attachment(0).layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                VkAttachmentReference depthRef = VkAttachmentReference.calloc(stack).attachment(1)
                        .layout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
                VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
                subpass.get(0).pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                        .colorAttachmentCount(1).pColorAttachments(colorRef).pDepthStencilAttachment(depthRef);
                VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(2, stack);
                int stages = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                        | VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                        | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
                int access = VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
                dependencies.get(0).srcSubpass(VK10.VK_SUBPASS_EXTERNAL).dstSubpass(0)
                        .srcStageMask(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT).dstStageMask(stages)
                        .srcAccessMask(VK10.VK_ACCESS_MEMORY_WRITE_BIT | VK10.VK_ACCESS_MEMORY_READ_BIT)
                        .dstAccessMask(access | VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                                | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT);
                dependencies.get(1).srcSubpass(0).dstSubpass(VK10.VK_SUBPASS_EXTERNAL)
                        .srcStageMask(stages).dstStageMask(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT)
                        .srcAccessMask(access).dstAccessMask(VK10.VK_ACCESS_MEMORY_READ_BIT
                                | VK10.VK_ACCESS_MEMORY_WRITE_BIT);
                VkRenderPassCreateInfo info = VkRenderPassCreateInfo.calloc(stack).sType$Default()
                        .pAttachments(attachments).pSubpasses(subpass).pDependencies(dependencies);
                LongBuffer result = stack.mallocLong(1);
                check(VK10.vkCreateRenderPass(device, info, null, result));
                return result.get(0);
            }
        }

        private Image createImage(int format, int usage, int aspect) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkImageCreateInfo info = VkImageCreateInfo.calloc(stack).sType$Default()
                        .imageType(VK10.VK_IMAGE_TYPE_2D).format(format).mipLevels(1).arrayLayers(1)
                        .samples(VK10.VK_SAMPLE_COUNT_1_BIT).tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                        .usage(usage | VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
                info.extent().set(SIZE, SIZE, 1);
                LongBuffer result = stack.mallocLong(1);
                check(VK10.vkCreateImage(device, info, null, result));
                long image = result.get(0);
                VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
                VK10.vkGetImageMemoryRequirements(device, image, requirements);
                long imageMemory = allocateMemory(requirements, VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
                check(VK10.vkBindImageMemory(device, image, imageMemory, 0));
                VkImageViewCreateInfo view = VkImageViewCreateInfo.calloc(stack).sType$Default()
                        .image(image).format(format).viewType(VK10.VK_IMAGE_VIEW_TYPE_2D);
                view.subresourceRange().aspectMask(aspect).levelCount(1).layerCount(1);
                check(VK10.vkCreateImageView(device, view, null, result));
                return new Image(image, imageMemory, result.get(0));
            }
        }

        private long allocateMemory(VkMemoryRequirements requirements, int flags) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.malloc(stack);
                VK10.vkGetPhysicalDeviceMemoryProperties(physical, properties);
                for (int index = 0; index < properties.memoryTypeCount(); index++) {
                    if ((requirements.memoryTypeBits() & (1 << index)) != 0
                            && (properties.memoryTypes(index).propertyFlags() & flags) == flags) {
                        VkMemoryAllocateInfo info = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                                .allocationSize(requirements.size()).memoryTypeIndex(index);
                        LongBuffer result = stack.mallocLong(1);
                        check(VK10.vkAllocateMemory(device, info, null, result));
                        return result.get(0);
                    }
                }
            }
            throw new IllegalStateException("No compatible test memory type.");
        }

        /**
         * Releases test-owned GPU resources after submitted work completes.
         */
        @Override
        public void close() {
            if (device != null) {
                VK10.vkDeviceWaitIdle(device);
                if (pixels != null) {
                    VK10.vkUnmapMemory(device, memory);
                }
                VK10.vkDestroyBuffer(device, buffer, null);
                VK10.vkFreeMemory(device, memory, null);
                VK10.vkDestroyCommandPool(device, pool, null);
                VK10.vkDestroyFramebuffer(device, framebuffer, null);
                destroyImage(color);
                destroyImage(depth);
                VK10.vkDestroyRenderPass(device, clearPass, null);
                VK10.vkDestroyRenderPass(device, loadPass, null);
                VK10.vkDestroyDevice(device, null);
            }
            if (instance != null) {
                VK10.vkDestroyInstance(instance, null);
            }
        }

        private void destroyImage(Image image) {
            if (image != null) {
                VK10.vkDestroyImageView(device, image.view(), null);
                VK10.vkDestroyImage(device, image.image(), null);
                VK10.vkFreeMemory(device, image.memory(), null);
            }
        }

        private static void check(int result) {
            assertEquals(VK10.VK_SUCCESS, result);
        }
    }

    private record Image(long image, long memory, long view) {
    }
}
