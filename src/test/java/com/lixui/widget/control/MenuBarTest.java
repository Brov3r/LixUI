package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.Viewport;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.ImageCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.overlay.ContextMenu;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestTexture;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void titlesUseAFlatStyleInsteadOfTheButtonSurface() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        MenuBar bar = bar();
        MenuBar.Menu file = bar.addMenu("File");
        file.addItem("New", null);
        manager.add(bar);
        manager.render();

        Widget title = button(bar, 0);
        WidgetStyle style = title.style();
        assertEquals(Color.TRANSPARENT, style.background());
        assertEquals(Color.TRANSPARENT, style.hoverBackground());
        assertEquals(Color.TRANSPARENT, style.pressedBackground());
        assertEquals(Color.TRANSPARENT, style.border());
        assertEquals(0, style.cornerRadius());
        assertEquals(0, style.borderWidth());
        assertEquals(manager.context().theme().text(), style.text());
        assertEquals(manager.context().theme().textMuted(), style.text(WidgetState.DISABLED));

        assertTrue(roundedRectsInside(backend.lastDrawList().commands(), absoluteBounds(title))
                .isEmpty());
    }

    @Test
    void hoverAndOpenTitlesPaintThinMenuHighlights() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        MenuBar bar = bar();
        MenuBar.Menu file = bar.addMenu("File");
        MenuBar.Menu empty = bar.addMenu("Empty");
        file.addItem("New", null);
        manager.add(bar);
        manager.render();

        Widget title = button(bar, 0);
        move(manager, title);
        manager.render();
        assertTrue(file.contextMenu().isOpen());

        Color primary = manager.context().theme().primary();
        List<RoundedRectCommand> open = roundedRectsInside(backend.lastDrawList().commands(),
                absoluteBounds(title));
        assertEquals(1, open.size());
        assertEquals(3, open.getFirst().radius(), 0.0001f);
        assertEquals(primary.withAlpha(0.16f), open.getFirst().color());

        Widget emptyTitle = button(bar, 1);
        move(manager, emptyTitle);
        manager.render();
        assertFalse(file.contextMenu().isOpen());
        assertFalse(empty.contextMenu().isOpen());

        List<RoundedRectCommand> hover = roundedRectsInside(backend.lastDrawList().commands(),
                absoluteBounds(emptyTitle));
        assertEquals(1, hover.size());
        assertEquals(3, hover.getFirst().radius(), 0.0001f);
        assertEquals(primary.withAlpha(0.12f), hover.getFirst().color());
    }

    @Test
    void disabledTitlesAreMutedAndDoNotOpenOrHighlight() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        MenuBar bar = bar();
        MenuBar.Menu file = bar.addMenu("File");
        file.addItem("New", null);
        file.setEnabled(false);
        manager.add(bar);
        manager.render();

        Widget title = button(bar, 0);
        assertFalse(file.isEnabled());
        assertFalse(title.isEnabled());
        assertEquals(manager.context().theme().textMuted(),
                title.style().text(WidgetState.DISABLED));

        move(manager, title);
        manager.render();
        assertFalse(file.contextMenu().isOpen());
        assertTrue(roundedRectsInside(backend.lastDrawList().commands(), absoluteBounds(title))
                .isEmpty());

        click(manager, title);
        manager.render();
        assertFalse(file.contextMenu().isOpen());
        assertTrue(roundedRectsInside(backend.lastDrawList().commands(), absoluteBounds(title))
                .isEmpty());

        file.setEnabled(true);
        move(manager, title);
        manager.render();
        assertTrue(file.contextMenu().isOpen());

        file.setEnabled(false);
        assertFalse(file.contextMenu().isOpen());
        manager.render();
        assertTrue(roundedRectsInside(backend.lastDrawList().commands(), absoluteBounds(title))
                .isEmpty());
    }

    @Test
    void hidesIconsAndCheckMarksInsideTheDropdown() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        MenuBar bar = bar();
        MenuBar.Menu file = bar.addMenu("File");
        ContextMenu.Item open = file.addItem("Open", null);
        open.setShortcut("Ctrl+O");
        open.setChecked(true);
        TestTexture icon = new TestTexture(16, 16);
        open.setIcon(icon);
        manager.add(bar);
        manager.render();

        move(manager, button(bar, 0));
        manager.render();

        assertTrue(file.contextMenu().isOpen());
        assertTrue(open.isChecked());
        assertEquals("Ctrl+O", open.shortcut());

        List<ImageCommand> images = backend.lastDrawList().commands().stream()
                .filter(ImageCommand.class::isInstance)
                .map(ImageCommand.class::cast)
                .toList();
        assertTrue(images.isEmpty());
    }

    private static MenuBar bar() {
        MenuBar bar = new MenuBar();
        bar.setBounds(new Rect(0, 0, 300, 32));
        return bar;
    }

    private static Rect absoluteBounds(Widget widget) {
        Point origin = widget.absolutePosition();
        return new Rect(origin.x(), origin.y(), widget.bounds().width(), widget.bounds().height());
    }

    private static List<RoundedRectCommand> roundedRectsInside(List<DrawCommand> commands, Rect area) {
        return commands.stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .filter(command -> contains(area, command.bounds()))
                .toList();
    }

    private static boolean contains(Rect outer, Rect inner) {
        return inner.x() >= outer.x() - 0.0001f
                && inner.y() >= outer.y() - 0.0001f
                && inner.right() <= outer.right() + 0.0001f
                && inner.bottom() <= outer.bottom() + 0.0001f;
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
        return manager(new TestBackend());
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }
}
