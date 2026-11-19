package com.lixui.widget.control;

import com.lixui.graphics.Color;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.text.TextDecoration;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.UiContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * A label that opens a URI in the default browser when activated.
 * The link displays underlined text in the theme primary color unless an explicit color is set.
 * It activates on a completed left click, a double click or Enter, and requests the pointing hand
 * cursor while hovered.
 */
public class Hyperlink extends Label {
    private static final Logger LOGGER = LoggerFactory.getLogger(Hyperlink.class);

    private String uri;

    /**
     * Creates an empty hyperlink without a URI.
     */
    public Hyperlink() {
        this("", "");
    }

    /**
     * Creates a hyperlink that uses the URI as its text.
     *
     * @param uri URI opened on activation and displayed as the link text
     * @throws NullPointerException if {@code uri} is null
     */
    public Hyperlink(String uri) {
        this(uri, uri);
    }

    /**
     * Creates a hyperlink with explicit text and URI.
     *
     * @param text link text
     * @param uri URI opened on activation
     * @throws NullPointerException if {@code text} or {@code uri} is null
     */
    public Hyperlink(String text, String uri) {
        super(text);
        this.uri = Objects.requireNonNull(uri, "uri");
        setFocusable(true);
        setDecoration(TextDecoration.UNDERLINE);
    }

    /**
     * Returns the URI opened on activation.
     *
     * @return the current URI, or an empty string when none is assigned
     */
    public String uri() {
        return uri;
    }

    /**
     * Replaces the URI opened on activation.
     *
     * @param uri URI to open
     * @throws NullPointerException if {@code uri} is null
     */
    public void setUri(String uri) {
        this.uri = Objects.requireNonNull(uri, "uri");
    }

    /**
     * Opens the assigned URI in the default browser.
     * Disabled links, links without a URI and links that are not attached to a UI context do
     * nothing. Browser launch failures are logged and do not propagate to the caller.
     */
    public void open() {
        if (!isEnabled() || uri.isEmpty()) {
            return;
        }
        UiContext current = context();
        if (current == null) {
            return;
        }
        playSound();
        try {
            current.openUri(uri);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to open URI '{}'.", uri, exception);
        }
    }

    /**
     * Requests the pointing hand cursor for the link.
     *
     * @return the pointer cursor
     */
    @Override
    public Cursor cursor() {
        return Cursor.POINTER;
    }

    /**
     * Uses the theme primary color until an explicit text color is assigned.
     * Disabled links keep the disabled style text color.
     *
     * @param widgetStyle resolved widget style
     * @return the link text color
     */
    @Override
    protected Color resolveTextColor(WidgetStyle widgetStyle) {
        if (textColor() != null) {
            return textColor();
        }
        UiContext current = context();
        if (current == null || state() == WidgetState.DISABLED) {
            return widgetStyle.text(state());
        }
        return current.theme().primary();
    }

    /**
     * Opens the link on a completed left click.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the click is consumed
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        open();
        return true;
    }

    /**
     * Opens the link on the second click of a double click.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the double click is consumed
     */
    @Override
    protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        open();
        return true;
    }

    /**
     * Opens the link when Enter is pressed while the link is focused.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when the key is consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        if (key != Key.ENTER) {
            return false;
        }
        if (!repeat) {
            open();
        }
        return true;
    }
}
