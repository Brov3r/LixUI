package com.lixui.widget;

import com.lixui.widget.testsupport.TestBackend;
import com.lixui.geometry.Rect;
import com.lixui.platform.Cursor;
import com.lixui.platform.UiPlatform;
import com.lixui.render.ImageData;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.theme.Theme;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiContextTest {
    private static final Viewport VIEWPORT = new Viewport(1600, 1200, 800, 600);
    private static final ImageData IMAGE = new ImageData(1, 1, new byte[4]);

    @Test
    void exposesViewportScaleThemeClipboardAndCursor() {
        TestBackend backend = new TestBackend();
        RecordingPlatform platform = new RecordingPlatform();
        try (UiContext context = new UiContext(new RenderManager(backend), platform)) {
            assertEquals(Rect.EMPTY, context.viewport());
            assertEquals(1, context.scaleX());
            assertEquals(1, context.scaleY());
            assertEquals(1, context.density());
            assertEquals(Theme.dark(), context.theme());
            assertEquals(Cursor.DEFAULT, context.cursor());
            assertEquals("", context.clipboardText());

            context.setViewport(VIEWPORT);
            context.setTheme(Theme.light());

            assertEquals(new Rect(0, 0, 800, 600), context.viewport());
            assertEquals(2, context.scaleX());
            assertEquals(2, context.scaleY());
            assertEquals(2, context.density());
            assertEquals(Theme.light(), context.theme());

            context.setClipboardText("clipboard");
            assertEquals("clipboard", context.clipboardText());
            assertEquals(1, platform.clipboardWrites);

            context.setCursor(Cursor.POINTER);
            context.setCursor(Cursor.POINTER);

            assertEquals(Cursor.POINTER, context.cursor());
            assertEquals(1, platform.cursorWrites);
            assertSame(Cursor.POINTER, platform.cursor);
        }
    }

    @Test
    void bundledIconPreservesOriginalCoverageAndTintAcrossTransparentEdges() {
        TestBackend backend = new TestBackend();
        try (UiContext context = new UiContext(new RenderManager(backend), UiPlatform.NONE)) {
            context.createBundledIcon("copy.svg", 16);
            byte[] source = ImageData.fromResource("assets/icons/copy.svg", 16, 16).pixels();
            byte[] uploaded = backend.lastImage().pixels();
            assertEquals(source.length, uploaded.length);
            for (int i = 0; i < source.length; i += 4) {
                assertEquals(source[i + 3], uploaded[i + 3]);
                for (int channel = 0; channel < 3; channel++) {
                    assertEquals(255, Byte.toUnsignedInt(uploaded[i + channel]));
                }
            }
        }
    }

    @Test
    void treatsMissingPlatformClipboardAsEmpty() {
        RecordingPlatform platform = new RecordingPlatform();
        platform.clipboard = null;
        try (UiContext context = new UiContext(new RenderManager(new TestBackend()), platform)) {
            assertEquals("", context.clipboardText());
        }
    }

    @Test
    void releasesOwnedTexturesAndFontsOnClose() {
        TestBackend backend = new TestBackend();
        RecordingPlatform platform = new RecordingPlatform();
        UiContext context = new UiContext(new RenderManager(backend), platform);
        Texture texture = context.createTexture(IMAGE);
        Optional<FontData> fontData = SystemFonts.findDefault();
        Assumptions.assumeTrue(fontData.isPresent());
        Font font = context.createFont(fontData.get(), 12);
        context.setDefaultFont(font);

        context.close();

        assertEquals(2, backend.destroyed().size());
        assertTrue(backend.destroyed().contains(texture));
        assertNull(context.defaultFont());
        assertThrows(IllegalStateException.class, () -> context.createTexture(IMAGE));
        assertThrows(IllegalStateException.class, context::clipboardText);

        context.close();
        assertEquals(2, backend.destroyed().size());
    }

    @Test
    void destroysOwnedResourcesOnlyOnce() {
        TestBackend backend = new TestBackend();
        try (UiContext context = new UiContext(new RenderManager(backend), new RecordingPlatform())) {
            Texture texture = context.createTexture(IMAGE);

            context.destroyTexture(texture);
            context.destroyTexture(texture);

            assertEquals(1, backend.destroyed().size());
            assertTrue(backend.destroyed().contains(texture));
        }
    }

    @Test
    void destroyingTheDefaultFontClearsIt() {
        Optional<FontData> fontData = SystemFonts.findDefault();
        Assumptions.assumeTrue(fontData.isPresent());
        TestBackend backend = new TestBackend();
        try (UiContext context = new UiContext(new RenderManager(backend), new RecordingPlatform())) {
            Font font = context.createFont(fontData.get(), 12);
            context.setDefaultFont(font);

            context.destroyFont(font);
            context.destroyFont(font);

            assertNull(context.defaultFont());
            assertEquals(1, backend.destroyed().size());
        }
    }

    @Test
    void rejectsNullPlatformArgumentsAndClosedOperations() {
        assertThrows(NullPointerException.class,
                () -> new UiContext(new RenderManager(new TestBackend()), null));
        RecordingPlatform platform = new RecordingPlatform();
        UiContext context = new UiContext(new RenderManager(new TestBackend()), platform);
        assertThrows(NullPointerException.class, () -> context.setClipboardText(null));
        assertThrows(NullPointerException.class, () -> context.setCursor(null));
        assertThrows(NullPointerException.class, () -> context.createTexture(null));
        assertThrows(NullPointerException.class, () -> context.destroyTexture(null));
        context.close();
    }

    private record RecordedTexture(int width, int height) implements Texture {
    }


    private static final class RecordingPlatform implements UiPlatform {
        private String clipboard = "";
        private Cursor cursor = Cursor.DEFAULT;
        private int clipboardWrites;
        private int cursorWrites;

        @Override
        public String clipboardText() {
            return clipboard;
        }

        @Override
        public void setClipboardText(String text) {
            clipboard = text;
            clipboardWrites++;
        }

        @Override
        public void setCursor(Cursor cursor) {
            this.cursor = cursor;
            cursorWrites++;
        }
    }
}
