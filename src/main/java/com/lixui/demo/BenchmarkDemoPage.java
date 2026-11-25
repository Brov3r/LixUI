package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.text.FontStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;
import com.lixui.widget.container.TabPanel;
import com.lixui.widget.container.Table;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.Label;
import com.lixui.widget.control.SegmentedButton;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The second top-level demo tab: a real stress benchmark with text, controls, data, graphics and
 * mixed scenarios.
 * CPU phases are measured with {@link System#nanoTime()} around real widget operations. Live
 * sampling records end-to-end frame intervals and backend draw calls only while this page and the
 * selected sub-tab are visible.
 */
final class BenchmarkDemoPage extends Panel {
    private static final Logger LOGGER = LoggerFactory.getLogger(BenchmarkDemoPage.class);
    private static final int LIVE_FRAME_LIMIT = 240;
    private static final int UPDATE_FRAMES = 30;
    private static final int PROGRESS_INTERVAL = 30;
    private static final float STAGE_HEIGHT = 112;

    private final Consumer<String> toastSink;
    private final VBox body = new VBox();
    private final TabPanel scenarioTabs = new TabPanel();
    private final List<ScenarioPanel> panels = new ArrayList<>();
    private final Table metricsTable = new Table();
    private final Label summaryLabel = new Label();
    private final Label liveLabel = new Label();
    private final Label statusLabel = new Label();
    private final BenchmarkLiveCapture liveCapture = new BenchmarkLiveCapture(LIVE_FRAME_LIMIT);
    private ScenarioPanel activePanel;
    private BenchmarkRunJob job;
    private BenchmarkRunResult lastRun;
    private BenchmarkRunResult previousRun;
    private BenchmarkLiveMetrics lastLive;
    private BenchmarkLiveMetrics previousLive;

    /**
     * Creates the benchmark page.
     *
     * @param toastSink optional callback used by the mixed scenario to show demo toasts
     */
    BenchmarkDemoPage(Consumer<String> toastSink) {
        this.toastSink = toastSink;
        body.setPadding(8);
        body.setGap(6);
        add(body);
        Label info = new Label("Real widget stress runs. Build, measure/layout and update phases "
                + "are CPU wall-clock System.nanoTime measurements. Live sampling records the "
                + "end-to-end frame interval between presented frames and backend draw calls for "
                + "the whole demo; the interval includes vsync and is not GPU time. Results are "
                + "indicative and depend on the machine, display refresh and current load.");
        info.setWrap(true);
        body.add(info);

        for (BenchmarkScenario scenario : BenchmarkScenario.values()) {
            ScenarioPanel panel = new ScenarioPanel(scenario);
            panels.add(panel);
            scenarioTabs.addTab(scenario.title(), panel.view());
        }
        activePanel = panels.get(0);
        scenarioTabs.setOnChange(this::onScenarioTabChanged);
        body.add(scenarioTabs);

        summaryLabel.setWrap(true);
        body.add(summaryLabel);
        configureMetricsTable();
        body.add(metricsTable);
        liveLabel.setWrap(true);
        body.add(liveLabel);
        statusLabel.setWrap(true);
        body.add(statusLabel);
        refreshMetrics();
        statusLabel.setText("Idle. Run a scenario, then press Start to record live frames.");
        refreshButtons();
    }

    /**
     * Measures the page body against the available constraints.
     *
     * @param constraints available and required dimensions
     * @return the natural page size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return body.measure(constraints);
    }

    /**
     * Stretches the page body across the assigned bounds.
     */
    @Override
    protected void onLayout() {
        body.layout(new Rect(0, 0, bounds().width(), bounds().height()));
    }

    /**
     * Stops job stepping and live capture when the page leaves the tree.
     */
    @Override
    protected void onDetached() {
        job = null;
        liveCapture.stop();
        super.onDetached();
    }

    /**
     * Advances an active run and enforces the visibility rules for live capture.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        super.onUpdate(frame);
        if (!isActiveScenarioVisible()) {
            if (job != null) {
                cancelRun("Run cancelled: the Benchmark tab is hidden.");
            }
            if (liveCapture.isRunning()) {
                finishLive("Live stopped: the Benchmark tab is hidden.");
            }
            if (releaseStages(null)) {
                statusLabel.setText("Benchmark tab hidden: stress widgets released.");
                refreshButtons();
            }
            return;
        }
        if (job == null) {
            return;
        }
        try {
            if (job.step()) {
                completeRun();
            } else {
                statusLabel.setText(job.statusText());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Benchmark run failed.", exception);
            cancelRun("Run failed: " + exception.getMessage());
        }
    }

    /**
     * Records the rendered frame for a running capture.
     * The demo scene calls this after rendering so the frame delta and draw call count belong to the
     * same presented frame.
     *
     * @param deltaSeconds frame interval in seconds
     * @param drawCalls backend draw calls for the rendered frame
     */
    void recordLiveFrame(float deltaSeconds, int drawCalls) {
        if (!liveCapture.isRunning()) {
            return;
        }
        boolean visible = isActiveScenarioVisible();
        liveCapture.addFrame(visible, deltaSeconds, drawCalls);
        if (!visible) {
            finishLive("Live stopped: the Benchmark tab is hidden.");
            return;
        }
        if (!liveCapture.isRunning()) {
            finishLive("Live capture finished at the " + LIVE_FRAME_LIMIT + " frame limit.");
            return;
        }
        if (liveCapture.frameCount() % PROGRESS_INTERVAL == 0) {
            updateLiveProgress();
        }
    }

    private void startRun(ScenarioPanel panel) {
        if (job != null) {
            return;
        }
        if (liveCapture.isRunning()) {
            finishLive("Live stopped: a new run started.");
        }
        activePanel = panel;
        releaseStages(panel);
        panel.stage().clear();
        int count = panel.count();
        int iterations = measuredIterations(count);
        job = new BenchmarkRunJob(panel.stage(), panel.scenario(), count, 1, iterations,
                UPDATE_FRAMES);
        statusLabel.setText("Running " + panel.scenario().title() + " · " + count
                + " elements · " + iterations + " measured + 1 warmup iteration(s).");
        refreshButtons();
    }

    private void reset(ScenarioPanel panel) {
        if (job != null) {
            job = null;
            statusLabel.setText("Run cancelled by Reset.");
        }
        if (liveCapture.isRunning()) {
            finishLive("Live stopped: scenario reset.");
        }
        panel.stage().clear();
        if (job == null) {
            statusLabel.setText("Reset: " + panel.scenario().title()
                    + " stress widgets released.");
        }
        refreshMetrics();
        refreshButtons();
    }

    private void cancelRun(String message) {
        job = null;
        if (activePanel != null) {
            activePanel.stage().clear();
        }
        statusLabel.setText(message);
        refreshButtons();
    }

    private void completeRun() {
        BenchmarkRunResult completed = job == null ? null : job.result();
        if (completed == null) {
            cancelRun("Run failed: no result was produced.");
            return;
        }
        previousRun = lastRun;
        lastRun = completed;
        job = null;
        focusFirstControl(activePanel.stage());
        if (completed.scenario() == BenchmarkScenario.MIXED && toastSink != null) {
            toastSink.accept("Mixed benchmark complete: " + completed.count()
                    + " stress elements rendered.");
        }
        refreshMetrics();
        statusLabel.setText("Run complete: " + completed.scenario().title() + " · "
                + completed.count() + " elements · " + completed.widgetCount() + " widgets.");
        refreshButtons();
    }

    private void startLive(ScenarioPanel panel) {
        if (job != null) {
            statusLabel.setText("Finish the current run or press Reset before live capture.");
            return;
        }
        if (!panel.stage().hasContent()) {
            statusLabel.setText("Run a scenario before starting live capture.");
            return;
        }
        if (!isEffectivelyVisible(panel.view())) {
            statusLabel.setText("Start live capture only while the Benchmark tab is visible.");
            return;
        }
        if (!liveCapture.start(true)) {
            statusLabel.setText("Live capture requires a visible Benchmark tab.");
            return;
        }
        activePanel = panel;
        statusLabel.setText("Live recording: 0 / " + LIVE_FRAME_LIMIT + " frames.");
        refreshLiveLabel();
        refreshButtons();
    }

    private void finishLive(String reason) {
        BenchmarkLiveMetrics metrics = liveCapture.stop();
        if (metrics != null) {
            previousLive = lastLive;
            lastLive = metrics;
        }
        refreshMetrics();
        refreshLiveLabel();
        statusLabel.setText(metrics == null ? reason
                : reason + " Recorded " + metrics.frames() + " frames.");
        refreshButtons();
    }

    private void updateLiveProgress() {
        liveLabel.setText("Live recording: " + liveCapture.frameCount() + " / "
                + liveCapture.frameLimit() + " frames. The frame interval is end-to-end and "
                + "includes vsync; it is not GPU time.");
        statusLabel.setText("Live recording: " + liveCapture.frameCount() + " / "
                + liveCapture.frameLimit() + " frames.");
    }

    private void onScenarioTabChanged(TabPanel.Tab tab) {
        if (job != null) {
            cancelRun("Run cancelled: scenario tab changed.");
        }
        if (liveCapture.isRunning()) {
            finishLive("Live stopped: scenario tab changed.");
        }
        ScenarioPanel selected = null;
        for (ScenarioPanel panel : panels) {
            if (panel.view() == tab.content()) {
                selected = panel;
                break;
            }
        }
        if (selected != null) {
            releaseStages(selected);
            activePanel = selected;
            statusLabel.setText("Scenario: " + selected.scenario().title()
                    + ". Choose a count, then Run.");
        }
        refreshButtons();
    }

    private boolean releaseStages(ScenarioPanel keep) {
        boolean cleared = false;
        for (ScenarioPanel panel : panels) {
            if (panel != keep && panel.stage().hasContent()) {
                panel.stage().clear();
                cleared = true;
            }
        }
        return cleared;
    }

    private void configureMetricsTable() {
        metricsTable.setFillWidth(true);
        metricsTable.setRowHeight(22);
        metricsTable.setHeaderHeight(26);
        metricsTable.setColumnResizingEnabled(false);
        metricsTable.setSortingEnabled(false);
        metricsTable.setSelectionMode(Table.SelectionMode.NONE);
        metricsTable.addColumn("Metric", 118);
        metricsTable.addColumn("min", 58);
        metricsTable.addColumn("median", 66);
        metricsTable.addColumn("p95", 58);
        metricsTable.addColumn("prev med", 70);
        metricsTable.addColumn("change", 70);
    }

    private void refreshMetrics() {
        metricsTable.clearRows();
        if (lastRun == null) {
            summaryLabel.setText("No run yet. Choose a count and press Run.");
            addEmptyMetricRows();
        } else {
            summaryLabel.setText(String.format(Locale.ROOT,
                    "Last run: %s · N=%d · %d widgets · %d measured + %d warmup iteration(s) · "
                            + "%d update frames per iteration",
                    lastRun.scenario().title(), lastRun.count(), lastRun.widgetCount(),
                    lastRun.measuredIterations(), lastRun.warmupIterations(),
                    lastRun.updateFrames()));
            addStatRow("Build + attach", lastRun.buildNanos(),
                    previousRun == null ? null : previousRun.buildNanos());
            addStatRow("Measure + layout", lastRun.layoutNanos(),
                    previousRun == null ? null : previousRun.layoutNanos());
            addStatRow("Update / frame", lastRun.updateNanos(),
                    previousRun == null ? null : previousRun.updateNanos());
            addLiveRows();
        }
        refreshLiveLabel();
    }

    private void addEmptyMetricRows() {
        for (String name : List.of("Build + attach", "Measure + layout", "Update / frame",
                "Live frame", "Draw calls")) {
            metricsTable.addRow(name, "—", "—", "—", "—", "—");
        }
    }

    private void addLiveRows() {
        if (lastLive == null) {
            metricsTable.addRow("Live frame", "—", "—", "—", "—", "—");
            metricsTable.addRow("Draw calls", "—", "—", "—", "—", "—");
            return;
        }
        addStatRow("Live frame", lastLive.frameNanos(),
                previousLive == null ? null : previousLive.frameNanos());
        addCountRow("Draw calls", lastLive.drawCalls(),
                previousLive == null ? null : previousLive.drawCalls());
    }

    private void addStatRow(String name, BenchmarkStats current, BenchmarkStats previous) {
        if (current == null) {
            metricsTable.addRow(name, "—", "—", "—", "—", "—");
            return;
        }
        metricsTable.addRow(name,
                BenchmarkFormat.millis(current.min()),
                BenchmarkFormat.millis(current.median()),
                BenchmarkFormat.millis(current.p95()),
                previous == null ? "—" : BenchmarkFormat.millis(previous.median()),
                previous == null ? "—" : BenchmarkFormat.percentChange(
                        current.median(), previous.median()));
    }

    private void addCountRow(String name, BenchmarkStats current, BenchmarkStats previous) {
        metricsTable.addRow(name,
                BenchmarkFormat.integer(current.min()),
                BenchmarkFormat.integer(current.median()),
                BenchmarkFormat.integer(current.p95()),
                previous == null ? "—" : BenchmarkFormat.integer(previous.median()),
                previous == null ? "—" : BenchmarkFormat.percentChange(
                        current.median(), previous.median()));
    }

    private void refreshLiveLabel() {
        if (liveCapture.isRunning()) {
            liveLabel.setText("Live recording: " + liveCapture.frameCount() + " / "
                    + liveCapture.frameLimit() + " frames.");
            return;
        }
        if (lastLive == null) {
            liveLabel.setText("Live: not recorded. Start captures end-to-end frame intervals and "
                    + "draw calls while the visible tab is rendering.");
            return;
        }
        liveLabel.setText(String.format(Locale.ROOT,
                "Live: %d / %d frames · avg %s FPS · median %s ms · p95 %s ms · "
                        + "draw calls median %s. Frame interval is end-to-end and includes vsync; "
                        + "it is not GPU time.",
                lastLive.frames(), lastLive.frameLimit(),
                BenchmarkFormat.fps(lastLive.averageFps()),
                BenchmarkFormat.millis(lastLive.frameNanos().median()),
                BenchmarkFormat.millis(lastLive.frameNanos().p95()),
                BenchmarkFormat.integer(lastLive.drawCalls().median())));
    }

    private void refreshButtons() {
        for (ScenarioPanel panel : panels) {
            panel.runButton.setEnabled(job == null);
            panel.startButton.setEnabled(job == null && !liveCapture.isRunning()
                    && panel == activePanel && panel.stage().hasContent());
            panel.stopButton.setEnabled(liveCapture.isRunning() && panel == activePanel);
        }
    }

    private boolean isActiveScenarioVisible() {
        return activePanel != null && isEffectivelyVisible(this)
                && isEffectivelyVisible(activePanel.view());
    }

    private static boolean isEffectivelyVisible(Widget widget) {
        for (Widget current = widget; current != null; current = current.parent()) {
            if (!current.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private void focusFirstControl(Widget root) {
        Widget control = firstFocusable(root);
        UiContext context = context();
        if (control == null || context == null) {
            return;
        }
        try {
            context.requestFocus(control);
        } catch (IllegalArgumentException exception) {
            LOGGER.debug("Could not focus a benchmark control.", exception);
        }
    }

    private static Widget firstFocusable(Widget root) {
        for (Widget child : root.children()) {
            if (child.isVisible() && child.isEnabled() && child.isFocusable()) {
                return child;
            }
            Widget nested = firstFocusable(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static int measuredIterations(int count) {
        return count <= 1_000 ? 3 : 2;
    }

    private static String countLabel(int count) {
        return count >= 1_000 ? (count / 1_000) + "k" : Integer.toString(count);
    }

    private final class ScenarioPanel {
        private final VBox view = new VBox();
        private final BenchmarkScenario scenario;
        private final SegmentedButton counts = new SegmentedButton();
        private final BenchmarkStage stage = new BenchmarkStage(STAGE_HEIGHT);
        private final Button runButton = new Button("Run");
        private final Button resetButton = new Button("Reset");
        private final Button startButton = new Button("Start");
        private final Button stopButton = new Button("Stop");

        private ScenarioPanel(BenchmarkScenario scenario) {
            this.scenario = scenario;
            view.setPadding(4);
            view.setGap(5);
            Label description = new Label(scenario.description());
            description.setWrap(true);
            description.setFontStyle(FontStyle.REGULAR);
            view.add(description);

            counts.setSelectionMode(SegmentedButton.SelectionMode.SINGLE);
            int selected = 0;
            for (int index = 0; index < scenario.counts().size(); index++) {
                int value = scenario.counts().get(index);
                counts.addSegment(countLabel(value));
                if (value == scenario.defaultCount()) {
                    selected = index;
                }
            }
            counts.setSelectedIndex(selected);
            runButton.setOnAction(() -> startRun(this));
            resetButton.setOnAction(() -> reset(this));
            startButton.setOnAction(() -> startLive(this));
            stopButton.setOnAction(() -> {
                if (liveCapture.isRunning()) {
                    finishLive("Live stopped by the Stop button.");
                }
            });
            startButton.setEnabled(false);
            stopButton.setEnabled(false);

            HBox actions = new HBox();
            actions.setGap(6);
            actions.setCrossAlignment(HBox.CrossAlignment.CENTER);
            actions.add(counts).add(runButton).add(resetButton)
                    .add(startButton).add(stopButton);
            view.add(actions);
            view.add(stage);
        }

        private Widget view() {
            return view;
        }

        private BenchmarkScenario scenario() {
            return scenario;
        }

        private BenchmarkStage stage() {
            return stage;
        }

        private int count() {
            int index = counts.selectedIndex();
            if (index < 0 || index >= scenario.counts().size()) {
                return scenario.defaultCount();
            }
            return scenario.counts().get(index);
        }
    }
}
