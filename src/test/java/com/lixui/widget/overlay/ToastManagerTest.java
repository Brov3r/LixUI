package com.lixui.widget.overlay;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.event.PointerMoved;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.text.TextLayout;
import com.lixui.text.TextLine;
import com.lixui.text.TextOverflow;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.image.ImageSource;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToastManagerTest {
    private static final String LONG_MESSAGE = "A long notification wraps across several lines and "
            + "then stops at the configured line limit so the remaining words never make the card "
            + "grow beyond the available stack area.";

    @Test
    void showsToastExpiresAndRemovesIt() {
        WidgetManager manager = manager();
        ToastManager toasts = overlay(manager);
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
        ToastManager toasts = overlay(manager);
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
        ToastManager toasts = overlay(manager);
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
        ToastManager toasts = overlay(manager);
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
        ToastManager toasts = overlay(manager);
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
        ToastManager toasts = overlay(manager);
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
        ToastManager toasts = overlay(manager);
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
        ToastManager toasts = overlay(manager);
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
        ToastManager toasts = overlay(manager);
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
        ToastManager toasts = overlay(manager);
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
        ToastManager toasts = overlay(manager);
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

    @Test
    void customContentReplacesTheDefaultBodyAndReceivesInput() {
        WidgetManager manager = manager();
        ToastManager toasts = overlay(manager);
        MarkerContent content = new MarkerContent(140, 40);
        ToastManager.Toast toast = toasts.show(new ToastManager.Options("Unused message")
                .content(content)
                .duration(5));

        manager.update(0.3f);
        manager.layout();
        manager.render();

        assertSame(content, toast.widget().content());
        assertSame(toast.widget(), content.parent());
        assertFalse(content.bounds().isEmpty());
        assertTrue(content.bounds().width() >= 140, "content must keep its measured width");
        assertTrue(content.drawn, "custom content must be drawn inside the card");
        Rect bounds = toast.bounds();
        float centerX = bounds.x() + bounds.width() / 2;
        float centerY = bounds.y() + bounds.height() / 2;
        assertTrue(manager.blocksPointer(centerX, centerY),
                "custom content must receive pointer input");

        manager.handle(new PointerMoved(centerX, centerY));
        float remaining = toast.remainingSeconds();
        manager.update(0.5f);

        assertEquals(remaining, toast.remainingSeconds(), 0.001f,
                "hovering custom content must pause the countdown");
    }

    @Test
    void customRendererPaintsTheWholeCard() {
        WidgetManager manager = manager();
        ToastManager toasts = overlay(manager);
        int[] calls = {0};
        Rect[] area = new Rect[1];
        Toast[] painted = new Toast[1];
        ToastManager.Toast toast = toasts.show(new ToastManager.Options("")
                .maxWidth(220)
                .renderer((owner, renderer, bounds, frame) -> {
                    calls[0]++;
                    painted[0] = owner;
                    area[0] = bounds;
                })
                .duration(5));

        manager.update(0.3f);
        manager.layout();
        manager.render();

        assertEquals(1, calls[0], "the custom renderer must run once per frame");
        assertSame(toast.widget(), painted[0]);
        assertNotNull(area[0]);
        assertEquals(0f, area[0].x(), 0.001f);
        assertEquals(0f, area[0].y(), 0.001f);
        assertEquals(toast.bounds().width(), area[0].width(), 0.001f,
                "the renderer must receive the full card width");
        assertEquals(toast.bounds().height(), area[0].height(), 0.001f,
                "the renderer must receive the full card height");
        assertEquals(220, toast.bounds().width(), 0.001f,
                "a renderer-only card follows the configured text width");
    }

    @Test
    void customRendererReplacesTheDefaultCardChrome() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(TestViewports.LOGICAL);
        ToastManager toasts = new ToastManager();
        manager.add(toasts);
        toasts.show(new ToastManager.Options("")
                .maxWidth(200)
                .renderer((toast, renderer, bounds, frame) ->
                        renderer.rect(bounds, Color.hex("#3366CC")))
                .duration(5));

        manager.update(0.3f);
        manager.render();

        assertEquals(1, backend.lastCommandCount(),
                "a custom renderer must replace the default surface, border, text and timer");
    }

    @Test
    void noArgManagerAttachesThroughTheWidgetApi() {
        WidgetManager manager = manager();
        ToastManager toasts = new ToastManager();

        assertTrue(toasts.isFillViewport());
        manager.add(toasts);
        manager.layout();

        assertSame(toasts, manager.widgets().get(0));
        assertFalse(manager.blocksPointer(10, 10));

        ToastManager.Toast toast = toasts.show("Attached overlay", 5);
        manager.layout();
        Rect bounds = toast.bounds();

        assertFalse(bounds.isEmpty());
        assertTrue(manager.blocksPointer(bounds.x() + bounds.width() / 2,
                bounds.y() + bounds.height() / 2));

        toasts.close();

        assertFalse(manager.widgets().contains(toasts));
        assertThrows(IllegalStateException.class, () -> toasts.show("Closed"));
    }

    @Test
    void noArgManagerWorksInsideAnotherWidgetTree() {
        WidgetManager manager = manager();
        Panel host = new Panel();
        host.setFillViewport(true);
        ToastManager toasts = new ToastManager();
        toasts.setBounds(new Rect(0, 0, 400, 300));
        host.add(toasts);
        manager.add(host);
        manager.layout();

        ToastManager.Toast toast = toasts.show("Nested overlay", 5);
        manager.layout();
        Rect bounds = toast.bounds();

        assertTrue(host.children().contains(toasts));
        assertFalse(bounds.isEmpty());
        assertTrue(bounds.right() <= 400.001f, "the stack must stay inside its parent bounds");
        assertTrue(bounds.bottom() <= 300.001f, "the stack must stay inside its parent bounds");
    }

    @Test
    void stackStaysInsideSmallOverlayBoundsDuringSlide() {
        WidgetManager manager = manager();
        Panel host = new Panel();
        host.setFillViewport(true);
        ToastManager toasts = new ToastManager();
        toasts.setBounds(new Rect(0, 0, 120, 70));
        toasts.setCorner(ToastManager.Corner.TOP_RIGHT);
        host.add(toasts);
        manager.add(host);
        manager.layout();

        ToastManager.Toast toast = toasts.show("Tiny", 5);
        manager.layout();
        Rect bounds = toast.bounds();

        assertTrue(bounds.x() >= -0.001f, "the card must not start before the overlay");
        assertTrue(bounds.y() >= -0.001f, "the card must not start above the overlay");
        assertTrue(bounds.right() <= 120.001f, "the slide must stay inside the overlay width");
        assertTrue(bounds.bottom() <= 70.001f, "the stack must stay inside the overlay height");
    }

    @Test
    void contextConstructorStillAttachesAsATopLevelWidget() {
        WidgetManager manager = manager();
        ToastManager toasts = new ToastManager(manager.context());

        assertTrue(manager.widgets().contains(toasts));

        toasts.close();

        assertFalse(manager.widgets().contains(toasts));
    }

    @Test
    void standaloneToastExpiresAndRemovesItself() {
        WidgetManager manager = manager();
        Toast toast = new Toast(new Toast.Options("Standalone").duration(1));
        manager.add(toast);
        manager.layout();

        assertFalse(toast.viewportBounds().isEmpty());
        assertTrue(manager.widgets().contains(toast));

        manager.update(1.0f);
        manager.update(0.2f);

        assertTrue(toast.isFinished());
        assertFalse(manager.widgets().contains(toast),
                "a standalone toast must leave its manager after the exit animation");
    }

    @Test
    void standaloneToastHostedAsChildRemovesItselfWithoutManagerLookup() {
        WidgetManager manager = manager();
        Panel host = new Panel();
        host.setBounds(new Rect(0, 0, 400, 300));
        manager.add(host);
        Toast toast = new Toast(new Toast.Options("Nested standalone").duration(1));
        toast.setBounds(new Rect(0, 0, 240, 80));
        host.add(toast);
        manager.layout();

        assertSame(host, toast.parent());
        assertFalse(toast.viewportBounds().isEmpty());

        manager.update(1.0f);
        manager.update(0.2f);

        assertTrue(toast.isFinished());
        assertFalse(host.children().contains(toast),
                "a child-hosted toast must leave through its parent");
        assertTrue(manager.widgets().contains(host));
    }

    @Test
    void preservesLegacyNestedApiDescriptors() throws NoSuchMethodException {
        assertTrue(Widget.class.isAssignableFrom(ToastManager.class));
        assertTrue(AutoCloseable.class.isAssignableFrom(ToastManager.class));
        assertNotNull(ToastManager.class.getConstructor(UiContext.class));

        Method showMessage = ToastManager.class.getMethod("show", String.class);
        Method showDuration = ToastManager.class.getMethod("show", String.class, float.class);
        Method showOptions = ToastManager.class.getMethod("show", ToastManager.Options.class);
        assertEquals(ToastManager.Toast.class, showMessage.getReturnType());
        assertEquals(ToastManager.Toast.class, showDuration.getReturnType());
        assertEquals(ToastManager.Toast.class, showOptions.getReturnType());

        Method typeOption = ToastManager.Options.class.getMethod("type", ToastManager.Type.class);
        Method typeToast = ToastManager.Toast.class.getMethod("type");
        Method bounds = ToastManager.Toast.class.getMethod("bounds");
        assertEquals(ToastManager.Options.class, typeOption.getReturnType());
        assertEquals(ToastManager.Type.class, typeToast.getReturnType());
        assertEquals(Rect.class, bounds.getReturnType());
    }

    private static ToastManager overlay(WidgetManager manager) {
        ToastManager toasts = new ToastManager();
        manager.add(toasts);
        return toasts;
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

    private static final class MarkerContent extends Widget {
        private final Size preferred;
        private boolean drawn;

        private MarkerContent(float width, float height) {
            preferred = new Size(width, height);
        }

        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            return preferred;
        }

        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            drawn = true;
        }
    }
}
