package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.control.Button;
import com.lixui.widget.layout.Flow;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoPageScrollTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;
    private static final Rect PAGE_BOUNDS = new Rect(0, 0, 520, 320);
    private static final float FONT_SIZE = 16;

    @Test
    void chartPageKeepsItsNaturalHeightAndRevealsTheControls() {
        DemoScrollPage scroll = assertInstanceOf(DemoScrollPage.class,
                new ChartDemoPage(null).build());

        assertBottomRowReachable(scroll);
    }

    @Test
    void stepperPageKeepsTheStatusLineReachable() {
        DemoScrollPage scroll = assertInstanceOf(DemoScrollPage.class,
                new StepperDemoPage(null, (name, size, assign) -> {
                }).build());

        assertBottomRowReachable(scroll);
    }

    @Test
    void modalPageKeepsEveryVariantInOrder() {
        Widget modalPage = new DemoModalPage(null).page();
        DemoScrollPage scroll = new DemoScrollPage(modalPage);

        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            scroll.setBounds(PAGE_BOUNDS);
            manager.add(scroll);
            manager.render();

            VBox page = assertInstanceOf(VBox.class, modalPage);
            Flow actions = assertInstanceOf(Flow.class, page.children().get(1));
            List<String> labels = actions.children().stream()
                    .map(widget -> assertInstanceOf(Button.class, widget).text())
                    .toList();

            assertEquals(List.of("Information", "Confirmation", "Prompt", "Custom content",
                    "Backdrop dismiss", "No scrim", "Icon header"), labels);
            for (Widget action : actions.children()) {
                float actionBottom = actions.bounds().y() + action.bounds().y()
                        + action.bounds().height();
                assertTrue(actionBottom <= page.bounds().height(),
                        "every modal action must stay inside the page");
            }
        }
    }

    private static void assertBottomRowReachable(DemoScrollPage scroll) {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            scroll.setBounds(PAGE_BOUNDS);
            manager.add(scroll);
            manager.render();

            Widget page = scroll.content();
            assertTrue(page.bounds().height() > scroll.bounds().height(),
                    "the page must keep its natural height");
            Widget bottom = page.children().get(page.children().size() - 1);

            scroll.setScroll(0, Float.MAX_VALUE);

            assertTrue(scroll.scrollY() > 0, "the page must have a scroll range");
            float bottomEdge = page.bounds().y() + bottom.bounds().y() + bottom.bounds().height();
            assertTrue(bottomEdge > 0 && bottomEdge <= scroll.bounds().height() + 0.001f,
                    "the last row must be visible after scrolling to the bottom");
        }
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        Optional<FontData> data = SystemFonts.findDefault();
        if (data.isPresent()) {
            manager.setDefaultFont(manager.context().createFont(data.get(), FONT_SIZE));
        }
        return manager;
    }
}
