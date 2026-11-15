package com.lixui.demo;

import com.lixui.backend.opengl.OpenGlBackend;
import com.lixui.render.DrawList;
import com.lixui.render.ImageData;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
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
        try (DemoWindow window = new DemoWindow("LixUI Demo - OpenGL", GLFW.GLFW_OPENGL_API, placement);
                DemoInput input = new DemoInput(window.handle())) {
            try {
                GLFW.glfwMakeContextCurrent(window.handle());
                GL.createCapabilities();
                GLFW.glfwSwapInterval(1);
                GL11.glClearColor(0.08f, 0.09f, 0.11f, 1.0f);
                try (OpenGlBackend backend = new OpenGlBackend()) {
                    ImageData image = DemoTextures.checkerboard();
                    Texture texture = backend.createTexture(image);
                    int hostTexture = uploadHostTexture(image);
                    try {
                        Texture wrapped = backend.wrapTexture(hostTexture, image.width(), image.height());
                        window.show();

                        try (MemoryStack stack = MemoryStack.stackPush()) {
                            IntBuffer width = stack.mallocInt(1);
                            IntBuffer height = stack.mallocInt(1);
                            IntBuffer logicalWidth = stack.mallocInt(1);
                            IntBuffer logicalHeight = stack.mallocInt(1);
                            while (!window.shouldClose()) {
                                GLFW.glfwPollEvents();
                                input.poll();
                                window.framebufferSize(width, height);
                                window.windowSize(logicalWidth, logicalHeight);
                                int framebufferWidth = width.get(0);
                                int framebufferHeight = height.get(0);
                                int viewportWidth = logicalWidth.get(0);
                                int viewportHeight = logicalHeight.get(0);
                                if (framebufferWidth > 0 && framebufferHeight > 0
                                        && viewportWidth > 0 && viewportHeight > 0) {
                                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                                    backend.resize(new Viewport(
                                            framebufferWidth, framebufferHeight, viewportWidth, viewportHeight));
                                    DrawList drawList = DemoScene.create(
                                            viewportWidth, viewportHeight, texture, wrapped);
                                    backend.draw(drawList);
                                }
                                GLFW.glfwSwapBuffers(window.handle());
                            }
                        }
                    } finally {
                        GL11.glDeleteTextures(hostTexture);
                    }
                }
            } finally {
                GL.setCapabilities(null);
            }
        }
    }

    private static int uploadHostTexture(ImageData image) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        ByteBuffer pixels = MemoryUtil.memAlloc(image.pixels().length);
        try {
            pixels.put(image.pixels()).flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, image.width(), image.height(), 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        } finally {
            MemoryUtil.memFree(pixels);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
        return texture;
    }
}
