package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.platform.UiPlatform;
import com.lixui.render.RenderBackend;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontStyle;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.SystemFonts;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;

import java.util.Optional;

/**
 * Widget-based demo scene shared by the OpenGL and Vulkan demos.
 * The scene exercises layout, clipping, input, focus, wrapping and context resources.
 */
final class DemoWidgetScene implements AutoCloseable {
    private final WidgetManager manager;
    private final Dashboard dashboard;
    private Viewport viewport;

    DemoWidgetScene(InputState input, RenderBackend backend, UiPlatform platform) {
        manager = new WidgetManager(input, backend, platform);
        UiContext context = manager.context();
        Font regular = loadFont(context, FontStyle.REGULAR);
        Font bold = loadFont(context, FontStyle.BOLD);
        if (regular != null) {
            manager.setDefaultFont(regular);
        }
        Texture texture = context.createTexture(DemoTextures.checkerboard());
        dashboard = new Dashboard(texture, regular, bold, context.theme());
        manager.add(dashboard);
    }

    /**
     * Updates the viewport and stretches the dashboard over the whole window.
     *
     * @param viewport target viewport
     */
    void resize(Viewport viewport) {
        if (viewport.equals(this.viewport)) {
            return;
        }
        this.viewport = viewport;
        manager.resize(viewport);
        dashboard.setBounds(new Rect(0, 0, viewport.logicalWidth(), viewport.logicalHeight()));
    }

    /**
     * Updates the widget tree and drains input events.
     *
     * @param deltaSeconds elapsed time in seconds
     */
    void update(float deltaSeconds) {
        manager.update(deltaSeconds);
    }

    /**
     * Lays out and renders the widget tree.
     */
    void render() {
        manager.render();
    }

    /**
     * Closes the manager and releases context-owned textures and fonts.
     */
    @Override
    public void close() {
        manager.close();
    }

    private static Font loadFont(UiContext context, FontStyle style) {
        for (String family : DemoFonts.FAMILIES) {
            Optional<Font> font = context.createFont(family, 22, style);
            if (font.isPresent()) {
                return font.get();
            }
        }
        return SystemFonts.findDefault()
                .map(data -> context.createFont(data, 22, style))
                .orElse(null);
    }

    private static final class Dashboard extends Widget {
        private final DemoText title;
        private final DemoText subtitle;
        private final DemoButton button;
        private final DemoCards cards;
        private final DemoText details;

        Dashboard(Texture texture, Font regular, Font bold, Theme theme) {
            title = new DemoText("LixUI widget demo", regular, false, false, 0, TextOverflow.CLIP);
            subtitle = new DemoText("Retained widgets · layout · lifecycle · context",
                    regular, true, false, 0, TextOverflow.CLIP);
            button = new DemoButton("Click me", bold != null ? bold : regular);
            cards = new DemoCards(texture, regular);
            details = new DemoText(
                    "Hover, click, double-click, drag, scroll and Tab focus are handled by the "
                            + "widget manager. The card strip is clipped to its bounds and scrolls "
                            + "with the mouse wheel. Text wraps and ellipsizes inside its widget.",
                    regular, true, true, 3, TextOverflow.ELLIPSIS);
            setStyle(theme.panelStyle());
            button.setStyle(theme.primaryStyle());
            cards.setStyle(theme.panelStyle());
            add(title);
            add(subtitle);
            add(button);
            add(cards);
            add(details);
        }

        /**
         * Arranges the demo elements from the current dashboard size.
         */
        @Override
        protected void onLayout() {
            float width = Math.max(240, bounds().width());
            title.setBounds(new Rect(32, 24, width - 64, 32));
            subtitle.setBounds(new Rect(32, 62, width - 64, 24));
            button.setBounds(new Rect(32, 108, 200, 46));
            cards.setBounds(new Rect(32, 176, width - 64, 230));
            details.setBounds(new Rect(32, 430, width - 64,
                    Math.max(80, bounds().height() - 462)));
        }

        /**
         * Paints the window background.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            Theme theme = context().theme();
            renderer.rect(localBounds(), theme.background());
        }
    }

    private static final class DemoText extends Widget {
        private final String text;
        private final Font font;
        private final boolean muted;
        private final boolean wrap;
        private final int maxLines;
        private final TextOverflow overflow;

        DemoText(String text, Font font, boolean muted, boolean wrap, int maxLines,
                 TextOverflow overflow) {
            this.text = text;
            this.font = font;
            this.muted = muted;
            this.wrap = wrap;
            this.maxLines = maxLines;
            this.overflow = overflow;
        }

        /**
         * Draws the text using wrapping and the current theme colors.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            Font resolved = font != null ? font : font();
            if (resolved == null) {
                return;
            }
            Theme theme = context().theme();
            Color color = muted ? theme.textMuted() : theme.text();
            TextStyle style = new TextStyle(wrap ? bounds().width() : 0, 0, maxLines, overflow,
                    HorizontalAlignment.LEFT, VerticalAlignment.TOP);
            renderer.text(resolved, text, 0, 0, color, style);
        }
    }

    private static final class DemoButton extends Widget {
        private final String label;
        private final Font font;
        private int clicks;

        DemoButton(String label, Font font) {
            this.label = label;
            this.font = font;
            setFocusable(true);
        }

        /**
         * Requests the pointer cursor for the button.
         *
         * @return the pointer cursor
         */
        @Override
        public Cursor cursor() {
            return Cursor.POINTER;
        }

