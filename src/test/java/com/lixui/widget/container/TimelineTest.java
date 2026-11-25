package com.lixui.widget.container;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineTest {
    private static final Color BLUE = Color.hex("#7EA6F5");

    @Test
    void rejectsInvalidArgumentsAndForeignEntries() {
        Timeline timeline = new Timeline();
        Timeline other = new Timeline();
        other.addEntry("Other", "Text", "Now", null);

        assertThrows(NullPointerException.class, () -> timeline.addEntry(null));
        assertThrows(NullPointerException.class,
                () -> new Timeline.Entry(null, "Text", "Now", null));
        assertThrows(NullPointerException.class,
                () -> new Timeline.Entry("Title", null, "Now", null));
        assertThrows(NullPointerException.class,
                () -> new Timeline.Entry("Title", "Text", null, null));
        assertThrows(NullPointerException.class, () -> timeline.selectEntry(null));
        assertThrows(NullPointerException.class, () -> timeline.scrollToEntry(null));
        assertThrows(IllegalArgumentException.class,
                () -> timeline.selectEntry(other.entries().get(0)));
        assertThrows(IllegalArgumentException.class,
                () -> timeline.scrollToEntry(other.entries().get(0)));
    }

    @Test
    void addsEntriesAndMapsVisibleRowsBackToEntries() {
        Timeline timeline = new Timeline();
        timeline.setBounds(new Rect(0, 0, 300, 300));
        Timeline.Entry first = timeline.addEntry("First", "Short text", "09:00", BLUE);
        Timeline.Entry second = timeline.addEntry("Second", "Another text", "10:00", null);
        WidgetManager manager = manager();
        manager.add(timeline);
        manager.render();

        assertEquals(List.of(first, second), timeline.entries());
        assertSame(first, timeline.entryAt(0));
        assertSame(second, timeline.entryAt(timeline.contentHeight() - 0.1f));
        assertNull(timeline.entryAt(-1));
        assertNull(timeline.entryAt(300));
        assertTrue(timeline.contentHeight() > 0);
    }

    @Test
    void wrappedTextReflowsWhenTheWidthChanges() {
        Timeline timeline = new Timeline();
        timeline.setBounds(new Rect(0, 0, 320, 2000));
        timeline.addEntry("Short", "One short line.", "09:00", BLUE);
        timeline.addEntry("Long",
                "A much longer description that needs several wrapped lines when the timeline "
                        + "becomes narrow and a single wide line when the pane is wide enough.",
                "10:00", null);
        WidgetManager manager = managerWithFont();
        manager.add(timeline);
        manager.render();
        float wide = timeline.contentHeight();

        timeline.setBounds(new Rect(0, 0, 160, 2000));
        manager.render();
        float narrow = timeline.contentHeight();

        assertTrue(narrow > wide);
        assertSame(timeline.entries().get(1),
                timeline.entryAt(timeline.contentHeight() - 0.1f));
    }

    @Test
    void wideTimestampNarrowsTheBodyTextAndGrowsTheRow() {
        Timeline timeline = new Timeline();
        timeline.setBounds(new Rect(0, 0, 220, 1000));
        WidgetManager manager = managerWithFont();
        manager.add(timeline);
        String body = "A much longer description that needs several wrapped lines when the "
                + "timeline becomes narrow and fewer lines when the pane is wide enough.";

        timeline.addEntry("Title", body, "", BLUE);
        manager.render();
        float withoutTimestamp = timeline.contentHeight();

        timeline.clearEntries();
        timeline.addEntry("Title", body, "12:34 PM", BLUE);
        manager.render();
        float withTimestamp = timeline.contentHeight();

        assertTrue(withTimestamp > withoutTimestamp);
    }

    @Test
    void clickingSelectsAnEntryAndEmptySpaceClearsIt() {
        Timeline timeline = new Timeline();
        timeline.setBounds(new Rect(0, 0, 300, 300));
        Timeline.Entry first = timeline.addEntry("First", "Short text", "09:00", BLUE);
        timeline.addEntry("Second", "Another text", "10:00", null);
        List<Timeline.Entry> events = new ArrayList<>();
        timeline.setSelectionListener(events::add);
        WidgetManager manager = manager();
        manager.add(timeline);
        manager.render();

        click(manager, 100, 10);
        assertSame(first, timeline.selectedEntry());
        assertEquals(1, events.size());
        assertSame(first, events.get(0));

        click(manager, 100, 280);
        assertNull(timeline.selectedEntry());
        assertEquals(2, events.size());
        assertNull(events.get(1));
    }

    @Test
    void selectingAnEntryScrollsItIntoView() {
        Timeline timeline = new Timeline();
        timeline.setBounds(new Rect(0, 0, 300, 120));
        for (int index = 0; index < 40; index++) {
            timeline.addEntry("Entry " + index, "Body " + index, "10:" + index, BLUE);
        }
        WidgetManager manager = manager();
        manager.add(timeline);
        manager.render();
        Timeline.Entry last = timeline.entries().get(39);

        timeline.selectEntry(last);

        assertSame(last, timeline.selectedEntry());
        assertTrue(timeline.scrollY() > 0);
        assertSame(last, timeline.entryAt(119));
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(TestViewports.HIDPI);
        return manager;
    }

    private static WidgetManager managerWithFont() {
        WidgetManager manager = manager();
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        manager.setDefaultFont(manager.context().createFont(data.get(), 16));
        return manager;
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }
}
