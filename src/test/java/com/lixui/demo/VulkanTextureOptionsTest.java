package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.platform.UiPlatform;
import com.lixui.render.ImageData;
import com.lixui.render.RenderBackend;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.render.TextureOptions;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanTextureOptionsTest {
    @Test
    void createsAndDrawsTexturesWithMipmapsAndTiling() throws ReflectiveOperationException {
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
            window = GLFW.glfwCreateWindow(64, 64, "LixUI Vulkan texture options", 0, 0);
            Assumptions.assumeTrue(window != 0, "Vulkan window unavailable.");
            try (VulkanRenderer renderer = new VulkanRenderer(window, new InputState(), UiPlatform.NONE)) {
                RenderBackend backend = (RenderBackend) field(renderer, "backend");
                WidgetManager manager = (WidgetManager) field(field(renderer, "scene"), "manager");
                for (Widget widget : List.copyOf(manager.widgets())) {
                    manager.remove(widget);
                }

                ImageData image = new ImageData(4, 4, filledPixels(4, 4));
                Texture mipmapped = manager.context().createTexture(image, TextureOptions.MIPMAPPED);
                Texture tiled = manager.context().createTexture(image, TextureOptions.REPEAT);
                assertEquals(TextureOptions.MIPMAPPED, mipmapped.options());
                assertEquals(TextureOptions.REPEAT, tiled.options());

                manager.add(new Widget() {
                    /**
                     * Draws both sampled textures inside the active Vulkan frame.
                     *
                     * @param drawing renderer recording this frame
                     * @param frame current frame timing
                     */
                    @Override
                    protected void onDraw(RenderManager drawing, FrameInfo frame) {
                        drawing.image(mipmapped, new Rect(0, 0, 32, 32));
                        drawing.image(tiled, new Rect(32, 0, 32, 32));
                    }
                });
                assertTrue(renderer.render());
                assertTrue(backend.drawCallCount() > 0);
            }
        } finally {
            if (window != 0) {
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
        }
    }

    private static byte[] filledPixels(int width, int height) {
        byte[] pixels = new byte[width * height * 4];
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = (byte) 255;
        }
        return pixels;
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
