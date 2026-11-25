package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.Viewport;
import com.lixui.render.command.ImageCommand;
import com.lixui.render.command.TextCommand;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void clampsPagesAndReportsActualChanges() {
        Pagination pagination = new Pagination();
        List<Integer> changes = new ArrayList<>();
        pagination.setOnChange(changes::add);

        pagination.setPageCount(3);
        pagination.setPage(2);
        pagination.setPage(99);
        pagination.setPage(-5);
        pagination.setPageCount(1);

        assertEquals(0, pagination.page());
        assertEquals(List.of(2, 0), changes);

        pagination.setPageCount(0);
        assertEquals(0, pagination.page());
        assertEquals(0, pagination.pageCount());
    }

    @Test
    void arrowsChangeThePage() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            Pagination pagination = new Pagination();
            pagination.setPageCount(5);
            pagination.setMaxButtons(7);
            manager.add(pagination);
            Size natural = pagination.measure(LayoutConstraints.loose(1000, 1000));
            pagination.setBounds(new Rect(0, 0, natural.width(), natural.height()));
            manager.render();

            click(manager, natural.width() - 3, natural.height() / 2);
            assertEquals(1, pagination.page());

            click(manager, 3, natural.height() / 2);
            assertEquals(0, pagination.page());
        }
    }

    @Test
    void drawsOneTextSlotPerVisiblePage() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            Pagination pagination = new Pagination();
            pagination.setPageCount(12);
            pagination.setMaxButtons(7);
            pagination.setPage(5);
            pagination.setBounds(new Rect(0, 0, 260, 28));
            manager.add(pagination);

            manager.render();

            long labels = backend.lastDrawList().commands().stream()
                    .filter(command -> command instanceof TextCommand)
                    .count();
            assertEquals(7, labels);
            long arrows = backend.lastDrawList().commands().stream()
                    .filter(command -> command instanceof ImageCommand)
                    .count();
            assertEquals(2, arrows);
            assertTrue(backend.lastDrawList().commands().stream()
                    .anyMatch(command -> command instanceof TextCommand));
        }
    }

    @Test
    void rejectsInvalidConfiguration() {
        Pagination pagination = new Pagination();
        assertThrows(IllegalArgumentException.class, () -> pagination.setPageCount(-1));
        assertThrows(IllegalArgumentException.class, () -> pagination.setMaxButtons(4));
        assertThrows(IllegalArgumentException.class, () -> pagination.setMaxButtons(0));
    }

    @Test
    void emptyPaginationHasNoNaturalSize() {
        Pagination pagination = new Pagination();

        Size measured = pagination.measure(LayoutConstraints.loose(1000, 1000));

        assertEquals(0, measured.width(), 0.001f);
        assertEquals(0, measured.height(), 0.001f);
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
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
