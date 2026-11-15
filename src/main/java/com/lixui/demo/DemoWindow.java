package com.lixui.demo;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;

final class DemoWindow implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoWindow.class);
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;

    private final GLFWErrorCallback errorCallback;
    private long handle;
    private boolean closed;

    DemoWindow(String title, int clientApi, DemoPlacement placement) {
        errorCallback = GLFWErrorCallback.create((error, description) -> LOGGER.error(
                "GLFW error 0x{}: {}",
                Integer.toHexString(error),
                GLFWErrorCallback.getDescription(description))).set();
        try {
            if (!GLFW.glfwInit()) {
                throw new IllegalStateException("Failed to initialize GLFW.");
            }

            WindowBounds bounds = computeBounds(placement);
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, clientApi);
            if (clientApi == GLFW.GLFW_OPENGL_API) {
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
            }

            handle = GLFW.glfwCreateWindow(bounds.width(), bounds.height(), title,
                    MemoryUtil.NULL, MemoryUtil.NULL);
            if (handle == MemoryUtil.NULL) {
                throw new IllegalStateException("Failed to create the demo window.");
            }
            GLFW.glfwSetWindowPos(handle, bounds.x(), bounds.y());
        } catch (RuntimeException | Error exception) {
            close();
            throw exception;
        }
    }

    long handle() {
        return handle;
    }

    void framebufferSize(IntBuffer width, IntBuffer height) {
        GLFW.glfwGetFramebufferSize(handle, width, height);
    }

    void show() {
        GLFW.glfwShowWindow(handle);
        GLFW.glfwFocusWindow(handle);

        // Windows may deny foreground activation to a process launched by a build tool.
        // Toggling the topmost hint re-inserts the window at the top of the normal stack.
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE);
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_FLOATING, GLFW.GLFW_FALSE);
    }

    boolean shouldClose() {
        return GLFW.glfwWindowShouldClose(handle);
    }

    /**
     * Destroys the window and releases its GLFW resources.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (handle != MemoryUtil.NULL) {
            GLFW.glfwDestroyWindow(handle);
            handle = MemoryUtil.NULL;
        }
        GLFW.glfwTerminate();
        GLFW.glfwSetErrorCallback(null);
        errorCallback.free();
    }

    private static WindowBounds computeBounds(DemoPlacement placement) {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor == MemoryUtil.NULL) {
            throw new IllegalStateException("No primary monitor is available.");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x = stack.mallocInt(1);
            IntBuffer y = stack.mallocInt(1);
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            GLFW.glfwGetMonitorWorkarea(monitor, x, y, width, height);

            int workX = x.get(0);
            int workY = y.get(0);
            int workWidth = width.get(0);
            int workHeight = height.get(0);
            int windowWidth = Math.min(DEFAULT_WIDTH, workWidth);
            int windowHeight = Math.min(DEFAULT_HEIGHT, workHeight);
            int windowX = workX + (workWidth - windowWidth) / 2;
            int windowY = workY + (workHeight - windowHeight) / 2;

            if (placement != DemoPlacement.CENTERED) {
                int halfWidth = workWidth / 2;
                int halfHeight = Math.round(halfWidth * (float) DEFAULT_HEIGHT / DEFAULT_WIDTH);
                if (halfHeight > workHeight) {
                    halfHeight = workHeight;
                    halfWidth = Math.round(halfHeight * (float) DEFAULT_WIDTH / DEFAULT_HEIGHT);
                }
                windowWidth = Math.max(1, halfWidth);
                windowHeight = Math.max(1, halfHeight);
                windowX = placement == DemoPlacement.LEFT_HALF
                        ? workX
                        : workX + workWidth - windowWidth;
                windowY = workY + (workHeight - windowHeight) / 2;
            }
            return new WindowBounds(windowX, windowY, windowWidth, windowHeight);
        }
    }

    private record WindowBounds(int x, int y, int width, int height) {
    }
}
