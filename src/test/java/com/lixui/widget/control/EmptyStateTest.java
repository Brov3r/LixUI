package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmptyStateTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void measuresTheVisiblePartsOnly() {
        try (WidgetManager manager = manager(new TestBackend())) {
            manager.setDefaultFont(font(manager));
            EmptyState empty = new EmptyState("Nothing here",
                    "Create the first entry and it will appear in this list.");
            empty.setActionText("Create");
            manager.add(empty);

            Size full = empty.measure(LayoutConstraints.loose(300, 1000));
            assertTrue(full.width() > 0);
            assertTrue(full.height() > 0);

            empty.setTitle("");
            empty.setDescription("");
            empty.setActionText("");
            empty.clearIcon();

            Size bare = empty.measure(LayoutConstraints.loose(300, 1000));
            assertEquals(0, bare.width(), 0.001f);
            assertEquals(0, bare.height(), 0.001f);
        }
    }

    @Test
    void centersTheContentColumnInsideTheAssignedBounds() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            EmptyState empty = new EmptyState("Nothing here", "A shorter supporting line.");
            empty.setBounds(new Rect(0, 0, 300, 200));
            manager.add(empty);

            manager.render();

            VBox content = assertContent(empty);
            assertEquals((300 - content.bounds().width()) / 2, content.bounds().x(), 0.001f);
            assertEquals((200 - content.bounds().height()) / 2, content.bounds().y(), 0.001f);
        }
    }

    @Test
    void actionButtonRunsItsCallback() {
        try (WidgetManager manager = manager(new TestBackend())) {
            manager.setDefaultFont(font(manager));
            EmptyState empty = new EmptyState("Nothing here", "");
            empty.setActionText("Create");
            int[] actions = {0};
            empty.setOnAction(() -> actions[0]++);
            empty.setBounds(new Rect(0, 0, 300, 200));
            manager.add(empty);
            manager.render();

            VBox content = assertContent(empty);
            Button action = (Button) content.children().get(3);
            assertTrue(action.isVisible());
            action.click();

            assertEquals(1, actions[0]);
        }
    }

    @Test
    void hidesTheActionUntilTextIsAssigned() {
        EmptyState empty = new EmptyState();
        VBox content = assertContent(empty);
        Button action = (Button) content.children().get(3);
        assertFalse(action.isVisible());

        empty.setActionText("Retry");
        assertTrue(action.isVisible());

        empty.setActionText("");
        assertFalse(action.isVisible());
    }

    @Test
    void managesTheIconSourceExplicitly() {
        EmptyState empty = new EmptyState();
        empty.setIcon("folder.svg");
        assertEquals("folder.svg", empty.icon());

        empty.clearIcon();
        assertNull(empty.icon());
    }

    @Test
    void rejectsInvalidArguments() {
        EmptyState empty = new EmptyState();
        assertThrows(NullPointerException.class, () -> new EmptyState(null, ""));
        assertThrows(NullPointerException.class, () -> new EmptyState("", null));
        assertThrows(NullPointerException.class, () -> empty.setTitle(null));
        assertThrows(NullPointerException.class, () -> empty.setDescription(null));
        assertThrows(NullPointerException.class, () -> empty.setActionText(null));
        assertThrows(IllegalArgumentException.class, () -> empty.setIconSize(0));
        assertThrows(IllegalArgumentException.class, () -> empty.setIconSize(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> empty.setGap(-1));
        assertThrows(IllegalArgumentException.class, () -> empty.setContentPadding(-1));
    }

    private static VBox assertContent(EmptyState empty) {
        Widget content = empty.children().getFirst();
        return (VBox) content;
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
