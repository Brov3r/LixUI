package com.lixui.widget.overlay;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.event.PointerMoved;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToastManagerTest {
    @Test
    void showsToastExpiresAndRemovesIt() {
        WidgetManager manager = manager();
        ToastManager toasts = new ToastManager(manager.context());
        ToastManager.Toast toast = toasts.show("Saved", 1);

        assertEquals(1, toasts.visibleCount());
        assertEquals("Saved", toast.message());
        assertTrue(toast.isVisible());

        manager.layout();
        assertFalse(toast.bounds().isEmpty());

        manager.update(0.6f);
        assertEquals(0.4f, toast.remainingSeconds(), 0.001f);
        assertEquals(1, toasts.visibleCount());

        manager.update(0.5f);
        manager.update(0.2f);

        assertEquals(0, toasts.visibleCount());
        assertFalse(toast.isVisible());
    }

    @Test
    void queuesAboveTheVisibleLimitAndPromotesOnExpiry() {
        WidgetManager manager = manager();
        ToastManager toasts = new ToastManager(manager.context());
        toasts.setMaxVisible(2);
        ToastManager.Toast first = toasts.show("First", 1);
        ToastManager.Toast second = toasts.show("Second", 5);
        ToastManager.Toast third = toasts.show("Third", 5);

        assertEquals(2, toasts.visibleCount());
        assertEquals(1, toasts.queuedCount());
        assertEquals(List.of("First", "Second"), messages(toasts));

        manager.update(1.0f);
        manager.update(0.2f);

        assertEquals(2, toasts.visibleCount());
        assertEquals(0, toasts.queuedCount());
        assertEquals(List.of("Second", "Third"), messages(toasts));
        assertFalse(first.isVisible());
        assertTrue(second.isVisible());
        assertTrue(third.isVisible());
    }

    @Test
    void hoverPausesTheCountdown() {
        WidgetManager manager = manager();
        ToastManager toasts = new ToastManager(manager.context());
        ToastManager.Toast toast = toasts.show("Hover me", 1);
        manager.layout();
        Rect bounds = toast.bounds();
        assertFalse(bounds.isEmpty());

        manager.handle(new PointerMoved(bounds.x() + bounds.width() / 2,
                bounds.y() + bounds.height() / 2));
        manager.update(0.6f);

        assertEquals(1, toast.remainingSeconds(), 0.001f);

        manager.handle(new PointerMoved(1, 1));
        manager.update(0.4f);

        assertEquals(0.6f, toast.remainingSeconds(), 0.001f);
    }

    @Test
    void dismissAllClearsTheQueueAndFadesVisibleToasts() {
        WidgetManager manager = manager();
        ToastManager toasts = new ToastManager(manager.context());
        toasts.setMaxVisible(1);
        ToastManager.Toast visible = toasts.show("Visible", 5);
        ToastManager.Toast queued = toasts.show("Queued", 5);
        manager.layout();

        toasts.dismissAll();
        manager.update(0.2f);

        assertEquals(0, toasts.visibleCount());
        assertEquals(0, toasts.queuedCount());
        assertFalse(visible.isVisible());
        assertFalse(queued.isVisible());
    }

    @Test
    void emptyLayerDoesNotBlockInputButCardsAreHoverable() {
        WidgetManager manager = manager();
        ToastManager toasts = new ToastManager(manager.context());
        manager.layout();

        assertFalse(manager.blocksPointer(10, 10));

        ToastManager.Toast toast = toasts.show("Interactive card", 2);
        manager.layout();
        Rect bounds = toast.bounds();
        assertNotNull(bounds);
        assertFalse(bounds.isEmpty());
        assertTrue(manager.blocksPointer(bounds.x() + bounds.width() / 2,
                bounds.y() + bounds.height() / 2));
    }

    private static List<String> messages(ToastManager toasts) {
        return toasts.visibleToasts().stream().map(ToastManager.Toast::message).toList();
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(TestViewports.LOGICAL);
        return manager;
    }
}
