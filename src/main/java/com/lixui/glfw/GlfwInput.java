package com.lixui.glfw;

import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCursorEnterCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWWindowFocusCallback;
import org.lwjgl.system.Callback;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;
import java.util.Objects;

/**
 * A GLFW input adapter that translates callbacks into backend-neutral {@link InputState} events.
 * Existing callbacks installed by the host are chained and restored on {@link #close()}.
 * Pointer coordinates are reported in logical UI units, matching the window content size.
 */
public final class GlfwInput implements AutoCloseable {
    private final long window;
    private final InputState input;
    private GLFWCursorPosCallback cursorPosCallback;
    private GLFWMouseButtonCallback mouseButtonCallback;
    private GLFWScrollCallback scrollCallback;
    private GLFWKeyCallback keyCallback;
    private GLFWCharCallback charCallback;
    private GLFWWindowFocusCallback focusCallback;
    private GLFWCursorEnterCallback cursorEnterCallback;
    private GLFWCursorPosCallback previousCursorPos;
    private GLFWMouseButtonCallback previousMouseButton;
    private GLFWScrollCallback previousScroll;
    private GLFWKeyCallback previousKey;
    private GLFWCharCallback previousChar;
    private GLFWWindowFocusCallback previousFocus;
    private GLFWCursorEnterCallback previousCursorEnter;
    private float pointerX;
    private float pointerY;
    private boolean closed;

    private GlfwInput(long window, InputState input) {
        this.window = window;
        this.input = input;
        readPointer();
    }

    /**
     * Installs the adapter on a window and starts forwarding events to the supplied input state.
     *
     * @param window GLFW window handle
     * @param input input state that receives the translated events
     * @return the installed adapter, which must be closed to restore the previous callbacks
     * @throws NullPointerException if {@code input} is null
     * @throws IllegalStateException if callback installation fails
     */
    public static GlfwInput install(long window, InputState input) {
        Objects.requireNonNull(input, "input");
        GlfwInput adapter = new GlfwInput(window, input);
        try {
            adapter.attach();
            return adapter;
        } catch (RuntimeException | Error exception) {
            adapter.close();
            throw exception;
        }
    }

