package com.lixui.demo;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

final class DemoWindow implements AutoCloseable {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    private final GLFWErrorCallback errorCallback;
    private long handle;
    private boolean closed;

    DemoWindow(String title, int clientApi) {
        errorCallback = GLFWErrorCallback.createPrint(System.err).set();
        try {
            if (!GLFW.glfwInit()) {
                throw new IllegalStateException("Failed to initialize GLFW.");
            }

            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, clientApi);
            if (clientApi == GLFW.GLFW_OPENGL_API) {
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
            }

            handle = GLFW.glfwCreateWindow(WIDTH, HEIGHT, title, MemoryUtil.NULL, MemoryUtil.NULL);
            if (handle == MemoryUtil.NULL) {
                throw new IllegalStateException("Failed to create the demo window.");
            }
            center();
        } catch (RuntimeException | Error exception) {
            close();
            throw exception;
        }
    }

    long handle() {
        return handle;
    }

    void show() {
        GLFW.glfwShowWindow(handle);
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

    private void center() {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor == MemoryUtil.NULL) {
            throw new IllegalStateException("No primary monitor is available.");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x = stack.mallocInt(1);
            IntBuffer y = stack.mallocInt(1);
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer windowWidth = stack.mallocInt(1);
            IntBuffer windowHeight = stack.mallocInt(1);

            GLFW.glfwGetMonitorWorkarea(monitor, x, y, width, height);
            GLFW.glfwGetWindowSize(handle, windowWidth, windowHeight);
            GLFW.glfwSetWindowPos(handle,
                    x.get(0) + (width.get(0) - windowWidth.get(0)) / 2,
                    y.get(0) + (height.get(0) - windowHeight.get(0)) / 2);
        }
    }
}
