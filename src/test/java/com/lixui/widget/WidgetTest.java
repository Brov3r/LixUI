package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.render.DrawList;
import com.lixui.render.ImageData;
import com.lixui.render.RenderBackend;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetTest {
    private static final Viewport VIEWPORT = new Viewport(1600, 1200, 800, 600);

    @Test
    void containmentRespectsVisibilityAndEnabledState() {
        Probe probe = new Probe("probe", new ArrayList<>());
        probe.setBounds(new Rect(10, 10, 50, 50));

        assertTrue(probe.contains(20, 20));
        assertFalse(probe.contains(5, 20));

        probe.setVisible(false);
        assertFalse(probe.contains(20, 20));

        probe.setVisible(true);
        probe.setEnabled(false);
        assertFalse(probe.contains(20, 20));
    }

    @Test
    void childrenTrackParentsAndDetach() {
        Probe root = new Probe("root", new ArrayList<>());
        Probe branch = new Probe("branch", new ArrayList<>());
        Probe child = new Probe("child", new ArrayList<>());

        root.add(branch);
        branch.add(child);

        assertSame(root, branch.parent());
        assertSame(branch, child.parent());
        assertEquals(List.of(branch), root.children());
        assertEquals(List.of(child), branch.children());

        assertTrue(branch.remove(child));
        assertNull(child.parent());
        assertFalse(branch.remove(child));

        root.removeAll();
        assertNull(branch.parent());
        assertTrue(root.children().isEmpty());
    }

    @Test
    void childrenViewIsUnmodifiable() {
        Probe root = new Probe("root", new ArrayList<>());
        root.add(new Probe("child", new ArrayList<>()));

        assertThrows(UnsupportedOperationException.class, () -> root.children().clear());
    }

    @Test
    void rejectsDuplicateParentsAndCycles() {
        Probe root = new Probe("root", new ArrayList<>());
        Probe branch = new Probe("branch", new ArrayList<>());
        Probe child = new Probe("child", new ArrayList<>());

        root.add(branch);
        branch.add(child);

        assertThrows(IllegalArgumentException.class, () -> root.add(child));
        assertThrows(IllegalArgumentException.class, () -> root.add(root));
        assertThrows(IllegalArgumentException.class, () -> branch.add(root));
        assertThrows(NullPointerException.class, () -> root.add(null));
        assertThrows(NullPointerException.class, () -> root.remove(null));
    }

    @Test
    void hitTestingPrefersTheTopmostChild() {
        Probe root = new Probe("root", new ArrayList<>());
        Probe lower = new Probe("lower", new ArrayList<>());
        Probe upper = new Probe("upper", new ArrayList<>());
        root.add(lower);
        root.add(upper);
        root.setBounds(new Rect(0, 0, 200, 200));
        lower.setBounds(new Rect(10, 10, 100, 100));
        upper.setBounds(new Rect(50, 50, 100, 100));

        assertSame(upper, root.hitTest(60, 60));
        assertSame(lower, root.hitTest(20, 20));
        assertSame(root, root.hitTest(190, 190));
        assertNull(root.hitTest(250, 250));

        lower.setVisible(false);
        assertSame(root, root.hitTest(20, 20));

        lower.setVisible(true);
        lower.setEnabled(false);
        assertSame(root, root.hitTest(20, 20));
    }

    @Test
    void updateVisitsParentThenChildren() {
        List<String> log = new ArrayList<>();
        Probe root = new Probe("root", log);
        Probe child = new Probe("child", log);
        root.add(child);

        FrameInfo frame = new FrameInfo(0.5f, 1.0, 3);
        root.update(frame);

        assertEquals(List.of("update:root", "update:child"), log);
        assertSame(frame, root.lastFrame);
        assertSame(frame, child.lastFrame);
    }

    @Test
    void drawVisitsParentThenChildrenAndSkipsInvisibleSubtrees() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager renderer = new RenderManager(backend);
        renderer.resize(VIEWPORT);
        List<String> log = new ArrayList<>();
        Probe root = new Probe("root", log);
        Probe child = new Probe("child", log);
        root.setBounds(new Rect(0, 0, 200, 100));
        child.setBounds(new Rect(10, 10, 50, 50));
        root.add(child);
        FrameInfo frame = new FrameInfo(0.25f, 1.0, 2);

        renderer.begin();
        root.draw(renderer, frame);
        renderer.end();

        assertEquals(List.of("draw:root", "draw:child"), log);
        assertEquals(2, backend.lastDrawList.commands().size());
        assertSame(frame, root.lastFrame);
        assertSame(frame, child.lastFrame);

        log.clear();
        child.setVisible(false);
        renderer.begin();
        root.draw(renderer, frame);
        renderer.end();

        assertEquals(List.of("draw:root"), log);
        assertEquals(1, backend.lastDrawList.commands().size());

        log.clear();
        root.setVisible(false);
        renderer.begin();
        root.draw(renderer, frame);
        renderer.end();

        assertTrue(log.isEmpty());
        assertTrue(backend.lastDrawList.commands().isEmpty());
    }

    @Test
    void rejectsNullArguments() {
        Probe probe = new Probe("probe", new ArrayList<>());
        FrameInfo frame = FrameInfo.ZERO;

        assertThrows(NullPointerException.class, () -> probe.setBounds(null));
        assertThrows(NullPointerException.class, () -> probe.draw(null, frame));
        assertThrows(NullPointerException.class, () -> probe.draw(new RenderManager(new RecordingBackend()), null));
        assertThrows(NullPointerException.class, () -> probe.update(null));
    }

    private static final class Probe extends Widget {
        private final String name;
        private final List<String> log;
        private FrameInfo lastFrame;

        Probe(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        /**
         * Records the update call and frame timing.
         *
         * @param frame timing information for the current frame
         */
        @Override
        protected void onUpdate(FrameInfo frame) {
            lastFrame = frame;
            log.add("update:" + name);
        }

        /**
         * Records the draw call and paints a white rectangle.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            lastFrame = frame;
            log.add("draw:" + name);
            renderer.rect(bounds(), Color.WHITE);
        }
    }

    private static final class RecordingBackend implements RenderBackend {
        private DrawList lastDrawList;

        @Override
        public void resize(Viewport viewport) {
        }

        @Override
        public void draw(DrawList drawList) {
            lastDrawList = drawList;
        }

        @Override
        public Texture createTexture(ImageData image) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void destroyTexture(Texture texture) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }
}
