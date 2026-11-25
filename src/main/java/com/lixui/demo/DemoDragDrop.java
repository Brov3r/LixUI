package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.text.VerticalAlignment;
import com.lixui.widget.DropZone;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.Label;
import com.lixui.widget.layout.Flow;
import com.lixui.widget.layout.VBox;

/**
 * Builds the compact drag-and-drop samples shown on the Interactive demo page.
 * The samples cover a kind-routed drop and a widget swap between two drop zones.
 */
final class DemoDragDrop {
    private static final String QUEST_KIND = "quest";
    private static final String LOOT_KIND = "loot";
    private static final String CHIP_KIND = "chip";
    private static final float ROW_GAP = 8;
    private static final float ZONE_PADDING = 10;
    private static final float CARD_ZONE_MIN_WIDTH = 200;
    private static final float CARD_ZONE_MIN_HEIGHT = 56;
    private static final float CHIP_ZONE_MIN_WIDTH = 112;
    private static final float CHIP_ZONE_MIN_HEIGHT = 50;

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
     * @return the stage that stacks both drag-and-drop samples
     */
    static Widget create(Label status) {
        VBox stage = new VBox();
        stage.setGap(ROW_GAP);

        Label kindTitle = title("Kind routing: cards only enter their matching zone");
        DragCard quest = new DragCard("Quest: Slay the dragon", QUEST_KIND, "Slay the dragon",
                "Dragging a quest");
        DragCard loot = new DragCard("Loot: Silver key", LOOT_KIND, "Silver key",
                "Dragging loot");
        Flow cardRow = row();
        cardRow.add(quest).add(loot);

        DropArea questZone = new DropArea(QUEST_KIND, "Drop a quest card here",
                CARD_ZONE_MIN_WIDTH, CARD_ZONE_MIN_HEIGHT);
        DropArea lootZone = new DropArea(LOOT_KIND, "Drop a loot card here",
                CARD_ZONE_MIN_WIDTH, CARD_ZONE_MIN_HEIGHT);
        questZone.setOnDrop((source, kind, payload) -> {
            questZone.hint().setText("Received: " + payload);
            status.setText("Quest board accepted \"" + payload + "\".");
            return true;
        });
        lootZone.setOnDrop((source, kind, payload) -> {
            lootZone.hint().setText("Received: " + payload);
            status.setText("Stash accepted \"" + payload + "\".");
            return true;
        });
        Flow zoneRow = row();
        zoneRow.add(questZone).add(lootZone);

        Label swapTitle = title("Swap chips: any chip is accepted by both slots");
        DropArea firstSlot = new DropArea(CHIP_KIND, null,
                CHIP_ZONE_MIN_WIDTH, CHIP_ZONE_MIN_HEIGHT);
        DropArea secondSlot = new DropArea(CHIP_KIND, null,
                CHIP_ZONE_MIN_WIDTH, CHIP_ZONE_MIN_HEIGHT);
        DragCard ruby = chip("Ruby");
        DragCard sapphire = chip("Sapphire");
        firstSlot.add(ruby);
        secondSlot.add(sapphire);
        firstSlot.setOnDrop((source, kind, payload) -> swap(firstSlot, payload, status));
        secondSlot.setOnDrop((source, kind, payload) -> swap(secondSlot, payload, status));

        Label swapHint = new Label("Drag a chip from one slot onto the other to swap their places.");
        swapHint.setWrap(true);
        Flow chipRow = row();
        chipRow.add(firstSlot).add(secondSlot).add(swapHint);

        stage.add(kindTitle).add(cardRow).add(zoneRow).add(swapTitle).add(chipRow);
        status.setText("Drag a card onto a matching zone, or swap the two chips.");
        return stage;
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.setWrap(true);
        return label;
    }

    private static Flow row() {
        Flow row = new Flow();
        row.setGap(ROW_GAP);
        return row;
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
        }
        target.add(chip);
        relayout(sourceZone);
        relayout(target);
        status.setText("Swapped \"" + chipText(chip) + "\" with the other slot.");
        return true;
    }

    private static void relayout(DropZone zone) {
        zone.layout(zone.bounds());
    }

    private static String chipText(Widget chip) {
        return chip instanceof Button button ? button.text() : "chip";
    }

    private static DragCard chip(String text) {
        DragCard chip = new DragCard(text, CHIP_KIND, null, "Dragging " + text);
        chip.setDragPayload(chip);
        return chip;
    }

    /**
     * A drop zone that measures a placeholder and pads an optional dropped widget inside its bounds.
     */
    private static final class DropArea extends DropZone {
        private final Label hint;
        private final float minimumWidth;
        private final float minimumHeight;

        private DropArea(String kind, String hintText, float minimumWidth, float minimumHeight) {
            super(kind);
            this.minimumWidth = minimumWidth;
            this.minimumHeight = minimumHeight;
            if (hintText == null) {
                hint = null;
            } else {
                hint = new Label(hintText);
                hint.setAutoSize(false);
                hint.setWrap(true);
                hint.setVerticalAlignment(VerticalAlignment.CENTER);
                add(hint);
            }
        }

        private Label hint() {
            return hint;
        }

        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            float availableWidth = Float.isFinite(constraints.maxWidth())
                    ? Math.max(0, constraints.maxWidth()) : minimumWidth;
            float innerLimit = Math.max(0, availableWidth - ZONE_PADDING * 2);
            Size content = measureContent(innerLimit);
            float width = Math.min(availableWidth,
                    Math.max(minimumWidth, content.width() + ZONE_PADDING * 2));
            Size wrapped = measureContent(Math.max(0, width - ZONE_PADDING * 2));
            float height = Math.max(minimumHeight, wrapped.height() + ZONE_PADDING * 2);
            return new Size(width, height);
        }

        private Size measureContent(float availableWidth) {
            Size content = new Size(0, 0);
            if (hint != null) {
                content = hint.measure(LayoutConstraints.loose(availableWidth, Float.MAX_VALUE));
            }
            for (Widget child : children()) {
                if (child == hint) {
                    continue;
                }
                Size size = child.measure(LayoutConstraints.loose(availableWidth, Float.MAX_VALUE));
                content = new Size(Math.max(content.width(), size.width()),
                        Math.max(content.height(), size.height()));
            }
            return content;
        }

        @Override
        protected void onLayout() {
            float innerWidth = Math.max(0, bounds().width() - ZONE_PADDING * 2);
            float innerHeight = Math.max(0, bounds().height() - ZONE_PADDING * 2);
            for (Widget child : children()) {
                if (child == hint) {
                    child.layout(new Rect(ZONE_PADDING, ZONE_PADDING, innerWidth, innerHeight));
                    continue;
                }
                Size natural = child.measure(
                        LayoutConstraints.loose(innerWidth, innerHeight));
                float width = Math.min(natural.width(), innerWidth);
                float height = Math.min(natural.height(), innerHeight);
                child.layout(new Rect(
                        ZONE_PADDING + (innerWidth - width) / 2,
                        ZONE_PADDING + (innerHeight - height) / 2,
                        width, height));
            }
        }
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
