package com.lixui.demo;

import com.lixui.render.Texture;
import com.lixui.text.FontFamily;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.CheckBox;
import com.lixui.widget.control.Label;
import com.lixui.widget.control.Stepper;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Builds the demo page for the stepper widget.
 * The page shows a clickable pipeline, a row that contains every step state, and a large icon row.
 */
final class StepperDemoPage {
    private static final float CONTENT_PADDING = 10;
    private static final float CONTENT_GAP = 10;

    private final FontFamily fontFamily;
    private final IconBinder iconBinder;

    /**
     * Creates the demo page builder.
     *
     * @param fontFamily font family used by page labels, or null for the inherited font
     * @param iconBinder callback that binds a bundled icon at the requested logical size
     */
    StepperDemoPage(FontFamily fontFamily, IconBinder iconBinder) {
        this.fontFamily = fontFamily;
        this.iconBinder = iconBinder;
    }

    /**
     * Builds the demo page.
     *
     * @return the page content
     */
    Widget build() {
        Label status = new Label("Click a step or use the buttons to move the current step.");
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);

        Stepper pipeline = createPipeline();
        Button back = new Button("Back");
        Button next = new Button("Next");
        back.setOnAction(() -> pipeline.setCurrentIndex(
                Math.max(0, pipeline.currentIndex() - 1)));
        next.setOnAction(() -> pipeline.setCurrentIndex(
                Math.min(pipeline.steps().size() - 1, pipeline.currentIndex() + 1)));
        Runnable updateButtons = () -> {
            back.setEnabled(pipeline.currentIndex() > 0);
            next.setEnabled(pipeline.currentIndex() < pipeline.steps().size() - 1);
        };
        pipeline.setOnChange(index -> {
            status.setText("Pipeline: \"" + pipeline.steps().get(index).title()
                    + "\" is the current step.");
            updateButtons.run();
        });
        updateButtons.run();
        HBox pipelineControls = new HBox();
        pipelineControls.setGap(8);
        pipelineControls.setCrossAlignment(HBox.CrossAlignment.CENTER);
        pipelineControls.add(back).add(next);

        Stepper order = createOrder(status);
        Button restart = new Button("Restart order");
        restart.setOnAction(() -> order.setCurrentIndex(0));
        HBox restartRow = new HBox();
        restartRow.setGap(8);
        restartRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        restartRow.add(restart);

        Stepper icons = createIconRow(status);

        CheckBox clickable = new CheckBox("Allow clicking steps");
        clickable.setChecked(true);
        clickable.setOnChange(checked -> {
            for (Stepper stepper : List.of(pipeline, order, icons)) {
                stepper.setClickable(checked);
            }
        });

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(CONTENT_GAP);
        page.add(caption("A step row draws completed, current, future, failed and disabled faces. "
                        + "The connector fill animates when the current step changes."))
                .add(clickable)
                .add(caption("Pipeline: numbers, completed check marks and an animated connector"))
                .add(pipeline)
                .add(pipelineControls)
                .add(caption("Order: one row with a failed step and a disabled step"))
                .add(order)
                .add(restartRow)
                .add(caption("Icons: larger faces with a custom icon per step"))
                .add(icons)
                .add(status);
        return page;
    }

    private Stepper createPipeline() {
        Stepper stepper = new Stepper();
        stepper.setStepSize(36);
        stepper.setConnectorLength(64);
        stepper.setTitleGap(8);
        stepper.setClickable(true);
        stepper.addStep("Order");
        stepper.addStep("Payment");
        stepper.addStep("Packing");
        stepper.addStep("Shipped");
        stepper.setCurrentIndex(1);
        return stepper;
    }

    private Stepper createOrder(Label status) {
        Stepper stepper = new Stepper();
        stepper.setStepSize(30);
        stepper.setConnectorLength(48);
        stepper.setTitleGap(6);
        stepper.setClickable(true);
        stepper.addStep("Cart");
        stepper.addStep("Payment");
        stepper.addStep("Packing");
        stepper.addStep("Delivery");
        stepper.addStep("Done");
        stepper.steps().get(1).setError(true);
        stepper.steps().get(4).setDisabled(true);
        stepper.setCurrentIndex(2);
        stepper.setOnChange(index -> status.setText("Order: \""
                + stepper.steps().get(index).title() + "\" selected. Failed and disabled steps "
                + "ignore clicks."));
        return stepper;
    }

    private Stepper createIconRow(Label status) {
        Stepper stepper = new Stepper();
        stepper.setStepSize(44);
        stepper.setConnectorLength(70);
        stepper.setTitleGap(10);
        stepper.setIconSize(20);
        stepper.setClickable(true);
        Stepper.Step shop = stepper.addStep("Shop");
        Stepper.Step payment = stepper.addStep("Payment");
        Stepper.Step shipping = stepper.addStep("Shipping");
        Stepper.Step delivered = stepper.addStep("Delivered");
        iconBinder.bind("shop.svg", 20, shop::setIcon);
        iconBinder.bind("credit-card.svg", 20, payment::setIcon);
        iconBinder.bind("truck.svg", 20, shipping::setIcon);
        iconBinder.bind("location.svg", 20, delivered::setIcon);
        stepper.setCurrentIndex(2);
        stepper.setOnChange(index -> status.setText("Icons: \""
                + stepper.steps().get(index).title() + "\" selected."));
        return stepper;
    }

    private Label caption(String text) {
        Label label = new Label(text);
        label.setFontFamily(fontFamily);
        label.setAutoSize(false);
        label.setWrap(true);
        return label;
    }

    /**
     * Binds a bundled icon to a step at the requested logical size.
     */
    interface IconBinder {
        /**
         * Binds the icon by resource name.
         *
         * @param name bundled icon resource name
         * @param size logical icon edge length used for density-aware loading
         * @param assign consumer that receives the loaded texture
         */
        void bind(String name, float size, Consumer<Texture> assign);
    }
}
