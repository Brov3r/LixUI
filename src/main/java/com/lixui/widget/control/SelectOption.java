package com.lixui.widget.control;

import com.lixui.render.Texture;

import java.util.Objects;

/**
 * A single option shown by a dropdown or multi-select popup.
 * The label is displayed in the field and in the popup; the value is returned to callers and does
 * not need to be unique.
 */
public class SelectOption {
    private String label;
    private Object value;
    private Texture icon;
    private boolean disabled;

    /**
     * Creates an enabled option without an icon or explicit value.
     *
     * @param label option label
     * @throws NullPointerException if {@code label} is null
     */
    public SelectOption(String label) {
        this(label, label);
    }

    /**
     * Creates an enabled option without an icon.
     *
     * @param label option label
     * @param value caller-defined value
     * @throws NullPointerException if {@code label} is null
     */
    public SelectOption(String label, Object value) {
        this.label = Objects.requireNonNull(label, "label");
        this.value = value;
    }

    /**
     * Returns the option label.
     *
     * @return the label, never null
     */
    public final String label() {
        return label;
    }

    /**
     * Sets the option label.
     *
     * @param label option label
     * @throws NullPointerException if {@code label} is null
     */
    public final void setLabel(String label) {
        this.label = Objects.requireNonNull(label, "label");
    }

    /**
     * Returns the caller-defined option value.
     *
     * @return the value, or null when none is assigned
     */
    public final Object value() {
        return value;
    }

    /**
     * Sets the caller-defined option value.
     *
     * @param value new value, or null to clear it
     */
    public final void setValue(Object value) {
        this.value = value;
    }

    /**
     * Returns the option icon.
     *
     * @return the caller-owned icon, or null when the option has no icon
     */
    public final Texture icon() {
        return icon;
    }

    /**
     * Sets the option icon.
     *
     * @param icon caller-owned icon, or null to clear it
     */
    public final void setIcon(Texture icon) {
        this.icon = icon;
    }

    /**
     * Reports whether the option can be selected.
     *
     * @return true when the option is disabled
     */
    public final boolean isDisabled() {
        return disabled;
    }

    /**
     * Enables or disables the option.
     *
     * @param disabled true to prevent selection
     */
    public final void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }
}
