package com.lixui.demo;

import com.lixui.backend.BatchScenario;
import com.lixui.input.InputState;
import com.lixui.platform.UiPlatform;
import com.lixui.render.ImageData;
import com.lixui.render.RenderBackend;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanRendererTest {
    @Test
    void createsPipelinesAndSubmitsFramesWithTheUpdatedShaders() throws ReflectiveOperationException {
        Assumptions.assumeTrue(GLFW.glfwInit(), "GLFW display unavailable.");
        long window = 0;
        try {
            Assumptions.assumeTrue(GLFWVulkan.glfwVulkanSupported(), "Vulkan unavailable.");
            PointerBuffer extensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            Assumptions.assumeTrue(extensions != null && extensions.remaining() > 0,
                    "Vulkan surface extensions unavailable.");
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
            window = GLFW.glfwCreateWindow(320, 240, "LixUI Vulkan test", 0, 0);
            Assumptions.assumeTrue(window != 0, "Vulkan window unavailable.");
            try (VulkanRenderer renderer = new VulkanRenderer(window, new InputState(), UiPlatform.NONE)) {
                RenderBackend backend = (RenderBackend) field(renderer, "backend");
                WidgetManager manager = (WidgetManager) field(field(renderer, "scene"), "manager");
                for (Widget widget : List.copyOf(manager.widgets())) {
                    manager.remove(widget);
                }
                Texture texture = manager.context().createTexture(new ImageData(1, 1,
                        new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255}));
                manager.add(new Widget() {
                    /**
                     * Emits the common batch scenario inside the active Vulkan render pass.
                     *
                     * @param drawing renderer recording this frame
                     * @param frame current frame timing
                     */
                    @Override
                    protected void onDraw(RenderManager drawing, FrameInfo frame) {
                        BatchScenario.draw(drawing, texture);
                    }
                });
                assertTrue(renderer.render());
                BatchScenario.verify(backend);
                assertTrue(renderer.render());
                BatchScenario.verify(backend);
            }
        } finally {
            if (window != 0) {
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
        }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
