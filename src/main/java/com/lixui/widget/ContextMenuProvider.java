package com.lixui.widget;

/**
 * Creates a context menu for a widget at a pointer position.
 * The provider is invoked on a right-button press. Returning null keeps the default widget
 * behavior and lets the search continue with the parent widget.
 */
@FunctionalInterface
public interface ContextMenuProvider {
    /**
     * Creates a menu for a widget.
     *
     * @param widget widget that owns the provider
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @return the menu to open, or null when the widget has no menu at this position
     */
    ContextMenuHandle menuFor(Widget widget, float x, float y);
}
