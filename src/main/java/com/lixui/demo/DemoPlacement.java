package com.lixui.demo;

/**
 * Placement of a demo window on the primary monitor.
 */
enum DemoPlacement {
    /** A centered window with the default size. */
    CENTERED,
    /** The left half of the monitor work area, preserving the 16:9 aspect ratio. */
    LEFT_HALF,
    /** The right half of the monitor work area, preserving the 16:9 aspect ratio. */
    RIGHT_HALF;

    /**
     * Parses the optional {@code --placement=left|right} program argument.
     *
     * @param args program arguments
     * @return the requested placement, or {@link #CENTERED} when the flag is absent
     * @throws IllegalArgumentException if the flag value is unknown
     */
    static DemoPlacement fromArgs(String[] args) {
        for (String arg : args) {
            if (!arg.startsWith("--placement=")) {
                continue;
            }
            return switch (arg.substring("--placement=".length())) {
                case "left" -> LEFT_HALF;
                case "right" -> RIGHT_HALF;
                case "center", "centered" -> CENTERED;
                default -> throw new IllegalArgumentException("Unknown placement: " + arg);
            };
        }
        return CENTERED;
    }
}
