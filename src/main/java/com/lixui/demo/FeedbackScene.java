package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Alert;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.CheckBox;
import com.lixui.widget.control.EmptyState;
import com.lixui.widget.control.Label;
import com.lixui.widget.control.ProgressBar;
import com.lixui.widget.control.SegmentedButton;
import com.lixui.widget.control.Separator;
import com.lixui.widget.control.Spinner;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.overlay.Toast;
import com.lixui.widget.overlay.ToastManager;

/**
 * Builds the feedback scene with alerts, progress, spinners, badges, an empty state and a toast
 * stack that shows default, custom-content and custom-renderer cards.
 */
final class FeedbackScene {
    private static final Color BLUE = Color.hex("#4F8EF7");
    private static final Color GREEN = Color.hex("#2E8B57");
    private static final Color ORANGE = Color.hex("#C26A17");
    private static final Color PURPLE = Color.hex("#7C4DDB");

    private FeedbackScene() {
    }

    static ShowcasePage create(ShowcaseKit kit) {
        ToastManager toasts = new ToastManager();
        toasts.setCorner(ToastManager.Corner.BOTTOM_RIGHT);
        toasts.setMaxVisible(4);
        toasts.setVisible(false);
        kit.manager().add(toasts);

        ShowcaseCard notices = kit.card().padding(14);
        Alert info = new Alert(Alert.Tone.INFO);
        info.setTitle("Sync complete");
        info.setMessage("All 48 records were uploaded in 1.2 seconds.");
        Alert warning = new Alert(Alert.Tone.WARNING);
        warning.setTitle("Storage almost full");
        warning.setMessage("92% of the shared workspace is in use.");
        warning.setClosable(true);
        notices.with(kit.heading("System notices")).with(info).with(warning);
        Widget noticesCard = ShowcaseKit.fixHeight(notices, 200);

        ShowcaseCard progress = createProgressCard(kit);
        Widget progressCard = ShowcaseKit.fixHeight(progress, 220);

        ShowcaseCard badges = kit.card().padding(14);
        HBox badgeRow = new HBox();
        badgeRow.setGap(8);
        badgeRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        badgeRow.add(kit.badge("New", BLUE))
                .add(kit.badge("12 open", ORANGE))
                .add(kit.badge("Stable", GREEN))
                .add(kit.badge("Beta", PURPLE));
        badges.with(kit.heading("Badges"))
                .with(badgeRow)
                .with(kit.wrappedMuted("Counters, status labels and accent chips share the same "
                        + "pill surface."));
        Widget badgesCard = ShowcaseKit.fixWidth(badges, 300);

        EmptyState empty = new EmptyState("No drafts yet",
                "New documents appear here once a teammate saves a draft.");
        empty.setIcon("file.svg");
        empty.setIconSize(34);
        empty.setContentPadding(6);
        empty.setGap(4);
        ShowcaseCard emptyCard = kit.card().padding(10);
        emptyCard.with(empty);
        Widget emptySlot = ShowcaseKit.fixWidth(emptyCard, 424);

        HBox bottom = new HBox();
        bottom.setGap(16);
        bottom.setGrow(1);
        bottom.add(badgesCard).add(emptySlot);

        VBox left = new VBox();
        left.setGap(16);
        left.add(noticesCard).add(progressCard).add(bottom);

        ShowcaseCard center = createToastCard(kit, toasts);
        Widget centerSlot = ShowcaseKit.fixHeight(center, 244);

        VBox right = new VBox();
        right.setGap(12);
        right.add(centerSlot).add(kit.spacer());

        HBox body = new HBox();
        body.setGap(16);
        body.add(ShowcaseKit.fixWidth(left, 740)).add(ShowcaseKit.fixWidth(right, 476));

        boolean[] shown = {false};
        ShowcasePage page = new ShowcasePage(kit, 4, 4, "Feedback and notifications",
                "Alerts, progress and three kinds of toast cards.",
                Theme.catppuccinLatte(), Theme.gruvboxDark(), true, body);
        page.onActivate(() -> {
            toasts.setVisible(true);
            if (!shown[0]) {
                showToasts(kit, toasts);
                shown[0] = true;
            }
        });
        page.onDeactivate(() -> toasts.setVisible(false));
        return page;
    }

