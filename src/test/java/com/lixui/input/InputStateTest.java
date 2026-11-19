package com.lixui.input;

import com.lixui.input.event.CharTyped;
import com.lixui.input.event.FocusChanged;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.KeyReleased;
import com.lixui.input.event.PointerEntered;
import com.lixui.input.event.PointerExited;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.input.event.PointerScrolled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputStateTest {
    @Test
    void preservesEventOrderAndDrainsTheQueue() {
        InputState state = new InputState();
        state.pointerMoved(10, 20);
        state.pointerPressed(10, 20, MouseButton.LEFT, Modifiers.NONE);
        state.pointerReleased(10, 20, MouseButton.LEFT, Modifiers.NONE);
        state.pointerScrolled(10, 20, 0, 1);
        state.keyPressed(Key.A, Modifiers.NONE, false);
        state.keyReleased(Key.A, Modifiers.NONE);
        state.charTyped('a');
        state.focusChanged(true);

        assertEquals(new PointerMoved(10, 20), state.poll());
        assertEquals(new PointerPressed(10, 20, MouseButton.LEFT, Modifiers.NONE), state.poll());
        assertEquals(new PointerReleased(10, 20, MouseButton.LEFT, Modifiers.NONE), state.poll());
        assertEquals(new PointerScrolled(10, 20, 0, 1), state.poll());
        assertEquals(new KeyPressed(Key.A, Modifiers.NONE, false), state.poll());
        assertEquals(new KeyReleased(Key.A, Modifiers.NONE), state.poll());
        assertEquals(new CharTyped('a'), state.poll());
        assertEquals(new FocusChanged(true), state.poll());
        assertNull(state.poll());
        assertFalse(state.hasEvents());
    }

    @Test
    void supportsPointerEnterAndExitEvents() {
        InputState state = new InputState();
        state.pointerEntered(1, 2);
        state.pointerExited(3, 4);

        assertEquals(new PointerEntered(1, 2), state.poll());
        assertEquals(new PointerExited(3, 4), state.poll());
    }

    @Test
    void clearDropsQueuedEvents() {
        InputState state = new InputState();
        state.pointerMoved(1, 2);
        state.keyPressed(Key.ESCAPE, Modifiers.NONE, false);
        state.clear();

        assertFalse(state.hasEvents());
        assertNull(state.poll());
    }

    @Test
    void rejectsNullEventsAndMissingEventComponents() {
        InputState state = new InputState();

        assertThrows(NullPointerException.class, () -> state.add(null));
        assertThrows(NullPointerException.class,
                () -> state.pointerPressed(0, 0, null, Modifiers.NONE));
        assertThrows(NullPointerException.class,
                () -> state.pointerPressed(0, 0, MouseButton.LEFT, null));
        assertThrows(NullPointerException.class,
                () -> state.keyPressed(null, Modifiers.NONE, false));
        assertThrows(NullPointerException.class,
                () -> state.keyPressed(Key.A, null, false));
        assertThrows(NullPointerException.class,
                () -> state.keyReleased(null, Modifiers.NONE));
        assertFalse(state.hasEvents());
    }
}
