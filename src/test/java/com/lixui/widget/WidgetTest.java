package com.lixui.widget;

import com.lixui.widget.testsupport.TestBackend;
import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.render.RenderManager;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetStyle;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        probe.setBounds(new Rect(0, 0, 50, 50));

        assertTrue(probe.contains(20, 20));
        assertFalse(probe.contains(60, 20));

        probe.setVisible(false);
        assertFalse(probe.contains(20, 20));

        probe.setVisible(true);
        probe.setEnabled(false);
        assertFalse(probe.contains(20, 20));
    }

    @Test
    void boundsAreRelativeToParentAndLocalBoundsStartAtTheOrigin() {
        Probe probe = new Probe("probe", new ArrayList<>());
        probe.setBounds(new Rect(30, 40, 120, 80));

        assertEquals(new Rect(30, 40, 120, 80), probe.bounds());
        assertEquals(new Rect(0, 0, 120, 80), probe.localBounds());
        assertEquals(new Point(30, 40), probe.position());
        assertEquals(120, probe.size().width());
        assertEquals(80, probe.size().height());

        probe.setPosition(10, 20);
        probe.setSize(200, 100);

        assertEquals(new Rect(10, 20, 200, 100), probe.bounds());
    }

    @Test
    void exposesSizeLimitsForResizableWidgets() {
        Probe probe = new Probe("probe", new ArrayList<>());

        assertEquals(0, probe.minimumWidth());
        assertEquals(Float.POSITIVE_INFINITY, probe.maximumWidth());

        probe.setMinimumSize(20, 30);
        probe.setMaximumSize(200, 300);

        assertEquals(20, probe.minimumWidth());
        assertEquals(30, probe.minimumHeight());
        assertEquals(200, probe.maximumWidth());
        assertEquals(300, probe.maximumHeight());
        assertThrows(IllegalArgumentException.class, () -> probe.setMinimumSize(201, 30));
        assertThrows(IllegalArgumentException.class, () -> probe.setMaximumSize(19, 300));
        assertThrows(IllegalArgumentException.class, () -> probe.setMinimumSize(-1, 30));
    }

    @Test
    void defaultLayoutKeepsChildrenFreelyPositioned() {
        Probe root = new Probe("root", new ArrayList<>());
        Probe child = new Probe("child", new ArrayList<>());
        root.setBounds(new Rect(100, 50, 300, 200));
        child.setBounds(new Rect(10, 20, 50, 40));
        root.add(child);

        root.layout(new Rect(100, 50, 300, 200));

        assertEquals(new Rect(10, 20, 50, 40), child.bounds());
        assertSame(child, root.hitTest(15, 25));
        assertSame(root, root.hitTest(5, 5));
        assertNull(root.hitTest(300, 250));

        root.setPosition(200, 100);

        assertEquals(new Rect(10, 20, 50, 40), child.bounds());
    }

    @Test
    void measureAndLayoutUseTheSubclassContract() {
        LayoutProbe probe = new LayoutProbe();

        Size loose = probe.measure(LayoutConstraints.loose(200, 150));
        assertEquals(80, loose.width());
        assertEquals(40, loose.height());

        Size tight = probe.measure(LayoutConstraints.tight(40, 30));
        assertEquals(40, tight.width());
        assertEquals(30, tight.height());

        probe.layout(new Rect(10, 20, 80, 40));

        assertEquals(new Rect(10, 20, 80, 40), probe.bounds());
        assertEquals(1, probe.layoutCount);
    }

    @Test
    void defaultLayoutReachesNestedContainersWithoutChangingTheirBounds() {
        Widget root = new Widget() { };
        Widget branch = new Widget() { };
        LayoutProbe child = new LayoutProbe();
        branch.setBounds(new Rect(10, 20, 100, 80));
        child.setBounds(new Rect(5, 6, 40, 30));
        root.add(branch);
        branch.add(child);

        root.layout(new Rect(0, 0, 200, 200));

        assertEquals(1, child.layoutCount);
        assertEquals(new Rect(10, 20, 100, 80), branch.bounds());
        assertEquals(new Rect(5, 6, 40, 30), child.bounds());
    }

    @Test
    void fontsInheritFromAncestorsAndCanBeCleared() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        Font font = Font.load(data.get(), 12);
        Probe root = new Probe("root", new ArrayList<>());
        Probe child = new Probe("child", new ArrayList<>());
        root.add(child);

        root.setFont(font);

        assertSame(font, root.font());
        assertSame(font, child.font());

        root.clearFont();

        assertNull(root.font());
        assertNull(child.font());
    }

    @Test
    void stylesInheritFromAncestorsAndCanBeCleared() {
        WidgetStyle style = Theme.light().controlStyle();
        Probe root = new Probe("root", new ArrayList<>());
        Probe child = new Probe("child", new ArrayList<>());
        root.add(child);

        root.setStyle(style);

        assertSame(style, root.style());
        assertSame(style, child.style());

        root.clearStyle();

        assertNull(root.style());
        assertNull(child.style());
    }

    @Test
    void drawingTranslatesParentAndChildIntoTheirLocalOrigins() {
        TestBackend backend = new TestBackend();
        RenderManager renderer = new RenderManager(backend);
        renderer.resize(VIEWPORT);
        Probe root = new Probe("root", new ArrayList<>());
        Probe child = new Probe("child", new ArrayList<>());
        root.setBounds(new Rect(100, 50, 300, 200));
        child.setBounds(new Rect(10, 20, 50, 40));
        root.add(child);

        renderer.begin();
        root.draw(renderer, FrameInfo.ZERO);
        renderer.end();

        assertEquals(Transform.translation(100, 50), root.lastDrawTransform);
        assertEquals(Transform.translation(110, 70), child.lastDrawTransform);
    }

    @Test
    void clipToBoundsClipsTheWidgetAndItsChildren() {
        TestBackend backend = new TestBackend();
        RenderManager renderer = new RenderManager(backend);
        renderer.resize(VIEWPORT);
        Probe root = new Probe("root", new ArrayList<>());
        Probe child = new Probe("child", new ArrayList<>());
        root.setBounds(new Rect(0, 0, 50, 50));
        child.setBounds(new Rect(40, 40, 40, 40));
        root.setClipToBounds(true);
        child.setClipToBounds(true);
        root.add(child);

        renderer.begin();
        root.draw(renderer, FrameInfo.ZERO);
        renderer.end();

        assertEquals(2, backend.lastDrawList().commands().size());
        assertEquals(new Rect(0, 0, 50, 50), backend.lastDrawList().commands().get(0).clip());
        assertEquals(new Rect(40, 40, 10, 10), backend.lastDrawList().commands().get(1).clip());

        child.setBounds(new Rect(60, 0, 40, 40));
        renderer.begin();
        root.draw(renderer, FrameInfo.ZERO);
        renderer.end();

        assertEquals(1, backend.lastDrawList().commands().size());
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
        TestBackend backend = new TestBackend();
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
        assertEquals(2, backend.lastDrawList().commands().size());
        assertSame(frame, root.lastFrame);
        assertSame(frame, child.lastFrame);

        log.clear();
        child.setVisible(false);
        renderer.begin();
        root.draw(renderer, frame);
        renderer.end();

        assertEquals(List.of("draw:root"), log);
        assertEquals(1, backend.lastDrawList().commands().size());

        log.clear();
        root.setVisible(false);
        renderer.begin();
        root.draw(renderer, frame);
        renderer.end();

        assertTrue(log.isEmpty());
        assertTrue(backend.lastDrawList().commands().isEmpty());
    }

    @Test
    void cullsOffscreenDrawingButStillUpdatesAndFindsOverflowingChildren() {
        TestBackend backend = new TestBackend();
        RenderManager renderer = new RenderManager(backend);
        renderer.resize(VIEWPORT);
        List<String> log = new ArrayList<>();
        Probe root = new Probe("root", log);
        Probe visibleChild = new Probe("visible", log);
        Probe hiddenChild = new Probe("hidden", log);
        root.setBounds(new Rect(900, 0, 100, 100));
        visibleChild.setBounds(new Rect(-200, 0, 50, 50));
        hiddenChild.setBounds(new Rect(0, 0, 50, 50));
        root.add(visibleChild).add(hiddenChild);

        root.update(FrameInfo.ZERO);
        assertEquals(List.of("update:root", "update:visible", "update:hidden"), log);
        log.clear();
        renderer.begin();
        root.draw(renderer, FrameInfo.ZERO);
        renderer.end();

        assertEquals(List.of("draw:visible"), log);
        assertEquals(1, backend.lastDrawList().commands().size());

        root.setClipToBounds(true);
        log.clear();
        renderer.begin();
        root.draw(renderer, FrameInfo.ZERO);
        renderer.end();
        assertTrue(log.isEmpty());
    }

    @Test
    void cullsUnclippedChildrenOutsideTheParentClip() {
        RenderManager renderer = new RenderManager(new TestBackend());
        renderer.resize(VIEWPORT);
        List<String> log = new ArrayList<>();
        Probe root = new Probe("root", log);
        Probe child = new Probe("child", log);
        root.setBounds(new Rect(10, 10, 100, 100));
        root.setClipToBounds(true);
        child.setBounds(new Rect(0, 200, 50, 50));
        root.add(child);

        renderer.begin();
        root.draw(renderer, FrameInfo.ZERO);
        renderer.end();
        assertEquals(List.of("draw:root"), log);

        child.setPosition(0, 90);
        log.clear();
        renderer.begin();
        root.draw(renderer, FrameInfo.ZERO);
        renderer.end();
        assertEquals(List.of("draw:root", "draw:child"), log);
    }

    @Test
    void rejectsNullArguments() {
        Probe probe = new Probe("probe", new ArrayList<>());
        FrameInfo frame = FrameInfo.ZERO;

        assertThrows(NullPointerException.class, () -> probe.setBounds(null));
        assertThrows(NullPointerException.class, () -> probe.draw(null, frame));
        assertThrows(NullPointerException.class, () -> probe.draw(new RenderManager(new TestBackend()), null));
        assertThrows(NullPointerException.class, () -> probe.update(null));
    }

    private static final class Probe extends Widget {
        private final String name;
        private final List<String> log;
        private FrameInfo lastFrame;
        private Transform lastDrawTransform;
        private int layoutCount;

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
         * Records the draw call, the active transform and paints a white rectangle.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            lastFrame = frame;
            lastDrawTransform = renderer.currentTransform();
            log.add("draw:" + name);
            renderer.rect(localBounds(), Color.WHITE);
        }

        /**
         * Counts layout passes performed by the manager.
         */
        @Override
        protected void onLayout() {
            layoutCount++;
        }
    }

    private static final class LayoutProbe extends Widget {
        private int layoutCount;

        /**
         * Returns a fixed natural size for layout contract checks.
         *
         * @param constraints available and required dimensions
         * @return the fixed natural size
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            return new Size(80, 40);
        }

        /**
         * Counts layout calls.
         */
        @Override
        protected void onLayout() {
            layoutCount++;
        }
    }

}
