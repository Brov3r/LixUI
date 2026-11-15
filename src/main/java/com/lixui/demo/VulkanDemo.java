package com.lixui.demo;

import com.lixui.glfw.GlfwPlatform;
import com.lixui.input.InputState;
import org.lwjgl.glfw.GLFW;

/**
 * Displays an empty window rendered and presented with Vulkan.
 */
public final class VulkanDemo {
    private VulkanDemo() {
    }

    /**
     * Runs the Vulkan demo until its window is closed.
     * Accepts an optional {@code --placement=left|right} argument to occupy half of the monitor.
     *
     * @param args command-line arguments
     * @throws IllegalStateException if window initialization or a Vulkan operation fails
     * @throws IllegalArgumentException if the placement argument is unknown
     */
    public static void main(String[] args) {
        DemoPlacement placement = DemoPlacement.fromArgs(args);
        InputState input = new InputState();
        try (DemoWindow window = new DemoWindow("LixUI Demo - Vulkan", GLFW.GLFW_NO_API, placement);
                DemoInput inputAdapter = new DemoInput(window.handle(), input);
                GlfwPlatform platform = GlfwPlatform.install(window.handle());
                VulkanRenderer renderer = new VulkanRenderer(window.handle(), input, platform)) {
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
