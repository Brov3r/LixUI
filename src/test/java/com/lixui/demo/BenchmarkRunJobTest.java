package com.lixui.demo;

import com.lixui.animation.Animation;
import com.lixui.animation.Easing;
import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkRunJobTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void jobRunsWarmupAndMeasuredIterationsAndLeavesTheFinalTree() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            BenchmarkStage stage = new BenchmarkStage(120);
            stage.setBounds(new Rect(0, 0, 420, 120));
            manager.add(stage);
            manager.render();

            BenchmarkRunJob job = new BenchmarkRunJob(stage, BenchmarkScenario.TEXT,
                    12, 1, 2, 2);
            int guard = 0;
            while (!job.isFinished() && guard++ < 20) {
                job.step();
            }

            assertTrue(job.isFinished());
            assertFalse(stage.children().isEmpty());
            assertTrue(stage.hasContent());

            BenchmarkRunResult result = job.result();
            assertNotNull(result);
            assertEquals(BenchmarkScenario.TEXT, result.scenario());
            assertEquals(12, result.count());
            assertEquals(1, result.warmupIterations());
            assertEquals(2, result.measuredIterations());
            assertEquals(2, result.updateFrames());
            assertEquals(2, result.buildNanos().count());
            assertEquals(2, result.layoutNanos().count());
            assertEquals(4, result.updateNanos().count());
            assertTrue(result.widgetCount() >= 13);
            assertTrue(result.buildNanos().median() > 0);

            stage.clear();
            assertFalse(stage.hasContent());
            assertTrue(stage.children().isEmpty());
        }
    }

    @Test
    void jobDoesNotAdvanceTheSharedHostAnimator() {
        try (WidgetManager manager = manager(new TestBackend())) {
            Animation hostAnimation = manager.context().animator().animate(
                    0, 1, 1, Easing.LINEAR, value -> { });
            BenchmarkStage stage = new BenchmarkStage(120);
            stage.setBounds(new Rect(0, 0, 420, 120));
            manager.add(stage);
            manager.render();

            BenchmarkRunJob job = new BenchmarkRunJob(stage, BenchmarkScenario.TEXT,
                    12, 0, 1, 4);
            int guard = 0;
            while (!job.isFinished() && guard++ < 20) {
                job.step();
            }

            assertTrue(job.isFinished());
            assertEquals(0, hostAnimation.progress(), 0.0001f);
            assertFalse(hostAnimation.isFinished());
            assertTrue(manager.context().animator().size() >= 1);
        }
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        Optional<FontData> data = SystemFonts.findDefault();
        if (data.isPresent()) {
            manager.setDefaultFont(manager.context().createFont(data.get(), 16));
        }
        return manager;
    }
}
