package com.lixui.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Updates a collection of running animations and removes finished ones.
 * Instances are single-threaded and are intended to be updated once per frame.
 */
public final class Animator {
    private final List<Animation> animations = new ArrayList<>();

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
     */
    public Animation animate(float from, float to, float duration, Easing easing,
                             Consumer<Float> onUpdate) {
        Animation animation = new Animation(from, to, duration, easing, onUpdate);
        play(animation);
        return animation;
    }

    /**
     * Starts an existing animation.
     *
     * @param animation animation to start
     * @throws NullPointerException if {@code animation} is null
     */
    public void play(Animation animation) {
        Objects.requireNonNull(animation, "animation");
        if (!animations.contains(animation)) {
            animations.add(animation);
        }
    }

    /**
     * Advances every running animation and drops finished ones.
     *
     * @param deltaSeconds elapsed time in seconds
     * @throws IllegalArgumentException if the delta is negative or non-finite
     */
    public void update(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0) {
            throw new IllegalArgumentException("Animation delta must be finite and non-negative.");
        }
        for (Animation animation : List.copyOf(animations)) {
            if (animations.contains(animation)) {
                animation.update(deltaSeconds);
            }
        }
        animations.removeIf(Animation::isFinished);
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
