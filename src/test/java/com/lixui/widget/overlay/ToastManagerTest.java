package com.lixui.widget.overlay;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.event.PointerMoved;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.text.TextLayout;
import com.lixui.text.TextLine;
import com.lixui.text.TextOverflow;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.image.ImageSource;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToastManagerTest {
    private static final String LONG_MESSAGE = "A long notification wraps across several lines and "
            + "then stops at the configured line limit so the remaining words never make the card "
            + "grow beyond the available stack area.";

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

    @Test
    void optionsCarryTypeTitleIconAndAccent() {
        WidgetManager manager = manager();
        manager.setDefaultFont(font(manager));
        ToastManager toasts = new ToastManager(manager.context());
        Color accent = Color.hex("#3366CC");
        ToastManager.Toast toast = toasts.show(new ToastManager.Options("Saved")
                .type(ToastManager.Type.SUCCESS)
                .title("All set")
                .icon(ImageSource.icon("heart.svg"))
                .accent(accent)
                .duration(5));
        ToastManager.Toast plain = toasts.show("Saved", 5);
        manager.layout();

        assertEquals(ToastManager.Type.SUCCESS, toast.type());
        assertEquals("All set", toast.title());
        assertEquals(ImageSource.icon("heart.svg"), toast.icon());
        assertEquals(accent, toast.accent());
        assertEquals(ImageSource.icon("info.svg"), plain.icon());
        assertTrue(toast.bounds().height() > plain.bounds().height(),
                "a title row must add height above the plain message");
    }

    @Test
    void maxLinesTruncatesTheMessageWithAnEllipsis() {
        WidgetManager manager = manager();
        manager.setDefaultFont(font(manager));
        ToastManager toasts = new ToastManager(manager.context());
        ToastManager.Toast toast = toasts.show(new ToastManager.Options(LONG_MESSAGE)
                .maxWidth(140)
                .maxLines(2)
                .duration(5));
        manager.layout();

        TextLayout layout = toast.messageLayout();
        assertNotNull(layout);
        assertEquals(2, layout.lines().size());
        assertEquals(TextOverflow.ELLIPSIS, layout.style().overflow());
        assertTrue(layout.lines().get(1).end() < LONG_MESSAGE.length(),
                "text beyond the line limit must be truncated");
    }

    @Test
    void maxWidthWrapsTheMessageInsideTheTextColumn() {
        WidgetManager manager = manager();
        manager.setDefaultFont(font(manager));
        ToastManager toasts = new ToastManager(manager.context());
        ToastManager.Toast toast = toasts.show(new ToastManager.Options(LONG_MESSAGE)
                .maxWidth(120)
                .minCardWidth(0)
                .maxCardWidth(400)
                .maxLines(0)
                .duration(5));
        manager.layout();

        TextLayout layout = toast.messageLayout();
        assertNotNull(layout);
        assertTrue(layout.lines().size() > 1, "the message must wrap at the configured width");
        for (TextLine line : layout.lines()) {
            assertTrue(line.width() <= 120.001f,
                    "a wrapped line must not exceed the maximum text width");
        }
        assertTrue(toast.bounds().width() <= 120 + 24 + 30 + 0.001f,
                "the card must follow the wrapped text width");
    }

    @Test
    void showTimerControlsTheRemainingTimeBar() {
        WidgetManager manager = manager();
        manager.setDefaultFont(font(manager));
        ToastManager toasts = new ToastManager(manager.context());
        ToastManager.Toast withTimer = toasts.show(new ToastManager.Options(LONG_MESSAGE)
                .maxWidth(140)
                .maxLines(3)
                .showTimer(true)
                .duration(5));
        ToastManager.Toast withoutTimer = toasts.show(new ToastManager.Options(LONG_MESSAGE)
                .maxWidth(140)
                .maxLines(3)
                .showTimer(false)
                .duration(5));
        manager.layout();

        assertTrue(withTimer.showTimer());
        assertFalse(withoutTimer.showTimer());
        assertEquals(10, withTimer.bounds().height() - withoutTimer.bounds().height(), 0.001f,
                "the hidden timer must not reserve the progress bar row");
    }

    @Test
    void queuedOptionsKeepTheirPresentationWhenPromoted() {
        WidgetManager manager = manager();
        ToastManager toasts = new ToastManager(manager.context());
        toasts.setMaxVisible(1);
        ToastManager.Toast first = toasts.show("First", 1);
        ToastManager.Toast second = toasts.show(new ToastManager.Options("Second")
                .type(ToastManager.Type.ERROR)
                .title("Queued")
                .duration(5));
        manager.layout();

        assertEquals(1, toasts.queuedCount());

        manager.update(1.0f);
        manager.update(0.2f);

        assertFalse(first.isVisible());
        assertTrue(second.isVisible());
        assertEquals(ToastManager.Type.ERROR, second.type());
        assertEquals("Queued", second.title());
    }

    @Test
    void rejectsInvalidOptions() {
        WidgetManager manager = manager();
        ToastManager toasts = new ToastManager(manager.context());
        ToastManager.Options options = new ToastManager.Options("Message");

        assertThrows(NullPointerException.class, () -> new ToastManager.Options(null));
        assertThrows(NullPointerException.class, () -> options.type(null));
        assertThrows(IllegalArgumentException.class, () -> options.maxLines(-1));
        assertThrows(IllegalArgumentException.class, () -> options.maxWidth(0));
        assertThrows(IllegalArgumentException.class, () -> options.minCardWidth(-1));
        assertThrows(IllegalArgumentException.class, () -> options.maxCardWidth(0));
        assertThrows(IllegalArgumentException.class, () -> options.duration(0));
        assertThrows(IllegalArgumentException.class, () -> toasts.show(
                new ToastManager.Options("Message").minCardWidth(400).maxCardWidth(300)));
    }

    private static List<String> messages(ToastManager toasts) {
        return toasts.visibleToasts().stream().map(ToastManager.Toast::message).toList();
    }

    private static Font font(WidgetManager manager) {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        return manager.context().createFont(data.get(), 16);
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(TestViewports.LOGICAL);
        return manager;
    }
}
