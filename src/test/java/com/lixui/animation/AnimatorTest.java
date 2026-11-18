package com.lixui.animation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimatorTest {
    @Test
    void playsAndRemovesFinishedAnimations() {
        Animator animator = new Animator();
        List<Float> values = new ArrayList<>();
        Animation animation = animator.animate(0, 1, 1, Easing.LINEAR, values::add);

        assertEquals(1, animator.size());
        animator.update(0.5f);
        assertEquals(0.5f, values.get(values.size() - 1), 0.0001f);
        assertEquals(1, animator.size());

        animator.update(0.5f);
        assertEquals(1, values.get(values.size() - 1), 0.0001f);
        assertTrue(animation.isFinished());
        assertTrue(animator.isEmpty());
    }

    @Test
    void canCancelWithoutCompleting() {
        Animator animator = new Animator();
        boolean[] completed = {false};
        Animation animation = new Animation(0, 1, 1, Easing.LINEAR, value -> { })
                .setOnComplete(() -> completed[0] = true);
        animator.play(animation);

        animator.cancel(animation);

        assertTrue(animator.isEmpty());
        assertFalse(completed[0]);
        assertFalse(animation.isFinished());
    }

    @Test
    void callbacksCanStartMoreAnimations() {
        Animator animator = new Animator();
        int[] started = {0};
        Animation first = animator.animate(0, 1, 0.5f, Easing.LINEAR, value -> {
            if (value == 1 && started[0] == 0) {
                started[0]++;
                animator.animate(0, 1, 0.5f, Easing.LINEAR, ignored -> { });
            }
        });

        animator.update(0.5f);

        assertTrue(first.isFinished());
        assertEquals(1, started[0]);
        assertEquals(1, animator.size());
    }

    @Test
    void rejectsInvalidUpdatesAndDuplicates() {
        Animator animator = new Animator();
        Animation animation = animator.animate(0, 1, 1, Easing.LINEAR, value -> { });
        animator.play(animation);
        assertEquals(1, animator.size());

        assertThrows(IllegalArgumentException.class, () -> animator.update(-0.1f));
        assertThrows(IllegalArgumentException.class, () -> animator.update(Float.NaN));
        assertThrows(NullPointerException.class, () -> animator.play(null));
    }
}
