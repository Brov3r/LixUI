package com.lixui.theme;

/**
 * Visual interaction state used to resolve colors from a widget style.
 * States are ordered from least to most specific by {@link #priority()}.
 */
public enum WidgetState {
    /** The widget is enabled and idle. */
    NORMAL(0),
    /** The widget or one of its descendants has keyboard focus. */
    FOCUSED(1),
    /** The pointer is over the widget. */
    HOVERED(2),
    /** A pointer button is pressed on the widget. */
    PRESSED(3),
    /** The widget or an ancestor is disabled. */
    DISABLED(4);

    private final int priority;

    WidgetState(int priority) {
        this.priority = priority;
    }

    /**
     * Returns the state precedence.
     *
     * @return a larger value for a more specific visual state
     */
    public int priority() {
        return priority;
    }
}
