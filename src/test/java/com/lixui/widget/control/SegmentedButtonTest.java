package com.lixui.widget.control;

import com.lixui.widget.Panel;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.LayoutConstraints;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.Viewport;
import com.lixui.render.ImageData;
import com.lixui.render.Texture;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.widget.testsupport.TestBackend;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentedButtonTest {
    private static final Viewport VIEWPORT = new Viewport(800, 600, 800, 600);

    @Test
    void runsTheActionOfTheClickedSegment() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            SegmentedButton button = new SegmentedButton();
            button.setBounds(new Rect(0, 0, 180, 32));
            List<String> actions = new ArrayList<>();
            button.addSegment("Cut").setOnAction(() -> actions.add("cut"));
            button.addSegment("Copy").setOnAction(() -> actions.add("copy"));
            SegmentedButton.Segment paste = button.addSegment("Paste");
            paste.setOnAction(() -> actions.add("paste"));
            manager.add(button);
            manager.render();

            click(manager, 20, 16);
            click(manager, 90, 16);
            click(manager, 160, 16);
            assertEquals(List.of("cut", "copy", "paste"), actions);

            paste.setEnabled(false);
            click(manager, 160, 16);
            assertEquals(3, actions.size());

            button.clickSegment(0);
            assertEquals(4, actions.size());
            assertThrows(IllegalArgumentException.class, () -> button.clickSegment(3));
        }
    }

    @Test
    void managesSegmentsAndMeasuresTheirContent() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            SegmentedButton button = new SegmentedButton();
            manager.add(button);
            SegmentedButton.Segment first = button.addSegment("One");
            SegmentedButton.Segment second = button.addSegment("Two");
            assertEquals(List.of(first, second), button.segments());
            Size measured = button.measure(LayoutConstraints.loose(400, 100));
            assertTrue(measured.width() > 0);
            assertTrue(measured.height() > 0);

            assertTrue(button.removeSegment(first));
            assertFalse(button.removeSegment(first));
            assertEquals(List.of(second), button.segments());
            button.clearSegments();
            assertTrue(button.segments().isEmpty());
        }
    }

    @Test
    void drawsTheSurfaceAndDividers() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            SegmentedButton button = new SegmentedButton();
            button.setBounds(new Rect(0, 0, 180, 32));
            button.addSegment("Left");
            button.addSegment("Center");
            button.addSegment("Right");
            manager.add(button);
            manager.render();
            long dividers = backend.lastDrawList().commands().stream()
                    .filter(RoundedRectCommand.class::isInstance)
                    .map(RoundedRectCommand.class::cast)
                    .filter(command -> command.radius() == 0 && command.bounds().width() == 1)
                    .count();
            assertEquals(2, dividers);
        }
    }

    @Test
    void highlightsOnlyTheHoveredOrPressedSegment() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Theme theme = manager.context().theme();
            SegmentedButton button = new SegmentedButton();
            button.setBounds(new Rect(0, 0, 180, 32));
            button.addSegment("One");
            button.addSegment("Two");
            button.addSegment("Three");
            manager.add(button);
            manager.render();

            manager.handle(new PointerMoved(90, 16));
            manager.render();
            assertTrue(hasRoundedColor(backend, theme.controlStyle()
                    .background(WidgetState.HOVERED)));
            assertFalse(hasFullWidthRoundedColor(backend, 180, theme.controlStyle()
                    .background(WidgetState.HOVERED)));

            manager.handle(new PointerPressed(90, 16, MouseButton.LEFT, Modifiers.NONE));
            manager.render();
            assertTrue(hasRoundedColor(backend, theme.controlStyle()
                    .background(WidgetState.PRESSED)));
            manager.handle(new PointerReleased(90, 16, MouseButton.LEFT, Modifiers.NONE));
        }
    }

    @Test
    void animatesTheSelectionAndKeepsTheAccentWhileHoveredOrPressed() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Theme theme = manager.context().theme();
            Color accent = theme.primary().withAlpha(0.4f);
            SegmentedButton button = new SegmentedButton();
            button.setBounds(new Rect(0, 0, 180, 32));
            button.setSelectionMode(SegmentedButton.SelectionMode.SINGLE);
            button.addSegment("One");
            button.addSegment("Two");
            button.addSegment("Three");
            manager.add(button);
            manager.update(0.016f);

            button.setSelectedIndex(0);
            manager.update(0.016f);
            manager.render();
            assertFalse(hasRoundedColor(backend, accent));

            advance(manager, 120);
            manager.render();
            assertTrue(hasRoundedColor(backend, accent));

            manager.handle(new PointerMoved(20, 16));
            manager.render();
            int hoveredBackground = roundedColorIndex(backend,
                    theme.controlStyle().background(WidgetState.HOVERED));
            assertTrue(hoveredBackground >= 0);
            assertTrue(roundedColorIndex(backend, accent) > hoveredBackground);

            manager.handle(new PointerPressed(20, 16, MouseButton.LEFT, Modifiers.NONE));
            manager.render();
            int pressedBackground = roundedColorIndex(backend,
                    theme.controlStyle().background(WidgetState.PRESSED));
            assertTrue(pressedBackground >= 0);
            assertTrue(roundedColorIndex(backend, accent) > pressedBackground);
            manager.handle(new PointerReleased(20, 16, MouseButton.LEFT, Modifiers.NONE));

            button.setSelectedIndex(2);
            manager.update(0.016f);
            manager.render();
            assertFalse(hasRoundedColor(backend, accent));

            advance(manager, 120);
            manager.render();
            assertTrue(hasRoundedColor(backend, accent));
        }
    }

    @Test
    void keepsTheAccentFillAndDarkensTheSelectedLabelOnLightThemes() {
        FontData data = SystemFonts.findDefault().orElse(null);
        Assumptions.assumeTrue(data != null, "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setTheme(Theme.light());
            manager.setDefaultFont(manager.context().createFont(data, 16));
            Theme theme = manager.context().theme();
            SegmentedButton button = new SegmentedButton();
            button.setBounds(new Rect(0, 0, 180, 32));
            button.setSelectionMode(SegmentedButton.SelectionMode.SINGLE);
            button.addSegment("One");
            button.addSegment("Two");
            button.setSelectedIndex(0);
            manager.add(button);
            manager.update(0.016f);
            manager.render();

            Color fill = theme.primary().withAlpha(0.4f);
            assertTrue(hasRoundedColor(backend, fill));

            TextCommand label = backend.lastDrawList().commands().stream()
                    .filter(TextCommand.class::isInstance)
                    .map(TextCommand.class::cast)
                    .findFirst().orElseThrow();
            Color labelColor = label.colors().getFirst();
            assertTrue(luminance(labelColor) < luminance(theme.primary()));
            assertFalse(labelColor.equals(theme.primary()));
        }
    }

    @Test
    void centersDividersBetweenSelectedSegments() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Theme theme = manager.context().theme();
            SegmentedButton button = new SegmentedButton();
            button.setBounds(new Rect(0, 0, 180, 32));
            button.setSelectionMode(SegmentedButton.SelectionMode.MULTIPLE);
            button.addSegment("One");
            button.addSegment("Two");
            button.addSegment("Three");
            for (int index = 0; index < 3; index++) {
                button.setSelected(index, true);
            }
            manager.add(button);
            manager.update(0.016f);
            manager.render();

            List<RoundedRectCommand> commands = backend.lastDrawList().commands().stream()
                    .filter(RoundedRectCommand.class::isInstance)
                    .map(RoundedRectCommand.class::cast)
                    .toList();
            Color accent = theme.primary().withAlpha(0.4f);
            List<RoundedRectCommand> pills = commands.stream()
                    .filter(command -> command.color().equals(accent))
                    .toList();
            List<RoundedRectCommand> dividers = commands.stream()
                    .filter(command -> command.radius() == 0 && command.bounds().width() == 1)
                    .toList();
            assertEquals(2, dividers.size());
            for (RoundedRectCommand divider : dividers) {
                float dividerLeft = divider.bounds().x();
                float dividerRight = divider.bounds().right();
                float leftGap = dividerLeft - (float) pills.stream()
                        .filter(pill -> pill.bounds().right() <= dividerLeft + 0.01f)
                        .mapToDouble(pill -> pill.bounds().right())
                        .max().orElseThrow();
                float rightGap = (float) pills.stream()
                        .filter(pill -> pill.bounds().x() >= dividerRight - 0.01f)
                        .mapToDouble(pill -> pill.bounds().x())
                        .min().orElseThrow() - dividerRight;
                assertEquals(leftGap, rightGap, 0.01f);
            }
        }
    }

    @Test
    void sharesExtraWidthEquallyBetweenStretchedSegments() {
        FontData data = SystemFonts.findDefault().orElse(null);
        Assumptions.assumeTrue(data != null, "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(manager.context().createFont(data, 16));
            SegmentedButton button = new SegmentedButton();
            button.setBounds(new Rect(0, 0, 600, 32));
            button.addSegment("A");
            button.addSegment("A much longer label");
            button.addSegment("B");
            manager.add(button);
            manager.render();

            float firstSlack = button.segmentBounds(0).width() - button.font().measure("A").width();
            float secondSlack = button.segmentBounds(1).width()
                    - button.font().measure("A much longer label").width();
            float thirdSlack = button.segmentBounds(2).width() - button.font().measure("B").width();
            assertEquals(firstSlack, secondSlack, 0.01f);
            assertEquals(secondSlack, thirdSlack, 0.01f);
        }
    }

    @Test
    void playsSoundsBeforeSegmentActions() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            SegmentedButton button = new SegmentedButton();
            button.setBounds(new Rect(0, 0, 180, 32));
            List<String> events = new ArrayList<>();
            button.setSoundPlayer(() -> events.add("sound"));
            SegmentedButton.Segment segment = button.addSegment("Copy");
            segment.setOnAction(() -> events.add("action"));
            segment.setSoundPlayer(() -> events.add("segment-sound"));
            manager.add(button);
            manager.render();

            click(manager, 90, 16);
            assertEquals(List.of("segment-sound", "action"), events);

            segment.setSoundPlayer(null);
            click(manager, 90, 16);
            assertEquals(List.of("segment-sound", "action", "sound", "action"), events);
        }
    }

    @Test
    void supportsRadioAndCheckBoxSelectionModes() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            SegmentedButton button = new SegmentedButton();
            button.setBounds(new Rect(0, 0, 180, 32));
            button.addSegment("One");
            button.addSegment("Two");
            button.addSegment("Three");
            manager.add(button);
            manager.render();

            button.setSelectionMode(SegmentedButton.SelectionMode.SINGLE);
            click(manager, 20, 16);
            assertEquals(List.of(0), button.selectedIndices());
            assertEquals(0, button.selectedIndex());
            click(manager, 20, 16);
            assertEquals(List.of(0), button.selectedIndices());
            click(manager, 160, 16);
            assertEquals(List.of(2), button.selectedIndices());
            assertTrue(button.isSelected(2));
            assertFalse(button.isSelected(0));

            button.setSelectionMode(SegmentedButton.SelectionMode.MULTIPLE);
            click(manager, 20, 16);
            assertEquals(List.of(2, 0), button.selectedIndices());
            click(manager, 160, 16);
            assertEquals(List.of(0), button.selectedIndices());

            button.setSelectionMode(SegmentedButton.SelectionMode.NONE);
            assertTrue(button.selectedIndices().isEmpty());

            button.setSelected(1, true);
            assertTrue(button.isSelected(1));
            button.setSelected(1, false);
            assertFalse(button.isSelected(1));
            assertThrows(IllegalArgumentException.class, () -> button.setSelected(3, true));
        }
    }

    @Test
    void exposesFineTuningAndRejectsInvalidValues() {
        SegmentedButton button = new SegmentedButton();
        button.setSegmentPadding(10);
        button.setIconSize(18);
        button.setIconGap(5);
        assertEquals(10f, button.segmentPadding(), 0.001f);
        assertEquals(18f, button.iconSize(), 0.001f);
        assertEquals(5f, button.iconGap(), 0.001f);
        assertTrue(button.showIcons());
        button.setShowIcons(false);
        assertFalse(button.showIcons());

        assertThrows(IllegalArgumentException.class, () -> button.setSegmentPadding(-1));
        assertThrows(IllegalArgumentException.class, () -> button.setIconSize(0));
        assertThrows(IllegalArgumentException.class, () -> button.setIconGap(-1));
        assertThrows(NullPointerException.class, () -> button.addSegment(null));
        assertThrows(NullPointerException.class, () -> button.removeSegment(null));
    }

    @Test
    void measuresIconsOnlyWhileTheyAreShown() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            SegmentedButton button = new SegmentedButton();
            Texture icon = manager.context().createTexture(new ImageData(2, 2, new byte[16]));
            button.addSegment("Copy", icon);
            manager.add(button);
            Size withIcons = button.measure(LayoutConstraints.loose(400, 100));
            button.setShowIcons(false);
            Size withoutIcons = button.measure(LayoutConstraints.loose(400, 100));
            assertTrue(withIcons.width() > withoutIcons.width());
        }
    }

    @Test
    void usesTheControlStyleEvenInsideAPanelWithAnInheritedStyle() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Theme theme = manager.context().theme();
            Panel root = new Panel();
            root.setStyle(theme.panelStyle());
            root.setBounds(new Rect(0, 0, 300, 100));
            SegmentedButton button = new SegmentedButton();
            button.setBounds(new Rect(20, 20, 160, 32));
            button.addSegment("One");
            button.addSegment("Two");
            root.add(button);
            manager.add(root);
            manager.render();

            boolean controlSurface = backend.lastDrawList().commands().stream()
                    .filter(RoundedRectCommand.class::isInstance)
                    .map(RoundedRectCommand.class::cast)
                    .anyMatch(command -> command.color().equals(
                            theme.controlStyle().background(WidgetState.NORMAL))
                            && Math.abs(command.radius() - (theme.controlStyle().cornerRadius()
                            - theme.controlStyle().borderWidth())) < 0.01f);
            assertTrue(controlSurface);
        }
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }

    private static float luminance(Color color) {
        return color.r() * 0.299f + color.g() * 0.587f + color.b() * 0.114f;
    }

    private static void advance(WidgetManager manager, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            manager.update(0.016f);
        }
    }

    private static int roundedColorIndex(TestBackend backend, Color color) {
        var commands = backend.lastDrawList().commands();
        for (int index = 0; index < commands.size(); index++) {
            if (commands.get(index) instanceof RoundedRectCommand command
                    && command.color().equals(color)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean hasRoundedColor(TestBackend backend, Color color) {
        return backend.lastDrawList().commands().stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .anyMatch(command -> command.color().equals(color));
    }

    private static boolean hasFullWidthRoundedColor(TestBackend backend, float width, Color color) {
        return backend.lastDrawList().commands().stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .anyMatch(command -> command.color().equals(color)
                        && Math.abs(command.bounds().width() - width) < 0.01f);
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }
}
