package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;
import com.lixui.widget.DropZone;
import com.lixui.widget.Panel;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.Label;

/**
 * Builds the compact drag-and-drop samples shown on the Interactive demo page.
 * The samples cover a kind-routed drop and a widget swap between two drop zones.
 */
final class DemoDragDrop {
    private static final String QUEST_KIND = "quest";
    private static final String LOOT_KIND = "loot";
    private static final String CHIP_KIND = "chip";
    private static final float STAGE_WIDTH = 540;
    private static final float STAGE_HEIGHT = 230;

    /**
     * Prevents instantiation of the static demo helper.
     *
     * @throws UnsupportedOperationException always
     */
    private DemoDragDrop() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    /**
     * Creates the drag-and-drop stage.
     *
     * @param status label updated after drops and swaps
     * @return a panel containing both drag-and-drop samples
     */
    static Widget create(Label status) {
        Panel stage = new Panel();
        stage.setMinimumSize(STAGE_WIDTH, STAGE_HEIGHT);

        Label kindTitle = label("Kind routing: cards only enter their matching zone", 12, 10, 516, 16);
        DragCard quest = new DragCard("Quest: Slay the dragon", QUEST_KIND, "Slay the dragon",
                "Dragging a quest");
        quest.setBounds(new Rect(12, 34, 170, 30));
        DragCard loot = new DragCard("Loot: Silver key", LOOT_KIND, "Silver key",
                "Dragging loot");
        loot.setBounds(new Rect(192, 34, 150, 30));

        Label questText = label("Drop a quest card here", 10, 8, 230, 44);
        Label lootText = label("Drop a loot card here", 10, 8, 230, 44);
        DropZone questZone = zone(QUEST_KIND, new Rect(12, 72, 250, 60), questText);
        DropZone lootZone = zone(LOOT_KIND, new Rect(272, 72, 250, 60), lootText);
        questZone.setOnDrop((source, kind, payload) -> {
            questText.setText("Received: " + payload);
            status.setText("Quest board accepted \"" + payload + "\".");
            return true;
        });
        lootZone.setOnDrop((source, kind, payload) -> {
            lootText.setText("Received: " + payload);
            status.setText("Stash accepted \"" + payload + "\".");
            return true;
        });

        Label swapTitle = label("Swap chips: any chip is accepted by both slots", 12, 144, 516, 16);
        DropZone firstSlot = zone(CHIP_KIND, new Rect(12, 168, 112, 50), null);
        DropZone secondSlot = zone(CHIP_KIND, new Rect(136, 168, 112, 50), null);
        DragCard ruby = chip("Ruby");
        DragCard sapphire = chip("Sapphire");
        ruby.setBounds(chipBounds());
        sapphire.setBounds(chipBounds());
        firstSlot.add(ruby);
        secondSlot.add(sapphire);
        firstSlot.setOnDrop((source, kind, payload) -> swap(firstSlot, payload, status));
        secondSlot.setOnDrop((source, kind, payload) -> swap(secondSlot, payload, status));

        Label swapHint = label("Drag a chip from one slot onto the other to swap their places.",
                272, 168, 250, 50);
        swapHint.setVerticalAlignment(VerticalAlignment.CENTER);

        stage.add(kindTitle).add(quest).add(loot)
                .add(questZone).add(lootZone)
                .add(swapTitle).add(firstSlot).add(secondSlot).add(swapHint);
        status.setText("Drag a card onto a matching zone, or swap the two chips.");
        return stage;
    }

    private static boolean swap(DropZone target, Object payload, Label status) {
        if (!(payload instanceof Widget chip)) {
            return false;
        }
        Widget origin = chip.parent();
        if (!(origin instanceof DropZone sourceZone) || origin == target) {
            return true;
        }
        Widget occupant = target.children().isEmpty() ? null : target.children().get(0);
        sourceZone.remove(chip);
        if (occupant != null) {
            target.remove(occupant);
            sourceZone.add(occupant);
            occupant.setBounds(chipBounds());
        }
        target.add(chip);
        chip.setBounds(chipBounds());
        status.setText("Swapped \"" + chipText(chip) + "\" with the other slot.");
        return true;
    }

    private static String chipText(Widget chip) {
        return chip instanceof Button button ? button.text() : "chip";
    }

    private static Rect chipBounds() {
        return new Rect(8, 8, 96, 34);
    }

    private static DragCard chip(String text) {
        DragCard chip = new DragCard(text, CHIP_KIND, null, "Dragging " + text);
        chip.setDragPayload(chip);
        return chip;
    }

    private static DropZone zone(String kind, Rect bounds, Label text) {
        DropZone zone = new DropZone(kind);
        zone.setBounds(bounds);
        if (text != null) {
            zone.add(text);
        }
        return zone;
    }

    private static Label label(String text, float x, float y, float width, float height) {
        Label label = new Label(text);
        label.setAutoSize(false);
        label.setBounds(new Rect(x, y, width, height));
        label.setAlignment(HorizontalAlignment.LEFT);
        label.setVerticalAlignment(VerticalAlignment.TOP);
        return label;
    }

    private static final class DragCard extends Button {
        private final String kind;
        private Object dragPayload;
        private final String preview;

        private DragCard(String text, String kind, Object payload, String preview) {
            super(text);
            this.kind = kind;
            this.dragPayload = payload;
            this.preview = preview;
        }

        private void setDragPayload(Object dragPayload) {
            this.dragPayload = dragPayload;
        }

        @Override
        protected boolean onDragStart(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button != MouseButton.LEFT) {
                return false;
            }
            context().startDrag(this, button, kind, dragPayload, preview);
            return true;
        }
    }
}
