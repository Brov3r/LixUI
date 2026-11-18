package com.lixui.glfw;

import com.lixui.input.CharTyped;
import com.lixui.input.FocusChanged;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.KeyPressed;
import com.lixui.input.KeyReleased;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.PointerMoved;
import com.lixui.input.PointerPressed;
import com.lixui.input.PointerReleased;
import com.lixui.input.PointerScrolled;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWWindowFocusCallback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlfwInputTest {
    @Test
    void translatesNativeCallbacksInOrderAndRestoresHostCallbacks() {
        Assumptions.assumeTrue(GLFW.glfwInit(), "GLFW display unavailable.");
        long window = 0;
        int[] hostCalls = {0};
        try (GLFWKeyCallback host = GLFWKeyCallback.create((handle, key, scan, action, mods) -> hostCalls[0]++)) {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
            window = GLFW.glfwCreateWindow(64, 64, "LixUI input test", 0, 0);
            Assumptions.assumeTrue(window != 0, "GLFW window unavailable.");
            GLFW.glfwSetKeyCallback(window, host);
            InputState input = new InputState();
            Modifiers modifiers = new Modifiers(true, true, false, false);
            try (GlfwInput adapter = GlfwInput.install(window, input)) {
                GLFWCursorPosCallback move = GLFW.glfwSetCursorPosCallback(window, null);
                GLFW.glfwSetCursorPosCallback(window, move);
                GLFWMouseButtonCallback mouse = GLFW.glfwSetMouseButtonCallback(window, null);
                GLFW.glfwSetMouseButtonCallback(window, mouse);
                GLFWScrollCallback scroll = GLFW.glfwSetScrollCallback(window, null);
                GLFW.glfwSetScrollCallback(window, scroll);
                GLFWKeyCallback key = GLFW.glfwSetKeyCallback(window, null);
                GLFW.glfwSetKeyCallback(window, key);
                GLFWCharCallback character = GLFW.glfwSetCharCallback(window, null);
                GLFW.glfwSetCharCallback(window, character);
                GLFWWindowFocusCallback focus = GLFW.glfwSetWindowFocusCallback(window, null);
                GLFW.glfwSetWindowFocusCallback(window, focus);

                int mods = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL;
                move.invoke(window, 12.5, 24.25);
                mouse.invoke(window, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, mods);
                scroll.invoke(window, -1, 2);
                mouse.invoke(window, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, mods);
                key.invoke(window, GLFW.GLFW_KEY_A, 0, GLFW.GLFW_REPEAT, mods);
                key.invoke(window, GLFW.GLFW_KEY_A, 0, GLFW.GLFW_RELEASE, mods);
                character.invoke(window, 0x1F600);
                focus.invoke(window, false);

                assertEquals(new PointerMoved(12.5f, 24.25f), input.poll());
                assertEquals(new PointerPressed(12.5f, 24.25f, MouseButton.LEFT, modifiers), input.poll());
                assertEquals(new PointerScrolled(12.5f, 24.25f, -1, 2), input.poll());
                assertEquals(new PointerReleased(12.5f, 24.25f, MouseButton.LEFT, modifiers), input.poll());
                assertEquals(new KeyPressed(Key.A, modifiers, true), input.poll());
                assertEquals(new KeyReleased(Key.A, modifiers), input.poll());
                assertEquals(new CharTyped(0x1F600), input.poll());
                assertEquals(new FocusChanged(false), input.poll());
                assertNull(input.poll());
                assertEquals(2, hostCalls[0]);
            }
            GLFWKeyCallback restored = GLFW.glfwSetKeyCallback(window, null);
            assertEquals(host.address(), restored.address());
            assertNull(GLFW.glfwSetCursorPosCallback(window, null));
            assertNull(GLFW.glfwSetMouseButtonCallback(window, null));
            assertNull(GLFW.glfwSetScrollCallback(window, null));
            assertNull(GLFW.glfwSetCharCallback(window, null));
            assertNull(GLFW.glfwSetWindowFocusCallback(window, null));
        } finally {
            if (window != 0) {
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
        }
    }
}
