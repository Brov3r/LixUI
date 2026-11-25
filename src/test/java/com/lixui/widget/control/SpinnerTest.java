package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpinnerTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void advancesRotationWithFrameTime() {
        Spinner spinner = new Spinner();
        spinner.setSpeed(1);

        spinner.onUpdate(new FrameInfo(0.25f, 0.25, 1));
        assertEquals(Math.PI / 2, spinner.rotation(), 0.001);

        spinner.onUpdate(new FrameInfo(1, 1.25, 2));
        assertEquals(Math.PI / 2, spinner.rotation(), 0.001);
    }

    @Test
    void drawsTheTrackAndTheArc() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Spinner spinner = new Spinner();
            spinner.setBounds(new Rect(10, 20, 40, 40));
            manager.add(spinner);

            manager.render();
            assertEquals(2, backend.lastDrawList().commands().size());

            spinner.setTrackVisible(false);
            manager.render();
            assertEquals(1, backend.lastDrawList().commands().size());
        }
    }

    @Test
    void measuresTheConfiguredDiameter() {
        Spinner spinner = new Spinner();
        spinner.setDiameter(48);

        Size measured = spinner.measure(LayoutConstraints.loose(200, 200));

        assertEquals(48, measured.width(), 0.001f);
        assertEquals(48, measured.height(), 0.001f);
    }

    @Test
    void rejectsInvalidConfiguration() {
        Spinner spinner = new Spinner();
        assertThrows(IllegalArgumentException.class, () -> spinner.setDiameter(0));
        assertThrows(IllegalArgumentException.class, () -> spinner.setDiameter(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> spinner.setThickness(0));
        assertThrows(IllegalArgumentException.class, () -> spinner.setSpeed(-1));
        assertThrows(IllegalArgumentException.class, () -> spinner.setSpeed(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> spinner.setArcDegrees(0));
        assertThrows(IllegalArgumentException.class, () -> spinner.setArcDegrees(361));
        assertThrows(IllegalArgumentException.class, () -> spinner.setArcDegrees(Float.NaN));
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }
}
