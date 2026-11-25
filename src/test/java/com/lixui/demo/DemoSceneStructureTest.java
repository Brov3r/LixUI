package com.lixui.demo;

import com.lixui.input.InputState;
import com.lixui.platform.UiPlatform;
import com.lixui.render.Viewport;
import com.lixui.widget.container.TabPanel;
import com.lixui.widget.testsupport.TestBackend;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoSceneStructureTest {
    @Test
    void benchmarkIsTheSecondTopLevelTabAndTheEightGroupsRemain() throws Exception {
        TestBackend backend = new TestBackend();
        try (DemoWidgetScene scene = new DemoWidgetScene(new InputState(), backend,
                UiPlatform.NONE)) {
            scene.resize(new Viewport(620, 560, 620, 560));
            TabPanel outerTabs = (TabPanel) field(scene, "outerTabs");
            assertEquals(List.of("Basics", "Benchmark", "Inputs", "Forms", "Data", "Feedback",
                            "Navigation", "Overlays", "Structure"),
                    outerTabs.tabs().stream().map(TabPanel.Tab::title).toList());

            outerTabs.select(1);
            scene.update(1f / 60f);
            scene.render();

            DemoScrollPage benchmarkScroll = assertInstanceOf(DemoScrollPage.class,
                    outerTabs.tabs().get(1).content());
            BenchmarkDemoPage benchmarkPage = assertInstanceOf(BenchmarkDemoPage.class,
                    benchmarkScroll.content());
            assertTrue(benchmarkPage.bounds().height() > 0);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
