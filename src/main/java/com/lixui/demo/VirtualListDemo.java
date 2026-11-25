package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.FontFamily;
import com.lixui.text.FontStyle;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Label;
import com.lixui.widget.container.SplitPanel;
import com.lixui.widget.container.VirtualList;
import com.lixui.widget.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the virtual list demos shown on the Virtual list page of the Data tab.
 */
final class VirtualListDemo {
    private static final int FEED_SIZE = 800;
    private static final int QUEST_SIZE = 240;
    private static final int SUMMARY_CHARS_PER_LINE = 40;
    private static final Color WARNING = Color.hex("#E39A5B");
    private static final Color DANGER = Color.hex("#E06C75");
    private static final Color DEBUG = Color.hex("#C39BF2");

    private static final String[] FEED_MESSAGES = {
            "Movement packet acknowledged by the shard",
            "Party invite delivered to Aria",
            "Quest state synchronized with the server",
            "Inventory snapshot stored",
            "Latency spike above the warning threshold",
            "Scene transition completed",
            "Loot table rolled for the encounter",
            "Chat channel subscription renewed",
            "Authentication token refreshed",
            "Region heartbeat received",
            "Asset bundle validated",
            "Player position corrected by the server"
    };

    private static final String[] FEED_LEVELS = {"INFO", "INFO", "DEBUG", "INFO", "WARN",
            "INFO", "DEBUG", "INFO", "INFO", "DEBUG", "INFO", "ERROR"};

    private static final String[] QUEST_TITLES = {
            "The Ashen Gate",
            "Whispers in the Crypt",
            "A Debt Unpaid",
            "The Lost Caravan",
            "Embers of the Old Forge",
            "Tides of the Hollow Coast",
            "The Silent Bell",
            "Roots Beneath the City",
            "A Letter for the Captain",
            "The Glass Orchard"
    };

    private static final String[] QUEST_SUMMARIES = {
            "Travel north and inspect the sealed gate. Report any runes that glow after sunset.",
            "The crypt keeper has not returned. Search the lower halls and recover his lantern.",
            "A merchant in the lower market needs protection while collecting an old debt.",
            "Follow the wheel ruts past the mill and find out why the caravan never reached town.",
            "The forge master will trade her best steel for three chunks of ember ore.",
            "Small boats vanish along the coast at night. Question the lighthouse keeper.",
            "Villagers hear a bell under the water. Find the tower it belongs to.",
            "The sewer tunnels are older than the city. Map the collapsed chamber beneath the plaza.",
            "Deliver the sealed letter to the harbor captain before the evening tide.",
            "The orchard grows glass fruit that never ripens. Learn who planted the first tree."
    };

    private VirtualListDemo() {
    }

    /**
     * Creates the page with the fixed-height feed and the variable-height quest journal.
     *
     * @param fontFamily demo font family, or null when no system font is available
     * @return the virtual list demo page
     */
    static Widget create(FontFamily fontFamily) {
        SplitPanel split = new SplitPanel();
        split.setDividerThickness(8);
        split.setDividerPosition(0.5f);
        split.setMinimumPaneSize(150);
        split.setGrow(1);
        split.setFirst(createFeedPane(fontFamily));
        split.setSecond(createQuestPane(fontFamily));

        VBox page = new VBox();
        page.setPadding(8);
        page.add(split);
        return page;
    }

    private static Widget createFeedPane(FontFamily fontFamily) {
        List<FeedEntry> entries = new ArrayList<>(FEED_SIZE);
        for (int index = 0; index < FEED_SIZE; index++) {
            int seconds = 7 + index * 3;
            String time = String.format(Locale.ROOT, "%02d:%02d:%02d",
                    (9 + seconds / 3600) % 24, seconds / 60 % 60, seconds % 60);
            String level = FEED_LEVELS[index % FEED_LEVELS.length];
            String message = FEED_MESSAGES[index % FEED_MESSAGES.length] + " #" + (index + 1);
            entries.add(new FeedEntry(time, level, message));
        }

        Label status = label(fontFamily, "No entry selected.");
        status.setFontStyle(FontStyle.ITALIC);

        VirtualList list = new VirtualList();
        list.setSelectionMode(VirtualList.SelectionMode.SINGLE);
        list.setRowHeight(32);
        list.setItemCount(entries.size());
        list.setSelectionListener(selection -> status.setText(selection.isEmpty()
                ? "No entry selected."
                : "Selected " + entries.get(selection.get(0)).time() + "  "
                + entries.get(selection.get(0)).level() + "  "
                + entries.get(selection.get(0)).message()));
        list.setRowRenderer((index, bounds, state, renderer) ->
                drawFeedRow(fontFamily, list, entries.get(index), bounds, renderer));
        list.setGrow(1);

        VBox pane = new VBox();
        pane.setPadding(8);
        pane.setGap(6);
        pane.add(caption(fontFamily, "Activity feed - fixed 32 px rows, single selection"))
                .add(label(fontFamily, "Wheel, arrow keys, Home/End and click select. "
                        + "Only visible rows are rendered."))
                .add(list)
                .add(status);
        return pane;
    }

