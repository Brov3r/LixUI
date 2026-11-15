package com.lixui.demo;

import com.lixui.backend.opengl.OpenGlBackend;
import com.lixui.render.DrawList;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

/**
 * Displays the sample UI rendered with OpenGL 4.5 Core.
 */
public final class OpenGlDemo {
    private OpenGlDemo() {
    }

    /**
     * Runs the OpenGL demo until its window is closed.
     * Accepts an optional {@code --placement=left|right} argument to occupy half of the monitor.
     *
     * @param args command-line arguments
     * @throws IllegalStateException if the demo window cannot be initialized
     * @throws IllegalArgumentException if the placement argument is unknown
     */
    public static void main(String[] args) {
        DemoPlacement placement = DemoPlacement.fromArgs(args);
        try (DemoWindow window = new DemoWindow("LixUI Demo - OpenGL", GLFW.GLFW_OPENGL_API, placement)) {
            try {
                GLFW.glfwMakeContextCurrent(window.handle());
                GL.createCapabilities();
                GLFW.glfwSwapInterval(1);
                GL11.glClearColor(0.08f, 0.09f, 0.11f, 1.0f);
                try (OpenGlBackend backend = new OpenGlBackend()) {
                    window.show();

                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        IntBuffer width = stack.mallocInt(1);
                        IntBuffer height = stack.mallocInt(1);
                        while (!window.shouldClose()) {
                            GLFW.glfwPollEvents();
                            window.framebufferSize(width, height);
                            int framebufferWidth = width.get(0);
                            int framebufferHeight = height.get(0);
                            if (framebufferWidth > 0 && framebufferHeight > 0) {
                                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                                backend.resize(framebufferWidth, framebufferHeight);
                                DrawList drawList = DemoScene.create(framebufferWidth, framebufferHeight);
                                backend.draw(drawList);
                            }
                            GLFW.glfwSwapBuffers(window.handle());
                        }
                    }
                }
            } finally {
                GL.setCapabilities(null);
            }
        }
    }
}
