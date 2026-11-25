package com.lixui.widget.overlay;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.CharTyped;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.ImageData;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import com.lixui.render.command.ImageCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.Input;
import com.lixui.widget.control.Label;
import com.lixui.widget.image.ImageSource;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.List;
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
    void everyDialogPresetDrawsItsScrimOverTheWholeViewport() {
        assertScrimCoversViewport(new ModalDialog());
        assertScrimCoversViewport(new ConfirmDialog("Remove the item", "This cannot be undone."));
        assertScrimCoversViewport(new PromptDialog("Rename the item", "Enter a name.", "Aria"));
    }

    @Test
    void disablingTheScrimKeepsItsColorAndBlocksTheBackground() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        AtomicInteger backgroundClicks = new AtomicInteger();
        Button background = new Button("Behind");
        background.setBounds(new Rect(10, 10, 120, 36));
        background.setOnAction(backgroundClicks::incrementAndGet);
        manager.add(background);

        Color scrim = new Color(0.18f, 0.32f, 0.64f, 0.37f);
        ModalDialog dialog = new ModalDialog();
        dialog.setTitle("Visible background");
        dialog.setMessage("The scrim is hidden without making the dialog non-modal.");
        dialog.addAction("Close", true, null);
        dialog.setScrimColor(scrim);

        dialog.show(manager.context());
        manager.render();

        assertTrue(dialog.isScrimEnabled());
        assertCoversViewport(dialog);
        assertScrimCommand(backend, scrim);

        dialog.setScrimEnabled(false);
        assertFalse(dialog.isScrimEnabled());
        assertEquals(scrim, dialog.scrimColor());
        manager.render();

        assertCoversViewport(dialog);
        assertNull(viewportRect(backend));
        click(manager, 20, 20);
        assertTrue(dialog.isOpen());
        assertEquals(0, backgroundClicks.get());
        assertTrue(isWithin(manager.focused(), dialog));

        dialog.setScrimEnabled(true);
        manager.render();
        assertScrimCommand(backend, scrim);

        dialog.setScrimEnabled(false);
        dialog.setCloseOnOutsideClick(true);
        click(manager, 5, 5);

        assertFalse(dialog.isOpen());
        assertTrue(dialog.panelBounds().isEmpty());
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
    void drawsTheTitleIconBeforeTheTitle() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        Texture icon = texture(manager, 24, 24);
        ModalDialog dialog = new ModalDialog();
        dialog.setTitle("Update ready");
        dialog.setMessage("Restart when convenient.");
        dialog.addAction("Close", true, null);
        dialog.setIcon(icon);
        dialog.setIconSize(24);

        dialog.show(manager.context());
        manager.render();

        ImageCommand command = imageCommand(backend, icon);
        Rect iconRect = drawnBounds(command);
        Rect titleRect = absoluteBounds(findLabel(dialog, "Update ready"));
        float contentX = dialog.panelBounds().x() + dialog.padding();

        assertEquals(new Rect(0, 0, 24, 24), command.bounds());
        assertEquals(contentX, iconRect.x(), 0.001f);
        assertEquals(titleRect.y() + (titleRect.height() - 24) / 2, iconRect.y(), 0.001f);
        assertEquals(24, iconRect.width(), 0.001f);
        assertEquals(24, iconRect.height(), 0.001f);
        assertTrue(iconRect.right() <= titleRect.x());
        assertTrue(titleRect.x() > contentX);
    }

    @Test
    void layoutWithoutAnIconKeepsTheTitleFullWidth() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        ModalDialog dialog = new ModalDialog();
        dialog.setTitle("No icon");
        dialog.setMessage("The header keeps its original layout.");
        dialog.addAction("Close", true, null);

        dialog.show(manager.context());
        manager.render();

        Rect panel = dialog.panelBounds();
        float contentX = panel.x() + dialog.padding();
        float contentWidth = panel.width() - dialog.padding() * 2;
        Rect titleRect = absoluteBounds(findLabel(dialog, "No icon"));
        Rect messageRect = absoluteBounds(findLabel(dialog,
                "The header keeps its original layout."));

        assertTrue(imageCommands(backend).isEmpty());
        assertEquals(contentX, titleRect.x(), 0.001f);
        assertEquals(panel.y() + dialog.padding(), titleRect.y(), 0.001f);
        assertEquals(contentWidth, titleRect.width(), 0.001f);
        assertEquals(titleRect.x(), messageRect.x(), 0.001f);
        assertEquals(titleRect.width(), messageRect.width(), 0.001f);
        assertTrue(messageRect.y() >= titleRect.bottom());

        Texture icon = texture(manager, 24, 24);
        dialog.setIcon(icon);
        manager.render();
        Rect iconTitleRect = absoluteBounds(findLabel(dialog, "No icon"));
        assertTrue(iconTitleRect.x() > titleRect.x());

        dialog.clearIcon();
        manager.render();
        Rect restoredTitleRect = absoluteBounds(findLabel(dialog, "No icon"));
        assertEquals(titleRect, restoredTitleRect);
        assertEquals(panel, dialog.panelBounds());
        assertTrue(imageCommands(backend).isEmpty());
    }

    @Test
    void titlelessDialogWithoutAnIconKeepsTheMessageAtTheTop() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        ModalDialog dialog = new ModalDialog();
        dialog.setMessage("Without a title the message is the first row.");
        dialog.addAction("Close", true, null);

        dialog.show(manager.context());
        manager.render();

        Rect panel = dialog.panelBounds();
        Rect messageRect = absoluteBounds(findLabel(dialog,
                "Without a title the message is the first row."));
        assertEquals(panel.x() + dialog.padding(), messageRect.x(), 0.001f);
        assertEquals(panel.y() + dialog.padding(), messageRect.y(), 0.001f);
        assertEquals(panel.width() - dialog.padding() * 2, messageRect.width(), 0.001f);
    }

    @Test
    void longTitleWrapsBesideTheIconWithoutOverlapping() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        Texture icon = texture(manager, 24, 24);
        String longTitle = "A very long patch note title that has to wrap over several lines "
                + "while the icon keeps its own column in the header";
        ModalDialog dialog = new ModalDialog();
        dialog.setPreferredWidth(320);
        dialog.setTitle(longTitle);
        dialog.setMessage("The message stays below the wrapped title.");
        dialog.addAction("Close", true, null);
        dialog.setIcon(icon);
        dialog.setIconSize(24);

        dialog.show(manager.context());
        manager.render();

        Rect panel = dialog.panelBounds();
        Rect titleRect = absoluteBounds(findLabel(dialog, longTitle));
        Rect messageRect = absoluteBounds(findLabel(dialog,
                "The message stays below the wrapped title."));
        Rect iconRect = drawnBounds(imageCommand(backend, icon));

        assertEquals(320, panel.width(), 0.001f);
        assertTrue(iconRect.right() <= titleRect.x());
        assertTrue(titleRect.right() <= panel.right() - dialog.padding() + 0.001f);
        assertTrue(titleRect.width() < panel.width() - dialog.padding() * 2);
        assertTrue(messageRect.y() >= titleRect.bottom());
        assertEquals(panel.x() + dialog.padding(), iconRect.x(), 0.001f);
    }

    @Test
    void presetsInheritTheTitleIconAndAcceptRemoval() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        ConfirmDialog confirm = new ConfirmDialog("Remove the save",
                "This cannot be undone.");
        confirm.setIcon(ImageSource.icon("info.svg"));

        confirm.show(manager.context());
        manager.render();

        Rect panel = confirm.panelBounds();
        float contentX = panel.x() + confirm.padding();
        Rect confirmTitle = absoluteBounds(findLabel(confirm, "Remove the save"));
        assertEquals(1, imageCommands(backend).size());
        assertTrue(confirmTitle.x() > contentX);

        confirm.setIcon((ImageSource) null);
        manager.render();
        Rect restoredTitle = absoluteBounds(findLabel(confirm, "Remove the save"));
        assertEquals(contentX, restoredTitle.x(), 0.001f);
        assertTrue(imageCommands(backend).isEmpty());
        confirm.close();

        PromptDialog prompt = new PromptDialog("Rename the party",
                "Enter a new name.", "Aria");
        prompt.setIcon(ImageSource.icon("exclamation.svg"));
        prompt.setIconSize(20);
        prompt.show(manager.context());
        manager.render();

        Rect promptTitle = absoluteBounds(findLabel(prompt, "Rename the party"));
        assertEquals(1, imageCommands(backend).size());
        assertTrue(promptTitle.x() > prompt.panelBounds().x() + prompt.padding());
        assertEquals(new Rect(0, 0, 20, 20), imageCommands(backend).getFirst().bounds());
    }

    @Test
    void rejectsInvalidConfiguration() {
        ModalDialog dialog = new ModalDialog();

        assertThrows(IllegalArgumentException.class, () -> dialog.setPreferredWidth(0));
        assertThrows(IllegalArgumentException.class, () -> dialog.setPreferredWidth(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> dialog.setPadding(-1));
        assertThrows(IllegalArgumentException.class, () -> dialog.setIconSize(0));
        assertThrows(IllegalArgumentException.class, () -> dialog.setIconSize(Float.NaN));
        assertThrows(NullPointerException.class, () -> dialog.setScrimColor(null));
        assertThrows(NullPointerException.class, () -> dialog.show(null));
        assertThrows(IllegalArgumentException.class, () -> dialog.setBody(dialog));
        assertNotNull(dialog.addAction("Close", null));
    }

    private static void assertScrimCoversViewport(ModalDialog dialog) {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);

        dialog.show(manager.context());
        manager.render();

        assertTrue(dialog.isScrimEnabled(), dialog.getClass().getSimpleName());
        assertCoversViewport(dialog);
        assertScrimCommand(backend, dialog.scrimColor());
    }

    private static void assertCoversViewport(ModalDialog dialog) {
        assertEquals(VIEWPORT.logicalWidth(), dialog.bounds().width(), 0.001f);
        assertEquals(VIEWPORT.logicalHeight(), dialog.bounds().height(), 0.001f);
    }

    private static void assertScrimCommand(TestBackend backend, Color expected) {
        RoundedRectCommand command = viewportRect(backend);
        assertNotNull(command);
        assertEquals(new Rect(0, 0, VIEWPORT.logicalWidth(), VIEWPORT.logicalHeight()),
                command.bounds());
        assertEquals(expected.r(), command.color().r(), 0.0f);
        assertEquals(expected.g(), command.color().g(), 0.0f);
        assertEquals(expected.b(), command.color().b(), 0.0f);
        assertEquals(expected.a(), command.color().a(), 0.0f);
    }

    private static RoundedRectCommand viewportRect(TestBackend backend) {
        Rect viewport = new Rect(0, 0, VIEWPORT.logicalWidth(), VIEWPORT.logicalHeight());
        return backend.lastDrawList().commands().stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .filter(command -> command.bounds().equals(viewport))
                .findFirst()
                .orElse(null);
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

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }

    private static Texture texture(WidgetManager manager, int width, int height) {
        return manager.context().createTexture(new ImageData(width, height,
                new byte[width * height * 4]));
    }

    private static ImageCommand imageCommand(TestBackend backend, Texture texture) {
        return imageCommands(backend).stream()
                .filter(command -> command.texture() == texture)
                .findFirst().orElseThrow();
    }

    private static List<ImageCommand> imageCommands(TestBackend backend) {
        return backend.lastDrawList().commands().stream()
                .filter(ImageCommand.class::isInstance)
                .map(ImageCommand.class::cast)
                .toList();
    }

    private static Rect drawnBounds(ImageCommand command) {
        Transform transform = command.transform();
        Rect bounds = command.bounds();
        return new Rect(bounds.x() * transform.m00() + bounds.y() * transform.m01()
                + transform.m02(),
                bounds.x() * transform.m10() + bounds.y() * transform.m11()
                        + transform.m12(),
                bounds.width() * transform.m00(), bounds.height() * transform.m11());
    }

    private static Rect absoluteBounds(Widget widget) {
        Point position = widget.absolutePosition();
        return new Rect(position.x(), position.y(), widget.bounds().width(),
                widget.bounds().height());
    }

    private static Label findLabel(Widget root, String text) {
        for (Widget child : root.children()) {
            if (child instanceof Label label && text.equals(label.text())) {
                return label;
            }
            Label nested = findLabelOrNull(child, text);
            if (nested != null) {
                return nested;
            }
        }
        throw new AssertionError("Label not found: " + text);
    }

    private static Label findLabelOrNull(Widget root, String text) {
        for (Widget child : root.children()) {
            if (child instanceof Label label && text.equals(label.text())) {
                return label;
            }
            Label nested = findLabelOrNull(child, text);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }
}
