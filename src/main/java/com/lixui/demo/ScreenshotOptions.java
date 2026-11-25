package com.lixui.demo;

import java.util.Objects;

/**
 * Parsed command-line options for the screenshot showcase.
 * Supported flags are {@code --scene=1..4}, {@code --theme=dark|light},
 * {@code --capture=<file.png>} and {@code --frames=<count>}. Unknown flags are ignored so the
 * entry point can be started with the same shared demo arguments as the other demos.
 *
 * @param scene one-based initial scene index
 * @param theme forced theme mode, or null to use each scene's curated theme
 * @param capturePath PNG output path, or null when the showcase runs interactively
 * @param frames number of frames rendered before a capture
 */
record ScreenshotOptions(int scene, ThemeMode theme, String capturePath, int frames) {
    private static final int DEFAULT_SCENE = 1;
    private static final int DEFAULT_FRAMES = 5;
    private static final int SCENE_COUNT = 4;

    /**
     * Creates validated options.
     *
     * @param scene one-based initial scene index
     * @param theme forced theme mode, or null for curated themes
     * @param capturePath PNG output path, or null for interactive mode
     * @param frames number of frames rendered before a capture
     * @throws IllegalArgumentException if a value is out of range
     */
    ScreenshotOptions {
        if (scene < 1 || scene > SCENE_COUNT) {
            throw new IllegalArgumentException("Scene must be between 1 and " + SCENE_COUNT + ".");
        }
        if (frames < 1) {
            throw new IllegalArgumentException("Frame count must be positive.");
        }
        if (capturePath != null && capturePath.isBlank()) {
            throw new IllegalArgumentException("Capture path must not be blank.");
        }
    }

    /**
     * Parses the showcase arguments.
     *
     * @param args command-line arguments
     * @return the parsed options
     * @throws NullPointerException if {@code args} is null
     * @throws IllegalArgumentException if a known flag has an invalid value
     */
    static ScreenshotOptions parse(String[] args) {
        Objects.requireNonNull(args, "args");
        int scene = DEFAULT_SCENE;
        int frames = DEFAULT_FRAMES;
        ThemeMode theme = null;
        String capturePath = null;
        for (String arg : args) {
            if (arg.startsWith("--scene=")) {
                scene = parseInteger(arg, "--scene=", "Scene");
            } else if (arg.startsWith("--frames=")) {
                frames = parseInteger(arg, "--frames=", "Frame count");
            } else if (arg.startsWith("--theme=")) {
                theme = ThemeMode.fromArgument(arg.substring("--theme=".length()));
            } else if (arg.startsWith("--capture=")) {
                capturePath = arg.substring("--capture=".length());
            }
        }
        return new ScreenshotOptions(scene, theme, capturePath, frames);
    }

    private static int parseInteger(String argument, String prefix, String name) {
        String value = argument.substring(prefix.length());
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer: " + argument, exception);
        }
    }

    /**
     * Forced theme mode selected by {@code --theme} and toggled with the T key.
     */
    enum ThemeMode {
        /** Standard dark palette. */
        DARK,
        /** Standard light palette. */
        LIGHT;

        /**
         * Resolves a theme mode from its argument value.
         *
         * @param value lower-case flag value
         * @return the matching mode
         * @throws IllegalArgumentException if the value is unknown
         */
        static ThemeMode fromArgument(String value) {
            return switch (value) {
                case "dark" -> DARK;
                case "light" -> LIGHT;
                default -> throw new IllegalArgumentException("Unknown theme: " + value);
            };
        }

        /**
         * Returns the opposite theme mode.
         *
         * @return the other mode
         */
        ThemeMode toggled() {
            return this == DARK ? LIGHT : DARK;
        }
    }
}
