package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.Viewport;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.overlay.ContextMenu;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuBarTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;
    private static final float MENU_PADDING = 4;
    private static final float ITEM_HEIGHT = 28;

    @Test
    void opensOnHoverSwitchesMenusAndTogglesThePressedTitle() {
        WidgetManager manager = manager();
        MenuBar bar = bar();
        MenuBar.Menu file = bar.addMenu("File");
        MenuBar.Menu edit = bar.addMenu("Edit");
        file.addItem("New", null);
        edit.addItem("Undo", null);
        manager.add(bar);
        manager.render();

        move(manager, button(bar, 0));
        manager.render();
        assertTrue(file.contextMenu().isOpen());

        move(manager, button(bar, 1));
        manager.render();
        assertFalse(file.contextMenu().isOpen());
        assertTrue(edit.contextMenu().isOpen());

        move(manager, button(bar, 0));
        manager.render();
        assertTrue(file.contextMenu().isOpen());
        assertFalse(edit.contextMenu().isOpen());

        click(manager, button(bar, 0));
        assertFalse(file.contextMenu().isOpen());
        assertSame(button(bar, 0), manager.focused());

        click(manager, button(bar, 0));
        assertTrue(file.contextMenu().isOpen());
    }

    @Test
    void activatesAnItemAndReturnsFocusToTheTitle() {
        WidgetManager manager = manager();
        MenuBar bar = bar();
        int[] actions = {0};
        MenuBar.Menu file = bar.addMenu("File");
        file.addItem("New", () -> actions[0]++);
        manager.add(bar);
        manager.render();

        Widget title = button(bar, 0);
        click(manager, title);
        manager.render();
        assertTrue(file.contextMenu().isOpen());

        Point origin = title.absolutePosition();
        float itemX = Math.max(4, origin.x()) + 12;
        float itemY = origin.y() + title.bounds().height() + MENU_PADDING + ITEM_HEIGHT / 2;
        clickAt(manager, itemX, itemY);

        assertEquals(1, actions[0]);
        assertFalse(file.contextMenu().isOpen());
        assertSame(title, manager.focused());
    }

    @Test
    void keepsCheckedShortcutDisabledAndSubmenuItems() {
        MenuBar bar = bar();
        MenuBar.Menu file = bar.addMenu("File");

        ContextMenu.Item open = file.addItem("Open", null);
        open.setShortcut("Ctrl+O");
        open.setChecked(true);

        ContextMenu.Item remove = file.addItem("Delete", null);
        remove.setEnabled(false);

        ContextMenu.Item recent = file.addItem("Recent", null);
        ContextMenu.Item first = recent.addChild(new ContextMenu.Item("First"));
        ContextMenu.Item second = new ContextMenu.Item("Second");
        second.setChecked(true);
        recent.addChild(second);

        assertTrue(open.isChecked());
        assertEquals("Ctrl+O", open.shortcut());
        assertFalse(remove.isEnabled());
        assertTrue(recent.hasChildren());
        assertEquals(2, recent.children().size());
        assertFalse(first.isChecked());
        assertTrue(second.isChecked());
    }

    private static MenuBar bar() {
        MenuBar bar = new MenuBar();
        bar.setBounds(new Rect(0, 0, 300, 32));
        return bar;
    }

    private static Widget button(MenuBar bar, int index) {
        return bar.children().get(0).children().get(index);
    }

    private static void move(WidgetManager manager, Widget target) {
        Point origin = target.absolutePosition();
        Rect bounds = target.bounds();
        manager.handle(new PointerMoved(origin.x() + bounds.width() / 2,
                origin.y() + bounds.height() / 2));
    }

    private static void click(WidgetManager manager, Widget target) {
        Point origin = target.absolutePosition();
        Rect bounds = target.bounds();
        clickAt(manager, origin.x() + bounds.width() / 2, origin.y() + bounds.height() / 2);
    }

    private static void clickAt(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        return manager;
    }
}
