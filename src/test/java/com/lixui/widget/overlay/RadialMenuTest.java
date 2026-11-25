package com.lixui.widget.overlay;

import com.lixui.animation.Easing;
import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.platform.Cursor;
import com.lixui.render.Viewport;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadialMenuTest {
    @Test
    void hitTestsSectorsByAngleWithSectorGaps() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.render();

            manager.handle(new PointerMoved(400, 200));
            assertEquals(0, menu.hoveredIndex());
            manager.handle(new PointerMoved(500, 300));
            assertEquals(1, menu.hoveredIndex());
            manager.handle(new PointerMoved(400, 400));
            assertEquals(2, menu.hoveredIndex());
            manager.handle(new PointerMoved(300, 300));
            assertEquals(3, menu.hoveredIndex());
            manager.handle(new PointerMoved(470.7f, 229.3f));
            assertEquals(-1, menu.hoveredIndex());

            manager.handle(new PointerMoved(400, 300));
            assertEquals(-1, menu.hoveredIndex());
            assertEquals(Cursor.POINTER, menu.cursor());

            manager.handle(new PointerMoved(10, 10));
            assertEquals(-1, menu.hoveredIndex());
        }
    }

    @Test
    void clampsTheMenuInsideTheViewport() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.open(manager.context(), 2, 2);
            manager.layout();
            Rect bounds = menu.menuBounds();
            assertTrue(bounds.x() >= 0);
            assertTrue(bounds.y() >= 0);
            assertTrue(bounds.right() <= 800);
            assertTrue(bounds.bottom() <= 600);

            menu.open(manager.context(), 798, 598);
            manager.layout();
            bounds = menu.menuBounds();
            assertTrue(bounds.x() >= 0);
            assertTrue(bounds.y() >= 0);
            assertTrue(bounds.right() <= 800);
            assertTrue(bounds.bottom() <= 600);
        }
    }

    @Test
    void keepsTheMenuInsideAResizedViewport() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.open(manager.context(), 700, 500);
            manager.layout();
            manager.resize(new Viewport(400, 300, 400, 300));
            manager.layout();
            Rect bounds = menu.menuBounds();
            assertTrue(bounds.x() >= 0);
            assertTrue(bounds.y() >= 0);
            assertTrue(bounds.right() <= 400);
            assertTrue(bounds.bottom() <= 300);
        }
    }

    @Test
    void clickRunsTheSectorActionAndClosesTheMenu() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            List<String> actions = new ArrayList<>();
            RadialMenu.Item second = menu.items().get(1);
            second.setAction(() -> actions.add("second"));
            second.setEnabled(false);
            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.render();

            click(manager, 500, 300);
            assertTrue(actions.isEmpty());
            assertTrue(menu.isOpen());

            manager.update(0.5f);
            second.setEnabled(true);
            click(manager, 500, 300);
            assertEquals(List.of("second"), actions);
            assertFalse(menu.isOpen());
            assertFalse(manager.widgets().contains(menu));
        }
    }

    @Test
    void actionCanOpenAnotherFocusableOverlayWithoutLosingFocus() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Widget owner = focusable(new Rect(10, 10, 100, 30));
            manager.context().addTopLevel(owner);
            manager.focus(owner);

            RadialMenu menu = menu(4);
            menu.setAnimationDuration(0.3f);
            Widget[] dialog = new Widget[1];
            menu.items().getFirst().setAction(() -> {
                dialog[0] = focusable(new Rect(140, 10, 160, 40));
                manager.context().addTopLevel(dialog[0]);
                manager.focus(dialog[0]);
            });
            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.update(1);

            click(manager, 400, 200);
            assertTrue(menu.isClosing());
            assertSame(dialog[0], manager.focused());

            manager.update(1);
            assertFalse(menu.isOpen());
            assertSame(dialog[0], manager.focused());
        }
    }

    @Test
    void callbackFailureStillClosesTheMenu() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.setAnimationDuration(0.3f);
            menu.items().getFirst().setAction(() -> {
                throw new IllegalStateException("Action failed.");
            });
            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.update(1);

            assertThrows(IllegalStateException.class, () -> click(manager, 400, 200));
            assertTrue(menu.isClosing());

            manager.update(1);
            assertFalse(menu.isOpen());
            assertFalse(manager.widgets().contains(menu));
        }
    }

    @Test
    void closesOnEscapeAndOutsideClick() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.open(manager.context(), 400, 300);
            manager.layout();

            manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
            assertFalse(menu.isOpen());

            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
            assertFalse(menu.isOpen());

            menu.setCloseOnOutsideClick(false);
            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
            assertTrue(menu.isOpen());
            manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
            assertFalse(menu.isOpen());
        }
    }

    @Test
    void animatesOpenAndClose() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.setAnimationDuration(0.3f);
            int[] closes = {0};
            menu.setCloseListener(() -> closes[0]++);
            menu.open(manager.context(), 400, 300);
            manager.layout();
            assertEquals(0, menu.openAmount(), 0.001f);

            manager.update(0.05f);
            float partial = menu.openAmount();
            assertTrue(partial > 0 && partial < 1);

            manager.update(1);
            assertEquals(1, menu.openAmount(), 0.001f);

            menu.close();
            assertTrue(menu.isOpen());
            assertTrue(menu.isClosing());
            manager.update(0.05f);
            assertTrue(menu.isOpen());
            assertTrue(menu.openAmount() > 0 && menu.openAmount() < 1);

            manager.update(1);
            assertFalse(menu.isOpen());
            assertFalse(menu.isClosing());
            assertEquals(1, closes[0]);
            assertFalse(manager.widgets().contains(menu));
        }
    }

    @Test
    void restoresPreviousFocusAndClosesWhenFocusLeaves() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Widget owner = focusable(new Rect(10, 10, 100, 30));
            manager.context().addTopLevel(owner);
            manager.focus(owner);

            RadialMenu menu = menu(4);
            menu.open(manager.context(), 400, 300);
            manager.layout();
            assertSame(menu, manager.focused());

            menu.close();
            assertFalse(menu.isOpen());
            assertSame(owner, manager.focused());

            menu.open(manager.context(), 400, 300);
            manager.layout();
            Widget other = focusable(new Rect(130, 10, 100, 30));
            manager.context().addTopLevel(other);
            manager.focus(other);
            assertFalse(menu.isOpen());
            assertSame(other, manager.focused());
        }
    }

    @Test
    void rejectsInvalidConfiguration() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = new RadialMenu();
            assertFalse(menu.isOpen());
            menu.open(manager.context(), 100, 100);
            assertFalse(menu.isOpen());
            assertThrows(NullPointerException.class,
                    () -> menu.open(null, 10, 10));
            assertThrows(IllegalArgumentException.class,
                    () -> menu.open(manager.context(), Float.NaN, 10));
            assertThrows(IllegalArgumentException.class, () -> menu.setRadius(0));
            assertThrows(IllegalArgumentException.class, () -> menu.setInnerRadiusRatio(1));
            assertThrows(IllegalArgumentException.class, () -> menu.setInnerRadiusRatio(-1));
            assertThrows(IllegalArgumentException.class, () -> menu.setIconSize(0));
            assertThrows(IllegalArgumentException.class, () -> menu.setAnimationDuration(-1));
            assertThrows(NullPointerException.class, () -> menu.setAnimationEasing(null));
            assertThrows(NullPointerException.class, () -> menu.setTitle(null));
            assertThrows(NullPointerException.class, () -> menu.addItem(null));
            assertThrows(NullPointerException.class, () -> menu.removeItem(null));
            assertEquals(Easing.EASE_OUT, menu.animationEasing());
        }
    }

    private static RadialMenu menu(int count) {
        RadialMenu menu = new RadialMenu();
        for (int index = 0; index < count; index++) {
            menu.addItem("Item " + index, null);
        }
        menu.setAnimationDuration(0);
        return menu;
    }

    private static Widget focusable(Rect bounds) {
        Widget widget = new Widget() { };
        widget.setFocusable(true);
        widget.setBounds(bounds);
        return widget;
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(TestViewports.LOGICAL);
        Widget root = new Widget() { };
        root.setBounds(new Rect(0, 0, TestViewports.LOGICAL.logicalWidth(),
                TestViewports.LOGICAL.logicalHeight()));
        manager.add(root);
        return manager;
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }
}
