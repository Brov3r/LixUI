package com.lixui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureOptionsTest {
    @Test
    void defaultOptionsUseClampedLinearSamplingWithoutMipmaps() {
        assertEquals(TextureFilter.LINEAR, TextureOptions.DEFAULT.filter());
        assertEquals(TextureWrap.CLAMP, TextureOptions.DEFAULT.wrapU());
        assertEquals(TextureWrap.CLAMP, TextureOptions.DEFAULT.wrapV());
        assertFalse(TextureOptions.DEFAULT.mipmaps());
    }

    @Test
    void presetsEnableTilingPixelArtAndMipmaps() {
        assertEquals(TextureWrap.REPEAT, TextureOptions.REPEAT.wrapU());
        assertEquals(TextureWrap.REPEAT, TextureOptions.REPEAT.wrapV());
        assertFalse(TextureOptions.REPEAT.mipmaps());

        assertEquals(TextureFilter.NEAREST, TextureOptions.PIXEL_ART.filter());
        assertFalse(TextureOptions.PIXEL_ART.mipmaps());

        assertEquals(TextureFilter.LINEAR, TextureOptions.MIPMAPPED.filter());
        assertTrue(TextureOptions.MIPMAPPED.mipmaps());
    }

    @Test
    void rejectsNullSettings() {
        assertThrows(NullPointerException.class,
                () -> new TextureOptions(null, TextureWrap.CLAMP, TextureWrap.CLAMP, false));
        assertThrows(NullPointerException.class,
                () -> new TextureOptions(TextureFilter.LINEAR, null, TextureWrap.CLAMP, false));
        assertThrows(NullPointerException.class,
                () -> new TextureOptions(TextureFilter.LINEAR, TextureWrap.CLAMP, null, false));
    }
}
