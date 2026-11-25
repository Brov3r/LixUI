package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.platform.Cursor;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.theme.WidgetStyle;
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

class SearchInputTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void debouncesThenReportsTheQuery() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            SearchInput search = new SearchInput();
            search.setDebounceSeconds(0.5f);
            search.setBounds(new Rect(0, 0, 240, 40));
            List<String> results = new ArrayList<>();
            search.setOnSearch(results::add);
            manager.add(search);
            manager.render();

            search.setText("aria");
            manager.update(0.2f);
            assertTrue(results.isEmpty());

            manager.update(0.31f);
            assertEquals(List.of("aria"), results);

            manager.update(1);
            assertEquals(List.of("aria"), results);
        }
    }

    @Test
    void enterReportsImmediatelyAndCancelsTheTimer() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            SearchInput search = new SearchInput();
            List<String> results = new ArrayList<>();
            search.setOnSearch(results::add);
            search.setBounds(new Rect(0, 0, 240, 40));
            manager.add(search);
            manager.render();
            manager.focus(search);

            search.setText("knight");
            manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

            assertEquals(List.of("knight"), results);
            manager.update(1);
            assertEquals(List.of("knight"), results);
        }
    }

    @Test
    void reservesSpaceForTheLeadingIconAndClearAction() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            SearchInput search = new SearchInput();
            Input plain = new Input();
            search.setBounds(new Rect(0, 0, 240, 40));
            plain.setBounds(new Rect(0, 0, 240, 40));
            manager.add(search).add(plain);
            manager.render();

            WidgetStyle style = manager.context().theme().controlStyle();
            float iconOnly = search.contentRect(style).width();
            assertTrue(iconOnly < plain.contentRect(style).width());

            search.setText("abc");
            assertTrue(search.contentRect(style).width() < iconOnly);
        }
    }

    @Test
    void clearActionClearsTheValue() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            SearchInput search = new SearchInput();
            search.setBounds(new Rect(0, 0, 240, 40));
            manager.add(search);
            manager.render();
            WidgetStyle style = manager.context().theme().controlStyle();
            search.setText("abc");

            float x = search.localBounds().width() - style.padding() - 4;
            float y = search.localBounds().height() / 2;
            boolean consumed = search.onClick(x, y, MouseButton.LEFT, Modifiers.NONE);

            assertTrue(consumed);
            assertTrue(search.text().isEmpty());
        }
    }

    @Test
    void clearActionUsesThePointerCursor() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            SearchInput search = new SearchInput();
            search.setBounds(new Rect(0, 0, 240, 40));
            manager.add(search);
            manager.render();
            WidgetStyle style = manager.context().theme().controlStyle();
            search.setText("abc");

            float clearX = search.localBounds().width() - style.padding() - 4;
            float y = search.localBounds().height() / 2;
            search.onPointerMove(clearX, y);

            assertEquals(Cursor.POINTER, search.cursor());
            search.onPointerMove(style.padding() + 2, y);
            assertEquals(Cursor.TEXT, search.cursor());
        }
    }

    @Test
    void rejectsInvalidDebounceDelay() {
        SearchInput search = new SearchInput();
        assertThrows(IllegalArgumentException.class, () -> search.setDebounceSeconds(-1));
        assertThrows(IllegalArgumentException.class, () -> search.setDebounceSeconds(Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> search.setDebounceSeconds(Float.POSITIVE_INFINITY));
    }

    @Test
    void rejectsNullText() {
        assertThrows(NullPointerException.class, () -> new SearchInput(null));
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
