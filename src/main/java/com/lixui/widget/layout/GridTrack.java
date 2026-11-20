package com.lixui.widget.layout;

/**
 * A size specification for a {@link Grid} row or column.
 * A track is either fixed to a concrete size or proportional to the free space left after fixed and
 * content-sized tracks. Create tracks with {@link #fixed(float)} and {@link #proportional(float)}.
 */
public sealed interface GridTrack permits GridTrack.Fixed, GridTrack.Proportional {
    /**
     * Creates a track with a fixed size.
     *
     * @param size non-negative track size in logical UI units
     * @return the fixed track
     * @throws IllegalArgumentException if the size is negative or non-finite
     */
    static GridTrack fixed(float size) {
        return new Fixed(size);
    }

    /**
     * Creates a track that shares free space in proportion to its weight.
     *
     * @param weight positive share weight
     * @return the proportional track
     * @throws IllegalArgumentException if the weight is not positive or non-finite
     */
    static GridTrack proportional(float weight) {
        return new Proportional(weight);
    }

    /**
     * A track with a concrete size that does not depend on the container.
     *
     * @param size track size in logical UI units
     */
    record Fixed(float size) implements GridTrack {
        /**
         * Creates a fixed track.
         *
         * @param size non-negative track size in logical UI units
         * @throws IllegalArgumentException if the size is negative or non-finite
         */
        public Fixed {
            if (!Float.isFinite(size) || size < 0) {
                throw new IllegalArgumentException("Grid track size must be finite and non-negative.");
            }
        }
    }

    /**
     * A track that receives a share of the free space in proportion to its weight.
     *
     * @param weight positive share weight
     */
    record Proportional(float weight) implements GridTrack {
        /**
         * Creates a proportional track.
         *
         * @param weight positive share weight
         * @throws IllegalArgumentException if the weight is not positive or non-finite
         */
        public Proportional {
            if (!Float.isFinite(weight) || weight <= 0) {
                throw new IllegalArgumentException("Grid track weight must be finite and positive.");
            }
        }
    }
}
