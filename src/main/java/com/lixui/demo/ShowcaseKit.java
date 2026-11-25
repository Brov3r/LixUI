package com.lixui.demo;

import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.FontFamily;
import com.lixui.text.FontStyle;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.SystemFonts;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.control.Badge;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.Label;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

import java.util.Optional;

/**
 * Creates fonts, labels, cards and layout helpers shared by the screenshot scenes.
 * The kit keeps the manager and input queue so scene builders can request layouts and queue
 * synthetic pointer moves for the pre-opened navigation overlays.
 */
final class ShowcaseKit {
    private static final float BODY_SIZE = 15;
    private static final float TITLE_SIZE = 24;

    private final WidgetManager manager;
    private final InputState input;
    private final FontFamily body;
    private final FontFamily title;

    ShowcaseKit(WidgetManager manager, InputState input) {
        this.manager = manager;
        this.input = input;
        Optional<FontData> data = SystemFonts.findDefault();
        if (data.isEmpty()) {
            body = null;
            title = null;
        } else {
            Font regular = manager.context().createFont(data.get(), BODY_SIZE);
            manager.setDefaultFont(regular);
            body = new FontFamily(regular,
                    manager.context().createFont(data.get(), BODY_SIZE, FontStyle.BOLD),
                    manager.context().createFont(data.get(), BODY_SIZE, FontStyle.ITALIC),
                    manager.context().createFont(data.get(), BODY_SIZE, FontStyle.BOLD_ITALIC));
            Font titleRegular = manager.context().createFont(data.get(), TITLE_SIZE);
            title = new FontFamily(titleRegular,
                    manager.context().createFont(data.get(), TITLE_SIZE, FontStyle.BOLD),
                    manager.context().createFont(data.get(), TITLE_SIZE, FontStyle.ITALIC),
                    manager.context().createFont(data.get(), TITLE_SIZE, FontStyle.BOLD_ITALIC));
        }
    }

    WidgetManager manager() {
        return manager;
    }

    InputState input() {
        return input;
    }

    FontFamily bodyFamily() {
        return body;
    }

    FontFamily titleFamily() {
        return title;
    }

    Font font(FontStyle style) {
        return body == null ? null : body.resolve(style);
    }

    Label text(String value) {
        return configure(new Label(value), body, false);
    }

    Label heading(String value) {
        Label label = text(value);
        label.setFontStyle(FontStyle.BOLD);
        return label;
    }

    Label wrapped(String value) {
        return configure(new Label(value), body, true);
    }

    Label muted(String value) {
        return configure(new MutedLabel(value), body, false);
    }

    Label wrappedMuted(String value) {
        return configure(new MutedLabel(value), body, true);
    }

    Label value(String value) {
        return configure(new AccentLabel(value), title, false);
    }

    Label centeredHeading(String value) {
        Label label = heading(value);
        label.setAlignment(HorizontalAlignment.CENTER);
        return label;
    }

    Button primaryButton(String value) {
        return new PrimaryButton(value);
    }

    Badge badge(String value, Color accent) {
        Badge badge = new Badge(value);
        badge.setPadding(6);
        badge.setBackgroundColor(accent.withAlpha(0.16f));
        badge.setBorderWidth(1);
        badge.setBorderColor(accent.withAlpha(0.55f));
        badge.setTextColor(accent);
        return badge;
    }

    ShowcaseCard card() {
        return new ShowcaseCard();
    }

    VBox field(String label, Widget control) {
        VBox field = new VBox();
        field.setGap(4);
        field.add(text(label)).add(control);
        return field;
    }

    HBox header(String titleText, String subtitleText, Widget trailing) {
        HBox header = new HBox();
        header.setGap(16);
        header.setCrossAlignment(HBox.CrossAlignment.CENTER);
        VBox titles = new VBox();
        titles.setGap(2);
        titles.add(configure(new Label(titleText), title, false))
                .add(wrappedMuted(subtitleText));
        header.add(titles).add(spacer()).add(trailing);
        return header;
    }

    ShowcaseSpacer spacer() {
        return new ShowcaseSpacer();
    }

    static Widget fixWidth(Widget widget, float width) {
        return ShowcaseSized.width(widget, width);
    }

    static Widget fixHeight(Widget widget, float height) {
        return ShowcaseSized.height(widget, height);
    }

    static Widget fixSize(Widget widget, float width, float height) {
        return ShowcaseSized.size(widget, width, height);
    }

    private static Label configure(Label label, FontFamily family, boolean wrap) {
        if (family != null) {
            label.setFontFamily(family);
        }
        label.setAutoSize(false);
        label.setWrap(wrap);
        return label;
    }

    /**
     * A label that follows the theme's secondary text color at draw time.
     */
    static final class MutedLabel extends Label {
        MutedLabel(String text) {
            super(text);
        }

        @Override
        protected Color resolveTextColor(WidgetStyle style) {
            Color explicit = textColor();
            if (explicit != null) {
                return explicit;
            }
            return context() == null ? super.resolveTextColor(style) : context().theme().textMuted();
        }
    }

    /**
     * A label that follows the theme's accent color at draw time.
     */
    static final class AccentLabel extends Label {
        AccentLabel(String text) {
            super(text);
        }

        @Override
        protected Color resolveTextColor(WidgetStyle style) {
            Color explicit = textColor();
            if (explicit != null) {
                return explicit;
            }
            if (context() == null) {
                return super.resolveTextColor(style);
            }
            Color accent = context().theme().primary();
            if (context().theme().background().luminance() <= 0.5f) {
                return accent;
            }
            return new Color(accent.r() * 0.55f, accent.g() * 0.55f, accent.b() * 0.55f, accent.a());
        }
    }

    /**
     * A button that resolves the theme's primary action style at draw time.
     */
    static final class PrimaryButton extends Button {
        PrimaryButton(String text) {
            super(text);
        }

        @Override
        protected WidgetStyle defaultStyle() {
            return context() == null ? null : context().theme().primaryStyle();
        }
    }
}
