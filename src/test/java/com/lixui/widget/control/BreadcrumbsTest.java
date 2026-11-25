package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.platform.Cursor;
import com.lixui.render.Viewport;
import com.lixui.render.command.TextCommand;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreadcrumbsTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void activatesClickableCrumbsAndSkipsTheCurrentLocation() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        useFont(manager);
        Breadcrumbs breadcrumbs = breadcrumbs(520);
        List<String> actions = new ArrayList<>();
        List<String> selected = new ArrayList<>();
        breadcrumbs.setOnSelect(crumb -> selected.add(crumb.label()));
        breadcrumbs.addCrumb("Home", () -> actions.add("home"));
        breadcrumbs.addCrumb("Projects", () -> actions.add("projects"));
        breadcrumbs.addCrumb("Current");
        manager.add(breadcrumbs);
        manager.render();

        clickCrumb(manager, breadcrumbs, 0);
        clickCrumb(manager, breadcrumbs, 1);
        clickCrumb(manager, breadcrumbs, 2);

        assertEquals(List.of("home", "projects"), actions);
        assertEquals(List.of("Home", "Projects"), selected);

        move(breadcrumbs, 0);
        assertSame(Cursor.POINTER, breadcrumbs.cursor());
        move(breadcrumbs, 2);
        assertSame(Cursor.DEFAULT, breadcrumbs.cursor());
    }

    @Test
    void collapsesMiddleCrumbsIntoEllipsisAndKeepsFirstAndLast() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        useFont(manager);
        Breadcrumbs breadcrumbs = breadcrumbs(190);
        List<String> actions = new ArrayList<>();
        breadcrumbs.addCrumb("Home", () -> actions.add("home"));
        breadcrumbs.addCrumb("Projects", () -> actions.add("projects"));
        breadcrumbs.addCrumb("LixUI", () -> actions.add("lixui"));
        breadcrumbs.addCrumb("Widgets", () -> actions.add("widgets"));
        breadcrumbs.addCrumb("Current", () -> actions.add("current"));
        manager.add(breadcrumbs);
        manager.render();

        assertFalse(breadcrumbs.crumbBounds(0).isEmpty());
        assertTrue(breadcrumbs.crumbBounds(1).isEmpty());
        assertTrue(breadcrumbs.crumbBounds(2).isEmpty());
        assertTrue(breadcrumbs.crumbBounds(3).isEmpty());
        assertFalse(breadcrumbs.crumbBounds(4).isEmpty());
        assertFalse(breadcrumbs.crumbBounds(-1).isEmpty());
        assertEquals(3, textCommandCount(backend));

        clickCrumb(manager, breadcrumbs, 0);
        clickCrumb(manager, breadcrumbs, 4);
        clickCrumb(manager, breadcrumbs, -1);

        assertEquals(List.of("home", "current"), actions);
    }

    @Test
    void keepsFirstAndLastInsideTheBoundsWhenEvenTheEllipsisDoesNotFit() {
        WidgetManager manager = manager(new TestBackend());
        useFont(manager);
        Breadcrumbs breadcrumbs = breadcrumbs(90);
        for (int index = 0; index < 5; index++) {
            breadcrumbs.addCrumb("Page " + index, () -> { });
        }
        manager.add(breadcrumbs);
        manager.render();

        Rect first = breadcrumbs.crumbBounds(0);
        Rect last = breadcrumbs.crumbBounds(4);
        assertFalse(first.isEmpty());
        assertFalse(last.isEmpty());
        assertTrue(first.x() >= 0);
        assertTrue(last.right() <= 90);
    }

    @Test
    void updatesAfterCrumbsAreRemovedOrCleared() {
        WidgetManager manager = manager(new TestBackend());
        useFont(manager);
        Breadcrumbs breadcrumbs = breadcrumbs(400);
        Breadcrumbs.Crumb home = breadcrumbs.addCrumb("Home", () -> { });
        breadcrumbs.addCrumb("Library");
        manager.add(breadcrumbs);
        manager.render();
        assertFalse(breadcrumbs.crumbBounds(0).isEmpty());

        breadcrumbs.removeCrumb(home);
        manager.render();
        assertEquals("Library", breadcrumbs.crumbs().get(0).label());
        assertFalse(breadcrumbs.crumbBounds(0).isEmpty());

        breadcrumbs.clearCrumbs();
        manager.render();
        assertTrue(breadcrumbs.crumbs().isEmpty());
        assertTrue(breadcrumbs.crumbBounds(0).isEmpty());
        assertEquals(0, breadcrumbs.measure(LayoutConstraints.loose(400, 40)).width());
    }

    private static Breadcrumbs breadcrumbs(float width) {
        Breadcrumbs breadcrumbs = new Breadcrumbs();
        breadcrumbs.setBounds(new Rect(10, 10, width, 30));
        return breadcrumbs;
    }

    private static void clickCrumb(WidgetManager manager, Breadcrumbs breadcrumbs, int index) {
        Rect bounds = breadcrumbs.crumbBounds(index);
        Point origin = breadcrumbs.absolutePosition();
        float x = origin.x() + bounds.x() + bounds.width() / 2;
        float y = origin.y() + bounds.y() + bounds.height() / 2;
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static void move(Breadcrumbs breadcrumbs, int index) {
        Rect bounds = breadcrumbs.crumbBounds(index);
        breadcrumbs.onPointerMove(bounds.x() + bounds.width() / 2,
                bounds.y() + bounds.height() / 2);
    }

    private static int textCommandCount(TestBackend backend) {
        return (int) backend.lastDrawList().commands().stream()
                .filter(TextCommand.class::isInstance)
                .count();
    }

    private static void useFont(WidgetManager manager) {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        manager.setDefaultFont(manager.context().createFont(data.get(), 16));
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }
}
