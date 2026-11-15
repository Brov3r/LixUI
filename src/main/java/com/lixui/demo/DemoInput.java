package com.lixui.demo;

import com.lixui.glfw.GlfwInput;
import com.lixui.input.CharTyped;
import com.lixui.input.FocusChanged;
import com.lixui.input.InputEvent;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.KeyPressed;
import com.lixui.input.KeyReleased;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.PointerEntered;
import com.lixui.input.PointerExited;
import com.lixui.input.PointerMoved;
import com.lixui.input.PointerPressed;
import com.lixui.input.PointerReleased;
import com.lixui.input.PointerScrolled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Set;

/**
 * Logs translated GLFW input events to the console for manual verification.
 */
final class DemoInput implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoInput.class);

    private final InputState input = new InputState();
    private final GlfwInput adapter;
    private final Set<MouseButton> buttons = EnumSet.noneOf(MouseButton.class);
    private final Set<Key> keys = EnumSet.noneOf(Key.class);

    DemoInput(long window) {
        adapter = GlfwInput.install(window, input);
        LOGGER.info("Input logging enabled");
    }

    void poll() {
        InputEvent event;
        while ((event = input.poll()) != null) {
            log(event);
        }
    }

    /**
     * Restores the window callbacks installed by the input adapter.
     */
    @Override
    public void close() {
        adapter.close();
    }

    private static String formatModifiers(Modifiers modifiers) {
        return "shift=" + modifiers.shift()
                + " ctrl=" + modifiers.control()
                + " alt=" + modifiers.alt()
                + " super=" + modifiers.superKey();
    }

    private static String printable(int codepoint) {
        if (!Character.isValidCodePoint(codepoint)) {
            return "?";
        }
        if (Character.isISOControl(codepoint)) {
            return "\\u" + String.format(java.util.Locale.ROOT, "%04X", codepoint);
        }
        return new String(Character.toChars(codepoint));
    }

    private void log(InputEvent event) {
        switch (event) {
            case PointerMoved ignored -> {
            }
            case PointerEntered entered -> LOGGER.info("mouse entered at ({}, {})",
                    Math.round(entered.x()), Math.round(entered.y()));
            case PointerExited exited -> LOGGER.info("mouse exited at ({}, {})",
                    Math.round(exited.x()), Math.round(exited.y()));
            case PointerPressed pressed -> {
                buttons.add(pressed.button());
                LOGGER.info("mouse {} down at ({}, {}); buttons={}; {}",
                        pressed.button(), Math.round(pressed.x()), Math.round(pressed.y()), buttons,
                        formatModifiers(pressed.modifiers()));
            }
            case PointerReleased released -> {
                buttons.remove(released.button());
                LOGGER.info("mouse {} up at ({}, {}); buttons={}; {}",
                        released.button(), Math.round(released.x()), Math.round(released.y()), buttons,
                        formatModifiers(released.modifiers()));
            }
            case PointerScrolled scrolled -> LOGGER.info("scroll dx={} dy={} at ({}, {}); buttons={}",
                    scrolled.deltaX(), scrolled.deltaY(),
                    Math.round(scrolled.x()), Math.round(scrolled.y()), buttons);
            case KeyPressed pressed -> {
                if (!pressed.repeat()) {
                    keys.add(pressed.key());
                }
                LOGGER.info("key {} down (repeat={}); keys={}; {}",
                        pressed.key(), pressed.repeat(), keys, formatModifiers(pressed.modifiers()));
            }
            case KeyReleased released -> {
                keys.remove(released.key());
                LOGGER.info("key {} up; keys={}; {}",
                        released.key(), keys, formatModifiers(released.modifiers()));
            }
            case CharTyped typed -> LOGGER.info("char '{}' U+{}",
                    printable(typed.codepoint()),
                    Integer.toHexString(typed.codepoint()).toUpperCase(java.util.Locale.ROOT));
            case FocusChanged focus -> {
                if (focus.focused()) {
                    LOGGER.info("window focus gained");
                } else {
                    buttons.clear();
                    keys.clear();
                    LOGGER.info("window focus lost; buttons={} keys={}", buttons, keys);
                }
            }
        }
    }
}
