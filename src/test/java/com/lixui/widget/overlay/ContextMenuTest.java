package com.lixui.widget.overlay;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.Viewport;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.Panel;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMenuTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;
    private static final float MARGIN = 4;
    private static final float ITEM_HEIGHT = 28;
    private static final float PADDING = 4;

    @Test
    void clampsToTheViewportAndActivatesEnabledItems() {
        WidgetManager manager = manager();
        ContextMenu menu = new ContextMenu();
        int[] actions = {0};
        menu.addItem("Open", () -> actions[0]++);
        ContextMenu.Item disabled = menu.addItem("Inspect", () -> actions[0]++);
        disabled.setEnabled(false);

        menu.open(manager.context(), VIEWPORT.logicalWidth() - 20,
                VIEWPORT.logicalHeight() - 20);
        manager.layout();

        Rect root = menu.levelBounds(0);
        assertTrue(root.right() <= VIEWPORT.logicalWidth() - MARGIN + 0.01f);
        assertTrue(root.bottom() <= VIEWPORT.logicalHeight() - MARGIN + 0.01f);

        click(manager, root.x() + 20, root.y() + PADDING + ITEM_HEIGHT + ITEM_HEIGHT / 2);
        assertTrue(menu.isOpen());
        assertEquals(0, actions[0]);

        click(manager, root.x() + 20, root.y() + PADDING + ITEM_HEIGHT / 2);
        assertFalse(menu.isOpen());
        assertEquals(1, actions[0]);
    }

    @Test
    void measuresItemTextWithTheLeadingInset() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        WidgetManager manager = manager();
        manager.setDefaultFont(manager.context().createFont(data.get(), 16));
        ContextMenu menu = new ContextMenu();
        menu.setMinimumMenuWidth(0);
        menu.addItem("Save as", null);
        menu.open(manager.context(), 100, 100);
        manager.layout();

        Rect root = menu.levelBounds(0);
        float expected = menu.padding() * 2 + ContextMenu.LEFT_INSET
                + menu.font().measure("Save as").width() + ContextMenu.RIGHT_PADDING;
        assertEquals(expected, root.width(), 0.01f);
    }

    @Test
    void opensSubmenuOnHoverAndKeepsItAcrossTheGap() {
        WidgetManager manager = manager();
        ContextMenu menu = new ContextMenu();
        ContextMenu.Item move = menu.addItem("Move to", null);
        move.addChild(new ContextMenu.Item("Inventory", null));
        move.addChild(new ContextMenu.Item("Vault", null));
        menu.open(manager.context(), 200, 100);
        manager.layout();

        Rect root = menu.levelBounds(0);
        float itemY = root.y() + PADDING + ITEM_HEIGHT / 2;
        manager.handle(new PointerMoved(root.x() + 20, itemY));

        assertEquals(2, menu.levelCount());
        Rect submenu = menu.levelBounds(1);
        float gap = submenu.x() >= root.right()
                ? submenu.x() - root.right() : root.x() - submenu.right();
        assertEquals(menu.submenuGap(), gap, 0.01f);

        manager.handle(new PointerMoved(root.right() + 2, itemY));
        assertEquals(2, menu.levelCount());
    }

    @Test
    void flipsSubmenuToTheLeftAndStaysInsideTheViewport() {
        WidgetManager manager = manager();
        ContextMenu menu = new ContextMenu();
        ContextMenu.Item move = menu.addItem("Move to", null);
        move.addChild(new ContextMenu.Item("Inventory", null));
        move.addChild(new ContextMenu.Item("Vault", null));
        menu.open(manager.context(), VIEWPORT.logicalWidth() - 180, 100);
        manager.layout();

        Rect root = menu.levelBounds(0);
        float itemY = root.y() + PADDING + ITEM_HEIGHT / 2;
        manager.handle(new PointerMoved(root.x() + 20, itemY));

        Rect submenu = menu.levelBounds(1);
        assertTrue(submenu.x() < root.x());
        assertTrue(submenu.x() >= MARGIN - 0.01f);
        assertTrue(submenu.right() <= VIEWPORT.logicalWidth() - MARGIN + 0.01f);
        assertEquals(menu.submenuGap(), root.x() - submenu.right(), 0.01f);
    }

    @Test
    void nestedSubmenuAvoidsAncestorMenus() {
        WidgetManager manager = manager();
        ContextMenu menu = new ContextMenu();
        ContextMenu.Item move = menu.addItem("Move to", null);
        ContextMenu.Item archive = new ContextMenu.Item("Archive", null);
        archive.addChild(new ContextMenu.Item("Rare shelf", null));
        move.addChild(archive);
        menu.open(manager.context(), VIEWPORT.logicalWidth() - 180, 100);
        manager.layout();

        Rect root = menu.levelBounds(0);
        manager.handle(new PointerMoved(root.x() + 20, root.y() + PADDING + ITEM_HEIGHT / 2));
        Rect submenu = menu.levelBounds(1);
        manager.handle(new PointerMoved(submenu.x() + 20,
                submenu.y() + PADDING + ITEM_HEIGHT / 2));

        assertEquals(3, menu.levelCount());
        Rect nested = menu.levelBounds(2);
        assertTrue(nested.right() <= root.x() + 0.01f);
        assertTrue(nested.right() <= submenu.x() - menu.submenuGap() + 0.01f);
    }

    @Test
    void opensFromAWidgetProviderOnRightClickAndClosesOutside() {
        WidgetManager manager = manager();
        Panel root = new Panel();
        root.setBounds(new Rect(0, 0, 300, 200));
        ContextMenu menu = new ContextMenu();
        menu.addItem("Open", null);
        root.setContextMenuProvider((widget, x, y) -> menu);
        manager.add(root);
        manager.render();

        manager.handle(new PointerPressed(60, 80, MouseButton.RIGHT, Modifiers.NONE));
        manager.render();

        assertTrue(menu.isOpen());
        assertEquals(1, menu.levelCount());

        manager.handle(new PointerPressed(700, 500, MouseButton.LEFT, Modifiers.NONE));

        assertFalse(menu.isOpen());
    }

    @Test
    void closesOnEscapeAndRejectsInvalidConfiguration() {
        WidgetManager manager = manager();
        ContextMenu menu = new ContextMenu();
        menu.addItem("Open", null);
        menu.open(manager.context(), 100, 100);
        manager.layout();

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
        assertFalse(menu.isOpen());

        assertThrows(NullPointerException.class, () -> menu.open(null, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> menu.open(manager.context(), Float.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> menu.setItemHeight(0));
        assertThrows(IllegalArgumentException.class, () -> menu.setSeparatorHeight(0));
        assertThrows(IllegalArgumentException.class, () -> menu.setPadding(-1));
        assertThrows(IllegalArgumentException.class, () -> menu.setMinimumMenuWidth(-1));
        assertThrows(IllegalArgumentException.class, () -> menu.setIconSize(0));
        assertThrows(IllegalArgumentException.class, () -> menu.setSubmenuGap(-1));
        assertThrows(IllegalArgumentException.class, () -> menu.setViewportMargin(-1));
        assertThrows(IllegalArgumentException.class, () -> menu.setCornerRadius(-1));
        assertThrows(NullPointerException.class, () -> menu.setDangerColor(null));
        assertThrows(NullPointerException.class, () -> menu.addItem((ContextMenu.Item) null));
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
