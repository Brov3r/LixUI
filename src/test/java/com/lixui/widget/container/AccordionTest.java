package com.lixui.widget.container;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.ImageData;
import com.lixui.render.Texture;
import com.lixui.render.command.ImageCommand;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.FixedWidget;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccordionTest {
    private static final float HEADER_HEIGHT = 32;

    @Test
    void expandsAndCollapsesWithAnimation() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Accordion accordion = accordion();
            FixedWidget content = new FixedWidget(120, 40);
            Accordion.Section section = accordion.addSection("Section", content);
            manager.add(accordion);
            manager.render();

            assertFalse(section.isExpanded());
            assertEquals(0, section.expandedAmount(), 0.001f);
            assertFalse(content.isVisible());

            click(manager, accordion.headerBounds(section));
            assertTrue(section.isExpanded());
            assertTrue(content.isVisible());
            assertEquals(0, section.expandedAmount(), 0.001f);

            manager.update(0.1f);
            float middle = section.expandedAmount();
            assertTrue(middle > 0 && middle < 1);
            manager.render();
            assertTrue(content.bounds().y() >= HEADER_HEIGHT);

            manager.update(0.2f);
            assertEquals(1, section.expandedAmount(), 0.001f);

            click(manager, accordion.headerBounds(section));
            assertFalse(section.isExpanded());
            manager.update(0.1f);
            assertTrue(section.expandedAmount() > 0 && section.expandedAmount() < 1);
            manager.update(0.2f);
            assertEquals(0, section.expandedAmount(), 0.001f);
            assertFalse(content.isVisible());
        }
    }

    @Test
    void exclusiveModeKeepsOnlyOneSectionOpen() {
        Accordion accordion = new Accordion();
        Accordion.Section first = accordion.addSection("First", null,
                new FixedWidget(10, 10), true);
        Accordion.Section second = accordion.addSection("Second", null,
                new FixedWidget(10, 10), true);

        accordion.setExclusive(true);
        assertFalse(first.isExpanded());
        assertTrue(second.isExpanded());
        assertEquals(List.of(second), accordion.expandedSections());

        assertTrue(accordion.expand(first));
        assertTrue(first.isExpanded());
        assertFalse(second.isExpanded());

        accordion.setExclusive(false);
        assertTrue(accordion.expand(second));
        assertEquals(List.of(first, second), accordion.expandedSections());
        assertFalse(accordion.expand(first));
    }

    @Test
    void disabledHeaderIgnoresClicksButAllowsProgrammaticChanges() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Accordion accordion = accordion();
            Accordion.Section section = accordion.addSection("Disabled", new FixedWidget(10, 10));
            section.setEnabled(false);
            manager.add(accordion);
            manager.render();

            click(manager, accordion.headerBounds(section));
            assertFalse(section.isExpanded());

            assertTrue(accordion.expand(section));
            assertTrue(section.isExpanded());
        }
    }

    @Test
    void notifiesExpansionChangesAndDetachesRemovedContent() {
        Accordion accordion = new Accordion();
        Widget content = new FixedWidget(10, 10);
        Accordion.Section section = accordion.addSection("Section", content);
        List<Accordion.Section> changes = new ArrayList<>();
        accordion.setOnExpandedChange(changes::add);

        accordion.expand(section);
        accordion.collapse(section);
        assertEquals(List.of(section, section), changes);

        assertTrue(accordion.removeSection(section));
        assertNull(content.parent());
        assertTrue(accordion.sections().isEmpty());
        assertFalse(accordion.removeSection(section));
    }

    @Test
    void drawsTitleIconAndRotatesTheExpansionArrow() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Accordion accordion = accordion();
            Texture icon = manager.context().createTexture(new ImageData(2, 2, new byte[16]));
            Accordion.Section section = accordion.addSection("Icons", icon,
                    new FixedWidget(10, 10));
            manager.add(accordion);
            manager.render();

            ImageCommand closed = arrowCommand(backend);
            assertEquals(1, closed.transform().m00(), 0.001f);
            assertEquals(0, closed.transform().m01(), 0.001f);

            click(manager, accordion.headerBounds(section));
            manager.update(0.1f);
            manager.render();
            ImageCommand turning = arrowCommand(backend);
            assertTrue(turning.transform().m00() > 0.5f);
            assertTrue(turning.transform().m00() < 0.9f);

            manager.update(0.2f);
            manager.render();
            ImageCommand open = arrowCommand(backend);
            assertEquals(0, open.transform().m00(), 0.001f);
            assertEquals(-1, open.transform().m01(), 0.001f);
            assertSame(accordion, section.content().parent());
        }
    }

    @Test
    void rejectsInvalidArgumentsAndSettings() {
        Accordion accordion = new Accordion();
        assertThrows(NullPointerException.class, () -> accordion.addSection(null, null));
        assertThrows(NullPointerException.class, () -> accordion.removeSection(null));
        assertThrows(NullPointerException.class, () -> accordion.expand(null));
        assertThrows(NullPointerException.class, () -> accordion.collapse(null));
        assertThrows(NullPointerException.class, () -> accordion.toggle(null));
        assertThrows(NullPointerException.class, () -> accordion.indexOf(null));
        assertThrows(IllegalArgumentException.class, () -> accordion.setHeaderHeight(0));
        assertThrows(IllegalArgumentException.class, () -> accordion.setHeaderPadding(-1));
        assertThrows(IllegalArgumentException.class, () -> accordion.setContentPadding(-1));
        assertThrows(IllegalArgumentException.class, () -> accordion.setIconSize(0));
        assertThrows(IllegalArgumentException.class, () -> accordion.setIconGap(-1));
        assertThrows(IllegalArgumentException.class, () -> accordion.setExpansionDuration(-1));
        assertThrows(NullPointerException.class, () -> accordion.setIconStyle(null));
        assertThrows(NullPointerException.class, () -> accordion.setExpansionEasing(null));

        Accordion.Section section = accordion.addSection("Section", null);
        Accordion other = new Accordion();
        Accordion.Section foreign = other.addSection("Other", null);
        assertThrows(IllegalArgumentException.class, () -> accordion.expand(foreign));
        assertThrows(IllegalArgumentException.class, () -> other.collapse(section));
        assertThrows(NullPointerException.class, () -> section.setTitle(null));
    }

    private static Accordion accordion() {
        Accordion accordion = new Accordion();
        accordion.setBounds(new Rect(0, 0, 400, 300));
        return accordion;
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(TestViewports.LOGICAL);
        return manager;
    }

    private static ImageCommand arrowCommand(TestBackend backend) {
        return backend.lastDrawList().commands().stream()
                .filter(ImageCommand.class::isInstance)
                .map(ImageCommand.class::cast)
                .filter(command -> command.texture().width() == 16)
                .findFirst().orElseThrow();
    }

    private static void click(WidgetManager manager, Rect bounds) {
        float x = bounds.x() + bounds.width() / 2;
        float y = bounds.y() + bounds.height() / 2;
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }
}
