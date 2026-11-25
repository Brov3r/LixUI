package com.lixui.demo;

import com.lixui.graphics.Color;
import com.lixui.text.FontFamily;
import com.lixui.text.FontStyle;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Label;
import com.lixui.widget.container.TabPanel;
import com.lixui.widget.container.Timeline;
import com.lixui.widget.layout.VBox;

/**
 * Builds the timeline demo shown on the Timeline page of the Data tab.
 */
final class TimelineDemo {
    private static final Color BLUE = Color.hex("#7EA6F5");
    private static final Color GREEN = Color.hex("#86C46A");
    private static final Color ORANGE = Color.hex("#E39A5B");
    private static final Color RED = Color.hex("#E06C75");
    private static final Color PURPLE = Color.hex("#C39BF2");

    private static final String[] RELEASE_TOPICS = {
            "Widget pass", "Virtual scrolling", "Tree navigation", "Theme refresh",
            "Input polish", "Layout fixes"
    };
    private static final String[] RELEASE_NOTES = {
            "Virtual lists now render only visible rows, so very large data sets stay smooth.",
            "The tree view animates branch heights and keeps keyboard movement inside open branches.",
            "Timelines wrap variable-height text and share one rail with colored markers.",
            "Small fixes for focus traversal, hover feedback and scrollbar dragging.",
            "New demo tabs cover virtual lists, tree navigation and timeline variations.",
            "Build scripts and documentation were updated for the current toolchain."
    };
    private static final String[] SERVICES = {"Gateway", "Matchmaker", "Inventory", "Chat", "Auth"};
    private static final String[] INCIDENT_TEXTS = {
            "p95 latency exceeded the warning threshold for two minutes.",
            "A regional node dropped its heartbeat and recovered automatically.",
            "Queue depth grew while the consumer restarted.",
            "Database failover completed without client-visible errors.",
            "Rate limiting rejected a burst of reconnects."
    };
    private static final String[] QUEST_TEXTS = {
            "The path leads through the old orchard. Every tree carries glass fruit that chimes "
                    + "when the wind changes, and the keeper insists that one of them remembers "
                    + "the name of the missing caravan.",
            "Search the flooded archive beneath the lighthouse. The lower shelves are still "
                    + "readable, but the tide returns before noon and the door will not open twice.",
            "A courier waits by the north gate with a sealed letter. Deliver it before the evening "
                    + "bell, and do not break the wax seal even if the road is quiet.",
            "The forge has been cold for three days. Find the apprentice, recover the ember ore and "
                    + "convince the master to light the coals before the frost arrives.",
            "Follow the marked stones through the hollow coast. The last stone is missing, and the "
                    + "fishermen say the tide took it along with the bell tower."
    };

    private TimelineDemo() {
    }

    /**
     * Creates the timeline variants.
     *
     * @param fontFamily demo font family, or null when no system font is available
     * @return a tab panel with the release, incident and quest timelines
     */
    static Widget create(FontFamily fontFamily) {
        TabPanel tabs = new TabPanel();
        tabs.addTab("Releases", createReleasePane(fontFamily));
        tabs.addTab("Incidents", createIncidentPane(fontFamily));
        tabs.addTab("Quests", createQuestPane(fontFamily));
        return tabs;
    }

    private static Widget createReleasePane(FontFamily fontFamily) {
        Timeline timeline = new Timeline();
        timeline.setGrow(1);
        for (int index = 0; index < 36; index++) {
            String topic = RELEASE_TOPICS[index % RELEASE_TOPICS.length];
            String note = RELEASE_NOTES[index % RELEASE_NOTES.length];
            String title = "v2." + (36 - index) + ".0 - " + topic;
            String text = index % 3 == 0
                    ? note + " " + note : note;
            Color color = switch (index % 3) {
                case 0 -> GREEN;
                case 1 -> BLUE;
                default -> ORANGE;
            };
            timeline.addEntry(title, text, "2026-10-" + twoDigits(index + 1), color);
        }
        return pane(fontFamily, "Release history - mixed text lengths, one rail",
                "Wheel and arrow keys scroll; click a row to select it.", timeline);
    }

    private static Widget createIncidentPane(FontFamily fontFamily) {
        Timeline timeline = new Timeline();
        timeline.setGrow(1);
        for (int index = 0; index < 140; index++) {
            String severity = index % 5 == 0 ? "WARN" : (index % 11 == 0 ? "ERROR" : "INFO");
            String service = SERVICES[index % SERVICES.length];
            String text = INCIDENT_TEXTS[index % INCIDENT_TEXTS.length];
            Color color = switch (severity) {
                case "ERROR" -> RED;
                case "WARN" -> ORANGE;
                default -> BLUE;
            };
            timeline.addEntry(severity + " - " + service, text,
                    twoDigits(index / 60) + ":" + twoDigits(index % 60), color);
        }
        return pane(fontFamily, "Incident log - dense short rows and many entries",
                "Only visible rows are rendered; scroll far enough to see the virtual list.",
                timeline);
    }

    private static Widget createQuestPane(FontFamily fontFamily) {
        Timeline timeline = new Timeline();
        timeline.setGrow(1);
        for (int index = 0; index < 24; index++) {
            String text = QUEST_TEXTS[index % QUEST_TEXTS.length] + " Chapter " + (index + 1) + ".";
            Color color = switch (index % 3) {
                case 0 -> PURPLE;
                case 1 -> BLUE;
                default -> GREEN;
            };
            timeline.addEntry("Chronicle " + (index + 1), text, "Day " + (index + 1), color);
        }
        return pane(fontFamily, "Quest chronicle - long wrapped descriptions",
                "Rows grow with the wrapped text, so the scrollbar reflects real content height.",
                timeline);
    }

    private static Widget pane(FontFamily fontFamily, String caption, String hint,
                               Timeline timeline) {
        Label status = label(fontFamily, "No entry selected.");
        status.setFontStyle(FontStyle.ITALIC);
        timeline.setSelectionListener(entry -> status.setText(entry == null
                ? "No entry selected."
                : "Selected: " + entry.title() + "  " + entry.time()));

        VBox pane = new VBox();
        pane.setPadding(8);
        pane.setGap(6);
        pane.add(caption(fontFamily, caption))
                .add(label(fontFamily, hint))
                .add(timeline)
                .add(status);
        return pane;
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
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
}
