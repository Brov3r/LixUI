package com.lixui.demo;

import com.lixui.glfw.GlfwInput;
import com.lixui.input.InputState;

/**
 * Installs the GLFW input adapter for the widget demo and restores the previous callbacks on close.
 */
final class DemoInput implements AutoCloseable {
    private final GlfwInput adapter;

    DemoInput(long window, InputState input) {
        adapter = GlfwInput.install(window, input);
    }

    /**
     * Restores the window callbacks installed by the input adapter.
     */
    @Override
    public void close() {
        adapter.close();
    }
}
