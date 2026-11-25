package com.lixui.widget;

import com.lixui.widget.testsupport.TestBackend;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.render.ImageData;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetLifecycleTest {
    @Test
    void rootUpdateCanRemoveItselfAndDefersNewRoots() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        List<String> updates = new ArrayList<>();
        Widget added = updatingWidget("added", updates);
        Widget first = new Widget() {
            /**
             * Replaces this root during traversal.
             *
             * @param frame current frame timing
             */
            @Override
            protected void onUpdate(FrameInfo frame) {
                updates.add("first");
                manager.remove(this);
                manager.add(added);
            }
        };
        manager.add(first).add(updatingWidget("second", updates));

        manager.update(0);
        assertEquals(List.of("first", "second"), updates);
        updates.clear();
        manager.update(0);
        assertEquals(List.of("second", "added"), updates);
    }

    @Test
    void childUpdateCanRemoveASiblingAndDefersNewChildren() {
        Widget root = new Widget() { };
        List<String> updates = new ArrayList<>();
        Widget removed = updatingWidget("removed", updates);
        Widget added = updatingWidget("added", updates);
        Widget first = new Widget() {
            /**
             * Replaces a sibling while the parent traverses its children.
             *
             * @param frame current frame timing
             */
            @Override
            protected void onUpdate(FrameInfo frame) {
                root.remove(this);
                root.remove(removed);
                root.add(added);
            }
        };
        root.add(first).add(removed).add(updatingWidget("last", updates));

        root.update(FrameInfo.ZERO);
        assertEquals(List.of("last"), updates);
        updates.clear();
        root.update(FrameInfo.ZERO);
        assertEquals(List.of("last", "added"), updates);
    }

    @Test
    void drawingCanRemoveASiblingWithoutDrawingTheDetachedWidget() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(new Viewport(100, 100, 100, 100));
        Widget root = new Widget() { };
        Widget removed = new Widget() {
            /**
             * Fails if a detached sibling is still drawn.
             *
             * @param renderer drawing manager
             * @param frame current frame timing
             * @throws AssertionError always
             */
            @Override
            protected void onDraw(RenderManager renderer, FrameInfo frame) {
                throw new AssertionError("Detached widget was drawn.");
            }
        };
        Widget first = new Widget() {
            /**
             * Removes the next child during drawing.
             *
             * @param renderer drawing manager
             * @param frame current frame timing
             */
            @Override
            protected void onDraw(RenderManager renderer, FrameInfo frame) {
                root.remove(removed);
            }
        };
        root.add(first).add(removed);
        manager.add(root);

        manager.render();

        assertEquals(1, backend.drawCalls());
        assertNull(removed.context());
    }

    @Test
    void failedDrawingDiscardsCommandsAndAllowsTheNextFrame() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(new Viewport(100, 100, 100, 100));
        boolean[] fail = {true};
        Widget widget = new Widget() {
            /**
             * Simulates an application drawing failure.
             *
             * @param renderer drawing manager
             * @param frame current frame timing
             * @throws IllegalStateException while the failure flag is set
             */
            @Override
            protected void onDraw(RenderManager renderer, FrameInfo frame) {
                renderer.rect(localBounds(), Color.WHITE);
                if (fail[0]) {
                    throw new IllegalStateException("Drawing failed.");
                }
            }
        };
        widget.setBounds(new Rect(10, 10, 20, 20));
        widget.setClipToBounds(true);
        manager.add(widget);

        assertThrows(IllegalStateException.class, manager::render);
        assertEquals(0, backend.drawCalls());
        fail[0] = false;
        manager.render();
        assertEquals(1, backend.drawCalls());
        assertEquals(1, backend.lastCommandCount());
    }

    @Test
    void closeDetachesEverySubtreeAndReleasesResourcesAfterCallbackFailures() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        RuntimeException failure = new IllegalStateException("Detach failed.");
        List<String> detached = new ArrayList<>();
        Widget root = detachingWidget("root", detached, failure);
        Widget first = detachingWidget("first", detached, failure);
        Widget second = detachingWidget("second", detached, null);
        root.add(first).add(second);
        Widget other = detachingWidget("other", detached, null);
        manager.add(root).add(other);
        Texture texture = manager.context().createTexture(new ImageData(1, 1, new byte[4]));

        assertSame(failure, assertThrows(IllegalStateException.class, manager::close));

        assertEquals(List.of("first", "second", "root", "other"), detached);
        assertNull(root.context());
        assertNull(first.context());
        assertNull(second.context());
        assertNull(other.context());
        assertTrue(manager.widgets().isEmpty());
        assertEquals(List.of(texture), backend.destroyed());
        manager.close();
        assertEquals(1, backend.destroyed().size());
    }

    @Test
    void attachmentFailureRemainsPrimaryWhenRollbackAlsoFails() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        RuntimeException attachFailure = new IllegalArgumentException("Attach failed.");
        RuntimeException detachFailure = new IllegalStateException("Detach failed.");
        Widget widget = new Widget() {
            /**
             * Simulates an initialization failure.
             *
             * @throws RuntimeException always
             */
            @Override
            protected void onAttached() {
                throw attachFailure;
            }

            /**
             * Simulates a rollback failure.
             *
             * @throws RuntimeException always
             */
            @Override
            protected void onDetached() {
                throw detachFailure;
            }
        };

        assertSame(attachFailure, assertThrows(IllegalArgumentException.class, () -> manager.add(widget)));
        assertEquals(List.of(detachFailure), List.of(attachFailure.getSuppressed()));
        assertNull(widget.context());
        assertTrue(manager.widgets().isEmpty());
    }

    @Test
    void removeKeepsDetachFailureAndSuppressesChildRemovalFailure() {
        RuntimeException detachFailure = new IllegalStateException("Detach failed.");
        RuntimeException removalFailure = new IllegalArgumentException("Removal callback failed.");
        Widget root = new Widget() {
            /**
             * Simulates a failing child removal callback.
             *
             * @param child removed child
             * @throws RuntimeException always
             */
            @Override
            protected void onChildRemoved(Widget child) {
                throw removalFailure;
            }
        };
        Widget child = new Widget() {
            /**
             * Simulates a failing cleanup callback.
             *
             * @throws RuntimeException always
             */
            @Override
            protected void onDetached() {
                throw detachFailure;
            }
        };

        try (WidgetManager manager = new WidgetManager(new InputState(), new TestBackend())) {
            root.add(child);
            manager.add(root);

            assertSame(detachFailure, assertThrows(IllegalStateException.class,
                    () -> root.remove(child)));
            assertEquals(1, detachFailure.getSuppressed().length);
            assertSame(removalFailure, detachFailure.getSuppressed()[0]);
            assertTrue(root.children().isEmpty());
            assertNull(child.parent());
            assertNull(child.context());
        }
    }

    @Test
    void removeAllDetachesEveryChildAfterAnError() {
        Error detachFailure = new Error("Detach failed.");
        List<String> detached = new ArrayList<>();
        Widget root = new Widget() { };
        Widget first = errorDetachingWidget("first", detached, detachFailure);
        Widget second = errorDetachingWidget("second", detached, null);
        Widget third = errorDetachingWidget("third", detached, null);

        try (WidgetManager manager = new WidgetManager(new InputState(), new TestBackend())) {
            root.add(first).add(second).add(third);
            manager.add(root);

            assertSame(detachFailure, assertThrows(Error.class, root::removeAll));

            assertEquals(List.of("first", "second", "third"), detached);
            assertTrue(root.children().isEmpty());
            assertNull(first.parent());
            assertNull(second.parent());
            assertNull(third.parent());
            assertNull(first.context());
            assertNull(second.context());
            assertNull(third.context());
        }
    }

    @Test
    void closeKeepsTheFirstErrorAndSuppressesCleanupErrors() {
        Error firstFailure = new Error("First detach failed.");
        Error secondFailure = new Error("Second detach failed.");
        List<String> detached = new ArrayList<>();
        Widget root = new Widget() { };
        Widget first = errorDetachingWidget("first", detached, firstFailure);
        Widget second = errorDetachingWidget("second", detached, secondFailure);
        root.add(first).add(second);
        Widget other = errorDetachingWidget("other", detached, null);
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.add(root).add(other);

        assertSame(firstFailure, assertThrows(Error.class, manager::close));

        assertEquals(1, firstFailure.getSuppressed().length);
        assertSame(secondFailure, firstFailure.getSuppressed()[0]);
        assertEquals(List.of("first", "second", "other"), detached);
        assertTrue(manager.widgets().isEmpty());
        assertNull(root.context());
        assertNull(first.context());
        assertNull(second.context());
        assertNull(other.context());
    }

    private static Widget updatingWidget(String name, List<String> updates) {
        return new Widget() {
            /**
             * Records this widget's update.
             *
             * @param frame current frame timing
             */
            @Override
            protected void onUpdate(FrameInfo frame) {
                updates.add(name);
            }
        };
    }

    private static Widget detachingWidget(String name, List<String> detached, RuntimeException failure) {
        return new Widget() {
            /**
             * Records detachment and optionally fails.
             *
             * @throws RuntimeException when a failure was supplied
             */
            @Override
            protected void onDetached() {
                detached.add(name);
                if (failure != null) {
                    throw failure;
                }
            }
        };
    }

    private static Widget errorDetachingWidget(String name, List<String> detached, Error failure) {
        return new Widget() {
            /**
             * Records detachment and optionally fails with an error.
             *
             * @throws Error when a failure was supplied
             */
            @Override
            protected void onDetached() {
                detached.add(name);
                if (failure != null) {
                    throw failure;
                }
            }
        };
    }


}
