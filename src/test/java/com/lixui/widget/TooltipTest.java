package com.lixui.widget;

import com.lixui.widget.testsupport.TestViewports;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.Viewport;
import com.lixui.widget.testsupport.FixedWidget;
import com.lixui.widget.tooltip.TooltipSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TooltipTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void reportsTheTooltipDelayAndFadeThroughDrawCommands() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            Panel owner = new Panel();
            owner.setBounds(new Rect(10, 10, 100, 50));
            Panel tooltip = new Panel();
            tooltip.setBounds(new Rect(0, 0, 80, 30));
            owner.setTooltip(tooltip);
            manager.add(owner);

            manager.handle(new PointerMoved(20, 20));
            manager.update(0.5f);
            assertNotNull(tooltip.context());
            manager.render();
            assertEquals(2, backend.lastDrawList().commands().size());

            manager.update(0.06f);
            manager.render();
            List<DrawCommand> fadingIn = backend.lastDrawList().commands();
            assertEquals(4, fadingIn.size());
            assertEquals(0.5f, assertInstanceOf(RoundedRectCommand.class,
                    fadingIn.get(2)).color().a(), 0.02f);

            manager.update(0.06f);
            manager.render();
            List<DrawCommand> shown = backend.lastDrawList().commands();
            assertEquals(1, assertInstanceOf(RoundedRectCommand.class,
                    shown.get(2)).color().a(), 0.02f);
            assertEquals(new Rect(32, 38, 80, 30), tooltip.bounds());

            manager.handle(new PointerMoved(600, 400));
            assertNotNull(tooltip.context());
            manager.update(0.06f);
            manager.render();
            assertEquals(0.5f, assertInstanceOf(RoundedRectCommand.class,
                    backend.lastDrawList().commands().get(2)).color().a(), 0.02f);

            manager.update(0.07f);
            assertNull(tooltip.context());
            manager.render();
            assertEquals(2, backend.lastDrawList().commands().size());
        }
    }

    @Test
    void settingsCustomizeDelayOffsetFadeAndGlobalToggle() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            manager.setTooltipSettings(TooltipSettings.defaults()
                    .withDelay(0.1f).withOffset(5, 7).withFade(0, 0));
            Panel owner = new Panel();
            owner.setBounds(new Rect(0, 0, 300, 200));
            Panel tooltip = new Panel();
            tooltip.setBounds(new Rect(0, 0, 80, 30));
            owner.setTooltip(tooltip);
            manager.add(owner);
            manager.handle(new PointerMoved(20, 20));

            manager.update(0.11f);
            assertNotNull(tooltip.context());
            manager.update(0.01f);
            manager.render();
            assertEquals(new Rect(25, 27, 80, 30), tooltip.bounds());

            manager.setTooltipsEnabled(false);
            assertNull(tooltip.context());
            manager.update(0.5f);
            manager.render();
            assertNull(tooltip.context());
            assertFalse(manager.areTooltipsEnabled());
            assertEquals(2, backend.lastDrawList().commands().size());

            manager.setTooltipsEnabled(true);
            manager.update(0.11f);
            manager.update(0.01f);
            assertNotNull(tooltip.context());
        }
    }

    @Test
    void perWidgetSettingsOverrideTheManagerDefaults() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            Panel outer = new Panel();
            outer.setBounds(new Rect(0, 0, 300, 200));
            Panel tooltip = new Panel();
            tooltip.setBounds(new Rect(0, 0, 60, 20));
            outer.setTooltip(tooltip);
            outer.setTooltipSettings(TooltipSettings.defaults()
                    .withDelay(0.2f).withOffset(3, 4).withFade(0, 0));
            FixedWidget child = new FixedWidget(40, 20);
            child.setBounds(new Rect(10, 10, 40, 20));
            outer.add(child);
            manager.add(outer);

            manager.handle(new PointerMoved(20, 20));
            manager.update(0.15f);
            assertNull(tooltip.context());
            manager.update(0.06f);
            manager.update(0.01f);
            manager.render();

            assertNotNull(tooltip.context());
            assertEquals(new Rect(23, 24, 60, 20), tooltip.bounds());
        }
    }

    @Test
    void disabledSubtreeSuppressesInheritedTooltips() {
        try (WidgetManager manager = new WidgetManager(new InputState(), new TestBackend())) {
            manager.resize(VIEWPORT);
            Panel outer = new Panel();
            outer.setBounds(new Rect(0, 0, 300, 200));
            Panel tooltip = new Panel();
            tooltip.setBounds(new Rect(0, 0, 60, 20));
            outer.setTooltip(tooltip);
            FixedWidget child = new FixedWidget(40, 20);
            child.setBounds(new Rect(10, 10, 40, 20));
            child.setTooltipEnabled(false);
            outer.add(child);
            manager.add(outer);

            manager.handle(new PointerMoved(20, 20));
            manager.update(1);
            assertNull(tooltip.context());

            child.setTooltipEnabled(true);
            manager.update(1);
            assertNotNull(tooltip.context());

            outer.setTooltipEnabled(false);
            manager.handle(new PointerMoved(120, 120));
            manager.handle(new PointerMoved(20, 20));
            manager.update(1);
            assertNull(tooltip.context());
        }
    }

    @Test
    void tooltipDoesNotBlockPointerInput() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            manager.setTooltipSettings(TooltipSettings.defaults().withFade(0, 0));
            Panel owner = new Panel();
            owner.setBounds(new Rect(10, 10, 100, 50));
            Panel tooltip = new Panel();
            tooltip.setBounds(new Rect(0, 0, 200, 100));
            owner.setTooltip(tooltip);
            manager.add(owner);

            manager.handle(new PointerMoved(20, 20));
            manager.update(0.6f);
            manager.update(0.01f);
            manager.render();

            assertNotNull(tooltip.context());
            assertEquals(new Rect(32, 38, 200, 100), tooltip.bounds());
            assertFalse(manager.blocksPointer(150, 80));
        }
    }

    @Test
    void pressingHidesTheTooltipAndCloseDetachesIt() {
        try (WidgetManager manager = new WidgetManager(new InputState(), new TestBackend())) {
            manager.resize(VIEWPORT);
            Panel owner = new Panel();
            owner.setBounds(new Rect(0, 0, 300, 200));
            Panel tooltip = new Panel();
            tooltip.setBounds(new Rect(0, 0, 60, 20));
            owner.setTooltip(tooltip);
            manager.add(owner);
            manager.handle(new PointerMoved(20, 20));
            manager.update(0.6f);
            assertNotNull(tooltip.context());

            manager.handle(new PointerPressed(20, 20, MouseButton.LEFT, Modifiers.NONE));
            assertNull(tooltip.context());

            manager.update(0.6f);
            assertNotNull(tooltip.context());
            manager.close();
            assertNull(tooltip.context());
        }
    }

    @Test
    void ownerCanReclaimItsTooltipWhileItFadesOut() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            manager.setTooltipSettings(TooltipSettings.defaults()
                    .withDelay(0).withFade(0.01f, 0.2f));
            Panel owner = new Panel();
            owner.setBounds(new Rect(0, 0, 300, 200));
            Panel tooltip = new Panel();
            tooltip.setBounds(new Rect(0, 0, 60, 20));
            owner.setTooltip(tooltip);
            manager.add(owner);

            manager.handle(new PointerMoved(20, 20));
            manager.update(0.01f);
            manager.update(0.03f);
            assertNotNull(tooltip.context());

            owner.setTooltip(null);
            manager.update(0.05f);
            assertNotNull(tooltip.context());

            owner.setTooltip(tooltip);
            manager.update(0.05f);
            manager.render();
            assertSame(tooltip, owner.tooltip());
            assertNotNull(tooltip.context());
        }
    }

    @Test
    void onlyTheOriginalOwnerCanReclaimTheFadingTooltip() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            manager.setTooltipSettings(TooltipSettings.defaults()
                    .withDelay(0).withFade(0.01f, 0.2f));
            Panel owner = new Panel();
            owner.setBounds(new Rect(0, 0, 100, 100));
            Panel otherOwner = new Panel();
            otherOwner.setBounds(new Rect(200, 0, 100, 100));
            Panel tooltip = new Panel();
            tooltip.setBounds(new Rect(0, 0, 60, 20));
            Panel otherTooltip = new Panel();
            otherTooltip.setBounds(new Rect(0, 0, 60, 20));
            owner.setTooltip(tooltip);
            otherOwner.setTooltip(otherTooltip);
            manager.add(owner);
            manager.add(otherOwner);

            manager.handle(new PointerMoved(20, 20));
            manager.update(0.01f);
            manager.update(0.03f);
            assertNotNull(tooltip.context());

            owner.setTooltip(null);
            manager.update(0.05f);
            assertNotNull(tooltip.context());

            manager.handle(new PointerMoved(220, 20));
            manager.update(0.03f);
            assertNotNull(tooltip.context());

            assertThrows(IllegalArgumentException.class,
                    () -> otherOwner.setTooltip(tooltip));
            assertSame(otherTooltip, otherOwner.tooltip());

            owner.setTooltip(tooltip);
            manager.handle(new PointerMoved(20, 20));
            manager.update(0.03f);
            manager.render();
            assertSame(tooltip, owner.tooltip());
            assertNotNull(tooltip.context());
        }
    }

    @Test
    void attachedTopLevelWidgetCannotBecomeATooltip() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            Panel owner = new Panel();
            owner.setBounds(new Rect(10, 10, 100, 50));
            Panel registered = new Panel();
            registered.setBounds(new Rect(300, 300, 80, 30));
            manager.add(owner);
            manager.add(registered);
            manager.handle(new PointerMoved(20, 20));

            assertThrows(IllegalArgumentException.class, () -> owner.setTooltip(registered));
            manager.update(1);
            manager.render();
            manager.handle(new PointerMoved(600, 400));
            manager.update(1);

            assertNull(owner.tooltip());
            assertSame(manager.context(), registered.context());
            assertTrue(manager.widgets().contains(registered));
        }
    }

    @Test
    void rejectsInvalidTooltipAssignmentsAndSettings() {
        Panel owner = new Panel();
        assertThrows(IllegalArgumentException.class, () -> owner.setTooltip(owner));

        Panel parent = new Panel();
        Panel child = new Panel();
        parent.add(child);
        assertThrows(IllegalArgumentException.class, () -> owner.setTooltip(child));

        owner.setTooltip(null);
        assertNull(owner.tooltip());

        assertThrows(IllegalArgumentException.class,
                () -> new TooltipSettings(-1, 0.1f, 0.1f, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> TooltipSettings.defaults().withFade(0.1f, Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> TooltipSettings.defaults().withOffset(Float.POSITIVE_INFINITY, 0));
    }


}
