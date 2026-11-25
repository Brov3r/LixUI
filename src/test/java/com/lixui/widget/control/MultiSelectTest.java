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
import com.lixui.render.Viewport;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.overlay.Popup;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiSelectTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void togglesSeveralOptionsWithoutClosingThePopup() {
        WidgetManager manager = manager();
        MultiSelect multi = multiSelect();
        List<String> changes = new ArrayList<>();
        multi.setOnChange((option, checked) ->
                changes.add(option.label() + ":" + checked));
        manager.add(multi);
        manager.render();

        click(manager, 60, 20);
        manager.render();
        Popup popup = popup(manager);
        Widget list = popup.content();

        clickOption(manager, list, 0);
        assertTrue(multi.isPopupOpen());
        clickOption(manager, list, 2);

        assertTrue(multi.isPopupOpen());
        assertEquals(List.of(0, 2), multi.selectedIndices());
        assertEquals(List.of("Alpha", "Gamma"),
                multi.selectedOptions().stream().map(SelectOption::label).toList());
        assertEquals(2, multi.selectedCount());
        assertEquals(List.of("Alpha:true", "Gamma:true"), changes);

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
        assertFalse(multi.isPopupOpen());
        assertSame(multi, manager.focused());
    }

    @Test
    void keepsTheSelectedOptionsWhenAnEarlierOptionIsRemoved() {
        MultiSelect multi = multiSelect();
        multi.setSelected(1, true);

        multi.removeOption(multi.options().get(0));

        assertEquals(List.of(0), multi.selectedIndices());
        assertEquals(List.of("Beta"),
                multi.selectedOptions().stream().map(SelectOption::label).toList());

        multi.removeOption(multi.options().get(0));

        assertTrue(multi.selectedIndices().isEmpty());
    }

    @Test
    void scrollsToTheSelectedRowOnTheFirstOpen() {
        WidgetManager manager = manager();
        MultiSelect multi = new MultiSelect();
        multi.setBounds(new Rect(10, 10, 240, 36));
        multi.setMinimumSize(240, 36);
        multi.setMaxPopupHeight(150);
        for (int index = 0; index < 30; index++) {
            multi.addOption(new SelectOption("Option " + index, index));
        }
        multi.setSelected(25, true);
        manager.add(multi);
        manager.render();

        click(manager, 60, 20);
        manager.render();

        OptionList list = (OptionList) popup(manager).content();
        assertEquals(25, list.highlighted());
        assertTrue(list.scrollY() > 0);
    }

    @Test
    void supportsProgrammaticSelectionAndDisabledOptions() {
        MultiSelect multi = multiSelect();
        multi.setSelected(1, true);
        assertTrue(multi.isSelected(1));

        multi.setAll(true);
        assertEquals(List.of(0, 1, 2), multi.selectedIndices());

        multi.setSelected(1, false);
        assertEquals(List.of(0, 2), multi.selectedIndices());

        multi.clearSelection();
        assertTrue(multi.selectedIndices().isEmpty());

        multi.options().get(0).setDisabled(true);
        multi.setOptions(multi.options());
        assertThrows(IllegalArgumentException.class, () -> multi.setSelected(0, true));
        assertThrows(IllegalArgumentException.class, () -> multi.setSelected(3, true));
    }

    @Test
    void measuresTheFieldByTheChipStrip() {
        MultiSelect multi = new MultiSelect();
        multi.addOption(new SelectOption("Alpha", 1));
        multi.addOption(new SelectOption("Beta", 2));
        multi.addOption(new SelectOption("Gamma", 3));
        multi.setSelected(0, true);
        multi.setSelected(2, true);

        assertEquals(ChipStrip.CHIP_PADDING * 4 + ChipStrip.CHIP_GAP,
                multi.fieldContentSize(null).width(), 0.001f);
        assertEquals(ChipStrip.CHIP_HEIGHT, multi.fieldContentSize(null).height(), 0.001f);

        multi.setSelected(1, true);

        assertEquals(ChipStrip.CHIP_PADDING * 6 + ChipStrip.CHIP_GAP * 2,
                multi.fieldContentSize(null).width(), 0.001f);

        multi.setMaximumSize(80, 40);
        assertEquals(80, multi.measure(LayoutConstraints.loose(1000, 1000)).width(), 0.001f);
    }

    @Test
    void clipsTheChipStripToTheContentArea() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        MultiSelect multi = multiSelect();
        multi.setAll(true);
        manager.add(multi);
        manager.render();

        List<RoundedRectCommand> chips = chipCommands(manager, backend);

        assertEquals(3, chips.size());
        assertEquals(new Rect(18, 18, 200, 20), chips.get(0).clip());
        assertEquals(new Rect(18, 18, 200, 20), chips.get(2).clip());
    }

    @Test
    void drawsAnOverflowPillInsideTheContentClip() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        useFont(manager);
        MultiSelect multi = new MultiSelect();
        for (String label : List.of("Alpha", "Beta", "Gamma", "Delta")) {
            multi.addOption(label);
        }
        multi.setAll(true);
        manager.add(multi);
        manager.render();
        Font font = multi.font();

        float alpha = font.measure("Alpha").width() + ChipStrip.CHIP_PADDING * 2;
        float beta = font.measure("Beta").width() + ChipStrip.CHIP_PADDING * 2;
        float pill = font.measure("+3").width() + ChipStrip.CHIP_PADDING * 2;
        float contentWidth = alpha + ChipStrip.CHIP_GAP + pill + ChipStrip.CHIP_GAP
                + beta - 1;
        multi.setBounds(new Rect(10, 10, contentWidth + 40, 36));
        manager.render();

        List<RoundedRectCommand> chips = chipCommands(manager, backend);

        assertEquals(2, chips.size());
        assertEquals(alpha, chips.get(0).bounds().width(), 0.01f);
        assertEquals(pill, chips.get(1).bounds().width(), 0.01f);
        assertEquals(new Rect(18, 18, contentWidth, 20), chips.get(0).clip());
        assertEquals(new Rect(18, 18, contentWidth, 20), chips.get(1).clip());
    }

    @Test
    void drawsTheFirstChipWhenTheFieldCannotFitTheOverflowPill() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        MultiSelect multi = multiSelect();
        multi.setAll(true);
        manager.add(multi);
        multi.setBounds(new Rect(10, 10, 50, 36));
        manager.render();

        List<RoundedRectCommand> chips = chipCommands(manager, backend);

        assertEquals(1, chips.size());
        assertEquals(10, chips.get(0).bounds().width(), 0.01f);
    }

    @Test
    void ignoresDisabledOptionsWhenMeasuringTheChipStrip() {
        MultiSelect multi = multiSelect();
        multi.setSelected(0, true);
        multi.setSelected(1, true);
        multi.options().get(0).setDisabled(true);

        assertEquals(ChipStrip.CHIP_PADDING * 2, multi.fieldContentSize(null).width(), 0.001f);
    }

    @Test
    void ignoresClicksOnDisabledOptions() {
        WidgetManager manager = manager();
        MultiSelect multi = multiSelect();
        multi.options().get(1).setDisabled(true);
        manager.add(multi);
        manager.render();

        click(manager, 60, 20);
        manager.render();
        Widget list = popup(manager).content();

        clickOption(manager, list, 1);

        assertTrue(multi.selectedIndices().isEmpty());
        assertTrue(multi.isPopupOpen());
    }

    private static MultiSelect multiSelect() {
        MultiSelect multi = new MultiSelect();
        multi.setBounds(new Rect(10, 10, 240, 36));
        multi.setMinimumSize(240, 36);
        multi.addOption(new SelectOption("Alpha", 1));
        multi.addOption(new SelectOption("Beta", 2));
        multi.addOption(new SelectOption("Gamma", 3));
        return multi;
    }

    private static void clickOption(WidgetManager manager, Widget list, int index) {
        Point origin = list.absolutePosition();
        float y = origin.y() + index * 24 + 12;
        click(manager, origin.x() + 20, y);
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static Popup popup(WidgetManager manager) {
        return manager.widgets().stream()
                .filter(Popup.class::isInstance)
                .map(Popup.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static List<RoundedRectCommand> chipCommands(WidgetManager manager,
                                                        TestBackend backend) {
        Color chipFill = Color.mix(manager.context().theme().surface(),
                manager.context().theme().primary(), 0.18f);
        return backend.lastDrawList().commands().stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .filter(command -> command.color().equals(chipFill))
                .toList();
    }

    private static void useFont(WidgetManager manager) {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        manager.setDefaultFont(manager.context().createFont(data.get(), 16));
    }

    private static WidgetManager manager() {
        return manager(new TestBackend());
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }
}
