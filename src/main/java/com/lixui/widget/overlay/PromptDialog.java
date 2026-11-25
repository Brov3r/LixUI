package com.lixui.widget.overlay;

import com.lixui.widget.Widget;
import com.lixui.widget.control.Input;

import java.util.function.Consumer;

/**
 * A modal dialog preset with a message and a text field.
 * The field receives focus when the dialog opens. Enter, the confirm button or the cancel button
 * close the dialog; Enter and the confirm button report the entered text through the submit action,
 * while Escape, a background press and the cancel button invoke the negative action.
 */
public final class PromptDialog extends ModalDialog {
    private final Input input = new Input();
    private Consumer<String> submitAction;

    /**
     * Creates a closed prompt dialog.
     *
     * @param title dialog title
     * @param message dialog message
     * @param value initial field text
     * @throws NullPointerException if an argument is null
     */
    public PromptDialog(String title, String message, String value) {
        setTitle(title);
        setMessage(message);
        input.setText(value);
        input.setOnSubmit(this::submit);
        setBody(input);
        addAction("Cancel", false, this::cancel);
        addAction("OK", true, this::submit);
    }

    /**
     * Returns the text field shown in the dialog.
     *
     * @return the editable input
     */
    public Input input() {
        return input;
    }

    /**
     * Returns the current field text.
     *
     * @return the entered text
     */
    public String value() {
        return input.text();
    }

    /**
     * Replaces the field text.
     *
     * @param value new text
     * @throws NullPointerException if {@code value} is null
     */
    public void setValue(String value) {
        input.setText(value);
    }

    /**
     * Sets the placeholder shown while the field is empty.
     *
     * @param placeholder placeholder text, or null to clear it
     */
    public void setPlaceholder(String placeholder) {
        input.setPlaceholder(placeholder);
    }

    /**
     * Returns the action invoked with the entered text when the user submits.
     *
     * @return the submit action, or null when none is assigned
     */
    public Consumer<String> submitAction() {
        return submitAction;
    }

    /**
     * Assigns the action invoked with the entered text when the user submits.
     *
     * @param submitAction action receiving the field text, or null to clear it
     */
    public void setOnSubmit(Consumer<String> submitAction) {
        this.submitAction = submitAction;
    }

    /**
     * Keeps focus in the text field.
     *
     * @return the text field
     */
    @Override
    protected Widget initialFocus() {
        return input;
    }

    private void submit() {
        if (!isOpen()) {
            return;
        }
        try {
            if (submitAction != null) {
                submitAction.accept(input.text());
            }
        } finally {
            close();
        }
    }
}
