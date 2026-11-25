package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.render.RenderManager;
import com.lixui.render.Viewport;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.theme.Theme;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

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
    void keepsWholeChipsAndPlansTheOverflowTextWidth() {
        Font font = font();
        ChipStrip strip = strip("Highpass", "Lowpass", "Bandpass", "Notch");
        float firstChip = chipWidth(font, "Highpass");
        float overflow = font.measure("+3").width();
        float availableWidth = firstChip + ChipStrip.CHIP_GAP + overflow + 1;

        ChipStrip.Plan plan = strip.plan(font, availableWidth);

        assertEquals(List.of("Highpass"), plan.labels());
        assertEquals(3, plan.hidden());
        assertEquals(firstChip + ChipStrip.CHIP_GAP + overflow, plan.width(), 0.001f);
        assertTrue(plan.width() <= availableWidth);
    }

    @Test
    void measuresTheOverflowIndicatorWithoutChipPadding() {
        Font font = font();
        ChipStrip strip = strip("Highpass", "Lowpass", "Bandpass", "Notch");
        float overflow = font.measure("+4").width();

        Size size = strip.measure(font, overflow);

        assertEquals(overflow, size.width(), 0.001f);
        assertEquals(ChipStrip.CHIP_HEIGHT, size.height(), 0.001f);
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
    void paintsTheFullOverflowCountWithoutAChipBackground() {
        ChipStrip strip = strip("Highpass", "Lowpass", "Bandpass", "Notch");
        Fixture fixture = fixture();
        Font font = fixture.font();
        float firstChip = chipWidth(font, "Highpass");
        float overflow = font.measure("+3").width();
        float availableWidth = firstChip + ChipStrip.CHIP_GAP + overflow + 1;
        Rect content = new Rect(10, 10, availableWidth, 20);
        Rect clip = new Rect(10, 10, availableWidth, 20);

        List<DrawCommand> commands = paint(strip, fixture, content, clip);

        assertEquals(1, chips(commands).size());
        List<TextCommand> indicators = overflowTexts(commands);
        assertEquals(2, indicators.size());
        assertEquals(2, overflowGlyphCount(indicators));
        assertTrue(indicators.getFirst().bounds().x() >= content.x());
        assertTrue(indicators.getLast().bounds().right() <= content.right() + 0.001f);
        for (TextCommand indicator : indicators) {
            assertEquals(clip, indicator.clip());
        }
    }

    @Test
    void showsTheDigitWhenTheOverflowTextExactlyFitsTheStrip() {
        ChipStrip strip = strip("Highpass", "Lowpass");
        Fixture fixture = fixture();
        Font font = fixture.font();
        float firstChip = chipWidth(font, "Highpass");
        float overflow = font.measure("+1").width();
        float availableWidth = firstChip + ChipStrip.CHIP_GAP + overflow;

        List<DrawCommand> commands = paint(strip, fixture, new Rect(0, 0, availableWidth, 20),
                new Rect(0, 0, availableWidth, 20));

        assertEquals(1, chips(commands).size());
        assertEquals(2, overflowGlyphCount(overflowTexts(commands)));
    }

    @Test
    void paintsOnlyTheOverflowTextWhenNoChipFitsNextToIt() {
        ChipStrip strip = strip("Alpha", "Beta", "Gamma", "Delta");
        Fixture fixture = fixture();
        Font font = fixture.font();
        float overflow = font.measure("+4").width();

        List<DrawCommand> commands = paint(strip, fixture, new Rect(0, 0, overflow, 20),
                new Rect(0, 0, overflow, 20));

        assertTrue(chips(commands).isEmpty());
        assertEquals(2, overflowGlyphCount(overflowTexts(commands)));
    }

    @Test
    void keepsEveryDigitOfALargeHiddenCount() {
        List<SelectOption> options = IntStream.range(0, 150)
                .mapToObj(index -> new SelectOption("Tag " + index))
                .toList();
        ChipStrip strip = ChipStrip.of(options);
        Fixture fixture = fixture();
        Font font = fixture.font();
        float overflow = font.measure("+150").width();

        List<DrawCommand> commands = paint(strip, fixture, new Rect(0, 0, overflow, 20),
                new Rect(0, 0, overflow, 20));

        assertTrue(chips(commands).isEmpty());
        assertEquals(4, overflowGlyphCount(overflowTexts(commands)));
    }

    @Test
    void ignoresDisabledOptionsWhenCountingTheOverflowText() {
        Font font = font();
        SelectOption alpha = new SelectOption("Alpha");
        SelectOption beta = new SelectOption("Beta");
        SelectOption gamma = new SelectOption("Gamma");
        beta.setDisabled(true);

        ChipStrip.Plan plan = ChipStrip.of(List.of(alpha, beta, gamma))
                .plan(font, font.measure("+2").width());

        assertTrue(plan.labels().isEmpty());
        assertEquals(2, plan.hidden());
    }

    private static ChipStrip strip(String... labels) {
        return ChipStrip.of(List.of(labels).stream().map(SelectOption::new).toList());
    }

    private static float chipWidth(Font font, String label) {
        return font.measure(label).width() + ChipStrip.CHIP_PADDING * 2;
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

    private static List<DrawCommand> paint(ChipStrip strip, Fixture fixture, Rect content,
                                           Rect clip) {
        RenderManager renderer = fixture.renderer();
        renderer.begin();
        strip.paint(renderer, content, clip, fixture.font(), Theme.dark(), false);
        renderer.end();
        return fixture.backend().lastDrawList().commands();
    }

    private static List<RoundedRectCommand> chips(List<DrawCommand> commands) {
        return commands.stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .toList();
    }

    private static List<TextCommand> overflowTexts(List<DrawCommand> commands) {
        Theme theme = Theme.dark();
        return commands.stream()
                .filter(TextCommand.class::isInstance)
                .map(TextCommand.class::cast)
                .filter(command -> command.colors().getFirst().equals(theme.textMuted())
                        || command.colors().getFirst().equals(theme.text()))
                .toList();
    }

    private static int overflowGlyphCount(List<TextCommand> commands) {
        return commands.stream().mapToInt(command -> command.glyphs().size()).sum();
    }

    private static Fixture fixture() {
        TestBackend backend = new TestBackend();
        RenderManager renderer = new RenderManager(backend);
        renderer.resize(VIEWPORT);
        Font font = renderer.createFont(fontData(), 16);
        return new Fixture(backend, renderer, font);
    }

    private static Font font() {
        return Font.load(fontData(), 16);
    }

    private static FontData fontData() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        return data.get();
    }

    private record Fixture(TestBackend backend, RenderManager renderer, Font font) {
    }
}
