package com.lixui.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScreenshotOptionsTest {
    @Test
    void defaultsToTheFirstSceneAndFiveFrames() {
        ScreenshotOptions options = ScreenshotOptions.parse(new String[0]);

        assertEquals(1, options.scene());
        assertEquals(5, options.frames());
        assertNull(options.theme());
        assertNull(options.capturePath());
    }

    @Test
    void parsesSceneThemeCaptureAndFrames() {
        ScreenshotOptions options = ScreenshotOptions.parse(new String[]{
                "--scene=3", "--theme=light", "--capture=build/shots/scene-3.png",
                "--frames=9", "--placement=center"});

        assertEquals(3, options.scene());
        assertEquals(ScreenshotOptions.ThemeMode.LIGHT, options.theme());
        assertEquals("build/shots/scene-3.png", options.capturePath());
        assertEquals(9, options.frames());
    }

    @Test
    void parsesTheDarkOverride() {
        ScreenshotOptions options = ScreenshotOptions.parse(new String[]{"--theme=dark"});

        assertEquals(ScreenshotOptions.ThemeMode.DARK, options.theme());
    }

    @Test
    void rejectsValuesOutsideTheSupportedRange() {
        assertThrows(IllegalArgumentException.class,
                () -> ScreenshotOptions.parse(new String[]{"--scene=0"}));
        assertThrows(IllegalArgumentException.class,
                () -> ScreenshotOptions.parse(new String[]{"--scene=5"}));
        assertThrows(IllegalArgumentException.class,
                () -> ScreenshotOptions.parse(new String[]{"--frames=0"}));
        assertThrows(IllegalArgumentException.class,
                () -> ScreenshotOptions.parse(new String[]{"--theme=sepia"}));
        assertThrows(IllegalArgumentException.class,
                () -> ScreenshotOptions.parse(new String[]{"--scene=three"}));
    }
}
