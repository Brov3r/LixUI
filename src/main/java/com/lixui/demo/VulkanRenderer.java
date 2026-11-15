package com.lixui.demo;

import com.lixui.backend.vulkan.VulkanBackend;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

final class VulkanRenderer implements AutoCloseable {
    private static final int SAMPLE_COUNT = VK10.VK_SAMPLE_COUNT_1_BIT;

    private final long window;
    private VkInstance instance;
    private long surface;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkQueue queue;
    private int queueFamily;
    private int surfaceFormat;
    private int surfaceColorSpace;
    private long renderPass;
    private long swapchain;
    private long[] imageViews;
    private long[] framebuffers;
    private long commandPool;
    private VkCommandBuffer[] commandBuffers;
    private long imageAvailable;
    private long[] renderFinished;
    private long frameFence;
    private VulkanBackend backend;
    private Texture sceneTexture;
    private int framebufferWidth;
    private int framebufferHeight;
    private int logicalWidth;
    private int logicalHeight;
    private boolean swapchainDirty = true;

    VulkanRenderer(long window) {
        this.window = window;
        try {
            if (!GLFWVulkan.glfwVulkanSupported()) {
                throw new IllegalStateException("A Vulkan-capable driver and Vulkan loader are required.");
            }
            createInstance();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer result = stack.mallocLong(1);
                check(GLFWVulkan.glfwCreateWindowSurface(instance, window, null, result), "Create window surface");
                surface = result.get(0);
            }
            selectPhysicalDevice();
            createDevice();
            chooseSurfaceFormat();
            createRenderPass();
            backend = new VulkanBackend(physicalDevice, device, queue, queueFamily, renderPass, SAMPLE_COUNT);
            sceneTexture = backend.createTexture(DemoTextures.checkerboard());
            createFrameSynchronization();
        } catch (RuntimeException | Error exception) {
            close();
            throw exception;
        }
    }

    boolean render() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            GLFW.glfwGetFramebufferSize(window, width, height);
            if (width.get(0) == 0 || height.get(0) == 0) {
                return false;
            }
            IntBuffer windowWidth = stack.mallocInt(1);
            IntBuffer windowHeight = stack.mallocInt(1);
            GLFW.glfwGetWindowSize(window, windowWidth, windowHeight);
            if (windowWidth.get(0) != logicalWidth || windowHeight.get(0) != logicalHeight) {
                swapchainDirty = true;
            }
            if (swapchainDirty || width.get(0) != framebufferWidth || height.get(0) != framebufferHeight) {
                if (!recreateSwapchain(width.get(0), height.get(0))) {
                    return false;
                }
            }

            check(VK10.vkWaitForFences(device, frameFence, true, -1L), "Wait for frame");
            IntBuffer imageIndex = stack.mallocInt(1);
            int acquired = KHRSwapchain.vkAcquireNextImageKHR(
                    device, swapchain, -1L, imageAvailable, VK10.VK_NULL_HANDLE, imageIndex);
            if (acquired == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                swapchainDirty = true;
                return false;
            }
            if (acquired != KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                check(acquired, "Acquire swapchain image");
            }

            int index = imageIndex.get(0);
            recordCommandBuffer(commandBuffers[index], framebuffers[index]);

            VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(imageAvailable))
                    .pWaitDstStageMask(stack.ints(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                    .pCommandBuffers(stack.pointers(commandBuffers[index].address()))
                    .pSignalSemaphores(stack.longs(renderFinished[index]));

            // Reset only after acquisition succeeds, otherwise an out-of-date swapchain would leave it unsignaled.
            check(VK10.vkResetFences(device, frameFence), "Reset frame fence");
            check(VK10.vkQueueSubmit(queue, submit, frameFence), "Submit frame commands");

            VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack)
                    .sType$Default()
                    .pWaitSemaphores(stack.longs(renderFinished[index]))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(swapchain))
                    .pImageIndices(imageIndex);
            int presented = KHRSwapchain.vkQueuePresentKHR(queue, present);
            if (presented == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR
                    || presented == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                swapchainDirty = true;
            } else {
                check(presented, "Present swapchain image");
            }
            swapchainDirty |= acquired == KHRSwapchain.VK_SUBOPTIMAL_KHR;
            return true;
        }
    }

    /**
     * Waits for submitted work and releases all Vulkan resources owned by this renderer.
     */
    @Override
    public void close() {
        if (device != null) {
            // Cleanup must also proceed after device loss.
            VK10.vkDeviceWaitIdle(device);
            if (backend != null) {
                backend.close();
                backend = null;
                sceneTexture = null;
            }
            destroySwapchain();
            if (renderPass != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyRenderPass(device, renderPass, null);
                renderPass = VK10.VK_NULL_HANDLE;
            }
            if (frameFence != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyFence(device, frameFence, null);
            }
            if (imageAvailable != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroySemaphore(device, imageAvailable, null);
            }
            VK10.vkDestroyDevice(device, null);
            device = null;
        }
        if (instance != null) {
            if (surface != VK10.VK_NULL_HANDLE) {
                KHRSurface.vkDestroySurfaceKHR(instance, surface, null);
                surface = VK10.VK_NULL_HANDLE;
            }
            VK10.vkDestroyInstance(instance, null);
            instance = null;
        }
    }

    private void createInstance() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer extensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (extensions == null) {
                throw new IllegalStateException("GLFW cannot provide Vulkan surface extensions.");
            }
            VkApplicationInfo application = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationName(stack.UTF8("LixUI Vulkan Demo"))
                    .apiVersion(VK10.VK_API_VERSION_1_0);
            VkInstanceCreateInfo info = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationInfo(application)
                    .ppEnabledExtensionNames(extensions);
            PointerBuffer result = stack.mallocPointer(1);
            check(VK10.vkCreateInstance(info, null, result), "Create Vulkan instance");
            instance = new VkInstance(result.get(0), info);
        }
    }

    private void selectPhysicalDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            check(VK10.vkEnumeratePhysicalDevices(instance, count, null), "Count physical devices");
            if (count.get(0) == 0) {
                throw new IllegalStateException("No Vulkan physical devices are available.");
            }
            PointerBuffer devices = stack.mallocPointer(count.get(0));
            check(VK10.vkEnumeratePhysicalDevices(instance, count, devices), "Enumerate physical devices");
            for (int i = 0; i < count.get(0); i++) {
                VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), instance);
                int family = findQueueFamily(candidate);
                if (family >= 0 && supportsSwapchain(candidate)) {
                    physicalDevice = candidate;
                    queueFamily = family;
                    return;
                }
            }
        }
        throw new IllegalStateException(
                "No Vulkan device supports a combined graphics/present queue and swapchain images.");
    }

    private int findQueueFamily(VkPhysicalDevice candidate) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, null);
            VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.malloc(count.get(0), stack);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, families);
            IntBuffer presentSupported = stack.mallocInt(1);
            for (int i = 0; i < families.capacity(); i++) {
                if (families.get(i).queueCount() == 0
                        || (families.get(i).queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) == 0) {
                    continue;
                }
                check(KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(candidate, i, surface, presentSupported),
                        "Check presentation support");
                if (presentSupported.get(0) == VK10.VK_TRUE) {
                    return i;
                }
            }
            return -1;
        }
    }

    private boolean supportsSwapchain(VkPhysicalDevice candidate) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            check(VK10.vkEnumerateDeviceExtensionProperties(candidate, (String) null, count, null),
                    "Count device extensions");
            VkExtensionProperties.Buffer extensions = VkExtensionProperties.malloc(count.get(0), stack);
            check(VK10.vkEnumerateDeviceExtensionProperties(candidate, (String) null, count, extensions),
                    "Enumerate device extensions");
            boolean supported = false;
            for (int i = 0; i < count.get(0); i++) {
                if (KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME.equals(extensions.get(i).extensionNameString())) {
                    supported = true;
                    break;
                }
            }
            if (!supported) {
                return false;
            }

            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
            check(KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(candidate, surface, capabilities),
                    "Query surface capabilities");
            check(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(candidate, surface, count, null),
                    "Count surface formats");
            return count.get(0) > 0
                    && (capabilities.supportedUsageFlags() & VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) != 0;
        }
    }

    private void createDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDeviceQueueCreateInfo.Buffer queues = VkDeviceQueueCreateInfo.calloc(1, stack);
            queues.get(0)
                    .sType$Default()
                    .queueFamilyIndex(queueFamily)
                    .pQueuePriorities(stack.floats(1.0f));
            VkDeviceCreateInfo info = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pQueueCreateInfos(queues)
                    .ppEnabledExtensionNames(stack.pointers(stack.UTF8(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME)));
            PointerBuffer result = stack.mallocPointer(1);
            check(VK10.vkCreateDevice(physicalDevice, info, null, result), "Create Vulkan device");
            device = new VkDevice(result.get(0), physicalDevice, info);
            VK10.vkGetDeviceQueue(device, queueFamily, 0, result);
            queue = new VkQueue(result.get(0), device);
        }
    }

    private void chooseSurfaceFormat() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            check(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, count, null),
                    "Count surface formats");
            if (count.get(0) == 0) {
                throw new IllegalStateException("The surface has no Vulkan image formats.");
            }
            VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.malloc(count.get(0), stack);
            check(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, count, formats),
                    "Query surface formats");
            VkSurfaceFormatKHR chosen = formats.get(0);
            for (int i = 0; i < count.get(0); i++) {
                if (formats.get(i).format() == VK10.VK_FORMAT_B8G8R8A8_UNORM
                        && formats.get(i).colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                    chosen = formats.get(i);
                    break;
                }
            }
            surfaceFormat = chosen.format();
            surfaceColorSpace = chosen.colorSpace();
        }
    }

    private void createRenderPass() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(1, stack);
            attachments.get(0)
                    .format(surfaceFormat)
                    .samples(SAMPLE_COUNT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            VkAttachmentReference.Buffer colorReference = VkAttachmentReference.calloc(1, stack);
            colorReference.get(0)
                    .attachment(0)
                    .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.get(0)
                    .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorReference);

            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack);
            dependency.get(0)
                    .srcSubpass(VK10.VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(0)
                    .dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            VkRenderPassCreateInfo info = VkRenderPassCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(attachments)
                    .pSubpasses(subpass)
                    .pDependencies(dependency);
            LongBuffer result = stack.mallocLong(1);
            check(VK10.vkCreateRenderPass(device, info, null, result), "Create render pass");
            renderPass = result.get(0);
        }
    }

    private void createFrameSynchronization() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer result = stack.mallocLong(1);
            VkSemaphoreCreateInfo semaphore = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            check(VK10.vkCreateSemaphore(device, semaphore, null, result), "Create acquisition semaphore");
            imageAvailable = result.get(0);
            VkFenceCreateInfo fence = VkFenceCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);
            check(VK10.vkCreateFence(device, fence, null, result), "Create frame fence");
            frameFence = result.get(0);
        }
    }

    private boolean recreateSwapchain(int width, int height) {
        check(VK10.vkDeviceWaitIdle(device), "Wait before recreating swapchain");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
            check(KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities),
                    "Query surface capabilities");
            int extentWidth = capabilities.currentExtent().width();
            int extentHeight = capabilities.currentExtent().height();
            if (extentWidth == -1) {
                extentWidth = Math.clamp(width,
                        capabilities.minImageExtent().width(), capabilities.maxImageExtent().width());
                extentHeight = Math.clamp(height,
                        capabilities.minImageExtent().height(), capabilities.maxImageExtent().height());
            }
            if (extentWidth == 0 || extentHeight == 0) {
                return false;
            }

            IntBuffer windowWidth = stack.mallocInt(1);
            IntBuffer windowHeight = stack.mallocInt(1);
            GLFW.glfwGetWindowSize(window, windowWidth, windowHeight);
            int viewportWidth = windowWidth.get(0);
            int viewportHeight = windowHeight.get(0);
            if (viewportWidth <= 0 || viewportHeight <= 0) {
                return false;
            }

            int imageCount = capabilities.minImageCount() + 1;
            if (capabilities.maxImageCount() > 0) {
                imageCount = Math.min(imageCount, capabilities.maxImageCount());
            }

            destroySwapchain();
            framebufferWidth = extentWidth;
            framebufferHeight = extentHeight;
            logicalWidth = viewportWidth;
            logicalHeight = viewportHeight;

            VkSwapchainCreateInfoKHR info = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .surface(surface)
                    .minImageCount(imageCount)
                    .imageFormat(surfaceFormat)
                    .imageColorSpace(surfaceColorSpace)
                    .imageArrayLayers(1)
                    .imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .preTransform(capabilities.currentTransform())
                    .compositeAlpha(Integer.lowestOneBit(capabilities.supportedCompositeAlpha()))
                    .presentMode(KHRSurface.VK_PRESENT_MODE_FIFO_KHR)
                    .clipped(true);
            info.imageExtent().set(extentWidth, extentHeight);
            LongBuffer result = stack.mallocLong(1);
            check(KHRSwapchain.vkCreateSwapchainKHR(device, info, null, result), "Create swapchain");
            swapchain = result.get(0);

            IntBuffer swapchainImageCount = stack.mallocInt(1);
            check(KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, swapchainImageCount, null),
                    "Count swapchain images");
            LongBuffer images = stack.mallocLong(swapchainImageCount.get(0));
            check(KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, swapchainImageCount, images),
                    "Get swapchain images");
            createImageViews(images);
            createFramebuffers(images.remaining());
            createCommands(images.remaining());
            backend.resize(new Viewport(extentWidth, extentHeight, viewportWidth, viewportHeight));
            swapchainDirty = false;
            return true;
        }
    }

    private void createImageViews(LongBuffer images) {
        imageViews = new long[images.remaining()];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer result = stack.mallocLong(1);
            for (int i = 0; i < imageViews.length; i++) {
                VkImageViewCreateInfo info = VkImageViewCreateInfo.calloc(stack)
                        .sType$Default()
                        .image(images.get(i))
                        .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                        .format(surfaceFormat);
                info.subresourceRange()
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .levelCount(1)
                        .layerCount(1);
                check(VK10.vkCreateImageView(device, info, null, result), "Create image view");
                imageViews[i] = result.get(0);
            }
        }
    }

    private void createFramebuffers(int imageCount) {
        framebuffers = new long[imageCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer result = stack.mallocLong(1);
            for (int i = 0; i < imageCount; i++) {
                VkFramebufferCreateInfo info = VkFramebufferCreateInfo.calloc(stack)
                        .sType$Default()
                        .renderPass(renderPass)
                        .attachmentCount(1)
                        .pAttachments(stack.longs(imageViews[i]))
                        .width(framebufferWidth)
                        .height(framebufferHeight)
                        .layers(1);
                check(VK10.vkCreateFramebuffer(device, info, null, result), "Create framebuffer");
                framebuffers[i] = result.get(0);
            }
        }
    }

    private void createCommands(int imageCount) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer result = stack.mallocLong(1);
            VkCommandPoolCreateInfo pool = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .queueFamilyIndex(queueFamily);
            check(VK10.vkCreateCommandPool(device, pool, null, result), "Create command pool");
            commandPool = result.get(0);

            VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(commandPool)
                    .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(imageCount);
            PointerBuffer commands = stack.mallocPointer(imageCount);
            check(VK10.vkAllocateCommandBuffers(device, allocate, commands), "Allocate command buffers");
            commandBuffers = new VkCommandBuffer[imageCount];
            renderFinished = new long[imageCount];
            VkSemaphoreCreateInfo semaphore = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            for (int i = 0; i < imageCount; i++) {
                commandBuffers[i] = new VkCommandBuffer(commands.get(i), device);
                // Acquisition of this image guarantees its previous presentation wait has completed.
                check(VK10.vkCreateSemaphore(device, semaphore, null, result), "Create presentation semaphore");
                renderFinished[i] = result.get(0);
            }
        }
    }

    private void recordCommandBuffer(VkCommandBuffer command, long framebuffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack).sType$Default();
            check(VK10.vkBeginCommandBuffer(command, begin), "Begin command buffer");

            VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
            clearValues.get(0).color()
                    .float32(0, 0.08f)
                    .float32(1, 0.09f)
                    .float32(2, 0.11f)
                    .float32(3, 1.0f);
            VkRenderPassBeginInfo renderPassBegin = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass)
                    .framebuffer(framebuffer)
                    .pClearValues(clearValues);
            renderPassBegin.renderArea().offset().set(0, 0);
            renderPassBegin.renderArea().extent().set(framebufferWidth, framebufferHeight);

            VK10.vkCmdBeginRenderPass(command, renderPassBegin, VK10.VK_SUBPASS_CONTENTS_INLINE);
            backend.beginFrame(command);
            backend.draw(DemoScene.create(logicalWidth, logicalHeight, sceneTexture, sceneTexture));
            backend.endFrame();
            VK10.vkCmdEndRenderPass(command);
            check(VK10.vkEndCommandBuffer(command), "End command buffer");
        }
    }

    private void destroySwapchain() {
        if (framebuffers != null) {
            for (long framebuffer : framebuffers) {
                if (framebuffer != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroyFramebuffer(device, framebuffer, null);
                }
            }
            framebuffers = null;
        }
        if (imageViews != null) {
            for (long imageView : imageViews) {
                if (imageView != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroyImageView(device, imageView, null);
                }
            }
            imageViews = null;
        }
        if (commandPool != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyCommandPool(device, commandPool, null);
            commandPool = VK10.VK_NULL_HANDLE;
            commandBuffers = null;
        }
        if (renderFinished != null) {
            for (long semaphore : renderFinished) {
                if (semaphore != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroySemaphore(device, semaphore, null);
                }
            }
            renderFinished = null;
        }
        if (swapchain != VK10.VK_NULL_HANDLE) {
            KHRSwapchain.vkDestroySwapchainKHR(device, swapchain, null);
            swapchain = VK10.VK_NULL_HANDLE;
        }
    }

    private static void check(int result, String operation) {
        if (result != VK10.VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with VkResult " + result + ".");
        }
    }
}
