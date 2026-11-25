package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.MeshVertex;
import com.lixui.render.Viewport;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.MaskBeginCommand;
import com.lixui.render.command.MaskEndCommand;
import com.lixui.render.command.MeshCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.text.FontData;
import com.lixui.text.FontStyle;
import com.lixui.text.SystemFonts;
import com.lixui.theme.Theme;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.overlay.Popup;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorPickerTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;
    private static final float TOLERANCE = 0.01f;

    @Test
    void exposesAndNormalizesTheSelectedColor() {
        Color initial = Color.hex("#FF000080");
        ColorPicker picker = new ColorPicker(initial);
        List<Color> changes = new ArrayList<>();
        picker.setOnChange(changes::add);

        assertEquals(initial.toRgba(), picker.color().toRgba());

        picker.setColor(initial);
        assertTrue(changes.isEmpty());

        Color green = Color.hex("#00FF00");
        picker.setColor(green);

        assertEquals(green.toRgba(), picker.color().toRgba());
        assertEquals(1, changes.size());

        assertThrows(NullPointerException.class, () -> picker.setColor(null));
        assertThrows(NullPointerException.class, () -> new ColorPicker(null));
    }

    @Test
    void updatesTheColorFromTheSquareAndStrips() {
        WidgetManager manager = manager();
        ColorPicker picker = picker(Color.hex("#FF0000"));
        List<Color> changes = new ArrayList<>();
        picker.setOnChange(changes::add);
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        List<Widget> content = popup(manager).content().children();
        Widget square = content.get(0);
        Widget hueStrip = content.get(1);
        Widget alphaStrip = content.get(2);

        clickAt(manager, square, 0.5f, 0.5f);
        assertColor(picker.color(), 0.5f, 0.25f, 0.25f, 1);

        clickAt(manager, hueStrip, 0.5f, 0.5f);
        assertColor(picker.color(), 0.25f, 0.5f, 0.5f, 1);

        clickAt(manager, alphaStrip, 0.5f, 0.5f);
        assertColor(picker.color(), 0.25f, 0.5f, 0.5f, 0.5f);

        assertEquals(3, changes.size());
        assertTrue(picker.isPopupOpen());

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(picker.isPopupOpen());
        assertTrue(picker.isFocused());
    }

    @Test
    void appliesHexAndRgbFieldChanges() {
        WidgetManager manager = manager();
        ColorPicker picker = picker(Color.hex("#FF0000"));
        List<Color> changes = new ArrayList<>();
        picker.setOnChange(changes::add);
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        List<Input> inputs = inputs(popup(manager).content());
        assertEquals(5, inputs.size());
        Input hex = inputs.get(0);
        Input red = inputs.get(1);

        hex.setText("#0000FF");
        manager.focus(hex);
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertColor(picker.color(), 0, 0, 1, 1);
        assertEquals("#0000FF", hex.text());
        assertEquals(1, changes.size());

        red.setText("128");

        assertColor(picker.color(), 128 / 255f, 0, 1, 1);
        assertEquals(2, changes.size());
    }

    @Test
    void preservesHueWhileSaturationOrBrightnessIsZero() {
        WidgetManager manager = manager();
        ColorPicker picker = picker(Color.WHITE);
        List<Color> changes = new ArrayList<>();
        picker.setOnChange(changes::add);
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        List<Widget> content = popup(manager).content().children();
        Widget square = content.get(0);
        Widget hueStrip = content.get(1);

        clickAt(manager, hueStrip, 0.5f, 0.5f);
        assertColor(picker.color(), 1, 1, 1, 1);
        assertTrue(changes.isEmpty());

        clickAt(manager, square, 0.5f, 0.5f);
        assertColor(picker.color(), 0.25f, 0.5f, 0.5f, 1);
        assertEquals(1, changes.size());

        picker.setColor(Color.BLACK);
        changes.clear();

        clickAt(manager, hueStrip, 0.25f, 0.5f);
        assertColor(picker.color(), 0, 0, 0, 1);
        assertTrue(changes.isEmpty());

        clickAt(manager, square, 0.5f, 0.5f);
        assertColor(picker.color(), 0.375f, 0.5f, 0.25f, 1);
        assertEquals(1, changes.size());
    }

    @Test
    void keepsAlphaWhenEditingRgbOrSixDigitHex() {
        WidgetManager manager = manager();
        ColorPicker picker = picker(new Color(1, 0, 0, 0.5f));
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        List<Input> inputs = inputs(popup(manager).content());
        Input hex = inputs.get(0);
        Input red = inputs.get(1);

        red.setText("128");
        assertColor(picker.color(), 128 / 255f, 0, 0, 0.5f);

        hex.setText("#00FF00");
        manager.focus(hex);
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertColor(picker.color(), 0, 1, 0, 0.5f);
        assertEquals("#00FF0080", hex.text());

        hex.setText("#0000FF40");
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertColor(picker.color(), 0, 0, 1, 64 / 255f);
    }

    @Test
    void fillsTheHueAndAlphaStripsAcrossThePopupContent() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        ColorPicker picker = picker(Color.hex("#4F8EF7"));
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        Widget content = popup(manager).content();
        List<Widget> children = content.children();
        Widget hueStrip = children.get(1);
        Widget alphaStrip = children.get(2);
        float contentWidth = content.bounds().width();

        assertEquals(0, hueStrip.bounds().x(), TOLERANCE);
        assertEquals(contentWidth, hueStrip.bounds().width(), TOLERANCE);
        assertEquals(0, alphaStrip.bounds().x(), TOLERANCE);
        assertEquals(contentWidth, alphaStrip.bounds().width(), TOLERANCE);

        Rect hueBounds = absoluteBounds(hueStrip);
        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertTrue(commands.stream().anyMatch(command -> command instanceof MeshCommand
                && command.bounds().equals(hueBounds)));
        assertTrue(commands.stream().anyMatch(command -> command instanceof MeshCommand
                && command.bounds().equals(absoluteBounds(alphaStrip))));
    }

    @Test
    void drawsTheStripsAsFullRowsWithoutRoundedBackgroundEdges() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        ColorPicker picker = picker(Color.hex("#4F8EF7"));
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        Widget content = popup(manager).content();
        float contentWidth = content.bounds().width();
        for (Widget strip : List.of(content.children().get(1), content.children().get(2))) {
            assertEquals(0, strip.bounds().x(), TOLERANCE);
            assertEquals(contentWidth, strip.bounds().width(), TOLERANCE);
            assertEquals(16, strip.bounds().height(), TOLERANCE);

            Rect bounds = absoluteBounds(strip);
            MeshCommand mesh = backend.lastDrawList().commands().stream()
                    .filter(MeshCommand.class::isInstance)
                    .map(MeshCommand.class::cast)
                    .filter(command -> command.bounds().equals(bounds))
                    .findFirst()
                    .orElseThrow();
            assertCoversCorners(mesh, bounds);
            assertFalse(backend.lastDrawList().commands().stream()
                    .anyMatch(command -> command instanceof RoundedRectCommand rounded
                            && rounded.bounds().equals(bounds)));
        }
    }

    private static void assertCoversCorners(MeshCommand mesh, Rect bounds) {
        for (Point corner : List.of(
                new Point(bounds.x(), bounds.y()),
                new Point(bounds.right(), bounds.y()),
                new Point(bounds.x(), bounds.bottom()),
                new Point(bounds.right(), bounds.bottom()))) {
            assertTrue(mesh.vertices().stream().anyMatch(vertex ->
                            Math.abs(vertex.x() - corner.x()) < TOLERANCE
                                    && Math.abs(vertex.y() - corner.y()) < TOLERANCE),
                    "The strip mesh must reach every corner of its row.");
        }
    }

    @Test
    void keepsChannelLabelsVisibleAboveTheEnteredValues() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        manager.setDefaultFont(manager.context().createFont(data.get(), 14, FontStyle.REGULAR));

        ColorPicker picker = picker(Color.hex("#123456"));
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        Widget content = popup(manager).content();
        Widget rgbRow = content.children().get(5);
        List<Input> inputs = inputs(content);
        assertEquals("18", inputs.get(1).text());
        assertEquals("52", inputs.get(2).text());
        assertEquals("86", inputs.get(3).text());
        assertEquals("255", inputs.get(4).text());

        List<Label> labels = labels(content);
        List<String> captions = labels.stream().map(Label::text).toList();
        assertTrue(captions.containsAll(List.of("Hex", "R", "G", "B", "A")));

        float contentWidth = content.bounds().width();
        assertTrue(rgbRow.bounds().x() + rgbRow.bounds().width() <= contentWidth + TOLERANCE);
        assertEquals(4, rgbRow.children().size());
        float columnWidth = rgbRow.children().getFirst().bounds().width();
        float previousRight = 0;
        for (int index = 0; index < rgbRow.children().size(); index++) {
            Widget slot = rgbRow.children().get(index);
            Widget column = slot.children().getFirst();
            Label caption = assertInstanceOf(Label.class, column.children().get(0));
            Input input = assertInstanceOf(Input.class, column.children().get(1));
            assertEquals(List.of("R", "G", "B", "A").get(index), caption.text());
            assertTrue(caption.isVisible());
            assertTrue(caption.bounds().width() > 0);
            assertTrue(caption.bounds().height() > 0);
            assertEquals(columnWidth, slot.bounds().width(), TOLERANCE);
            assertEquals(previousRight, slot.bounds().x(), TOLERANCE);
            previousRight = slot.bounds().right() + 6;
            assertEquals(input.absolutePosition().x(), caption.absolutePosition().x(),
                    TOLERANCE);
            assertEquals(input.bounds().width(), caption.bounds().width(), TOLERANCE);
            assertTrue(caption.absolutePosition().y() + caption.bounds().height()
                    <= input.absolutePosition().y() + TOLERANCE);
            assertTrue(slot.bounds().x() + slot.bounds().width()
                    <= contentWidth + TOLERANCE);
        }
        assertEquals(contentWidth, previousRight - 6, TOLERANCE);
    }

    @Test
    void drawsChannelLabelsWithTheThemeTextColor() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        for (Theme theme : List.of(Theme.dark(), Theme.light())) {
            TestBackend backend = new TestBackend();
            try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
                manager.resize(VIEWPORT);
                manager.setTheme(theme);
                manager.setDefaultFont(manager.context().createFont(data.get(), 14,
                        FontStyle.REGULAR));
                ColorPicker picker = picker(Color.hex("#123456"));
                manager.add(picker);
                manager.render();
                openPopup(manager, picker);

                for (Label label : labels(popup(manager).content())) {
                    TextCommand text = textCommandIn(backend, localBounds(label));
                    assertEquals(theme.text(), text.colors().getFirst(), label.text());
                }
                manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
            }
        }
    }

    @Test
    void editsAlphaThroughTheChannelFieldAndKeepsHexInSync() {
        WidgetManager manager = manager();
        ColorPicker picker = picker(new Color(1, 0, 0, 0.5f));
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        List<Input> inputs = inputs(popup(manager).content());
        Input hex = inputs.get(0);
        Input alpha = inputs.get(4);
        assertEquals("128", alpha.text());
        assertEquals("#FF000080", hex.text());

        alpha.setText("64");

        assertColor(picker.color(), 1, 0, 0, 64 / 255f);
        assertEquals("#FF000040", hex.text());

        picker.setColor(new Color(1, 0, 0, 0.25f));

        assertEquals("64", alpha.text());
        assertEquals("#FF000040", hex.text());
    }

    @Test
    void clipsCheckerboardsToTheRoundedPreviewContours() {
        for (Viewport viewport : List.of(TestViewports.LOGICAL, TestViewports.HIDPI)) {
            TestBackend backend = new TestBackend();
            WidgetManager manager = new WidgetManager(new InputState(), backend);
            manager.resize(viewport);
            ColorPicker picker = picker(new Color(1, 0.5f, 0.25f, 0.45f));
            manager.add(picker);
            manager.render();
            openPopup(manager, picker);

            assertRoundedCheckerboards(manager, backend);
        }
    }

    private static void assertRoundedCheckerboards(WidgetManager manager, TestBackend backend) {
        Widget content = popup(manager).content();
        float contentWidth = content.bounds().width();
        List<MaskedCheckerboard> allMasks = maskedCheckerboards(
                backend.lastDrawList().commands());
        List<MaskedCheckerboard> masks = allMasks.stream()
                .filter(mask -> mask.cells().size() > 4)
                .toList();
        assertEquals(2, masks.size());

        MaskedCheckerboard swatch = maskWithSize(masks, 18, 18);
        MaskedCheckerboard preview = maskWithSize(masks, contentWidth, 26);

        for (MaskedCheckerboard mask : List.of(swatch, preview)) {
            assertFalse(mask.cells().isEmpty());
            for (DrawCommand cell : mask.cells()) {
                RoundedRectCommand tile = assertInstanceOf(RoundedRectCommand.class, cell);
                assertEquals(0, tile.radius(), TOLERANCE);
                assertTrue(cell.bounds().x() >= mask.bounds().x() - TOLERANCE);
                assertTrue(cell.bounds().y() >= mask.bounds().y() - TOLERANCE);
                assertTrue(cell.bounds().right() <= mask.bounds().right() + TOLERANCE);
                assertTrue(cell.bounds().bottom() <= mask.bounds().bottom() + TOLERANCE);
            }
            assertRoundedContour(mask);
            assertTrue(backend.lastDrawList().commands().stream().anyMatch(command ->
                    command.bounds().equals(mask.bounds())
                            && (command.radius() == 4 || command instanceof MeshCommand)));
        }

        List<MaskedCheckerboard> markers = allMasks.stream()
                .filter(mask -> mask.cells().size() == 2)
                .toList();
        assertTrue(markers.isEmpty());
    }

    private static void assertRoundedContour(MaskedCheckerboard mask) {
        Rect bounds = mask.bounds();
        List<Point> corners = List.of(
                new Point(bounds.x(), bounds.y()),
                new Point(bounds.right(), bounds.y()),
                new Point(bounds.x(), bounds.bottom()),
                new Point(bounds.right(), bounds.bottom()));
        for (Point corner : corners) {
            assertFalse(mask.vertices().stream().anyMatch(vertex ->
                            Math.abs(vertex.x() - corner.x()) < TOLERANCE
                                    && Math.abs(vertex.y() - corner.y()) < TOLERANCE),
                    "Mask vertices must not reach the square rectangle corner.");
        }
    }

    private static List<MaskedCheckerboard> maskedCheckerboards(List<DrawCommand> commands) {
        List<MaskedCheckerboard> masks = new ArrayList<>();
        Rect bounds = null;
        List<MeshVertex> vertices = null;
        List<DrawCommand> cells = new ArrayList<>();
        for (DrawCommand command : commands) {
            if (command instanceof MaskBeginCommand begin) {
                bounds = begin.bounds();
                vertices = begin.vertices();
                cells = new ArrayList<>();
            } else if (command instanceof MaskEndCommand) {
                if (bounds != null) {
                    masks.add(new MaskedCheckerboard(bounds, vertices, List.copyOf(cells)));
                }
                bounds = null;
                vertices = null;
            } else if (bounds != null) {
                cells.add(command);
            }
        }
        return masks;
    }

    private static MaskedCheckerboard maskWithSize(List<MaskedCheckerboard> masks, float width,
                                                   float height) {
        return masks.stream()
                .filter(mask -> Math.abs(mask.bounds().width() - width) < TOLERANCE
                        && Math.abs(mask.bounds().height() - height) < TOLERANCE)
                .findFirst()
                .orElseThrow();
    }

    private static Rect absoluteBounds(Widget widget) {
        Point origin = widget.absolutePosition();
        return new Rect(origin.x(), origin.y(), widget.bounds().width(),
                widget.bounds().height());
    }

    private static TextCommand textCommandIn(TestBackend backend, Rect bounds) {
        return backend.lastDrawList().commands().stream()
                .filter(TextCommand.class::isInstance)
                .map(TextCommand.class::cast)
                .filter(command -> bounds.contains(command.bounds().center().x(),
                        command.bounds().center().y()))
                .findFirst()
                .orElseThrow();
    }

    private static Rect localBounds(Widget widget) {
        return new Rect(0, 0, widget.bounds().width(), widget.bounds().height());
    }

    private static ColorPicker picker(Color color) {
        ColorPicker picker = new ColorPicker(color);
        picker.setBounds(new Rect(10, 10, 220, 36));
        picker.setMinimumSize(160, 36);
        return picker;
    }

    private static void openPopup(WidgetManager manager, ColorPicker picker) {
        Point origin = picker.absolutePosition();
        click(manager, origin.x() + picker.bounds().width() / 2,
                origin.y() + picker.bounds().height() / 2);
        manager.render();
        assertTrue(picker.isPopupOpen());
    }

    private static void clickAt(WidgetManager manager, Widget widget, float fractionX,
                                float fractionY) {
        Point origin = widget.absolutePosition();
        click(manager, origin.x() + widget.bounds().width() * fractionX,
                origin.y() + widget.bounds().height() * fractionY);
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static List<Input> inputs(Widget root) {
        List<Input> inputs = new ArrayList<>();
        collectInputs(root, inputs);
        return inputs;
    }

    private static List<Label> labels(Widget root) {
        List<Label> labels = new ArrayList<>();
        collectLabels(root, labels);
        return labels;
    }

    private static void collectLabels(Widget root, List<Label> labels) {
        for (Widget child : root.children()) {
            if (child instanceof Label label) {
                labels.add(label);
            }
            collectLabels(child, labels);
        }
    }

    private static void collectInputs(Widget root, List<Input> inputs) {
        for (Widget child : root.children()) {
            if (child instanceof Input input) {
                inputs.add(input);
            }
            collectInputs(child, inputs);
        }
    }

    private static void assertColor(Color color, float red, float green, float blue, float alpha) {
        assertEquals(red, color.r(), TOLERANCE);
        assertEquals(green, color.g(), TOLERANCE);
        assertEquals(blue, color.b(), TOLERANCE);
        assertEquals(alpha, color.a(), TOLERANCE);
    }

    private static Popup popup(WidgetManager manager) {
        return manager.widgets().stream()
                .filter(Popup.class::isInstance)
                .map(Popup.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static WidgetManager manager() {
        return manager(new TestBackend());
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }

    private record MaskedCheckerboard(Rect bounds, List<MeshVertex> vertices,
                                      List<DrawCommand> cells) {
    }
}
