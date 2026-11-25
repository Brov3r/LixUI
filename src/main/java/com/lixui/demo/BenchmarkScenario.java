package com.lixui.demo;

import com.lixui.graphics.Color;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.Chart;
import com.lixui.widget.control.CheckBox;
import com.lixui.widget.control.DonutChart;
import com.lixui.widget.control.Label;
import com.lixui.widget.control.ProgressBar;
import com.lixui.widget.control.RichLabel;
import com.lixui.widget.control.Slider;
import com.lixui.widget.control.Spinner;
import com.lixui.widget.layout.VBox;

import java.util.List;

/**
 * The stress scenarios shown as benchmark sub-tabs.
 * Each scenario builds a real widget subtree with the requested number of top-level stress elements
 * (or data rows for {@link #DATA}).
 */
enum BenchmarkScenario {
    /** Wrapped plain and rich text labels. */
    TEXT("Text", "Wrapped Label and RichLabel instances; measurement includes text layout.", 10_000,
            List.of(100, 500, 1_000, 5_000, 10_000)),
    /** Interactive controls with checked, disabled and focusable states. */
    CONTROLS("Controls", "Buttons, check boxes, sliders and spinners stay hoverable and focusable; "
            + "the first control is focused after a run.", 5_000,
            List.of(100, 500, 1_000, 2_000, 5_000)),
    /** Table, virtual list and tree row volume. */
    DATA("Data", "Table, VirtualList and TreeView rows.",
            10_000, List.of(100, 500, 1_000, 5_000, 10_000)),
    /** Charts, donuts, spinners and animated progress bars. */
    GRAPHICS("Graphics", "Sparkline charts, donut charts, spinners and progress bars in fixed tiles.",
            2_000, List.of(100, 250, 500, 1_000, 2_000)),
    /** A blend of widgets intended to exercise several code paths together. */
    MIXED("Mixed", "Labels, rich text, buttons, toggles, spinners and progress bars plus toasts.",
            2_000, List.of(100, 500, 1_000, 2_000));

    private static final Color BLUE = Color.hex("#7EA6F5");
    private static final Color GREEN = Color.hex("#86C46A");
    private static final Color ORANGE = Color.hex("#E39A5B");
    private static final Color PURPLE = Color.hex("#C39BF2");

    private final String title;
    private final String description;
    private final int maximumCount;
    private final List<Integer> counts;

    BenchmarkScenario(String title, String description, int maximumCount, List<Integer> counts) {
        this.title = title;
        this.description = description;
        this.maximumCount = maximumCount;
        this.counts = counts;
    }

    /**
     * Returns the display title.
     *
     * @return the tab title
     */
    String title() {
        return title;
    }

    /**
     * Returns a short explanation of the stress content.
     *
     * @return the scenario description
     */
    String description() {
        return description;
    }

    /**
     * Returns the selectable stress element counts.
     *
     * @return an immutable count list
     */
    List<Integer> counts() {
        return counts;
    }

    /**
     * Returns the default element count.
     *
     * @return the third count when available, otherwise the last count
     */
    int defaultCount() {
        return counts.get(Math.min(2, counts.size() - 1));
    }

    /**
     * Returns the largest supported element count.
     *
     * @return the maximum count
     */
    int maximumCount() {
        return maximumCount;
    }

    /**
     * Builds a stress subtree.
     *
     * @param count requested stress element count
     * @return a detached widget root
     * @throws IllegalArgumentException if the count is negative or above the scenario maximum
     */
    Widget create(int count) {
        if (count < 0 || count > maximumCount) {
            throw new IllegalArgumentException("Stress count must be between 0 and "
                    + maximumCount + ".");
        }
        return switch (this) {
            case TEXT -> createText(count);
            case CONTROLS -> createControls(count);
            case DATA -> new BenchmarkDataContent(count);
            case GRAPHICS -> createGraphics(count);
            case MIXED -> createMixed(count);
        };
    }

