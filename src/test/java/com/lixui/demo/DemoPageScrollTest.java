package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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

    private static void assertBottomRowReachable(DemoScrollPage scroll) {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            Optional<FontData> data = SystemFonts.findDefault();
            if (data.isPresent()) {
                manager.setDefaultFont(manager.context().createFont(data.get(), FONT_SIZE));
            }
            scroll.setBounds(PAGE_BOUNDS);
            manager.add(scroll);
            manager.render();

            Widget page = scroll.content();
            assertTrue(page.bounds().height() > scroll.bounds().height(),
                    "the page must keep its natural height");
            Widget bottom = page.children().get(page.children().size() - 1);

            scroll.setScroll(0, Float.MAX_VALUE);
            manager.render();

            assertTrue(scroll.scrollY() > 0, "the page must have a scroll range");
            float bottomEdge = page.bounds().y() + bottom.bounds().y() + bottom.bounds().height();
            assertTrue(bottomEdge > 0 && bottomEdge <= scroll.bounds().height() + 0.001f,
                    "the last row must be visible after scrolling to the bottom");
        }
    }
}
