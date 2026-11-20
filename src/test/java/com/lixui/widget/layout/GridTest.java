package com.lixui.widget.layout;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.testsupport.FixedWidget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GridTest {
    @Test
    void placesChildrenInFixedAndProportionalTracks() {
        Grid grid = new Grid();
        grid.setColumns(GridTrack.fixed(40), GridTrack.proportional(1), GridTrack.proportional(2));
        grid.setRows(GridTrack.fixed(20), GridTrack.proportional(1));
        grid.setGap(5);
        for (int index = 0; index < 6; index++) {
            grid.add(new FixedWidget(10, 10));
        }

        grid.layout(new Rect(0, 0, 300, 100));

        List<Widget> children = grid.children();
        assertEquals(new Rect(0, 0, 40, 20), children.get(0).bounds());
        assertEquals(new Rect(45, 0, 250f / 3, 20), children.get(1).bounds());
        assertEquals(new Rect(45 + 250f / 3 + 5, 0, 500f / 3, 20), children.get(2).bounds());
        assertEquals(new Rect(0, 25, 40, 75), children.get(3).bounds());
        assertEquals(new Rect(45, 25, 250f / 3, 75), children.get(4).bounds());
        assertEquals(new Rect(45 + 250f / 3 + 5, 25, 500f / 3, 75), children.get(5).bounds());
    }

    @Test
    void addsImplicitRowsSizedToTheirContent() {
        Grid grid = new Grid();
        grid.setColumns(GridTrack.fixed(50), GridTrack.fixed(50));
        grid.setRows(GridTrack.fixed(10));
        grid.setGap(10);
        grid.add(new FixedWidget(10, 20)).add(new FixedWidget(10, 30))
                .add(new FixedWidget(10, 25)).add(new FixedWidget(10, 40));

        grid.layout(new Rect(0, 0, 300, 200));

        List<Widget> children = grid.children();
        assertEquals(new Rect(0, 0, 50, 10), children.get(0).bounds());
        assertEquals(new Rect(60, 0, 50, 10), children.get(1).bounds());
        assertEquals(new Rect(0, 20, 50, 40), children.get(2).bounds());
        assertEquals(new Rect(60, 20, 50, 40), children.get(3).bounds());
    }

    @Test
    void measuresFixedAndProportionalTracksFromTheirChildren() {
        Grid grid = new Grid();
        grid.setPadding(2);
        grid.setColumns(GridTrack.fixed(40), GridTrack.proportional(1));
        grid.setRows(GridTrack.proportional(1), GridTrack.fixed(20));
        grid.setGap(5);
        grid.add(new FixedWidget(100, 30)).add(new FixedWidget(60, 50));

        Size measured = grid.measure(LayoutConstraints.loose(500, 500));

        assertEquals(109, measured.width(), 0.001f);
        assertEquals(79, measured.height(), 0.001f);
    }

    @Test
    void usesOneStretchingColumnWhenNoTracksAreDefined() {
        Grid grid = new Grid();
        grid.add(new FixedWidget(30, 10)).add(new FixedWidget(20, 20)).add(new FixedWidget(50, 30));

        Size measured = grid.measure(LayoutConstraints.loose(500, 500));
        assertEquals(50, measured.width(), 0.001f);
        assertEquals(60, measured.height(), 0.001f);

        grid.layout(new Rect(0, 0, 200, 300));

        List<Widget> children = grid.children();
        assertEquals(new Rect(0, 0, 200, 10), children.get(0).bounds());
        assertEquals(new Rect(0, 10, 200, 20), children.get(1).bounds());
        assertEquals(new Rect(0, 30, 200, 30), children.get(2).bounds());
    }

    @Test
    void invisibleChildrenTakeNoCellAndKeepTheirBounds() {
        Grid grid = new Grid();
        grid.setColumns(GridTrack.fixed(40), GridTrack.proportional(1));
        grid.setRows(GridTrack.fixed(20));
        FixedWidget hidden = new FixedWidget(80, 80);
        hidden.setVisible(false);
        FixedWidget first = new FixedWidget(10, 10);
        FixedWidget second = new FixedWidget(20, 20);
        grid.add(hidden).add(first).add(second);

        grid.layout(new Rect(0, 0, 100, 50));

        assertEquals(new Rect(0, 0, 80, 80), hidden.bounds());
        assertEquals(new Rect(0, 0, 40, 20), first.bounds());
        assertEquals(new Rect(40, 0, 60, 20), second.bounds());
    }

    @Test
    void rejectsInvalidTracksAndSpacing() {
        assertThrows(IllegalArgumentException.class, () -> GridTrack.fixed(-1));
        assertThrows(IllegalArgumentException.class, () -> GridTrack.fixed(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> GridTrack.proportional(0));
        assertThrows(IllegalArgumentException.class, () -> GridTrack.proportional(-1));
        assertThrows(IllegalArgumentException.class, () -> GridTrack.proportional(Float.NaN));
        Grid grid = new Grid();
        assertThrows(IllegalArgumentException.class, () -> grid.setGap(-1));
        assertThrows(IllegalArgumentException.class, () -> grid.setGap(Float.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> grid.setColumns((GridTrack[]) null));
        assertThrows(NullPointerException.class, () -> grid.setRows((GridTrack[]) null));
        assertThrows(NullPointerException.class,
                () -> grid.setColumns(GridTrack.fixed(10), null));
    }
}
