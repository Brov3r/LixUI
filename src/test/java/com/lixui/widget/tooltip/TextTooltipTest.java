package com.lixui.widget.tooltip;

import com.lixui.widget.testsupport.TestViewports;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.InputState;
import com.lixui.input.event.PointerMoved;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.WidgetManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TextTooltipTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void measuresTheLabelWithCompactPadding() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        Font font = Font.load(data.get(), 16);
        TextTooltip tooltip = new TextTooltip("Hint");
        tooltip.setFont(font);

        Size measured = tooltip.measure(LayoutConstraints.loose(500, 500));
        Size text = font.measure("Hint");

        assertEquals(text.width() + 16, measured.width(), 0.001f);
        assertEquals(text.height() + 16, measured.height(), 0.001f);
        tooltip.setText("Longer");
        assertEquals("Longer", tooltip.text());
    }

    @Test
    void drawsPanelSurfaceAndTextThroughTheTooltipOverlay() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            Font font = manager.context().createFont(data.get(), 16);
            manager.setDefaultFont(font);
            TextTooltip tooltip = new TextTooltip("Hint");
            manager.setTooltipSettings(TooltipSettings.defaults().withFade(0, 0));
            Panel owner = new Panel();
            owner.setBounds(new Rect(0, 0, 300, 200));
            owner.setTooltip(tooltip);
            manager.add(owner);

            manager.handle(new PointerMoved(20, 20));
            manager.update(0.6f);
            manager.update(0.01f);
            manager.render();

            List<DrawCommand> commands = backend.lastDrawList().commands();
            assertEquals(5, commands.size());
            TextCommand text = assertInstanceOf(TextCommand.class, commands.get(4));
            assertEquals(4, text.glyphs().size());
            assertEquals(32, tooltip.bounds().x(), 0.001f);
            assertEquals(38, tooltip.bounds().y(), 0.001f);
        }
    }


}
