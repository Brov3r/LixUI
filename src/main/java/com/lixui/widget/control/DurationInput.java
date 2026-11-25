package com.lixui.widget.control;

import java.util.List;

/**
 * A unit input preset for durations stored in seconds.
 * The selector offers milliseconds, seconds, minutes and hours. Changing the unit keeps the
 * duration and rewrites only the displayed amount.
 */
public class DurationInput extends UnitInput {
    /** Millisecond unit with a factor of {@code 0.001} seconds. */
    public static final UnitOption MILLISECONDS = new UnitOption("ms", 0.001);
    /** Second unit with a factor of {@code 1} second. */
    public static final UnitOption SECONDS = new UnitOption("s", 1);
    /** Minute unit with a factor of {@code 60} seconds. */
    public static final UnitOption MINUTES = new UnitOption("min", 60);
    /** Hour unit with a factor of {@code 3600} seconds. */
    public static final UnitOption HOURS = new UnitOption("h", 3600);

    /**
     * Creates a duration input showing seconds.
     */
    public DurationInput() {
        setUnits(List.of(MILLISECONDS, SECONDS, MINUTES, HOURS));
        setUnit(SECONDS);
    }

    /**
     * Returns the duration in seconds.
     *
     * @return the stored duration in seconds
     */
    public final double seconds() {
        return value();
    }

    /**
     * Sets the duration in seconds.
     *
     * @param seconds duration in seconds
     * @throws IllegalArgumentException if the duration is non-finite
     */
    public final void setSeconds(double seconds) {
        setValue(seconds);
    }
}
