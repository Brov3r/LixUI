package com.lixui.demo;

import org.lwjgl.glfw.GLFW;

/**
 * Displays an empty window rendered and presented with Vulkan.
 */
public final class VulkanDemo {
    private VulkanDemo() {
    }

    /**
     * Runs the Vulkan demo until its window is closed.
     *
     * @param args command-line arguments, currently unused
     * @throws IllegalStateException if window initialization or a Vulkan operation fails
     */
    public static void main(String[] args) {
        try (DemoWindow window = new DemoWindow("LixUI Demo - Vulkan", GLFW.GLFW_NO_API);
                VulkanRenderer renderer = new VulkanRenderer(window.handle())) {
            window.show();
            while (!window.shouldClose()) {
                GLFW.glfwPollEvents();
                if (!renderer.render()) {
                    GLFW.glfwWaitEventsTimeout(0.1);
                }
            }
        }
    }
}
