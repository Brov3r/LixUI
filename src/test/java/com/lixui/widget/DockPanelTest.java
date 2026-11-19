package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DockPanelTest {
    @Test
    void docksStripsInInsertionOrderAndFillsTheRest() {
        DockPanel panel = new DockPanel();
        panel.add(new FixedWidget(10, 30), Dock.TOP);
        panel.add(new FixedWidget(10, 20), Dock.BOTTOM);
        panel.add(new FixedWidget(40, 10), Dock.LEFT);
        FixedWidget fill = new FixedWidget(10, 10);
        panel.add(fill);

        panel.layout(new Rect(0, 0, 200, 100));

        assertEquals(new Rect(0, 0, 200, 30), panel.children().get(0).bounds());
        assertEquals(new Rect(0, 80, 200, 20), panel.children().get(1).bounds());
        assertEquals(new Rect(0, 30, 40, 50), panel.children().get(2).bounds());
        assertEquals(new Rect(40, 30, 160, 50), fill.bounds());
    }

    @Test
    void rightDockRespectsPaddingAndTheRemainingArea() {
        DockPanel panel = new DockPanel();
        panel.setPadding(5);
        panel.add(new FixedWidget(25, 10), Dock.RIGHT);
        FixedWidget fill = new FixedWidget(10, 10);
        panel.add(fill);

        panel.layout(new Rect(0, 0, 100, 60));

        assertEquals(new Rect(70, 5, 25, 50), panel.children().get(0).bounds());
        assertEquals(new Rect(5, 5, 65, 50), fill.bounds());
    }

    @Test
    void measuresTheLargestDockedChildAndSkipsManualChildren() {
        DockPanel panel = new DockPanel();
        panel.setPadding(4);
        panel.add(new FixedWidget(60, 20), Dock.TOP);
        panel.add(new FixedWidget(30, 50), Dock.LEFT);
        FixedWidget manual = new FixedWidget(200, 200);
        panel.add(manual, Dock.NONE);

        Size measured = panel.measure(LayoutConstraints.loose(500, 500));

        assertEquals(68, measured.width(), 0.001f);
        assertEquals(58, measured.height(), 0.001f);
        panel.layout(new Rect(0, 0, 100, 100));
        assertEquals(new Rect(0, 0, 200, 200), manual.bounds());
    }

    @Test
    void dockAssignmentsDefaultToFillAndValidateOwnership() {
        DockPanel panel = new DockPanel();
        Widget child = new FixedWidget(10, 10);
        panel.add(child);
        assertEquals(Dock.FILL, panel.dock(child));

        panel.setDock(child, Dock.TOP);
        assertEquals(Dock.TOP, panel.dock(child));

        assertThrows(IllegalArgumentException.class,
                () -> panel.setDock(new FixedWidget(1, 1), Dock.LEFT));
        assertThrows(NullPointerException.class, () -> panel.add(new FixedWidget(1, 1), null));
        assertThrows(NullPointerException.class, () -> panel.dock(null));
        assertThrows(NullPointerException.class, () -> panel.setDock(null, Dock.TOP));
    }

    @Test
    void removedChildrenLoseTheirDockAssignment() {
        DockPanel panel = new DockPanel();
        Widget child = new FixedWidget(10, 30);
        panel.add(child, Dock.TOP);

        panel.remove(child);

        assertEquals(Dock.FILL, panel.dock(child));
        panel.add(new FixedWidget(10, 10));
        panel.layout(new Rect(0, 0, 100, 100));
        assertEquals(new Rect(0, 0, 100, 100), panel.children().get(0).bounds());
    }
}
