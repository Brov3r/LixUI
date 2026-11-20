package com.lixui.glfw;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlfwPlatformTest {
    @Test
    void copiesTextLargerThanTheDefaultMemoryStack() {
        Assumptions.assumeTrue(GLFW.glfwInit(), "GLFW display unavailable.");
        long window = 0;
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
            window = GLFW.glfwCreateWindow(64, 64, "LixUI clipboard test", 0, 0);
            Assumptions.assumeTrue(window != 0, "GLFW window unavailable.");
            try (GlfwPlatform platform = GlfwPlatform.install(window)) {
                String text = "x".repeat(256 * 1024);

                platform.setClipboardText(text);

                assertEquals(text, platform.clipboardText());
            }
        } finally {
            if (window != 0) {
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
        }
    }
}
