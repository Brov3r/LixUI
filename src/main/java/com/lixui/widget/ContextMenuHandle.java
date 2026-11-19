package com.lixui.widget;

/**
 * A transient context menu that can be opened at a viewport position.
 * The widget manager only depends on this abstraction, so custom menus are not coupled to a
 * concrete widget implementation.
 */
public interface ContextMenuHandle {
    /**
     * Opens the menu at a viewport position.
     *
     * @param context context that owns the widget manager
     * @param x horizontal viewport position
     * @param y vertical viewport position
     */
    void open(UiContext context, float x, float y);
}