        /**
         * Draws the button surface, focus border and click counter.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            WidgetStyle style = style();
            if (style == null) {
                return;
            }
            WidgetState state = !isEnabled() ? WidgetState.DISABLED
                    : isPressed() ? WidgetState.PRESSED
                    : isHovered() ? WidgetState.HOVERED
                    : isFocused() ? WidgetState.FOCUSED
                    : WidgetState.NORMAL;
            renderer.roundedRect(localBounds(), style.cornerRadius(), style.background(state));
            if (style.borderWidth() > 0) {
                renderer.strokeRoundedRect(localBounds(), style.cornerRadius(), style.borderWidth(),
                        Paint.solid(style.border(state)));
            }
            Font resolved = font != null ? font : font();
            if (resolved != null) {
                renderer.text(resolved, label + "  " + clicks,
                        bounds().width() / 2, bounds().height() / 2, style.text(state),
                        TextStyle.of(HorizontalAlignment.CENTER, VerticalAlignment.CENTER));
            }
        }

        /**
         * Counts a single click.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button clicked button
         * @param modifiers active modifiers
         * @return true when the click is consumed
         */
        @Override
        protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
            clicks++;
            return true;
        }

        /**
         * Counts the second press of a double click.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button clicked button
         * @param modifiers active modifiers
         * @return true when the double click is consumed
         */
        @Override
        protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
            clicks++;
            return true;
        }
    }

    private static final class DemoCards extends Widget {
        private static final int CARDS = 7;
        private static final float CARD_WIDTH = 132;
        private static final float GAP = 16;
        private static final Rect FULL_UV = new Rect(0, 0, 1, 1);
        private final Texture texture;
        private final Font font;
        private float scroll;
        private float lastDragX;

        DemoCards(Texture texture, Font font) {
            this.texture = texture;
            this.font = font;
            setClipToBounds(true);
        }

        /**
         * Requests a horizontal resize cursor to advertise scrolling.
         *
         * @return the horizontal resize cursor
         */
        @Override
        public Cursor cursor() {
            return Cursor.RESIZE_HORIZONTAL;
        }

        /**
         * Draws a clipped strip of image cards.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            WidgetStyle style = style();
            if (style == null) {
                return;
            }
            renderer.roundedRect(localBounds(), style.cornerRadius(),
                    style.background(WidgetState.NORMAL));
            float height = bounds().height() - 32;
            for (int index = 0; index < CARDS; index++) {
                float x = 16 + index * (CARD_WIDTH + GAP) - scroll;
                Rect card = new Rect(x, 16, CARD_WIDTH, height);
                if (card.width() <= 0 || card.x() + card.width() < 0
                        || card.x() > bounds().width()) {
                    continue;
                }
                renderer.roundedRect(card, style.cornerRadius(), Color.hex("#2C303A"));
                renderer.image(texture, new Rect(card.x() + 12, card.y() + 12,
                        card.width() - 24, card.height() - 58), 12, FULL_UV, Color.WHITE);
                if (font != null) {
                    renderer.text(font, "Card " + (index + 1),
                            card.x() + card.width() / 2, card.bottom() - 24,
                            Color.hex("#F2F4F8"),
                            TextStyle.of(HorizontalAlignment.CENTER, VerticalAlignment.CENTER));
                }
            }
        }

        /**
         * Scrolls the card strip with the mouse wheel.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param deltaX horizontal scroll amount
         * @param deltaY vertical scroll amount
         * @return true when the scroll is consumed
         */
        @Override
        protected boolean onPointerScroll(float x, float y, float deltaX, float deltaY) {
            scroll = clampScroll(scroll - deltaY * 48);
            return true;
        }

        /**
         * Remembers the press position so a horizontal drag can scroll the strip.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button pressed button
         * @param modifiers active modifiers
         * @return true when the press is consumed
         */
        @Override
        protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
            lastDragX = x;
            return true;
        }

        /**
         * Starts scrolling after the drag threshold is crossed.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button pressed button
         * @param modifiers modifiers captured at press time
         * @return true when the drag start is consumed
         */
        @Override
        protected boolean onDragStart(float x, float y, MouseButton button, Modifiers modifiers) {
            lastDragX = x;
            return true;
        }

        /**
         * Scrolls the card strip horizontally while dragging.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button pressed button
         * @param modifiers modifiers captured at press time
         * @return true when the drag is consumed
         */
        @Override
        protected boolean onDrag(float x, float y, MouseButton button, Modifiers modifiers) {
            scroll = clampScroll(scroll + lastDragX - x);
            lastDragX = x;
            return true;
        }

        private float clampScroll(float value) {
            float contentWidth = 32 + CARDS * CARD_WIDTH + (CARDS - 1) * GAP;
            float maxScroll = Math.max(0, contentWidth - bounds().width());
            return Math.max(0, Math.min(maxScroll, value));
        }
    }
}
