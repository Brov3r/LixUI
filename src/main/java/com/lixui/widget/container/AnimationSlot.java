package com.lixui.widget.container;

import com.lixui.animation.Animation;
import com.lixui.animation.Animator;
import com.lixui.animation.Easing;

import java.util.function.Consumer;

/**
 * Owns at most one animation and the animator that started it.
 * Starting a new animation cancels the previous one without running its completion callback.
 */
final class AnimationSlot {
    private Animator owner;
    private Animation animation;
    private float target;

    /**
     * Returns the target of the running animation, or the supplied value when idle.
     *
     * @param idleValue value returned while no animation is running
     * @return the animation target or {@code idleValue}
     */
    float target(float idleValue) {
        return animation == null ? idleValue : target;
    }

    /**
     * Reports whether an animation is currently running.
     *
     * @return true while an animation is active
     */
    boolean isActive() {
        return animation != null;
    }

    /**
     * Cancels the running animation and starts a new one.
     * A null animator applies the end value immediately.
     *
     * @param animator animator to run the animation, or null for an immediate update
     * @param from start value
     * @param to end value
     * @param duration duration in seconds
     * @param easing timing curve
     * @param onUpdate callback receiving the interpolated value
     * @param onComplete callback invoked after the animation finishes, or null
     */
    void start(Animator animator, float from, float to, float duration, Easing easing,
               Consumer<Float> onUpdate, Runnable onComplete) {
        cancel();
        target = to;
        if (animator == null) {
            onUpdate.accept(to);
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        owner = animator;
        animation = animator.animate(from, to, duration, easing, onUpdate).setOnComplete(() -> {
            clear();
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Cancels the running animation without running its completion callback.
     */
    void cancel() {
        if (animation != null) {
            owner.cancel(animation);
        }
        clear();
    }

    private void clear() {
        animation = null;
        owner = null;
    }
}
