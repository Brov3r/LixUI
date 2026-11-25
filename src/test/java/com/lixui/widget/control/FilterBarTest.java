package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterBarTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void reportsSelectedValuesAndTheActiveCount() {
        FilterBar bar = new FilterBar();
        List<FilterBar.State> states = new ArrayList<>();
        bar.setOnChange(states::add);
        bar.addFilter("status", "Status", "Online", "Away", "Offline");
        bar.addFilter("class", "Class", "Knight", "Ranger");

        bar.setValue("status", "Online");
        bar.setValue("class", "Ranger");

        assertEquals("Online", bar.value("status"));
        assertEquals(2, bar.activeFilterCount());
        assertEquals(Map.of("status", "Online", "class", "Ranger"), bar.values());
        assertEquals(2, states.size());
        assertEquals(2, states.get(1).activeFilters());

        bar.setValue("status", null);
        assertEquals(1, bar.activeFilterCount());
        assertNull(bar.value("status"));
    }

    @Test
    void customDropdownCallbackKeepsTheBarSynchronized() {
        FilterBar bar = new FilterBar();
        List<FilterBar.State> states = new ArrayList<>();
        List<SelectOption> options = new ArrayList<>();
        Dropdown dropdown = bar.addFilter("status", "Status", "Online", "Away");
        bar.setOnChange(states::add);
        dropdown.setOnChange(options::add);

        bar.setValue("status", "Online");

        assertEquals(1, options.size());
        assertEquals("Online", options.get(0).label());
        assertEquals(1, states.size());
        assertEquals("Online", states.get(0).values().get("status"));
        assertEquals("Online", bar.value("status"));
    }

    @Test
    void debouncesTheQueryAndReportsItOnce() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            FilterBar bar = new FilterBar();
            bar.setDebounceSeconds(0.4f);
            bar.addFilter("status", "Status", "Online", "Away");
            bar.setBounds(new Rect(0, 0, 500, 40));
            List<FilterBar.State> states = new ArrayList<>();
            bar.setOnChange(states::add);
            manager.add(bar);
            manager.render();

            SearchInput search = (SearchInput) ((HBox) bar.children().get(0))
                    .children().get(0);
            search.setText("aria");
            manager.update(0.2f);
            assertTrue(states.isEmpty());

            manager.update(0.21f);
            assertEquals(1, states.size());
            assertEquals("aria", states.get(0).query());
            assertEquals("aria", bar.query());

            manager.update(1);
            assertEquals(1, states.size());
        }
    }

    @Test
    void setQueryUpdatesImmediatelyWithoutDoubleReporting() {
        FilterBar bar = new FilterBar();
        List<FilterBar.State> states = new ArrayList<>();
        bar.setOnChange(states::add);

        bar.setQuery("knight");

        assertEquals("knight", bar.query());
        assertEquals(1, states.size());
        assertEquals("knight", states.get(0).query());
    }

    @Test
    void clearResetsEverythingAndNotifiesOnce() {
        FilterBar bar = new FilterBar();
        bar.addFilter("status", "Status", "Online", "Away");
        bar.addFilter("class", "Class", "Knight", "Ranger");
        bar.setQuery("aria");
        bar.setValue("status", "Online");
        bar.setValue("class", "Knight");
        List<FilterBar.State> states = new ArrayList<>();
        bar.setOnChange(states::add);

        bar.clear();

        assertEquals("", bar.query());
        assertEquals(0, bar.activeFilterCount());
        assertTrue(bar.values().isEmpty());
        assertEquals(1, states.size());
        assertEquals("", states.get(0).query());
    }

    @Test
    void rejectsInvalidFiltersAndValues() {
        FilterBar bar = new FilterBar();
        assertThrows(IllegalArgumentException.class, () -> bar.addFilter("", "Empty"));
        assertThrows(IllegalArgumentException.class, () -> bar.addFilter("status", "Status"));
        bar.addFilter("status", "Status", "Online", "Away");
        assertThrows(IllegalArgumentException.class,
                () -> bar.addFilter("status", "Status", "Online"));
        assertThrows(IllegalArgumentException.class, () -> bar.value("missing"));
        assertThrows(IllegalArgumentException.class, () -> bar.setValue("status", "Missing"));
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
