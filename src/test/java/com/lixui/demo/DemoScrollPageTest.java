package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.testsupport.FixedWidget;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DemoScrollPageTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void scrollingMakesTheLowerPageContentReachable() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            VBox page = new VBox();
            page.setPadding(10);
            page.setGap(10);
            FixedWidget top = new FixedWidget(100, 300);
            FixedWidget bottom = new FixedWidget(100, 300);
            page.add(top).add(bottom);

            DemoScrollPage scroll = new DemoScrollPage(page);
            scroll.setBounds(new Rect(0, 0, 200, 150));
            manager.add(scroll);
            manager.render();

            assertEquals(630, page.bounds().height(), 0.001f);
            assertSame(top, scroll.hitTest(50, 130));

            scroll.setScroll(0, 1000);
            manager.render();

            assertEquals(480, scroll.scrollY(), 0.001f);
            assertSame(bottom, scroll.hitTest(50, 130));
        }
    }
}
