package com.lixui.widget.overlay;

import com.lixui.widget.control.Button;

/**
 * A modal dialog preset with a message and a cancel/confirm pair.
 * Escape, a background press or the cancel button invokes the negative action; the confirm button
 * invokes the positive action. Both buttons close the dialog.
 */
public final class ConfirmDialog extends ModalDialog {
    private final Button cancelButton;
    private final Button confirmButton;
    private Runnable confirmAction;

    /**
     * Creates a closed confirmation dialog.
     *
     * @param title dialog title
     * @param message dialog message
     * @throws NullPointerException if an argument is null
     */
    public ConfirmDialog(String title, String message) {
        setTitle(title);
        setMessage(message);
        cancelButton = addAction("Cancel", false, this::cancel);
        confirmButton = addAction("Confirm", true, this::confirm);
    }

    /**
     * Returns the action invoked when the user confirms.
     *
     * @return the positive action, or null when none is assigned
     */
    public Runnable confirmAction() {
        return confirmAction;
    }

    /**
     * Assigns the action invoked when the user confirms.
     *
     * @param confirmAction positive action, or null to clear it
     */
    public void setOnConfirm(Runnable confirmAction) {
        this.confirmAction = confirmAction;
    }

    /**
     * Returns the cancel button label.
     *
     * @return the current label
     */
    public String cancelText() {
        return cancelButton.text();
    }

    /**
     * Replaces the cancel button label.
     *
     * @param text new label
     * @throws NullPointerException if {@code text} is null
     */
    public void setCancelText(String text) {
        cancelButton.setText(text);
    }

    /**
     * Returns the confirm button label.
     *
     * @return the current label
     */
    public String confirmText() {
        return confirmButton.text();
    }

    /**
     * Replaces the confirm button label.
     *
     * @param text new label
     * @throws NullPointerException if {@code text} is null
     */
    public void setConfirmText(String text) {
        confirmButton.setText(text);
    }

    private void confirm() {
        if (confirmAction != null) {
            confirmAction.run();
        }
    }
}