    private static ShowcaseCard createProgressCard(ShowcaseKit kit) {
        ShowcaseCard card = kit.card().padding(14).gap(8);
        card.with(kit.heading("Background jobs"));
        card.with(progressRow(kit, "Assets", 0.78f, BLUE))
                .with(progressRow(kit, "Index", 0.42f, GREEN))
                .with(progressRow(kit, "Backup", 0.94f, ORANGE));
        card.with(new Separator());

        Spinner small = new Spinner();
        small.setDiameter(26);
        small.setThickness(3);
        small.setSpeed(1.55f);
        Spinner medium = new Spinner();
        medium.setDiameter(34);
        medium.setThickness(4);
        medium.setSpeed(1.2f);
        Spinner large = new Spinner();
        large.setDiameter(42);
        large.setThickness(4.5f);
        large.setSpeed(0.85f);
        HBox workers = new HBox();
        workers.setGap(14);
        workers.setCrossAlignment(HBox.CrossAlignment.CENTER);
        workers.add(kit.muted("Workers")).add(small).add(medium).add(large)
                .add(kit.muted("Syncing in the background"));
        card.with(workers);
        return card;
    }

    private static Widget progressRow(ShowcaseKit kit, String label, float value, Color accent) {
        ProgressBar bar = new ProgressBar(value);
        bar.setMinimumSize(0, 12);
        bar.setMaximumSize(10_000, 12);
        bar.setFillColor(accent);
        bar.setAnimationRate(8);
        Label percent = kit.muted(Math.round(value * 100) + "%");
        Widget percentSlot = ShowcaseKit.fixWidth(percent, 42);
        HBox row = new HBox();
        row.setGap(10);
        row.setCrossAlignment(HBox.CrossAlignment.CENTER);
        row.add(kit.muted(label)).add(bar).add(percentSlot);
        bar.setGrow(1);
        return row;
    }

    private static ShowcaseCard createToastCard(ShowcaseKit kit, ToastManager toasts) {
        ShowcaseCard card = kit.card().padding(16).gap(10);
        card.with(kit.heading("Notification center"))
                .with(kit.muted("Choose what reaches your inbox."));
        CheckBox sounds = new CheckBox("Mute notification sounds");
        SegmentedButton scope = new SegmentedButton();
        scope.setSelectionMode(SegmentedButton.SelectionMode.SINGLE);
        scope.addSegment("All");
        scope.addSegment("Mentions");
        scope.addSegment("None");
        scope.setSelectedIndex(0);
        Button test = kit.primaryButton("Send a test toast");
        test.setMinimumSize(0, 38);
        test.setOnAction(() -> toasts.show(new ToastManager.Options(
                "A test notification was queued just now.")
                .title("Test toast")
                .type(ToastManager.Type.INFO)
                .duration(3600)));
        card.with(sounds).with(scope).with(test)
                .with(kit.muted("Toasts stack in the bottom-right corner."));
        return card;
    }

    private static void showToasts(ShowcaseKit kit, ToastManager toasts) {
        toasts.show(new ToastManager.Options("Your weekly report is ready to review.")
                .title("Report ready")
                .type(ToastManager.Type.SUCCESS)
                .duration(3600));

        Label message = kit.wrapped("A new comment was added to the Aurora migration plan.");
        ProgressBar progress = new ProgressBar(0.65f);
        progress.setMinimumSize(0, 10);
        progress.setMaximumSize(10_000, 10);
        progress.setFillColor(BLUE);
        progress.setAnimationRate(8);
        VBox content = new VBox();
        content.setGap(6);
        content.add(message).add(progress);
        toasts.show(new ToastManager.Options("")
                .content(content)
                .minCardWidth(280)
                .maxWidth(280)
                .duration(3600));

        toasts.show(new ToastManager.Options("")
                .minCardWidth(280)
                .maxWidth(280)
                .maxCardWidth(280)
                .renderer(FeedbackScene::drawCustomToast)
                .duration(3600));
    }

    private static void drawCustomToast(Toast toast, RenderManager renderer,
                                        Rect bounds, FrameInfo frame) {
        Color accent = Color.hex("#7FA6FF");
        renderer.roundedRect(bounds, 10, Color.hex("#1E2433"));
        renderer.roundedRect(new Rect(bounds.x(), bounds.y(), 4, bounds.height()), 2, accent);
        Font font = toast.font();
        if (font != null) {
            renderer.text(font, "Deploy complete", bounds.x() + 18, bounds.y() + 8,
                    Color.hex("#F5F7FF"), HorizontalAlignment.LEFT, VerticalAlignment.TOP);
            renderer.text(font, "Production is running v2.4.1", bounds.x() + 18,
                    bounds.y() + 27, Color.hex("#AEB8D4"),
                    HorizontalAlignment.LEFT, VerticalAlignment.TOP);
        }
        float barWidth = Math.max(0, bounds.width() - 36);
        renderer.rect(new Rect(bounds.x() + 18, bounds.bottom() - 8, barWidth, 3),
                Color.hex("#39415C"));
        renderer.rect(new Rect(bounds.x() + 18, bounds.bottom() - 8, barWidth * 0.72f, 3),
                accent);
    }
}
