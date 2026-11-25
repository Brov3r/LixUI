package com.lixui.demo;

import com.lixui.graphics.Color;
import com.lixui.text.FontFamily;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.CheckBox;
import com.lixui.widget.control.Label;
import com.lixui.widget.layout.Flow;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.overlay.ConfirmDialog;
import com.lixui.widget.overlay.ModalDialog;
import com.lixui.widget.overlay.PromptDialog;

/**
 * Builds the modal dialog samples shown in the Popup tab.
 * Every button opens an independent dialog variant and reports its result in a shared status line.
 */
final class DemoModalPage {
    private static final float GAP = 8;
    private static final float STATUS_GAP = 10;

    private final FontFamily fontFamily;
    private final Label status = new Label("Choose a dialog variant.");
    private final VBox page = new VBox();
    private String partyName = "Aria";

    /**
     * Creates the modal dialog page.
     *
     * @param fontFamily demo font family, or null to use the context default
     */
    DemoModalPage(FontFamily fontFamily) {
        this.fontFamily = fontFamily;
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);

        Button information = new Button("Information");
        information.setOnAction(() -> openInformation(information));
        Button confirmation = new Button("Confirmation");
        confirmation.setOnAction(() -> openConfirmation(confirmation));
        Button prompt = new Button("Prompt");
        prompt.setOnAction(() -> openPrompt(prompt));
        Button custom = new Button("Custom content");
        custom.setOnAction(() -> openCustom(custom));
        Button backdrop = new Button("Backdrop dismiss");
        backdrop.setOnAction(() -> openBackdropDismiss(backdrop));

        Flow actions = new Flow();
        actions.setGap(GAP);
        actions.add(information).add(confirmation).add(prompt).add(custom).add(backdrop);

        Label caption = new Label("Modal dialogs dim the scene, trap focus and close on Escape.");
        caption.setFontFamily(fontFamily);
        caption.setAutoSize(false);
        caption.setWrap(true);

        page.setGap(STATUS_GAP);
        page.add(caption).add(actions).add(status);
    }

    /**
     * Returns the page content.
     *
     * @return the modal dialog sample page
     */
    Widget page() {
        return page;
    }

    private void openInformation(Button source) {
        ModalDialog dialog = new ModalDialog();
        dialog.setTitle("Patch 1.4 is available");
        dialog.setMessage("Restart the client to apply the update. The rest of the interface "
                + "stays inactive until this dialog closes.");
        dialog.addAction("Got it", true, null);
        dialog.setOnCancel(() -> status.setText("Information dismissed with Escape."));
        dialog.show(source.context());
    }

    private void openConfirmation(Button source) {
        ConfirmDialog dialog = new ConfirmDialog("Delete the save",
                "This removes every local character and cannot be undone.");
        dialog.setConfirmText("Delete");
        dialog.setOnConfirm(() -> status.setText("Save deleted."));
        dialog.setOnCancel(() -> status.setText("Deletion cancelled."));
        dialog.show(source.context());
    }

    private void openPrompt(Button source) {
        PromptDialog dialog = new PromptDialog("Rename the party",
                "Enter a name that other players will see.", partyName);
        dialog.setPlaceholder("Party name");
        dialog.setOnSubmit(value -> {
            partyName = value;
            status.setText("Party renamed to \"" + value + "\".");
        });
        dialog.setOnCancel(() -> status.setText("Rename cancelled."));
        dialog.show(source.context());
    }

    private void openCustom(Button source) {
        CheckBox hardMode = new CheckBox("Enable hard mode");
        Label hint = new Label("Custom content is stretched inside the dialog panel and can host "
                + "any widget, including its own focus order.");
        hint.setFontFamily(fontFamily);
        hint.setAutoSize(false);
        hint.setWrap(true);
        VBox content = new VBox();
        content.setGap(GAP);
        content.add(hardMode).add(hint);

        ModalDialog dialog = new ModalDialog();
        dialog.setTitle("Start a new run");
        dialog.setMessage("The selected options apply to the whole party.");
        dialog.setBody(content);
        dialog.addAction("Cancel", false, dialog::cancel);
        dialog.addAction("Start", true, () -> status.setText("New run started. Hard mode "
                + (hardMode.isChecked() ? "on." : "off.")));
        dialog.setScrimColor(new Color(0.08f, 0.1f, 0.18f, 0.6f));
        dialog.show(source.context());
    }

    private void openBackdropDismiss(Button source) {
        ConfirmDialog dialog = new ConfirmDialog("Leave the party",
                "Press the dimmed background, Escape or Stay to cancel. Only the Leave button "
                        + "confirms, and this variant uses a lighter custom backdrop.");
        dialog.setConfirmText("Leave");
        dialog.setCancelText("Stay");
        dialog.setCloseOnOutsideClick(true);
        dialog.setScrimColor(new Color(0.12f, 0.16f, 0.24f, 0.42f));
        dialog.setOnConfirm(() -> status.setText("Party left."));
        dialog.setOnCancel(() -> status.setText("Stayed in the party."));
        dialog.show(source.context());
    }
}
