package com.lixui.demo;

import com.lixui.input.InputState;
import com.lixui.platform.UiPlatform;
import com.lixui.render.Viewport;
import com.lixui.widget.Widget;
import com.lixui.widget.container.TabPanel;
import com.lixui.widget.control.Label;
import com.lixui.widget.layout.Flow;
import com.lixui.widget.layout.Grid;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.testsupport.TestBackend;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoLayoutsPageTest {
    private static final float EPSILON = 0.001f;

    @Test
    void layoutsPageKeepsRowsFlowItemsAndCaptionsSeparated() throws Exception {
        try (DemoWidgetScene scene = new DemoWidgetScene(new InputState(), new TestBackend(),
                UiPlatform.NONE)) {
            TabPanel outerTabs = (TabPanel) field(scene, "outerTabs");
            TabPanel structure = assertInstanceOf(TabPanel.class,
                    select(outerTabs, "Structure").content());
            DemoScrollPage scroll = assertInstanceOf(DemoScrollPage.class,
                    select(structure, "Layouts").content());

            scene.resize(new Viewport(620, 560, 620, 560));
            scene.update(1f / 60f);
            scene.render();

            VBox page = assertInstanceOf(VBox.class, scroll.content());
            Grid grid = find(page.children(), Grid.class);
            Flow flow = find(page.children(), Flow.class);

            assertGridRowsDoNotOverlap(grid);
            assertFlowChildrenStayInside(flow);
            assertCaptionsDoNotOverlapTheNextSection(page);
        }
    }

    private static void assertGridRowsDoNotOverlap(Grid grid) {
        int columns = grid.columns().size();
        List<Widget> cells = grid.children();
        int rows = (cells.size() + columns - 1) / columns;
        float previousBottom = -1;
        for (int row = 0; row < rows; row++) {
            Widget first = cells.get(row * columns);
            float top = first.bounds().y();
            float height = 0;
            for (int column = 0; column < columns; column++) {
                Widget cell = cells.get(row * columns + column);
                assertEquals(top, cell.bounds().y(), EPSILON);
                height = Math.max(height, cell.bounds().height());
            }
            assertTrue(height > 0, "grid row " + row + " must have a visible height");
            assertTrue(top > previousBottom,
                    "grid row " + row + " must start below the previous row");
            previousBottom = top + height;
        }
    }

    private static void assertFlowChildrenStayInside(Flow flow) {
        for (Widget child : flow.children()) {
            if (!child.isVisible()) {
                continue;
            }
            assertTrue(child.bounds().width() > 0, "flow items must keep their preset width");
            assertTrue(child.bounds().height() > 0, "flow items must keep their preset height");
            assertTrue(child.bounds().x() >= 0, "flow items must start inside the flow");
            assertTrue(child.bounds().y() >= 0, "flow items must start inside the flow");
            assertTrue(child.bounds().x() + child.bounds().width()
                            <= flow.bounds().width() + EPSILON,
                    "flow items must stay inside the flow width");
            assertTrue(child.bounds().y() + child.bounds().height()
                            <= flow.bounds().height() + EPSILON,
                    "flow items must stay inside the flow height");
        }
    }

    private static void assertCaptionsDoNotOverlapTheNextSection(VBox page) {
        List<Widget> children = page.children();
        for (int index = 0; index < children.size() - 1; index++) {
            Widget child = children.get(index);
            if (!(child instanceof Label)) {
                continue;
            }
            float captionBottom = child.bounds().y() + child.bounds().height();
            assertTrue(captionBottom <= children.get(index + 1).bounds().y() + EPSILON,
                    "caption " + index + " must not overlap the next section");
        }
    }

    private static TabPanel.Tab select(TabPanel panel, String title) {
        for (int index = 0; index < panel.tabs().size(); index++) {
            TabPanel.Tab tab = panel.tabs().get(index);
            if (title.equals(tab.title())) {
                panel.select(index);
                return tab;
            }
        }
        throw new IllegalArgumentException("Missing tab: " + title);
    }

    private static <T extends Widget> T find(List<Widget> children, Class<T> type) {
        return children.stream().filter(type::isInstance).map(type::cast).findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + type.getSimpleName()));
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
