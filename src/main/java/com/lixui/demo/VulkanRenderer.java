package com.lixui.demo;

import com.lixui.backend.vulkan.VulkanBackend;
import com.lixui.backend.vulkan.VulkanLayer;
import com.lixui.input.InputState;
import com.lixui.platform.UiPlatform;
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
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
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
    private static final int SAMPLE_COUNT = VK10.VK_SAMPLE_COUNT_4_BIT;

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
    private long multisampleImage;
    private long multisampleMemory;
    private long multisampleView;
    private int depthStencilFormat;
    private long depthStencilImage;
    private long depthStencilMemory;
    private long depthStencilView;
    private long commandPool;
    private VkCommandBuffer[] commandBuffers;
    private long imageAvailable;
    private long[] renderFinished;
    private long frameFence;
    private VulkanBackend backend;
    private VulkanLayer layer;
    private DemoWidgetScene scene;
    private long previousFrameNanos;
    private int framebufferWidth;
    private int framebufferHeight;
    private int logicalWidth;
    private int logicalHeight;
    private boolean swapchainDirty = true;

    VulkanRenderer(long window, InputState input, UiPlatform platform) {
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
            requireFourSampleSupport();
            createDevice();
            chooseSurfaceFormat();
            depthStencilFormat = chooseDepthStencilFormat();
            createRenderPass();
            layer = new VulkanLayer(physicalDevice, device, queue, queueFamily, renderPass, SAMPLE_COUNT);
            backend = layer.backend();
            scene = new DemoWidgetScene(input, layer, platform);
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

            long now = System.nanoTime();
            float deltaSeconds = previousFrameNanos == 0
                    ? 0
                    : (now - previousFrameNanos) / 1_000_000_000f;
            previousFrameNanos = now;
            check(VK10.vkWaitForFences(device, frameFence, true, -1L), "Wait for frame");
            scene.resize(new Viewport(framebufferWidth, framebufferHeight,
                    logicalWidth, logicalHeight));
            scene.update(deltaSeconds);

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
     * Device teardown still completes when an owned layer or scene fails to close; the first failure
     * is rethrown afterwards.
     *
     * @throws RuntimeException if closing the scene or the UI layer fails
     */
    @Override
    public void close() {
        RuntimeException failure = null;
        if (device != null) {
            // Cleanup must also proceed after device loss.
            VK10.vkDeviceWaitIdle(device);
            if (scene != null) {
                try {
                    scene.close();
                } catch (RuntimeException exception) {
                    failure = recordFailure(failure, exception);
                }
                scene = null;
            }
            if (layer != null) {
                try {
                    layer.close();
                } catch (RuntimeException exception) {
                    failure = recordFailure(failure, exception);
                }
                layer = null;
                backend = null;
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
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException recordFailure(RuntimeException failure, RuntimeException exception) {
        if (failure == null) {
            return exception;
        }
        failure.addSuppressed(exception);
        return failure;
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
        if (!hasDeviceExtension(candidate, KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME)) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
            check(KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(candidate, surface, capabilities),
                    "Query surface capabilities");
            check(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(candidate, surface, count, null),
                    "Count surface formats");
            return count.get(0) > 0
                    && (capabilities.supportedUsageFlags() & VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) != 0;
        }
    }

    /**
     * Checks whether the physical device exposes a device extension.
     *
     * <p>Drivers can report several hundred extensions, so the property array is allocated on the heap
     * instead of the memory stack, whose default size is too small for drivers with large extension lists.</p>
     */
    private static boolean hasDeviceExtension(VkPhysicalDevice candidate, String extensionName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            VkExtensionProperties.Buffer extensions = null;
            try {
                int result;
                while (true) {
                    check(VK10.vkEnumerateDeviceExtensionProperties(candidate, (String) null, count, null),
                            "Count device extensions");
                    extensions = VkExtensionProperties.calloc(count.get(0));
                    result = VK10.vkEnumerateDeviceExtensionProperties(candidate, (String) null, count, extensions);
                    if (result != VK10.VK_INCOMPLETE) {
                        break;
                    }
                    extensions.free();
                    extensions = null;
                }
                check(result, "Enumerate device extensions");
                for (int i = 0; i < count.get(0); i++) {
                    if (extensionName.equals(extensions.get(i).extensionNameString())) {
                        return true;
                    }
                }
                return false;
            } finally {
                if (extensions != null) {
                    extensions.free();
                }
            }
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

    private int chooseDepthStencilFormat() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties properties = VkFormatProperties.calloc(stack);
            int[] candidates = {
                    VK10.VK_FORMAT_D24_UNORM_S8_UINT,
                    VK10.VK_FORMAT_D32_SFLOAT_S8_UINT,
                    VK10.VK_FORMAT_D16_UNORM_S8_UINT
            };
            for (int format : candidates) {
                VK10.vkGetPhysicalDeviceFormatProperties(physicalDevice, format, properties);
                if ((properties.optimalTilingFeatures()
                        & VK10.VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0) {
                    return format;
                }
            }
        }
        throw new IllegalStateException("No depth/stencil format is supported.");
    }

    private void requireFourSampleSupport() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(physicalDevice, properties);
            int colorSamples = properties.limits().framebufferColorSampleCounts();
            int depthSamples = properties.limits().framebufferDepthSampleCounts();
            if ((colorSamples & VK10.VK_SAMPLE_COUNT_4_BIT) == 0
                    || (depthSamples & VK10.VK_SAMPLE_COUNT_4_BIT) == 0) {
                throw new IllegalStateException("The selected Vulkan device does not support 4x MSAA.");
            }
        }
    }

    private void createRenderPass() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(3, stack);
            attachments.get(0)
                    .format(surfaceFormat)
                    .samples(SAMPLE_COUNT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            attachments.get(1)
                    .format(depthStencilFormat)
                    .samples(SAMPLE_COUNT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            attachments.get(2)
                    .format(surfaceFormat)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            VkAttachmentReference.Buffer colorReference = VkAttachmentReference.calloc(1, stack);
            colorReference.get(0)
                    .attachment(0)
                    .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            VkAttachmentReference.Buffer resolveReference = VkAttachmentReference.calloc(1, stack);
            resolveReference.get(0)
                    .attachment(2)
                    .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            VkAttachmentReference depthReference = VkAttachmentReference.calloc(stack)
                    .attachment(1)
                    .layout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.get(0)
                    .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorReference)
                    .pResolveAttachments(resolveReference)
                    .pDepthStencilAttachment(depthReference);

            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack);
            dependency.get(0)
                    .srcSubpass(VK10.VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                            | VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                    .dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                            | VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                    .srcAccessMask(0)
                    .dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);

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
            createMultisampleColor();
            createDepthStencil();
            createFramebuffers(images.remaining());
            createCommands(images.remaining());
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

    private void createMultisampleColor() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK10.VK_IMAGE_TYPE_2D)
                    .format(surfaceFormat)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(SAMPLE_COUNT)
                    .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().set(framebufferWidth, framebufferHeight, 1);
            LongBuffer result = stack.mallocLong(1);
            check(VK10.vkCreateImage(device, imageInfo, null, result), "Create multisample color image");
            multisampleImage = result.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
            VK10.vkGetImageMemoryRequirements(device, multisampleImage, requirements);
            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(),
                            VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
            check(VK10.vkAllocateMemory(device, allocateInfo, null, result),
                    "Allocate multisample color memory");
            multisampleMemory = result.get(0);
            check(VK10.vkBindImageMemory(device, multisampleImage, multisampleMemory, 0),
                    "Bind multisample color memory");

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(multisampleImage)
                    .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                    .format(surfaceFormat);
            viewInfo.subresourceRange()
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .levelCount(1)
                    .layerCount(1);
            check(VK10.vkCreateImageView(device, viewInfo, null, result),
                    "Create multisample color view");
            multisampleView = result.get(0);
        }
    }

    private void createDepthStencil() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK10.VK_IMAGE_TYPE_2D)
                    .format(depthStencilFormat)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(SAMPLE_COUNT)
                    .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().set(framebufferWidth, framebufferHeight, 1);
            LongBuffer result = stack.mallocLong(1);
            check(VK10.vkCreateImage(device, imageInfo, null, result), "Create depth/stencil image");
            depthStencilImage = result.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
            VK10.vkGetImageMemoryRequirements(device, depthStencilImage, requirements);
            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(),
                            VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
            check(VK10.vkAllocateMemory(device, allocateInfo, null, result),
                    "Allocate depth/stencil memory");
            depthStencilMemory = result.get(0);
            check(VK10.vkBindImageMemory(device, depthStencilImage, depthStencilMemory, 0),
                    "Bind depth/stencil memory");

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(depthStencilImage)
                    .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                    .format(depthStencilFormat);
            viewInfo.subresourceRange()
                    .aspectMask(VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT)
                    .levelCount(1)
                    .layerCount(1);
            check(VK10.vkCreateImageView(device, viewInfo, null, result),
                    "Create depth/stencil view");
            depthStencilView = result.get(0);
        }
    }

    private int findMemoryType(int typeFilter, int properties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties memoryProperties =
                    VkPhysicalDeviceMemoryProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
            for (int index = 0; index < memoryProperties.memoryTypeCount(); index++) {
                if ((typeFilter & (1 << index)) != 0
                        && (memoryProperties.memoryTypes(index).propertyFlags() & properties) == properties) {
                    return index;
                }
            }
        }
        throw new IllegalStateException("No suitable memory type.");
    }

    private void createFramebuffers(int imageCount) {
        framebuffers = new long[imageCount];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer result = stack.mallocLong(1);
            for (int i = 0; i < imageCount; i++) {
                VkFramebufferCreateInfo info = VkFramebufferCreateInfo.calloc(stack)
                        .sType$Default()
                        .renderPass(renderPass)
                        .attachmentCount(3)
                        .pAttachments(stack.longs(multisampleView, depthStencilView, imageViews[i]))
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
            // Image command buffers are re-recorded every frame, so their implicit reset is required.
            VkCommandPoolCreateInfo pool = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .queueFamilyIndex(queueFamily)
                    .flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
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

            layer.beginFrame(command);
            try {
                scene.render();
            } finally {
                layer.endFrame();
            }

            VkClearValue.Buffer clearValues = VkClearValue.calloc(3, stack);
            clearValues.get(0).color()
                    .float32(0, 0.102f)
                    .float32(1, 0.102f)
                    .float32(2, 0.102f)
                    .float32(3, 1.0f);
            clearValues.get(1).depthStencil()
                    .depth(1.0f)
                    .stencil(0);
            clearValues.get(2).color()
                    .float32(0, 0.102f)
                    .float32(1, 0.102f)
                    .float32(2, 0.102f)
                    .float32(3, 1.0f);
            VkRenderPassBeginInfo renderPassBegin = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass)
                    .framebuffer(framebuffer)
                    .pClearValues(clearValues);
            renderPassBegin.renderArea().offset().set(0, 0);
            renderPassBegin.renderArea().extent().set(framebufferWidth, framebufferHeight);

            VK10.vkCmdBeginRenderPass(command, renderPassBegin, VK10.VK_SUBPASS_CONTENTS_INLINE);
            layer.composite(command);
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
        if (depthStencilView != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImageView(device, depthStencilView, null);
            depthStencilView = VK10.VK_NULL_HANDLE;
        }
        if (depthStencilImage != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device, depthStencilImage, null);
            depthStencilImage = VK10.VK_NULL_HANDLE;
        }
        if (depthStencilMemory != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeMemory(device, depthStencilMemory, null);
            depthStencilMemory = VK10.VK_NULL_HANDLE;
        }
        if (multisampleView != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImageView(device, multisampleView, null);
            multisampleView = VK10.VK_NULL_HANDLE;
        }
        if (multisampleImage != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device, multisampleImage, null);
            multisampleImage = VK10.VK_NULL_HANDLE;
        }
        if (multisampleMemory != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeMemory(device, multisampleMemory, null);
            multisampleMemory = VK10.VK_NULL_HANDLE;
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
