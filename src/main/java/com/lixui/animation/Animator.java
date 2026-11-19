package com.lixui.animation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Updates a collection of running animations and removes finished ones.
 * Instances are single-threaded and are intended to be updated once per frame.
 */
public final class Animator {
    private final Set<Animation> animations = new LinkedHashSet<>();
    private final List<Animation> frameAnimations = new ArrayList<>();
    private boolean updating;

    /**
     * Creates and starts an animation.
     *
     * @param from start value
     * @param to end value
     * @param duration duration in seconds
     * @param easing timing curve
     * @param onUpdate callback receiving the interpolated value
     * @return the started animation
     * @throws NullPointerException if {@code easing} or {@code onUpdate} is null
     * @throws IllegalArgumentException if a value is non-finite or the duration is non-finite or negative
     */
    public Animation animate(float from, float to, float duration, Easing easing,
                             Consumer<Float> onUpdate) {
        Animation animation = new Animation(from, to, duration, easing, onUpdate);
        play(animation);
        return animation;
    }

    /**
     * Starts an existing animation unless it is already running or finished.
     *
     * @param animation animation to start
     * @throws NullPointerException if {@code animation} is null
     */
    public void play(Animation animation) {
        Objects.requireNonNull(animation, "animation");
        if (!animation.isFinished()) {
            animations.add(animation);
        }
    }

    /**
     * Advances every running animation and drops finished ones.
     * New animations added by callbacks are advanced on the next update.
     *
     * @param deltaSeconds elapsed time in seconds
     * @throws IllegalArgumentException if the delta is negative or non-finite
     * @throws IllegalStateException if called recursively from an animation callback
     */
    public void update(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0) {
            throw new IllegalArgumentException("Animation delta must be finite and non-negative.");
        }
        if (updating) {
            throw new IllegalStateException("Animator updates must not be recursive.");
        }
        updating = true;
        try {
            for (Animation animation : animations) {
                frameAnimations.add(animation);
            }
            for (Animation animation : frameAnimations) {
                if (animations.contains(animation)) {
                    animation.update(deltaSeconds);
                }
            }
        } finally {
            animations.removeIf(Animation::isFinished);
            frameAnimations.clear();
            updating = false;
        }
    }

    /**
     * Stops and removes an animation without running its completion callback.
     *
     * @param animation animation to cancel
     * @throws NullPointerException if {@code animation} is null
     */
    public void cancel(Animation animation) {
        animations.remove(Objects.requireNonNull(animation, "animation"));
    }

    /**
     * Removes every running animation without running completion callbacks.
     */
    public void clear() {
        animations.clear();
    }

    /**
     * Returns the number of running animations.
     *
     * @return the running animation count
     */
    public int size() {
        return animations.size();
    }

    /**
     * Reports whether no animations are running.
     *
     * @return true when the animator is empty
     */
    public boolean isEmpty() {
        return animations.isEmpty();
    }
}
