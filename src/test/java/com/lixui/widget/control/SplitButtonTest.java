package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.KeyReleased;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.Viewport;
import com.lixui.render.command.ImageCommand;
import com.lixui.render.command.MeshCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.overlay.ContextMenu;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestTexture;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitButtonTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;
    private static final Rect BOUNDS = new Rect(10, 10, 200, 36);

    @Test
    void runsThePrimaryActionAndContextMenuItemsIndependently() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            SplitButton button = splitButton();
            List<String> events = new ArrayList<>();
            button.setOnAction(() -> events.add("action"));
            button.menu().addItem("Save as", () -> events.add("save-as"));
            button.menu().addItem("Save all", () -> events.add("save-all"));
            manager.add(button);
            manager.render();

            click(manager, 40, 20);
            assertEquals(List.of("action"), events);
            assertFalse(button.isMenuOpen());

            click(manager, 200, 20);
            assertTrue(button.isMenuOpen());
            manager.render();
            clickMenuItem(manager, button, 0);

            assertFalse(button.isMenuOpen());
            assertEquals(List.of("action", "save-as"), events);
        }
    }

    @Test
    void ignoresDisabledMenuItemsAndClosesOnOutsidePress() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            SplitButton button = splitButton();
            List<String> events = new ArrayList<>();
            ContextMenu.Item disabled = button.menu().addItem("Disabled",
                    () -> events.add("disabled"));
            disabled.setEnabled(false);
            manager.add(button);
            manager.render();

            click(manager, 200, 20);
            manager.render();
            clickMenuItem(manager, button, 0);

            assertTrue(button.isMenuOpen());
            assertTrue(events.isEmpty());

            manager.handle(new PointerPressed(700, 500, MouseButton.LEFT, Modifiers.NONE));
            assertFalse(button.isMenuOpen());
        }
    }

    @Test
    void activatesThePrimaryActionFromTheKeyboardAndOpensTheMenu() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            SplitButton button = splitButton();
            List<String> events = new ArrayList<>();
            button.setOnAction(() -> events.add("action"));
            button.menu().addItem("Open", () -> events.add("open"));
            manager.add(button);
            manager.focus(button);

            manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
            assertEquals(List.of("action"), events);

            manager.handle(new KeyPressed(Key.SPACE, Modifiers.NONE, false));
            assertEquals(List.of("action"), events);
            manager.handle(new KeyReleased(Key.SPACE, Modifiers.NONE));
            assertEquals(List.of("action", "action"), events);

            manager.handle(new KeyPressed(Key.DOWN, Modifiers.NONE, false));
            assertTrue(button.isMenuOpen());
            manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
            assertFalse(button.isMenuOpen());
        }
    }

    @Test
    void opensAndClosesTheContextMenuProgrammatically() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            SplitButton button = splitButton();
            manager.add(button);

            button.openMenu();
            assertFalse(button.isMenuOpen());

            button.menu().addItem("Open", null);
            button.openMenu();
            assertTrue(button.isMenuOpen());
            button.closeMenu();
            assertFalse(button.isMenuOpen());

            button.setEnabled(false);
            button.openMenu();
            assertFalse(button.isMenuOpen());
        }
    }

    @Test
    void runsThePrimaryActionWhenTheMenuHasNoItems() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            SplitButton button = splitButton();
            int[] actions = {0};
            button.setOnAction(() -> actions[0]++);
            manager.add(button);
            manager.render();

            click(manager, 200, 20);

            assertEquals(1, actions[0]);
            assertFalse(button.isMenuOpen());
        }
    }

    @Test
    void reservesTheArrowSlotAndMeasuresTheIcon() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            SplitButton split = new SplitButton("Go");
            Button plain = new Button("Go");
            manager.add(split).add(plain);

            Size splitSize = split.measure(LayoutConstraints.loose(1000, 1000));
            Size plainSize = plain.measure(LayoutConstraints.loose(1000, 1000));
            assertTrue(splitSize.width() - plainSize.width() >= split.iconSize());

            split.setIcon(new TestTexture(16, 16));
            Size withIcon = split.measure(LayoutConstraints.loose(1000, 1000));
            assertTrue(withIcon.width() > splitSize.width());
            assertTrue(withIcon.height() >= splitSize.height());
        }
    }

    @Test
    void rejectsInvalidArguments() {
        SplitButton button = new SplitButton("Run");

        assertThrows(NullPointerException.class, () -> new SplitButton(null));
        assertThrows(NullPointerException.class, () -> button.setText(null));
        assertThrows(NullPointerException.class, () -> button.setIconStyle(null));
        assertThrows(IllegalArgumentException.class, () -> button.setIconSize(0));
        assertThrows(IllegalArgumentException.class, () -> button.setIconSize(Float.NaN));
    }

    @Test
    void drawsTheArrowSeparatorAndTheMenuArrow() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            SplitButton button = splitButton();
            button.menu().addItem("Open", null);
            manager.add(button);
            manager.render();

            boolean separator = backend.lastDrawList().commands().stream()
                    .filter(RoundedRectCommand.class::isInstance)
                    .map(RoundedRectCommand.class::cast)
                    .anyMatch(command -> command.radius() == 0 && command.bounds().width() == 1);
            assertTrue(separator);
            assertTrue(hasArrow(backend));

            click(manager, 200, 20);
            manager.render();
            assertTrue(button.isMenuOpen());
            assertTrue(hasArrow(backend));
        }
    }

    @Test
    void hidesTheFocusOutlineForPointerFocusAndRestoresItForKeyboardFocus() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Theme theme = manager.context().theme();
            SplitButton button = splitButton();
            manager.add(button);
            manager.focus(button);
            manager.render();
            assertTrue(hasFocusOutline(backend, theme));

            click(manager, 40, 20);
            manager.handle(new PointerMoved(700, 500));
            manager.render();
            assertFalse(hasFocusOutline(backend, theme));

            manager.handle(new KeyPressed(Key.SPACE, Modifiers.NONE, false));
            manager.render();
            assertTrue(hasFocusOutline(backend, theme));
        }
    }

    private static boolean hasFocusOutline(TestBackend backend, Theme theme) {
        return backend.lastDrawList().commands().stream()
                .filter(MeshCommand.class::isInstance)
                .map(MeshCommand.class::cast)
                .flatMap(command -> command.vertices().stream())
                .anyMatch(vertex -> vertex.color().equals(theme.focusRing()));
    }

    @Test
    void keepsTheControlSurfaceInsideAnInheritedStyle() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Theme theme = manager.context().theme();
            Panel root = new Panel();
            root.setStyle(new WidgetStyle(Color.TRANSPARENT, Color.TRANSPARENT,
                    Color.TRANSPARENT, Color.TRANSPARENT, theme.text(), theme.textMuted(),
                    theme.border(), theme.focusRing(), 0, 0, 0));
            root.setBounds(new Rect(0, 0, 400, 120));
            SplitButton button = splitButton();
            root.add(button);
            manager.add(root);
            manager.render();

            boolean controlSurface = backend.lastDrawList().commands().stream()
                    .filter(RoundedRectCommand.class::isInstance)
                    .map(RoundedRectCommand.class::cast)
                    .anyMatch(command -> command.color().equals(
                            theme.controlStyle().background(WidgetState.NORMAL)));
            assertTrue(controlSurface);
        }
    }

    private static boolean hasArrow(TestBackend backend) {
        return backend.lastDrawList().commands().stream()
                .anyMatch(ImageCommand.class::isInstance);
    }

    private static SplitButton splitButton() {
        SplitButton button = new SplitButton("Save");
        button.setBounds(BOUNDS);
        return button;
    }

    private static void clickMenuItem(WidgetManager manager, SplitButton button, int index) {
        float menuX = BOUNDS.x() + BOUNDS.width() - button.trailingSlotWidth();
        float menuY = BOUNDS.y() + BOUNDS.height() + SplitButton.MENU_GAP;
        float padding = button.menu().padding();
        float itemHeight = button.menu().itemHeight();
        click(manager, menuX + 20, menuY + padding + index * itemHeight + itemHeight / 2);
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }
}
