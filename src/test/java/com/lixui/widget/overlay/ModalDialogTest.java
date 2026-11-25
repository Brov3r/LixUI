package com.lixui.widget.overlay;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.CharTyped;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.Viewport;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.Input;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModalDialogTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void centersThePanelAndBlocksPointerInput() {
        WidgetManager manager = manager();
        AtomicInteger backgroundClicks = new AtomicInteger();
        Button background = new Button("Behind");
        background.setBounds(new Rect(10, 10, 120, 36));
        background.setOnAction(backgroundClicks::incrementAndGet);
        manager.add(background);

        ModalDialog dialog = new ModalDialog();
        dialog.setTitle("Patch notes");
        dialog.setMessage("The scene stays inactive until the dialog closes.");
        dialog.addAction("Close", true, null);
        dialog.show(manager.context());
        manager.render();

        assertTrue(dialog.isOpen());
        assertEquals(VIEWPORT.logicalWidth(), dialog.bounds().width(), 0.001f);
        Rect panel = dialog.panelBounds();
        assertFalse(panel.isEmpty());
        assertEquals(VIEWPORT.logicalWidth() / 2, panel.x() + panel.width() / 2, 0.001f);
        assertEquals(VIEWPORT.logicalHeight() / 2, panel.y() + panel.height() / 2, 0.001f);
        assertTrue(isWithin(manager.focused(), dialog));

        click(manager, 20, 20);

        assertTrue(dialog.isOpen());
        assertEquals(0, backgroundClicks.get());
    }

    @Test
    void escapeCancelsThroughTheNegativeAction() {
        WidgetManager manager = manager();
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        ConfirmDialog dialog = new ConfirmDialog("Delete the save",
                "This cannot be undone.");
        dialog.setOnConfirm(confirmed::incrementAndGet);
        dialog.setOnCancel(cancelled::incrementAndGet);

        dialog.show(manager.context());
        manager.render();
        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(dialog.isOpen());
        assertFalse(manager.widgets().contains(dialog));
        assertTrue(dialog.panelBounds().isEmpty());
        assertEquals(0, confirmed.get());
        assertEquals(1, cancelled.get());
    }

    @Test
    void confirmAndCancelButtonsRunTheirActions() {
        WidgetManager manager = manager();
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        ConfirmDialog dialog = new ConfirmDialog("Leave the party",
                "Choose whether to leave.");
        dialog.setOnConfirm(confirmed::incrementAndGet);
        dialog.setOnCancel(cancelled::incrementAndGet);

        dialog.show(manager.context());
        manager.render();
        clickWidget(manager, findButton(dialog, "Confirm"));

        assertEquals(1, confirmed.get());
        assertEquals(0, cancelled.get());
        assertFalse(dialog.isOpen());
        assertTrue(dialog.panelBounds().isEmpty());

        dialog.show(manager.context());
        manager.render();
        clickWidget(manager, findButton(dialog, "Cancel"));

        assertEquals(1, confirmed.get());
        assertEquals(1, cancelled.get());
        assertFalse(dialog.isOpen());
        assertTrue(dialog.panelBounds().isEmpty());
    }

    @Test
    void promptSubmitsTheEnteredTextAndCancelsWithEscape() {
        WidgetManager manager = manager();
        AtomicReference<String> submitted = new AtomicReference<>();
        AtomicInteger cancelled = new AtomicInteger();
        PromptDialog dialog = new PromptDialog("Rename the party", "Enter a new name.", "Aria");
        dialog.setOnSubmit(submitted::set);
        dialog.setOnCancel(cancelled::incrementAndGet);

        dialog.show(manager.context());
        manager.render();

        assertSame(dialog.input(), manager.focused());

        dialog.input().setText("Bran");
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertEquals("Bran", submitted.get());
        assertFalse(dialog.isOpen());
        assertTrue(dialog.panelBounds().isEmpty());

        dialog.show(manager.context());
        manager.render();
        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertEquals(1, cancelled.get());
        assertFalse(dialog.isOpen());
        assertTrue(dialog.panelBounds().isEmpty());
    }

    @Test
    void keyboardAndFocusStayInsideTheDialog() {
        WidgetManager manager = manager();
        Input background = new Input();
        background.setBounds(new Rect(10, 10, 200, 36));
        manager.add(background);
        manager.focus(background);

        ConfirmDialog dialog = new ConfirmDialog("Focus scope", "Tab wraps inside the dialog.");
        dialog.show(manager.context());
        manager.render();

        manager.handle(new CharTyped('X'));
        assertTrue(background.text().isEmpty());

        for (int index = 0; index < 6; index++) {
            manager.handle(new KeyPressed(Key.TAB, Modifiers.NONE, false));
            assertTrue(isWithin(manager.focused(), dialog));
            assertFalse(manager.focused() == background);
        }
    }

    @Test
    void backgroundDismissalIsOptionalAndTheScrimIsConfigurable() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        AtomicInteger cancelled = new AtomicInteger();
        Color scrim = new Color(0.1f, 0.12f, 0.2f, 0.45f);
        ConfirmDialog dialog = new ConfirmDialog("Leave the party", "Stay or leave.");
        dialog.setOnCancel(cancelled::incrementAndGet);
        dialog.setScrimColor(scrim);

        dialog.show(manager.context());
        manager.render();

        boolean scrimDrawn = backend.lastDrawList().commands().stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .anyMatch(command -> command.color().equals(scrim)
                        && command.bounds().equals(new Rect(0, 0,
                        VIEWPORT.logicalWidth(), VIEWPORT.logicalHeight())));
        assertTrue(scrimDrawn);

        click(manager, 5, 5);
        assertTrue(dialog.isOpen());
        assertEquals(0, cancelled.get());

        dialog.setCloseOnOutsideClick(true);
        click(manager, 5, 5);

        assertFalse(dialog.isOpen());
        assertTrue(dialog.panelBounds().isEmpty());
        assertEquals(1, cancelled.get());
    }

    @Test
    void keyboardOpenedConfirmCancelsAndRestoresFocus() {
        WidgetManager manager = manager();
        AtomicInteger cancelled = new AtomicInteger();
        ConfirmDialog dialog = new ConfirmDialog("Delete the save", "This cannot be undone.");
        dialog.setOnCancel(cancelled::incrementAndGet);
        Button trigger = new Button("Delete");
        trigger.setBounds(new Rect(10, 10, 120, 36));
        trigger.setOnAction(() -> dialog.show(trigger.context()));
        manager.add(trigger);
        manager.render();
        manager.focus(trigger);

        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
        manager.render();

        assertTrue(dialog.isOpen());
        assertTrue(isWithin(manager.focused(), dialog));

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(dialog.isOpen());
        assertTrue(dialog.panelBounds().isEmpty());
        assertEquals(1, cancelled.get());
        assertSame(trigger, manager.focused());
    }

    @Test
    void keyboardOpenedPromptSubmitsAndReopens() {
        WidgetManager manager = manager();
        AtomicReference<String> submitted = new AtomicReference<>();
        PromptDialog dialog = new PromptDialog("Rename the party", "Enter a new name.", "Aria");
        dialog.setOnSubmit(submitted::set);
        Button trigger = new Button("Rename");
        trigger.setBounds(new Rect(10, 10, 120, 36));
        trigger.setOnAction(() -> dialog.show(trigger.context()));
        manager.add(trigger);
        manager.render();
        manager.focus(trigger);

        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
        manager.render();

        assertTrue(dialog.isOpen());
        assertSame(dialog.input(), manager.focused());

        dialog.input().setText("Bran");
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertEquals("Bran", submitted.get());
        assertFalse(dialog.isOpen());
        assertTrue(dialog.panelBounds().isEmpty());
        assertSame(trigger, manager.focused());

        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
        manager.render();

        assertTrue(dialog.isOpen());
        assertSame(dialog.input(), manager.focused());

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(dialog.isOpen());
        assertTrue(dialog.panelBounds().isEmpty());
        assertSame(trigger, manager.focused());
    }

    @Test
    void doesNotRestoreFocusToAnUnavailableInitiator() {
        WidgetManager manager = manager();
        ConfirmDialog dialog = new ConfirmDialog("Close", "The trigger is disabled while open.");
        Button trigger = new Button("Open");
        trigger.setBounds(new Rect(10, 10, 120, 36));
        trigger.setOnAction(() -> dialog.show(trigger.context()));
        manager.add(trigger);
        manager.render();
        manager.focus(trigger);

        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
        manager.render();
        trigger.setEnabled(false);

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(dialog.isOpen());
        assertNull(manager.focused());
    }

    @Test
    void anActionThatOpensAnotherDialogKeepsFocusInTheNewDialog() {
        WidgetManager manager = manager();
        ModalDialog second = new ModalDialog();
        second.setTitle("Second");
        second.setMessage("Keeps focus after the first dialog closes.");
        second.addAction("Close", true, null);
        ModalDialog first = new ModalDialog();
        first.setTitle("First");
        first.setMessage("Opens the second dialog from an action.");
        first.addAction("Next", true, () -> second.show(manager.context()));

        first.show(manager.context());
        manager.render();
        clickWidget(manager, findButton(first, "Next"));

        assertFalse(first.isOpen());
        assertTrue(first.panelBounds().isEmpty());
        assertTrue(second.isOpen());
        assertTrue(isWithin(manager.focused(), second));
    }

    @Test
    void rejectsInvalidConfiguration() {
        ModalDialog dialog = new ModalDialog();

        assertThrows(IllegalArgumentException.class, () -> dialog.setPreferredWidth(0));
        assertThrows(IllegalArgumentException.class, () -> dialog.setPreferredWidth(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> dialog.setPadding(-1));
        assertThrows(NullPointerException.class, () -> dialog.setScrimColor(null));
        assertThrows(NullPointerException.class, () -> dialog.show(null));
        assertThrows(IllegalArgumentException.class, () -> dialog.setBody(dialog));
        assertNotNull(dialog.addAction("Close", null));
    }

    private static Button findButton(Widget root, String text) {
        for (Widget child : root.children()) {
            if (child instanceof Button button && text.equals(button.text())) {
                return button;
            }
            Button nested = findButtonOrNull(child, text);
            if (nested != null) {
                return nested;
            }
        }
        throw new AssertionError("Button not found: " + text);
    }

    private static Button findButtonOrNull(Widget root, String text) {
        for (Widget child : root.children()) {
            if (child instanceof Button button && text.equals(button.text())) {
                return button;
            }
            Button nested = findButtonOrNull(child, text);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static boolean isWithin(Widget widget, Widget ancestor) {
        for (Widget current = widget; current != null; current = current.parent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static void clickWidget(WidgetManager manager, Widget widget) {
        Point origin = widget.absolutePosition();
        click(manager, origin.x() + widget.bounds().width() / 2,
                origin.y() + widget.bounds().height() / 2);
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        return manager;
    }
}
