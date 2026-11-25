package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.render.RenderManager;
import com.lixui.render.Viewport;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.theme.Theme;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChipStripTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void measuresChipsWithSharedPaddingAndGap() {
        ChipStrip strip = strip("Alpha", "Beta", "Gamma");

        Size size = strip.measure(null);

        assertEquals(ChipStrip.CHIP_PADDING * 6 + ChipStrip.CHIP_GAP * 2, size.width(), 0.001f);
        assertEquals(ChipStrip.CHIP_HEIGHT, size.height(), 0.001f);
    }

    @Test
    void skipsDisabledOptions() {
        SelectOption alpha = new SelectOption("Alpha");
        SelectOption beta = new SelectOption("Beta");
        SelectOption gamma = new SelectOption("Gamma");
        beta.setDisabled(true);

        ChipStrip strip = ChipStrip.of(List.of(alpha, beta, gamma));

        assertEquals(ChipStrip.CHIP_PADDING * 4 + ChipStrip.CHIP_GAP,
                strip.measure(null).width(), 0.001f);
    }

    @Test
    void paintsEveryChipInsideTheSuppliedClip() {
        Rect content = new Rect(10, 10, 40, 20);
        Rect clip = new Rect(100, 100, 40, 20);

        List<RoundedRectCommand> chips = chips(paint(strip("Alpha", "Beta"), content, clip));

        assertEquals(2, chips.size());
        for (RoundedRectCommand chip : chips) {
            assertEquals(9, chip.radius(), 0.001f);
            assertEquals(clip, chip.clip());
            assertTrue(chip.bounds().x() >= content.x());
            assertTrue(chip.bounds().right() <= content.right());
        }
    }

    @Test
    void paintsATrailingPillForChipsThatDoNotFit() {
        List<RoundedRectCommand> chips = chips(paint(
                strip("Alpha", "Beta", "Gamma", "Delta"),
                new Rect(0, 0, 40, 20), new Rect(0, 0, 40, 20)));

        assertEquals(2, chips.size());
        assertEquals(ChipStrip.CHIP_PADDING * 2, chips.get(0).bounds().width(), 0.001f);
        assertEquals(ChipStrip.CHIP_PADDING * 2, chips.get(1).bounds().width(), 0.001f);
    }

    @Test
    void paintsASinglePillWhenNoChipFitsNextToIt() {
        List<RoundedRectCommand> chips = chips(paint(
                strip("Alpha", "Beta", "Gamma", "Delta"),
                new Rect(0, 0, 30, 20), new Rect(0, 0, 30, 20)));

        assertEquals(1, chips.size());
        assertEquals(ChipStrip.CHIP_PADDING * 2, chips.get(0).bounds().width(), 0.001f);
    }

    @Test
    void paintsTheFirstChipWhenEvenThePillDoesNotFit() {
        List<RoundedRectCommand> chips = chips(paint(
                strip("Alpha", "Beta", "Gamma", "Delta"),
                new Rect(0, 0, 12, 20), new Rect(0, 0, 12, 20)));

        assertEquals(1, chips.size());
        assertEquals(12, chips.get(0).bounds().width(), 0.001f);
    }

    private static ChipStrip strip(String... labels) {
        return ChipStrip.of(List.of(labels).stream().map(SelectOption::new).toList());
    }

    private static List<DrawCommand> paint(ChipStrip strip, Rect content, Rect clip) {
        TestBackend backend = new TestBackend();
        RenderManager renderer = new RenderManager(backend);
        renderer.resize(VIEWPORT);
        renderer.begin();
        strip.paint(renderer, content, clip, null, Theme.dark(), false);
        renderer.end();
        return backend.lastDrawList().commands();
    }

    private static List<RoundedRectCommand> chips(List<DrawCommand> commands) {
        return commands.stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .toList();
    }
}
