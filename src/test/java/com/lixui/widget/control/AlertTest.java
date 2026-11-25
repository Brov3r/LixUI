package com.lixui.widget.control;

import com.lixui.geometry.Size;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void wrapsTheMessageAndGrowsInHeight() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            Alert alert = new Alert(Alert.Tone.INFO);
            alert.setTitle("Attention");
            alert.setMessage("A long message wraps into several lines when the available width "
                    + "becomes small and the banner grows to keep every line visible.");
            manager.add(alert);

            Size wide = alert.measure(LayoutConstraints.loose(500, 1000));
            Size narrow = alert.measure(LayoutConstraints.loose(180, 1000));

            assertTrue(narrow.height() > wide.height());
        }
    }

    @Test
    void closingHidesTheAlertAndInvokesTheCallback() {
        Alert alert = new Alert(Alert.Tone.WARNING);
        int[] closes = {0};
        alert.setClosable(true);
        alert.setOnClose(() -> closes[0]++);

        alert.close();

        assertFalse(alert.isVisible());
        assertEquals(1, closes[0]);
    }

    @Test
    void emptyTextPartsAreHidden() {
        Alert alert = new Alert(Alert.Tone.SUCCESS);
        alert.setTitle("");
        alert.setMessage("");

        assertEquals("", alert.title());
        assertEquals("", alert.message());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(NullPointerException.class, () -> new Alert(null));
        Alert alert = new Alert();
        assertThrows(NullPointerException.class, () -> alert.setTone(null));
        assertThrows(NullPointerException.class, () -> alert.setTitle(null));
        assertThrows(NullPointerException.class, () -> alert.setMessage(null));
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