    /**
     * Restores the callbacks that were installed before this adapter and releases its callbacks.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        GLFW.glfwSetCursorPosCallback(window, previousCursorPos);
        GLFW.glfwSetMouseButtonCallback(window, previousMouseButton);
        GLFW.glfwSetScrollCallback(window, previousScroll);
        GLFW.glfwSetKeyCallback(window, previousKey);
        GLFW.glfwSetCharCallback(window, previousChar);
        GLFW.glfwSetWindowFocusCallback(window, previousFocus);
        GLFW.glfwSetCursorEnterCallback(window, previousCursorEnter);
        free(cursorPosCallback);
        free(mouseButtonCallback);
        free(scrollCallback);
        free(keyCallback);
        free(charCallback);
        free(focusCallback);
        free(cursorEnterCallback);
        cursorPosCallback = null;
        mouseButtonCallback = null;
        scrollCallback = null;
        keyCallback = null;
        charCallback = null;
        focusCallback = null;
        cursorEnterCallback = null;
    }

    private void attach() {
        cursorPosCallback = GLFWCursorPosCallback.create(this::onCursorPos);
        mouseButtonCallback = GLFWMouseButtonCallback.create(this::onMouseButton);
        scrollCallback = GLFWScrollCallback.create(this::onScroll);
        keyCallback = GLFWKeyCallback.create(this::onKey);
        charCallback = GLFWCharCallback.create(this::onChar);
        focusCallback = GLFWWindowFocusCallback.create(this::onFocus);
        cursorEnterCallback = GLFWCursorEnterCallback.create(this::onCursorEnter);

        previousCursorPos = GLFW.glfwSetCursorPosCallback(window, cursorPosCallback);
        previousMouseButton = GLFW.glfwSetMouseButtonCallback(window, mouseButtonCallback);
        previousScroll = GLFW.glfwSetScrollCallback(window, scrollCallback);
        previousKey = GLFW.glfwSetKeyCallback(window, keyCallback);
        previousChar = GLFW.glfwSetCharCallback(window, charCallback);
        previousFocus = GLFW.glfwSetWindowFocusCallback(window, focusCallback);
        previousCursorEnter = GLFW.glfwSetCursorEnterCallback(window, cursorEnterCallback);
    }

    private void onCursorPos(long ignoredWindow, double xpos, double ypos) {
        pointerX = (float) xpos;
        pointerY = (float) ypos;
        input.pointerMoved(pointerX, pointerY);
        if (previousCursorPos != null) {
            previousCursorPos.invoke(ignoredWindow, xpos, ypos);
        }
    }

    private void onMouseButton(long ignoredWindow, int button, int action, int mods) {
        MouseButton mouseButton = mouseButton(button);
        if (mouseButton != null) {
            Modifiers modifiers = modifiers(mods);
            if (action == GLFW.GLFW_PRESS) {
                input.pointerPressed(pointerX, pointerY, mouseButton, modifiers);
            } else if (action == GLFW.GLFW_RELEASE) {
                input.pointerReleased(pointerX, pointerY, mouseButton, modifiers);
            }
        }
        if (previousMouseButton != null) {
            previousMouseButton.invoke(ignoredWindow, button, action, mods);
        }
    }

    private void onScroll(long ignoredWindow, double xoffset, double yoffset) {
        input.pointerScrolled(pointerX, pointerY, (float) xoffset, (float) yoffset);
        if (previousScroll != null) {
            previousScroll.invoke(ignoredWindow, xoffset, yoffset);
        }
    }

    private void onKey(long ignoredWindow, int key, int scancode, int action, int mods) {
        Key mappedKey = key(key);
        Modifiers modifiers = modifiers(mods);
        if (action == GLFW.GLFW_PRESS) {
            input.keyPressed(mappedKey, modifiers, false);
        } else if (action == GLFW.GLFW_REPEAT) {
            input.keyPressed(mappedKey, modifiers, true);
        } else if (action == GLFW.GLFW_RELEASE) {
            input.keyReleased(mappedKey, modifiers);
        }
        if (previousKey != null) {
            previousKey.invoke(ignoredWindow, key, scancode, action, mods);
        }
    }

    private void onChar(long ignoredWindow, int codepoint) {
        input.charTyped(codepoint);
        if (previousChar != null) {
            previousChar.invoke(ignoredWindow, codepoint);
        }
    }

    private void onFocus(long ignoredWindow, boolean focused) {
        input.focusChanged(focused);
        if (previousFocus != null) {
            previousFocus.invoke(ignoredWindow, focused);
        }
    }

    private void onCursorEnter(long ignoredWindow, boolean entered) {
        readPointer();
        if (entered) {
            input.pointerEntered(pointerX, pointerY);
        } else {
            input.pointerExited(pointerX, pointerY);
        }
        if (previousCursorEnter != null) {
            previousCursorEnter.invoke(ignoredWindow, entered);
        }
    }

    private void readPointer() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer xpos = stack.mallocDouble(1);
            DoubleBuffer ypos = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window, xpos, ypos);
            pointerX = (float) xpos.get(0);
            pointerY = (float) ypos.get(0);
        }
    }

    private static void free(Callback callback) {
        if (callback != null) {
            callback.free();
        }
    }

    private static Modifiers modifiers(int mods) {
        return new Modifiers(
                (mods & GLFW.GLFW_MOD_SHIFT) != 0,
                (mods & GLFW.GLFW_MOD_CONTROL) != 0,
                (mods & GLFW.GLFW_MOD_ALT) != 0,
                (mods & GLFW.GLFW_MOD_SUPER) != 0);
    }

    private static MouseButton mouseButton(int button) {
        return switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> MouseButton.LEFT;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> MouseButton.RIGHT;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> MouseButton.MIDDLE;
            case GLFW.GLFW_MOUSE_BUTTON_4 -> MouseButton.BUTTON_4;
            case GLFW.GLFW_MOUSE_BUTTON_5 -> MouseButton.BUTTON_5;
            case GLFW.GLFW_MOUSE_BUTTON_6 -> MouseButton.BUTTON_6;
            case GLFW.GLFW_MOUSE_BUTTON_7 -> MouseButton.BUTTON_7;
            case GLFW.GLFW_MOUSE_BUTTON_8 -> MouseButton.BUTTON_8;
            default -> null;
        };
    }

    private static Key key(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_A -> Key.A;
            case GLFW.GLFW_KEY_B -> Key.B;
            case GLFW.GLFW_KEY_C -> Key.C;
            case GLFW.GLFW_KEY_D -> Key.D;
            case GLFW.GLFW_KEY_E -> Key.E;
            case GLFW.GLFW_KEY_F -> Key.F;
            case GLFW.GLFW_KEY_G -> Key.G;
            case GLFW.GLFW_KEY_H -> Key.H;
            case GLFW.GLFW_KEY_I -> Key.I;
            case GLFW.GLFW_KEY_J -> Key.J;
            case GLFW.GLFW_KEY_K -> Key.K;
            case GLFW.GLFW_KEY_L -> Key.L;
            case GLFW.GLFW_KEY_M -> Key.M;
            case GLFW.GLFW_KEY_N -> Key.N;
            case GLFW.GLFW_KEY_O -> Key.O;
            case GLFW.GLFW_KEY_P -> Key.P;
            case GLFW.GLFW_KEY_Q -> Key.Q;
            case GLFW.GLFW_KEY_R -> Key.R;
            case GLFW.GLFW_KEY_S -> Key.S;
            case GLFW.GLFW_KEY_T -> Key.T;
            case GLFW.GLFW_KEY_U -> Key.U;
            case GLFW.GLFW_KEY_V -> Key.V;
            case GLFW.GLFW_KEY_W -> Key.W;
            case GLFW.GLFW_KEY_X -> Key.X;
            case GLFW.GLFW_KEY_Y -> Key.Y;
            case GLFW.GLFW_KEY_Z -> Key.Z;
            case GLFW.GLFW_KEY_0 -> Key.DIGIT_0;
            case GLFW.GLFW_KEY_1 -> Key.DIGIT_1;
            case GLFW.GLFW_KEY_2 -> Key.DIGIT_2;
            case GLFW.GLFW_KEY_3 -> Key.DIGIT_3;
            case GLFW.GLFW_KEY_4 -> Key.DIGIT_4;
            case GLFW.GLFW_KEY_5 -> Key.DIGIT_5;
            case GLFW.GLFW_KEY_6 -> Key.DIGIT_6;
            case GLFW.GLFW_KEY_7 -> Key.DIGIT_7;
            case GLFW.GLFW_KEY_8 -> Key.DIGIT_8;
            case GLFW.GLFW_KEY_9 -> Key.DIGIT_9;
            case GLFW.GLFW_KEY_ESCAPE -> Key.ESCAPE;
            case GLFW.GLFW_KEY_ENTER -> Key.ENTER;
            case GLFW.GLFW_KEY_TAB -> Key.TAB;
            case GLFW.GLFW_KEY_SPACE -> Key.SPACE;
            case GLFW.GLFW_KEY_BACKSPACE -> Key.BACKSPACE;
            case GLFW.GLFW_KEY_DELETE -> Key.DELETE;
            case GLFW.GLFW_KEY_INSERT -> Key.INSERT;
            case GLFW.GLFW_KEY_LEFT -> Key.LEFT;
            case GLFW.GLFW_KEY_RIGHT -> Key.RIGHT;
            case GLFW.GLFW_KEY_UP -> Key.UP;
            case GLFW.GLFW_KEY_DOWN -> Key.DOWN;
            case GLFW.GLFW_KEY_HOME -> Key.HOME;
            case GLFW.GLFW_KEY_END -> Key.END;
            case GLFW.GLFW_KEY_PAGE_UP -> Key.PAGE_UP;
            case GLFW.GLFW_KEY_PAGE_DOWN -> Key.PAGE_DOWN;
            case GLFW.GLFW_KEY_LEFT_SHIFT -> Key.SHIFT_LEFT;
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> Key.SHIFT_RIGHT;
            case GLFW.GLFW_KEY_LEFT_CONTROL -> Key.CONTROL_LEFT;
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> Key.CONTROL_RIGHT;
            case GLFW.GLFW_KEY_LEFT_ALT -> Key.ALT_LEFT;
            case GLFW.GLFW_KEY_RIGHT_ALT -> Key.ALT_RIGHT;
            case GLFW.GLFW_KEY_LEFT_SUPER -> Key.SUPER_LEFT;
            case GLFW.GLFW_KEY_RIGHT_SUPER -> Key.SUPER_RIGHT;
            case GLFW.GLFW_KEY_CAPS_LOCK -> Key.CAPS_LOCK;
            case GLFW.GLFW_KEY_F1 -> Key.F1;
            case GLFW.GLFW_KEY_F2 -> Key.F2;
            case GLFW.GLFW_KEY_F3 -> Key.F3;
            case GLFW.GLFW_KEY_F4 -> Key.F4;
            case GLFW.GLFW_KEY_F5 -> Key.F5;
            case GLFW.GLFW_KEY_F6 -> Key.F6;
            case GLFW.GLFW_KEY_F7 -> Key.F7;
            case GLFW.GLFW_KEY_F8 -> Key.F8;
            case GLFW.GLFW_KEY_F9 -> Key.F9;
            case GLFW.GLFW_KEY_F10 -> Key.F10;
            case GLFW.GLFW_KEY_F11 -> Key.F11;
            case GLFW.GLFW_KEY_F12 -> Key.F12;
            case GLFW.GLFW_KEY_MINUS -> Key.MINUS;
            case GLFW.GLFW_KEY_EQUAL -> Key.EQUAL;
            case GLFW.GLFW_KEY_LEFT_BRACKET -> Key.LEFT_BRACKET;
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> Key.RIGHT_BRACKET;
            case GLFW.GLFW_KEY_BACKSLASH -> Key.BACKSLASH;
            case GLFW.GLFW_KEY_SEMICOLON -> Key.SEMICOLON;
            case GLFW.GLFW_KEY_APOSTROPHE -> Key.APOSTROPHE;
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> Key.GRAVE;
            case GLFW.GLFW_KEY_COMMA -> Key.COMMA;
            case GLFW.GLFW_KEY_PERIOD -> Key.PERIOD;
            case GLFW.GLFW_KEY_SLASH -> Key.SLASH;
            case GLFW.GLFW_KEY_SCROLL_LOCK -> Key.SCROLL_LOCK;
            case GLFW.GLFW_KEY_NUM_LOCK -> Key.NUM_LOCK;
            case GLFW.GLFW_KEY_PRINT_SCREEN -> Key.PRINT_SCREEN;
            case GLFW.GLFW_KEY_PAUSE -> Key.PAUSE;
            case GLFW.GLFW_KEY_MENU -> Key.MENU;
            case GLFW.GLFW_KEY_F13 -> Key.F13;
            case GLFW.GLFW_KEY_F14 -> Key.F14;
            case GLFW.GLFW_KEY_F15 -> Key.F15;
            case GLFW.GLFW_KEY_F16 -> Key.F16;
            case GLFW.GLFW_KEY_F17 -> Key.F17;
            case GLFW.GLFW_KEY_F18 -> Key.F18;
            case GLFW.GLFW_KEY_F19 -> Key.F19;
            case GLFW.GLFW_KEY_F20 -> Key.F20;
            case GLFW.GLFW_KEY_F21 -> Key.F21;
            case GLFW.GLFW_KEY_F22 -> Key.F22;
            case GLFW.GLFW_KEY_F23 -> Key.F23;
            case GLFW.GLFW_KEY_F24 -> Key.F24;
            case GLFW.GLFW_KEY_F25 -> Key.F25;
            case GLFW.GLFW_KEY_KP_0 -> Key.KEYPAD_0;
            case GLFW.GLFW_KEY_KP_1 -> Key.KEYPAD_1;
            case GLFW.GLFW_KEY_KP_2 -> Key.KEYPAD_2;
            case GLFW.GLFW_KEY_KP_3 -> Key.KEYPAD_3;
            case GLFW.GLFW_KEY_KP_4 -> Key.KEYPAD_4;
            case GLFW.GLFW_KEY_KP_5 -> Key.KEYPAD_5;
            case GLFW.GLFW_KEY_KP_6 -> Key.KEYPAD_6;
            case GLFW.GLFW_KEY_KP_7 -> Key.KEYPAD_7;
            case GLFW.GLFW_KEY_KP_8 -> Key.KEYPAD_8;
            case GLFW.GLFW_KEY_KP_9 -> Key.KEYPAD_9;
            case GLFW.GLFW_KEY_KP_DECIMAL -> Key.KEYPAD_DECIMAL;
            case GLFW.GLFW_KEY_KP_DIVIDE -> Key.KEYPAD_DIVIDE;
            case GLFW.GLFW_KEY_KP_MULTIPLY -> Key.KEYPAD_MULTIPLY;
            case GLFW.GLFW_KEY_KP_SUBTRACT -> Key.KEYPAD_SUBTRACT;
            case GLFW.GLFW_KEY_KP_ADD -> Key.KEYPAD_ADD;
            case GLFW.GLFW_KEY_KP_ENTER -> Key.KEYPAD_ENTER;
            case GLFW.GLFW_KEY_KP_EQUAL -> Key.KEYPAD_EQUAL;
            case GLFW.GLFW_KEY_WORLD_1 -> Key.WORLD_1;
            case GLFW.GLFW_KEY_WORLD_2 -> Key.WORLD_2;
            default -> Key.UNKNOWN;
        };
    }
}
