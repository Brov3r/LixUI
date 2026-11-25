package com.lixui.widget.overlay;

import com.lixui.animation.Easing;
import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.platform.Cursor;
import com.lixui.render.Viewport;
import com.lixui.render.command.TextCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestTexture;
import com.lixui.widget.testsupport.TestViewports;
import com.lixui.widget.tooltip.TextTooltip;
import com.lixui.widget.tooltip.TooltipSettings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadialMenuTest {
    @Test
    void hitTestsSectorsByAngleWithSectorGaps() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.render();

            manager.handle(new PointerMoved(400, 200));
            assertEquals(0, menu.hoveredIndex());
            manager.handle(new PointerMoved(500, 300));
            assertEquals(1, menu.hoveredIndex());
            manager.handle(new PointerMoved(400, 400));
            assertEquals(2, menu.hoveredIndex());
            manager.handle(new PointerMoved(300, 300));
            assertEquals(3, menu.hoveredIndex());
            manager.handle(new PointerMoved(470.7f, 229.3f));
            assertEquals(-1, menu.hoveredIndex());

            manager.handle(new PointerMoved(400, 300));
            assertEquals(-1, menu.hoveredIndex());
            assertEquals(Cursor.POINTER, menu.cursor());

            manager.handle(new PointerMoved(10, 10));
            assertEquals(-1, menu.hoveredIndex());
        }
    }

    @Test
    void clampsTheMenuInsideTheViewport() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.open(manager.context(), 2, 2);
            manager.layout();
            Rect bounds = menu.menuBounds();
            assertTrue(bounds.x() >= 0);
            assertTrue(bounds.y() >= 0);
            assertTrue(bounds.right() <= 800);
            assertTrue(bounds.bottom() <= 600);

            menu.open(manager.context(), 798, 598);
            manager.layout();
            bounds = menu.menuBounds();
            assertTrue(bounds.x() >= 0);
            assertTrue(bounds.y() >= 0);
            assertTrue(bounds.right() <= 800);
            assertTrue(bounds.bottom() <= 600);
        }
    }

    @Test
    void keepsTheMenuInsideAResizedViewport() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.open(manager.context(), 700, 500);
            manager.layout();
            manager.resize(new Viewport(400, 300, 400, 300));
            manager.layout();
            Rect bounds = menu.menuBounds();
            assertTrue(bounds.x() >= 0);
            assertTrue(bounds.y() >= 0);
            assertTrue(bounds.right() <= 400);
            assertTrue(bounds.bottom() <= 300);
        }
    }

    @Test
    void clickRunsTheSectorActionAndClosesTheMenu() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            List<String> actions = new ArrayList<>();
            RadialMenu.Item second = menu.items().get(1);
            second.setAction(() -> actions.add("second"));
            second.setEnabled(false);
            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.render();

            click(manager, 500, 300);
            assertTrue(actions.isEmpty());
            assertTrue(menu.isOpen());

            manager.update(0.5f);
            second.setEnabled(true);
            click(manager, 500, 300);
            assertEquals(List.of("second"), actions);
            assertFalse(menu.isOpen());
            assertFalse(manager.widgets().contains(menu));
        }
    }

    @Test
    void actionCanOpenAnotherFocusableOverlayWithoutLosingFocus() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Widget owner = focusable(new Rect(10, 10, 100, 30));
            manager.context().addTopLevel(owner);
            manager.focus(owner);

            RadialMenu menu = menu(4);
            menu.setAnimationDuration(0.3f);
            Widget[] dialog = new Widget[1];
            menu.items().getFirst().setAction(() -> {
                dialog[0] = focusable(new Rect(140, 10, 160, 40));
                manager.context().addTopLevel(dialog[0]);
                manager.focus(dialog[0]);
            });
            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.update(1);

            click(manager, 400, 200);
            assertTrue(menu.isClosing());
            assertSame(dialog[0], manager.focused());

            manager.update(1);
            assertFalse(menu.isOpen());
            assertSame(dialog[0], manager.focused());
        }
    }

    @Test
    void callbackFailureStillClosesTheMenu() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.setAnimationDuration(0.3f);
            menu.items().getFirst().setAction(() -> {
                throw new IllegalStateException("Action failed.");
            });
            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.update(1);

            assertThrows(IllegalStateException.class, () -> click(manager, 400, 200));
            assertTrue(menu.isClosing());

            manager.update(1);
            assertFalse(menu.isOpen());
            assertFalse(manager.widgets().contains(menu));
        }
    }

    @Test
    void closesOnEscapeAndOutsideClick() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.open(manager.context(), 400, 300);
            manager.layout();

            manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
            assertFalse(menu.isOpen());

            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
            assertFalse(menu.isOpen());

            menu.setCloseOnOutsideClick(false);
            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
            assertTrue(menu.isOpen());
            manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
            assertFalse(menu.isOpen());
        }
    }

    @Test
    void animatesOpenAndClose() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.setAnimationDuration(0.3f);
            int[] closes = {0};
            menu.setCloseListener(() -> closes[0]++);
            menu.open(manager.context(), 400, 300);
            manager.layout();
            assertEquals(0, menu.openAmount(), 0.001f);

            manager.update(0.05f);
            float partial = menu.openAmount();
            assertTrue(partial > 0 && partial < 1);

            manager.update(1);
            assertEquals(1, menu.openAmount(), 0.001f);

            menu.close();
            assertTrue(menu.isOpen());
            assertTrue(menu.isClosing());
            manager.update(0.05f);
            assertTrue(menu.isOpen());
            assertTrue(menu.openAmount() > 0 && menu.openAmount() < 1);

            manager.update(1);
            assertFalse(menu.isOpen());
            assertFalse(menu.isClosing());
            assertEquals(1, closes[0]);
            assertFalse(manager.widgets().contains(menu));
        }
    }

    @Test
    void restoresPreviousFocusAndClosesWhenFocusLeaves() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Widget owner = focusable(new Rect(10, 10, 100, 30));
            manager.context().addTopLevel(owner);
            manager.focus(owner);

            RadialMenu menu = menu(4);
            menu.open(manager.context(), 400, 300);
            manager.layout();
            assertSame(menu, manager.focused());

            menu.close();
            assertFalse(menu.isOpen());
            assertSame(owner, manager.focused());

            menu.open(manager.context(), 400, 300);
            manager.layout();
            Widget other = focusable(new Rect(130, 10, 100, 30));
            manager.context().addTopLevel(other);
            manager.focus(other);
            assertFalse(menu.isOpen());
            assertSame(other, manager.focused());
        }
    }

    @Test
    void longLabelsStayInsideNarrowSectorsWithoutIcons() {
        assertLongLabelsStayInsideSectors(false);
    }

    @Test
    void longLabelsStayInsideNarrowSectorsWithIcons() {
        assertLongLabelsStayInsideSectors(true);
    }

    @Test
    void longLabelsDoNotGrowTheViewportRadius() {
        FontData data = fontData();
        TestBackend backend = new TestBackend();
        Viewport viewport = new Viewport(300, 300, 300, 300);
        try (WidgetManager manager = manager(backend, viewport)) {
            manager.setDefaultFont(manager.context().createFont(data, 14));
            RadialMenu menu = menu(6);
            menu.setRadius(150);
            menu.setInnerRadiusRatio(0.4f);
            menu.setTitle("");
            menu.setAnimationDuration(0);
            String token = "SupercalifragilisticexpialidociousTokenWithoutSpaces0123456789";
            for (RadialMenu.Item item : menu.items()) {
                item.setLabel(token);
            }
            menu.open(manager.context(), 150, 150);
            manager.layout();

            assertEquals(144, menu.menuBounds().width() / 2, 0.01f);
        }
    }

    @Test
    void truncatedLabelsKeepSurrogatePairsWhole() {
        FontData data = fontData();
        Font font = Font.load(data, 20, new int[]{'A', 0x1F680, 0x2026});
        String text = "AAAA" + "\uD83D\uDE80".repeat(8) + "AAAA";
        boolean truncated = false;
        for (int width = 1; width <= 80; width++) {
            String fitted = RadialMenu.fitLabelText(font, text, width);
            if (fitted == null) {
                continue;
            }
            truncated |= fitted.length() < text.length();
            assertNoUnpairedSurrogates(fitted);
        }
        assertTrue(truncated);
    }

    @Test
    void showsHoveredSectorTooltipAndSwitchesItWithoutSticking() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.setTooltipSettings(new TooltipSettings(0, 0, 0, 12, 18));
            String longLabel = "SupercalifragilisticexpialidociousTokenWithoutSpaces0123456789";
            String customTooltip = "Second sector hint that differs from its drawn label";
            menu.items().getFirst().setLabel(longLabel);
            menu.items().get(1).setTooltip(customTooltip);
            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.render();

            manager.handle(new PointerMoved(400, 200));
            assertEquals(0, menu.hoveredIndex());
            TextTooltip tooltip = assertInstanceOf(TextTooltip.class, menu.tooltip());
            assertEquals(longLabel, tooltip.text());
            manager.update(0.01f);
            manager.update(0.02f);
            assertNotNull(tooltip.context());

            manager.handle(new PointerMoved(500, 300));
            assertEquals(1, menu.hoveredIndex());
            assertSame(tooltip, menu.tooltip());
            assertEquals(customTooltip, tooltip.text());
            assertNotNull(tooltip.context());

            manager.handle(new PointerMoved(400, 400));
            manager.handle(new PointerMoved(300, 300));
            manager.handle(new PointerMoved(400, 200));
            assertEquals(0, menu.hoveredIndex());
            assertSame(tooltip, menu.tooltip());
            assertEquals(longLabel, tooltip.text());
        }
    }

    @Test
    void clearsTheTooltipWhenHoverLeavesOrTheMenuCloses() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.setTooltipSettings(new TooltipSettings(0, 0, 0, 12, 18));
            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.render();

            manager.handle(new PointerMoved(400, 200));
            TextTooltip tooltip = assertInstanceOf(TextTooltip.class, menu.tooltip());
            manager.update(0.01f);
            manager.update(0.02f);
            assertNotNull(tooltip.context());

            manager.handle(new PointerMoved(400, 300));
            assertEquals(-1, menu.hoveredIndex());
            assertNull(menu.tooltip());
            manager.update(0.02f);
            assertNull(tooltip.context());

            manager.handle(new PointerMoved(400, 200));
            assertSame(tooltip, menu.tooltip());
            manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
            assertFalse(menu.isOpen());
            assertNull(menu.tooltip());
            manager.update(0.02f);
            assertNull(tooltip.context());

            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.handle(new PointerMoved(400, 200));
            assertSame(tooltip, menu.tooltip());
            menu.close();
            assertNull(menu.tooltip());
            manager.update(0.02f);
            assertNull(tooltip.context());
        }
    }

    @Test
    void disabledSectorStillShowsItsTooltip() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = menu(4);
            menu.setTooltipSettings(new TooltipSettings(0, 0, 0, 12, 18));
            RadialMenu.Item disabled = menu.items().getFirst();
            disabled.setLabel("Archive the selected document");
            disabled.setTooltip("Locked: archives are unavailable in this demo.");
            disabled.setEnabled(false);
            menu.open(manager.context(), 400, 300);
            manager.layout();
            manager.render();

            manager.handle(new PointerMoved(400, 200));
            assertEquals(0, menu.hoveredIndex());
            TextTooltip tooltip = assertInstanceOf(TextTooltip.class, menu.tooltip());
            assertEquals("Locked: archives are unavailable in this demo.", tooltip.text());
            manager.update(0.01f);
            manager.update(0.02f);
            assertNotNull(tooltip.context());
            assertTrue(menu.isOpen());
        }
    }

    @Test
    void rejectsInvalidConfiguration() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadialMenu menu = new RadialMenu();
            assertFalse(menu.isOpen());
            menu.open(manager.context(), 100, 100);
            assertFalse(menu.isOpen());
            assertThrows(NullPointerException.class,
                    () -> menu.open(null, 10, 10));
            assertThrows(IllegalArgumentException.class,
                    () -> menu.open(manager.context(), Float.NaN, 10));
            assertThrows(IllegalArgumentException.class, () -> menu.setRadius(0));
            assertThrows(IllegalArgumentException.class, () -> menu.setInnerRadiusRatio(1));
            assertThrows(IllegalArgumentException.class, () -> menu.setInnerRadiusRatio(-1));
            assertThrows(IllegalArgumentException.class, () -> menu.setIconSize(0));
            assertThrows(IllegalArgumentException.class, () -> menu.setAnimationDuration(-1));
            assertThrows(NullPointerException.class, () -> menu.setAnimationEasing(null));
            assertThrows(NullPointerException.class, () -> menu.setTitle(null));
            assertThrows(NullPointerException.class, () -> menu.addItem(null));
            assertThrows(NullPointerException.class, () -> menu.removeItem(null));
            assertEquals(Easing.EASE_OUT, menu.animationEasing());
        }
    }

    @Test
    void iconsNeverHideLabelsThatFitWithoutThem() {
        // A large font leaves no room for both an icon and a label in a narrow sector.
        int labelsWithoutIcons = assertLongLabelsStayInsideSectors(false, 20);
        int labelsWithIcons = assertLongLabelsStayInsideSectors(true, 20);
        assertTrue(labelsWithIcons >= 3);
        assertEquals(labelsWithoutIcons, labelsWithIcons);
    }

    private static void assertLongLabelsStayInsideSectors(boolean withIcons) {
        assertLongLabelsStayInsideSectors(withIcons, 14);
    }

    private static int assertLongLabelsStayInsideSectors(boolean withIcons, float fontSize) {
        FontData data = fontData();
        TestBackend backend = new TestBackend();
        Viewport viewport = new Viewport(400, 400, 400, 400);
        try (WidgetManager manager = manager(backend, viewport)) {
            manager.setDefaultFont(manager.context().createFont(data, fontSize));
            RadialMenu menu = new RadialMenu();
            menu.setRadius(100);
            menu.setInnerRadiusRatio(0.4f);
            menu.setIconSize(16);
            menu.setTitle("");
            menu.setAnimationDuration(0);
            String token = "SupercalifragilisticexpialidociousTokenWithoutSpaces0123456789";
            for (int index = 0; index < 6; index++) {
                RadialMenu.Item item = menu.addItem(token, null);
                if (withIcons) {
                    item.setIcon(new TestTexture(18, 18));
                }
            }
            menu.open(manager.context(), 200, 200);
            manager.layout();
            manager.render();

            List<TextCommand> labels = backend.lastDrawList().commands().stream()
                    .filter(TextCommand.class::isInstance)
                    .map(TextCommand.class::cast)
                    .toList();
            assertFalse(labels.isEmpty());
            assertTrue(labels.size() >= 3);
            assertLabelsInside(menu, labels, 6);
            Rect bounds = menu.menuBounds();
            Set<Integer> quadrants = new HashSet<>();
            for (TextCommand label : labels) {
                float angle = (float) Math.atan2(label.bounds().center().y() - bounds.center().y(),
                        label.bounds().center().x() - bounds.center().x());
                quadrants.add((int) (normalize(angle) / (Math.PI / 2)) % 4);
                assertTrue(label.glyphs().size() < token.codePointCount(0, token.length()));
            }
            assertTrue(quadrants.size() >= 2);
            assertEquals(token, menu.items().getFirst().label());
            return labels.size();
        }
    }

    private static void assertLabelsInside(RadialMenu menu, List<TextCommand> labels, int count) {
        Rect bounds = menu.menuBounds();
        float centerX = bounds.center().x();
        float centerY = bounds.center().y();
        float outer = bounds.width() / 2;
        float inner = outer * menu.innerRadiusRatio();
        float sweep = (float) (Math.PI * 2 / count);
        float start = (float) (-Math.PI / 2 - sweep / 2);
        for (TextCommand label : labels) {
            Rect text = label.bounds();
            float relative = normalize((float) Math.atan2(text.center().y() - centerY,
                    text.center().x() - centerX) - start);
            int index = Math.min((int) (relative / sweep), count - 1);
            float sectorCenter = start + (index + 0.5f) * sweep;
            for (float x : List.of(text.x(), text.right())) {
                for (float y : List.of(text.y(), text.bottom())) {
                    double radius = Math.hypot(x - centerX, y - centerY);
                    assertTrue(radius >= inner - 0.5 && radius <= outer + 0.5);
                    double difference = Math.abs(normalizeSigned((float) Math.atan2(y - centerY,
                            x - centerX) - sectorCenter));
                    assertTrue(difference <= sweep / 2 + 0.01);
                }
            }
        }
    }

    private static void assertNoUnpairedSurrogates(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.isHighSurrogate(character)) {
                assertTrue(index + 1 < text.length());
                assertTrue(Character.isLowSurrogate(text.charAt(index + 1)));
                index++;
            } else {
                assertFalse(Character.isLowSurrogate(character));
            }
        }
    }

    private static float normalize(float angle) {
        float fullTurn = (float) (Math.PI * 2);
        float normalized = angle % fullTurn;
        return normalized < 0 ? normalized + fullTurn : normalized;
    }

    private static float normalizeSigned(float angle) {
        float fullTurn = (float) (Math.PI * 2);
        float normalized = angle % fullTurn;
        if (normalized > Math.PI) {
            normalized -= fullTurn;
        } else if (normalized < -Math.PI) {
            normalized += fullTurn;
        }
        return normalized;
    }

    private static FontData fontData() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        return data.get();
    }

    private static RadialMenu menu(int count) {
        RadialMenu menu = new RadialMenu();
        for (int index = 0; index < count; index++) {
            menu.addItem("Item " + index, null);
        }
        menu.setAnimationDuration(0);
        return menu;
    }

    private static Widget focusable(Rect bounds) {
        Widget widget = new Widget() { };
        widget.setFocusable(true);
        widget.setBounds(bounds);
        return widget;
    }

    private static WidgetManager manager(TestBackend backend) {
        return manager(backend, TestViewports.LOGICAL);
    }

    private static WidgetManager manager(TestBackend backend, Viewport viewport) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(viewport);
        Widget root = new Widget() { };
        root.setBounds(new Rect(0, 0, viewport.logicalWidth(), viewport.logicalHeight()));
        manager.add(root);
        return manager;
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }
}