    private static Widget createText(int count) {
        VBox root = new VBox();
        root.setGap(2);
        root.setPadding(4);
        String paragraph = "A wrapped benchmark sentence reflows at the assigned width and "
                + "exercises line breaking and glyph layout.";
        for (int index = 0; index < count; index++) {
            if (index % 4 == 0) {
                RichLabel rich = new RichLabel();
                rich.add("Rich label #" + index + " ", BLUE);
                rich.add("with a second styled run that also wraps across the available width.",
                        null);
                root.add(rich);
            } else {
                root.add(new Label("Label #" + index + ": " + paragraph));
            }
        }
        return root;
    }

    private static Widget createControls(int count) {
        VBox root = new VBox();
        root.setGap(3);
        root.setPadding(4);
        for (int index = 0; index < count; index++) {
            Widget control;
            switch (index % 4) {
                case 0 -> {
                    Button button = new Button("Action " + index);
                    button.setEnabled(index % 11 != 0);
                    control = button;
                }
                case 1 -> {
                    CheckBox checkBox = new CheckBox("Option " + index);
                    checkBox.setChecked(index % 5 == 0);
                    control = checkBox;
                }
                case 2 -> {
                    Slider slider = new Slider();
                    slider.setRange(0, 100);
                    slider.setStep(1);
                    slider.setValue(index % 101);
                    control = slider;
                }
                default -> {
                    Spinner spinner = new Spinner();
                    spinner.setDiameter(24);
                    spinner.setThickness(2.5f);
                    control = spinner;
                }
            }
            root.add(control);
        }
        return root;
    }

    private static Widget createGraphics(int count) {
        BenchmarkTileGrid root = new BenchmarkTileGrid(72, 72, 6);
        for (int index = 0; index < count; index++) {
            Widget tile;
            switch (index % 4) {
                case 0 -> {
                    Spinner spinner = new Spinner();
                    spinner.setDiameter(28);
                    spinner.setThickness(3);
                    spinner.setColor(index % 8 == 0 ? GREEN : BLUE);
                    tile = spinner;
                }
                case 1 -> {
                    Chart chart = new Chart();
                    chart.setMode(Chart.Mode.SPARKLINE);
                    chart.setShowLegend(false);
                    chart.setShowGrid(false);
                    chart.setShowAxes(false);
                    chart.addSeries("series " + index,
                            List.of(2f, 5f, 3f, 7f, 4f, 8f, 6f), BLUE);
                    tile = chart;
                }
                case 2 -> {
                    DonutChart donut = new DonutChart();
                    donut.setShowLegend(false);
                    donut.setCenterLabel(Integer.toString(index % 100));
                    donut.addSegment("A", 3 + index % 5, ORANGE);
                    donut.addSegment("B", 2 + index % 3, PURPLE);
                    donut.addSegment("C", 1 + index % 4, GREEN);
                    tile = donut;
                }
                default -> {
                    ProgressBar bar = new ProgressBar((index % 100) / 100f);
                    bar.setShowText(false);
                    bar.setAnimationRate(9);
                    tile = bar;
                }
            }
            root.add(tile);
        }
        return root;
    }

    private static Widget createMixed(int count) {
        VBox root = new VBox();
        root.setGap(3);
        root.setPadding(4);
        for (int index = 0; index < count; index++) {
            Widget item;
            switch (index % 6) {
                case 0 -> item = new Label("Mixed label #" + index
                        + " keeps a wrapped sentence in the shared stress scene.");
                case 1 -> {
                    Button button = new Button("Mixed action " + index);
                    button.setEnabled(index % 10 != 0);
                    item = button;
                }
                case 2 -> {
                    CheckBox checkBox = new CheckBox("Mixed toggle " + index);
                    checkBox.setChecked(index % 4 == 0);
                    item = checkBox;
                }
                case 3 -> {
                    Spinner spinner = new Spinner();
                    spinner.setDiameter(22);
                    spinner.setThickness(2.5f);
                    item = spinner;
                }
                case 4 -> item = new ProgressBar((index % 100) / 100f);
                default -> {
                    RichLabel rich = new RichLabel();
                    rich.add("Rich mixed #" + index + " ", ORANGE);
                    rich.add("with a second run.", null);
                    item = rich;
                }
            }
            root.add(item);
        }
        return root;
    }
}
