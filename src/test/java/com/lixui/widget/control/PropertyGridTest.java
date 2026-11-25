package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyGridTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void typedPropertiesExposeValuesAndResetToDefaults() {
        PropertyGrid grid = new PropertyGrid();
        PropertyGrid.BooleanProperty visible = grid.addBoolean("visible", "Visible",
                "Display", false);
        PropertyGrid.TextProperty name = grid.addText("name", "Name", "Account", "Aria");
        PropertyGrid.NumberProperty volume = grid.addNumber("volume", "Volume", "Audio",
                5, 0, 20, 0.5, 1);
        PropertyGrid.SelectProperty quality = grid.addSelect("quality", "Quality", "Display",
                List.of("Low", "Medium", "High"), 1);
        List<PropertyGrid.Property<?>> changes = new ArrayList<>();
        grid.setOnValueChange(changes::add);

        visible.setChecked(true);
        name.setText("Bran");
        volume.setValue(12.5);
        quality.setSelectedIndex(2);

        assertTrue(visible.value());
        assertEquals("Bran", name.value());
        assertEquals(12.5, volume.value(), 0.001);
        assertEquals("High", quality.value());
        assertEquals(4, changes.size());

        changes.clear();
        grid.reset();

        assertFalse(visible.value());
        assertEquals("Aria", name.value());
        assertEquals(5, volume.value(), 0.001);
        assertEquals("Medium", quality.value());
        assertEquals(4, changes.size());
        assertEquals(List.of("Low", "Medium", "High"), quality.options());
    }

    @Test
    void editorChangesUpdateTheProperty() {
        PropertyGrid grid = new PropertyGrid();
        PropertyGrid.BooleanProperty visible = grid.addBoolean("visible", "Visible",
                "Display", false);
        PropertyGrid.TextProperty name = grid.addText("name", "Name", "Account", "Aria");
        List<String> changedKeys = new ArrayList<>();
        grid.setOnValueChange(property -> changedKeys.add(property.key()));

        ((CheckBox) visible.editor()).setChecked(true);
        ((Input) name.editor()).setText("Bran");

        assertTrue(visible.value());
        assertEquals("Bran", name.value());
        assertEquals(List.of("visible", "name"), changedKeys);
    }

    @Test
    void searchHidesRowsAndSectionsAndShrinksTheNaturalHeight() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            PropertyGrid grid = new PropertyGrid();
            grid.addText("name", "Player name", "Account", "Aria");
            grid.addText("email", "Email", "Account", "aria@example.com");
            grid.addBoolean("fullscreen", "Fullscreen", "Display", false);
            grid.addNumber("volume", "Volume", "Audio", 50);
            grid.setBounds(new Rect(0, 0, 420, 400));
            manager.add(grid);
            manager.render();

            float full = grid.measure(LayoutConstraints.loose(420, 5000)).height();
            grid.setQuery("volume");
            float filtered = grid.measure(LayoutConstraints.loose(420, 5000)).height();
            assertTrue(filtered < full);

            grid.setQuery("a");
            float named = grid.measure(LayoutConstraints.loose(420, 5000)).height();
            assertTrue(named > filtered);
            assertTrue(named < full);

            grid.setQuery("missing");
            assertTrue(grid.measure(LayoutConstraints.loose(420, 5000)).height() <= filtered);
        }
    }

    @Test
    void rangeChangesClampTheValue() {
        PropertyGrid grid = new PropertyGrid();
        PropertyGrid.NumberProperty number = grid.addNumber("count", "Count", "Numbers",
                50, 0, 100, 1, 0);
        List<PropertyGrid.Property<?>> changes = new ArrayList<>();
        grid.setOnValueChange(changes::add);

        number.setRange(0, 10);

        assertEquals(10, number.value(), 0.001);
        assertEquals(1, changes.size());
        assertThrows(IllegalArgumentException.class, () -> number.setRange(5, 1));
        assertThrows(IllegalArgumentException.class, () -> number.setValue(Double.NaN));
    }

    @Test
    void selectWithoutASelectionResetsToNoSelection() {
        PropertyGrid grid = new PropertyGrid();
        PropertyGrid.SelectProperty choice = grid.addSelect("choice", "Choice", "General",
                List.of("One", "Two"), -1);

        assertNull(choice.value());
        assertEquals(-1, choice.selectedIndex());

        choice.setSelectedIndex(1);
        assertEquals("Two", choice.value());

        grid.reset();
        assertNull(choice.value());
        assertEquals(-1, choice.selectedIndex());
    }

    @Test
    void resetAfterRangeChangeKeepsTheModelAndEditorConsistent() {
        PropertyGrid grid = new PropertyGrid();
        PropertyGrid.NumberProperty number = grid.addNumber("count", "Count", "Numbers",
                50, 0, 100, 1, 0);
        number.setRange(0, 10);
        number.setValue(5);

        grid.reset();

        assertEquals(10, number.value(), 0.001);
        assertEquals(10, ((NumberStepper) number.editor()).value(), 0.001);
        assertEquals(50, number.defaultValue(), 0.001);
    }

    @Test
    void largeValuesUseTheStepperNormalization() {
        PropertyGrid grid = new PropertyGrid();
        PropertyGrid.NumberProperty big = grid.addNumber("big", "Big number", "Numbers",
                1e12, 0, 1e15, 1, 9);

        assertEquals(1e12, big.value(), 0.001);
        assertEquals(1e12, ((NumberStepper) big.editor()).value(), 0.001);
        assertEquals(1e12, big.defaultValue(), 0.001);
    }

    @Test
    void editorsShareTheLabelColumn() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            PropertyGrid grid = new PropertyGrid();
            PropertyGrid.BooleanProperty enabled = grid.addBoolean("enabled", "Enabled",
                    "Display", false);
            PropertyGrid.TextProperty name = grid.addText("name", "Name", "Display", "Aria");
            grid.setBounds(new Rect(0, 0, 420, 400));
            manager.add(grid);
            manager.render();

            assertEquals(enabled.editor().absolutePosition().x(),
                    name.editor().absolutePosition().x(), 0.01f);
        }
    }

    @Test
    void rejectsInvalidDefinitions() {
        PropertyGrid grid = new PropertyGrid();
        grid.addBoolean("visible", "Visible", "Display", false);
        assertThrows(IllegalArgumentException.class,
                () -> grid.addBoolean("visible", "Again", "Display", true));
        assertThrows(IllegalArgumentException.class,
                () -> grid.addText("  ", "Blank", "Display", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> grid.addText("text", "Text", " ", "x"));
        assertThrows(NullPointerException.class,
                () -> grid.addText("text", "Text", "Display", null));
        assertThrows(IllegalArgumentException.class,
                () -> grid.addSelect("select", "Select", "Display", List.of(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> grid.addSelect("select", "Select", "Display", List.of("A"), 2));
        assertThrows(IllegalArgumentException.class,
                () -> grid.addNumber("number", "Number", "Display", 1, 5, 1, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> grid.addNumber("number", "Number", "Display", 1, 0, 5, 0, 0));
    }

    private static Font font(WidgetManager manager) {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        return manager.context().createFont(data.get(), 16);
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }
}
