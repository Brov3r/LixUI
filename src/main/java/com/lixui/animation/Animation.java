package com.lixui.animation;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A value animation from a start value to an end value over a duration.
 * Progress is measured in seconds and is advanced by {@link #update(float)} or by an
 * {@link Animator}. The update callback receives the eased value on every completed tick.
 */
public final class Animation {
    private final float from;
    private final float to;
    private final float duration;
    private final Easing easing;
    private final Consumer<Float> onUpdate;
    private Runnable onComplete;
    private float delay;
    private float elapsed;
    private float progress;
    private float value;
    private boolean finished;

    /**
     * Creates an animation.
     *
     * @param from start value
     * @param to end value
     * @param duration duration in seconds
     * @param easing timing curve
     * @param onUpdate callback receiving the interpolated value
     * @throws NullPointerException if {@code easing} or {@code onUpdate} is null
     * @throws IllegalArgumentException if a value or the duration is non-finite or negative
     */
    public Animation(float from, float to, float duration, Easing easing,
                     Consumer<Float> onUpdate) {
        if (!Float.isFinite(from) || !Float.isFinite(to)) {
            throw new IllegalArgumentException("Animation values must be finite.");
        }
        if (!Float.isFinite(duration) || duration < 0) {
            throw new IllegalArgumentException("Animation duration must be finite and non-negative.");
        }
        this.from = from;
        this.to = to;
        this.duration = duration;
        this.easing = Objects.requireNonNull(easing, "easing");
        this.onUpdate = Objects.requireNonNull(onUpdate, "onUpdate");
        this.value = from;
    }

    /**
     * Returns the start value.
     *
     * @return the start value
     */
    public float from() {
        return from;
    }

    /**
     * Returns the end value.
     *
     * @return the end value
     */
    public float to() {
        return to;
    }

    /**
     * Returns the duration.
     *
     * @return the duration in seconds
     */
    public float duration() {
        return duration;
    }

    /**
     * Returns the delay before the animation starts.
     *
     * @return the delay in seconds
     */
    public float delay() {
        return delay;
    }

    /**
     * Sets the delay before the animation starts.
     *
     * @param delay non-negative delay in seconds
     * @return this animation for chaining
     * @throws IllegalArgumentException if the delay is negative or non-finite
     */
    public Animation setDelay(float delay) {
        if (!Float.isFinite(delay) || delay < 0) {
            throw new IllegalArgumentException("Animation delay must be finite and non-negative.");
        }
        this.delay = delay;
        return this;
    }

    /**
     * Returns the completion callback.
     *
     * @return the callback, or null when none is assigned
     */
    public Runnable onComplete() {
        return onComplete;
    }

    /**
     * Assigns a callback invoked once when the animation reaches its end value.
     *
     * @param onComplete completion callback, or null to clear it
     * @return this animation for chaining
     */
    public Animation setOnComplete(Runnable onComplete) {
        this.onComplete = onComplete;
        return this;
    }

    /**
     * Returns the current interpolated value.
     *
     * @return the current value
     */
    public float value() {
        return value;
    }

    /**
     * Returns the raw normalized progress, excluding the delay.
     *
     * @return progress in {@code [0, 1]}
     */
    public float progress() {
        return progress;
    }

    /**
     * Reports whether the animation has reached its end value.
     *
     * @return true when the animation is finished
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Advances the animation.
     * The update callback is not called while the delay is still active.
     *
     * @param deltaSeconds elapsed time in seconds
     * @throws IllegalArgumentException if the delta is negative or non-finite
     */
    public void update(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0) {
            throw new IllegalArgumentException("Animation delta must be finite and non-negative.");
        }
        if (finished) {
            return;
        }
        elapsed += deltaSeconds;
        if (elapsed < delay) {
            return;
        }
        progress = duration <= 0 ? 1 : Math.min(1, (elapsed - delay) / duration);
        value = from + (to - from) * easing.apply(progress);
        boolean complete = progress >= 1;
        if (complete) {
            finished = true;
        }
        onUpdate.accept(value);
        if (complete && onComplete != null) {
            onComplete.run();
        }
    }
}
