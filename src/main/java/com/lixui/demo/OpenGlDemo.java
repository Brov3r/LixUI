package com.lixui.demo;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

/**
 * Displays an empty window rendered with OpenGL 4.5 Core.
 */
public final class OpenGlDemo {
    private OpenGlDemo() {
    }

    /**
     * Runs the OpenGL demo until its window is closed.
     *
     * @param args command-line arguments, currently unused
     * @throws IllegalStateException if the demo window cannot be initialized
     */
    public static void main(String[] args) {
        try (DemoWindow window = new DemoWindow("LixUI Demo - OpenGL", GLFW.GLFW_OPENGL_API)) {
            try {
                GLFW.glfwMakeContextCurrent(window.handle());
                GL.createCapabilities();
                GLFW.glfwSwapInterval(1);
                GL11.glClearColor(0.08f, 0.09f, 0.11f, 1.0f);
                window.show();

                while (!window.shouldClose()) {
                    GLFW.glfwPollEvents();
                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                    GLFW.glfwSwapBuffers(window.handle());
                }
            } finally {
                GL.setCapabilities(null);
            }
        }
    }
}
