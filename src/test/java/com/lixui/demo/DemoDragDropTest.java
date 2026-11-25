package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.widget.DropZone;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.Label;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoDragDropTest {
    private static final String QUEST = "quest";
    private static final String LOOT = "loot";
    private static final String CHIP = "chip";

    @Test
    void draggingAQuestIntoItsZoneUpdatesTheStatusAndTheZoneLabel() {
        Harness harness = new Harness();

        harness.drag(harness.center(harness.button("Quest: Slay the dragon")),
                harness.center(harness.zone(QUEST)));

        assertTrue(harness.status.text().contains("Quest board accepted \"Slay the dragon\""));
        Label zoneLabel = (Label) harness.zone(QUEST).children().get(0);
        assertEquals("Received: Slay the dragon", zoneLabel.text());
        assertNull(harness.manager.context().dragSession());
    }

    @Test
    void aQuestCardCannotEnterTheLootZone() {
        Harness harness = new Harness();
        String before = harness.status.text();
        String lootBefore = ((Label) harness.zone(LOOT).children().get(0)).text();

        harness.drag(harness.center(harness.button("Quest: Slay the dragon")),
                harness.center(harness.zone(LOOT)));

        assertEquals(before, harness.status.text());
        assertEquals(lootBefore, ((Label) harness.zone(LOOT).children().get(0)).text());
        assertNull(harness.manager.context().dragSession());
    }

    @Test
    void draggingAChipToTheOtherSlotSwapsTheChips() {
        Harness harness = new Harness();
        Button ruby = harness.button("Ruby");
        Button sapphire = harness.button("Sapphire");
        DropZone first = harness.zone(CHIP, 0);
        DropZone second = harness.zone(CHIP, 1);

        assertSame(first, ruby.parent());
        assertSame(second, sapphire.parent());

        harness.drag(harness.center(ruby), harness.center(second));

        assertNull(harness.manager.context().dragSession());
        assertEquals(1, first.children().size());
        assertEquals(1, second.children().size());
        assertSame(sapphire, first.children().get(0));
        assertSame(ruby, second.children().get(0));
        assertTrue(harness.status.text().contains("Swapped \"Ruby\""));
    }

    @Test
    void droppingAChipOntoItsOwnSlotDoesNotDuplicateIt() {
        Harness harness = new Harness();
        DropZone first = harness.zone(CHIP, 0);
        Button ruby = harness.button("Ruby");

        harness.drag(harness.center(ruby), harness.center(first));

        assertEquals(1, first.children().size());
        assertSame(ruby, first.children().get(0));
        assertEquals(1, harness.zone(CHIP, 1).children().size());
        assertNull(harness.manager.context().dragSession());
    }

    @Test
    void draggingOutsideEveryZoneChangesNothing() {
        Harness harness = new Harness();
        String before = harness.status.text();

        harness.drag(harness.center(harness.button("Ruby")), 700, 500);

        assertEquals(before, harness.status.text());
        assertNull(harness.manager.context().dragSession());
        assertEquals(1, harness.zone(CHIP, 0).children().size());
        assertEquals(1, harness.zone(CHIP, 1).children().size());
    }

    @Test
    void pressingAQuestWithoutMovingDoesNotStartADragOrDrop() {
        Harness harness = new Harness();
        Button quest = harness.button("Quest: Slay the dragon");
        var point = harness.center(quest);
        String before = harness.status.text();

        harness.manager.handle(new PointerPressed(point.x(), point.y(), MouseButton.LEFT,
                Modifiers.NONE));
        harness.manager.handle(new PointerMoved(point.x() + 2, point.y()));
        assertFalse(harness.manager.context().isDragging());

        harness.manager.handle(new PointerReleased(point.x(), point.y(), MouseButton.LEFT,
                Modifiers.NONE));

        assertEquals(before, harness.status.text());
        assertNull(harness.manager.context().dragSession());
    }

    private static final class Harness {
        private final WidgetManager manager;
        private final Widget stage;
        private final Label status = new Label("");
        private final List<Widget> widgets = new ArrayList<>();

        private Harness() {
            manager = new WidgetManager(new InputState(), new TestBackend());
            manager.resize(TestViewports.LOGICAL);
            stage = DemoDragDrop.create(status);
            stage.setBounds(new Rect(0, 0, 540, 230));
            manager.add(stage);
            manager.layout();
            collect(stage);
        }

        private void drag(com.lixui.geometry.Point from, com.lixui.geometry.Point to) {
            manager.handle(new PointerPressed(from.x(), from.y(), MouseButton.LEFT,
                    Modifiers.NONE));
            manager.handle(new PointerMoved(from.x() + 10, from.y()));
            manager.handle(new PointerMoved(to.x(), to.y()));
            assertTrue(manager.context().isDragging());
            manager.handle(new PointerReleased(to.x(), to.y(), MouseButton.LEFT, Modifiers.NONE));
        }

        private void drag(com.lixui.geometry.Point from, float toX, float toY) {
            manager.handle(new PointerPressed(from.x(), from.y(), MouseButton.LEFT,
                    Modifiers.NONE));
            manager.handle(new PointerMoved(from.x() + 10, from.y()));
            manager.handle(new PointerMoved(toX, toY));
            manager.handle(new PointerReleased(toX, toY, MouseButton.LEFT, Modifiers.NONE));
        }

        private Button button(String text) {
            return widgets.stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(button -> text.equals(button.text()))
                    .findFirst()
                    .orElseThrow();
        }

        private DropZone zone(String kind) {
            return zone(kind, 0);
        }

        private DropZone zone(String kind, int index) {
            List<DropZone> zones = widgets.stream()
                    .filter(DropZone.class::isInstance)
                    .map(DropZone.class::cast)
                    .filter(zone -> kind.equals(zone.acceptedKind()))
                    .toList();
            return zones.get(index);
        }

        private com.lixui.geometry.Point center(Widget widget) {
            com.lixui.geometry.Point origin = widget.absolutePosition();
            return new com.lixui.geometry.Point(origin.x() + widget.bounds().width() / 2,
                    origin.y() + widget.bounds().height() / 2);
        }

        private void collect(Widget widget) {
            widgets.add(widget);
            for (Widget child : widget.children()) {
                collect(child);
            }
        }
    }
}
