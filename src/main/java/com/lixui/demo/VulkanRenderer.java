package com.lixui.demo;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

final class VulkanRenderer implements AutoCloseable {
    private final long window;
    private VkInstance instance;
    private long surface;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkQueue queue;
    private int queueFamily;
    private long swapchain;
    private long commandPool;
    private VkCommandBuffer[] commandBuffers;
    private long imageAvailable;
    private long[] renderFinished;
    private long frameFence;
    private int framebufferWidth;
    private int framebufferHeight;
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
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(imageAvailable))
                    .pWaitDstStageMask(stack.ints(VK10.VK_PIPELINE_STAGE_TRANSFER_BIT))
                    .pCommandBuffers(stack.pointers(commandBuffers[index].address()))
                    .pSignalSemaphores(stack.longs(renderFinished[index]));

            // Reset only after acquisition succeeds, otherwise an out-of-date swapchain would leave it unsignaled.
            check(VK10.vkResetFences(device, frameFence), "Reset frame fence");
            check(VK10.vkQueueSubmit(queue, submit, frameFence), "Submit clear commands");

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
            destroySwapchain();
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
                "No Vulkan device supports a combined graphics/present queue and transfer-cleared swapchain images.");
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
                    && (capabilities.supportedUsageFlags() & VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT) != 0;
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

            IntBuffer count = stack.mallocInt(1);
            check(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, count, null),
                    "Count surface formats");
            if (count.get(0) == 0) {
                throw new IllegalStateException("The surface has no Vulkan image formats.");
            }
            VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.malloc(count.get(0), stack);
            check(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, count, formats),
                    "Query surface formats");
            VkSurfaceFormatKHR format = formats.get(0);
            for (int i = 0; i < count.get(0); i++) {
                if (formats.get(i).format() == VK10.VK_FORMAT_B8G8R8A8_UNORM
                        && formats.get(i).colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                    format = formats.get(i);
                    break;
                }
            }
            int imageCount = capabilities.minImageCount() + 1;
            if (capabilities.maxImageCount() > 0) {
                imageCount = Math.min(imageCount, capabilities.maxImageCount());
            }

            destroySwapchain();
            VkSwapchainCreateInfoKHR info = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .surface(surface)
                    .minImageCount(imageCount)
                    .imageFormat(format.format() == VK10.VK_FORMAT_UNDEFINED
                            ? VK10.VK_FORMAT_B8G8R8A8_UNORM : format.format())
                    .imageColorSpace(format.colorSpace())
                    .imageArrayLayers(1)
                    .imageUsage(VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                    .imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .preTransform(capabilities.currentTransform())
                    .compositeAlpha(Integer.lowestOneBit(capabilities.supportedCompositeAlpha()))
                    .presentMode(KHRSurface.VK_PRESENT_MODE_FIFO_KHR)
                    .clipped(true);
            info.imageExtent().set(extentWidth, extentHeight);
            LongBuffer result = stack.mallocLong(1);
            check(KHRSwapchain.vkCreateSwapchainKHR(device, info, null, result), "Create swapchain");
            swapchain = result.get(0);

            check(KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, count, null), "Count swapchain images");
            LongBuffer images = stack.mallocLong(count.get(0));
            check(KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, count, images), "Get swapchain images");
            createCommands(images);
            framebufferWidth = width;
            framebufferHeight = height;
            swapchainDirty = false;
            return true;
        }
    }

    private void createCommands(LongBuffer images) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer result = stack.mallocLong(1);
            VkCommandPoolCreateInfo pool = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .queueFamilyIndex(queueFamily);
            check(VK10.vkCreateCommandPool(device, pool, null, result), "Create command pool");
            commandPool = result.get(0);

            int imageCount = images.remaining();
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
                recordClear(commandBuffers[i], images.get(i));
                // Acquisition of this image guarantees its previous presentation wait has completed.
                check(VK10.vkCreateSemaphore(device, semaphore, null, result), "Create presentation semaphore");
                renderFinished[i] = result.get(0);
            }
        }
    }

    private void recordClear(VkCommandBuffer command, long image) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack).sType$Default();
            check(VK10.vkBeginCommandBuffer(command, begin), "Begin clear commands");

            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0)
                    .sType$Default()
                    .oldLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                    .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange()
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .levelCount(1)
                    .layerCount(1);
            VK10.vkCmdPipelineBarrier(command, VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);

            VkClearColorValue color = VkClearColorValue.calloc(stack);
            color.float32(0, 0.08f).float32(1, 0.09f).float32(2, 0.11f).float32(3, 1.0f);
            VK10.vkCmdClearColorImage(command, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    color, barrier.get(0).subresourceRange());

            barrier.get(0)
                    .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                    .srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(0);
            VK10.vkCmdPipelineBarrier(command, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, null, null, barrier);
            check(VK10.vkEndCommandBuffer(command), "End clear commands");
        }
    }

    private void destroySwapchain() {
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
