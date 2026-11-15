package com.lixui.demo;

import com.lixui.backend.opengl.OpenGlBackend;
import com.lixui.glfw.GlfwPlatform;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

/**
 * Displays the widget demo rendered with OpenGL 4.5 Core.
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
        InputState input = new InputState();
        try (DemoWindow window = new DemoWindow("LixUI Demo - OpenGL", GLFW.GLFW_OPENGL_API, placement);
                DemoInput inputAdapter = new DemoInput(window.handle(), input);
                GlfwPlatform platform = GlfwPlatform.install(window.handle())) {
            try {
                GLFW.glfwMakeContextCurrent(window.handle());
                GL.createCapabilities();
                GLFW.glfwSwapInterval(1);
                GL11.glClearColor(0.08f, 0.09f, 0.11f, 1.0f);
                try (OpenGlBackend backend = new OpenGlBackend();
                        DemoWidgetScene scene = new DemoWidgetScene(input, backend, platform)) {
                    window.show();
                    double previousTime = GLFW.glfwGetTime();

                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        IntBuffer width = stack.mallocInt(1);
                        IntBuffer height = stack.mallocInt(1);
                        IntBuffer logicalWidth = stack.mallocInt(1);
                        IntBuffer logicalHeight = stack.mallocInt(1);
                        while (!window.shouldClose()) {
                            GLFW.glfwPollEvents();
                            double currentTime = GLFW.glfwGetTime();
                            float deltaSeconds = (float) (currentTime - previousTime);
                            previousTime = currentTime;
                            window.framebufferSize(width, height);
                            window.windowSize(logicalWidth, logicalHeight);
                            int framebufferWidth = width.get(0);
                            int framebufferHeight = height.get(0);
                            int viewportWidth = logicalWidth.get(0);
                            int viewportHeight = logicalHeight.get(0);
                            if (framebufferWidth > 0 && framebufferHeight > 0
                                    && viewportWidth > 0 && viewportHeight > 0) {
                                scene.resize(new Viewport(framebufferWidth, framebufferHeight,
                                        viewportWidth, viewportHeight));
                                scene.update(deltaSeconds);
                                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                                scene.render();
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