    private static void drawFeedRow(FontFamily fontFamily, VirtualList list, FeedEntry entry,
                                    Rect bounds, RenderManager renderer) {
        Theme theme = list.context().theme();
        Font regular = font(fontFamily, list, FontStyle.REGULAR);
        Font bold = font(fontFamily, list, FontStyle.BOLD);
        float center = bounds.y() + bounds.height() / 2;
        renderer.roundedRect(new Rect(bounds.x() + 10, center - 3, 6, 6), 3,
                levelColor(entry.level(), theme));
        if (regular == null) {
            return;
        }
        renderer.text(regular, entry.time(), bounds.x() + 24, center, theme.textMuted(),
                HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        if (bold != null) {
            renderer.text(bold, entry.level(), bounds.x() + 78, center,
                    levelColor(entry.level(), theme),
                    HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        }
        float messageX = bounds.x() + 130;
        float messageWidth = Math.max(0, bounds.right() - messageX - 12);
        if (messageWidth > 0) {
            renderer.text(regular, entry.message(), messageX, center, theme.text(),
                    new TextStyle(messageWidth, 0, 1, TextOverflow.ELLIPSIS,
                            HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
        }
    }

    private static Widget createQuestPane(FontFamily fontFamily) {
        List<QuestEntry> quests = new ArrayList<>(QUEST_SIZE);
        for (int index = 0; index < QUEST_SIZE; index++) {
            String title = QUEST_TITLES[index % QUEST_TITLES.length] + " - part " + (index + 1);
            String summary = QUEST_SUMMARIES[(index * 3) % QUEST_SUMMARIES.length];
            quests.add(new QuestEntry(title, summary));
        }

        Label status = label(fontFamily, "No quest selected.");
        status.setFontStyle(FontStyle.ITALIC);

        VirtualList list = new VirtualList();
        list.setSelectionMode(VirtualList.SelectionMode.MULTIPLE);
        list.setRowHeightProvider(index -> questHeight(quests.get(index)));
        list.setItemCount(quests.size());
        list.setSelectionListener(selection -> status.setText(describeQuests(quests, selection)));
        list.setRowRenderer((index, bounds, state, renderer) ->
                drawQuestRow(fontFamily, list, quests.get(index), bounds, renderer));
        list.setGrow(1);

        VBox pane = new VBox();
        pane.setPadding(8);
        pane.setGap(6);
        pane.add(caption(fontFamily, "Quest journal - variable heights with prefix sums"))
                .add(label(fontFamily, "Ctrl toggles a row, Shift selects a range and Space "
                        + "toggles the active row. Wheel and Page Up/Page Down scroll."))
                .add(list)
                .add(status);
        return pane;
    }

    private static void drawQuestRow(FontFamily fontFamily, VirtualList list, QuestEntry quest,
                                     Rect bounds, RenderManager renderer) {
        Theme theme = list.context().theme();
        Font regular = font(fontFamily, list, FontStyle.REGULAR);
        Font bold = font(fontFamily, list, FontStyle.BOLD);
        float x = bounds.x() + 14;
        float width = Math.max(0, bounds.width() - 28);
        if (bold != null) {
            renderer.text(bold, quest.title(), x, bounds.y() + 9, theme.text(),
                    new TextStyle(width, 0, 1, TextOverflow.ELLIPSIS,
                            HorizontalAlignment.LEFT, VerticalAlignment.TOP));
        }
        if (regular != null && width > 0) {
            renderer.text(regular, quest.summary(), x, bounds.y() + 32, theme.textMuted(),
                    new TextStyle(width, 0, 2, TextOverflow.ELLIPSIS,
                            HorizontalAlignment.LEFT, VerticalAlignment.TOP));
        }
        renderer.rect(new Rect(x, bounds.bottom() - 1, width, 1),
                theme.border().withAlpha(0.45f));
    }

    private static float questHeight(QuestEntry quest) {
        int lines = Math.min(2, Math.max(1,
                (quest.summary().length() + SUMMARY_CHARS_PER_LINE - 1) / SUMMARY_CHARS_PER_LINE));
        return 34 + lines * 18;
    }

    private static String describeQuests(List<QuestEntry> quests, List<Integer> selection) {
        if (selection.isEmpty()) {
            return "No quest selected.";
        }
        if (selection.size() == 1) {
            return "Selected: " + quests.get(selection.get(0)).title();
        }
        String first = quests.get(selection.get(0)).title();
        String last = quests.get(selection.get(selection.size() - 1)).title();
        return "Selected " + selection.size() + " quests: " + first + " ... " + last;
    }

    private static Color levelColor(String level, Theme theme) {
        return switch (level) {
            case "WARN" -> WARNING;
            case "ERROR" -> DANGER;
            case "DEBUG" -> DEBUG;
            default -> theme.primary();
        };
    }

    private static Font font(FontFamily family, VirtualList list, FontStyle style) {
        return family == null ? list.font() : family.resolve(style);
    }

    private static Label caption(FontFamily family, String text) {
        Label label = label(family, text);
        label.setFontStyle(FontStyle.BOLD);
        return label;
    }

    private static Label label(FontFamily family, String text) {
        Label label = new Label(text);
        label.setFontFamily(family);
        label.setAutoSize(false);
        label.setWrap(true);
        return label;
    }

    private record FeedEntry(String time, String level, String message) {
    }

    private record QuestEntry(String title, String summary) {
    }
}
