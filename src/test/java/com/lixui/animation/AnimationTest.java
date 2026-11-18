package com.lixui.animation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationTest {
    @Test
    void advancesValuesAndCompletesOnce() {
        List<Float> values = new ArrayList<>();
        boolean[] completed = {false};
        Animation animation = new Animation(0, 10, 1, Easing.LINEAR, values::add)
                .setOnComplete(() -> completed[0] = true);

        animation.update(0.5f);
        assertEquals(5, values.get(values.size() - 1), 0.0001f);
        assertFalse(animation.isFinished());
        assertEquals(0.5f, animation.progress(), 0.0001f);

        animation.update(0.5f);
        assertEquals(10, values.get(values.size() - 1), 0.0001f);
        assertTrue(animation.isFinished());
        assertTrue(completed[0]);

        int updates = values.size();
        animation.update(1);
        assertEquals(updates, values.size());
    }

    @Test
    void honorsDelayBeforeApplyingValues() {
        List<Float> values = new ArrayList<>();
        Animation animation = new Animation(0, 1, 1, Easing.LINEAR, values::add).setDelay(0.5f);

        animation.update(0.25f);
        assertTrue(values.isEmpty());
        assertEquals(0, animation.progress(), 0.0001f);

        animation.update(0.25f);
        assertEquals(1, values.size());
        assertEquals(0, values.get(0), 0.0001f);

        animation.update(1);
        assertEquals(1, values.get(values.size() - 1), 0.0001f);
        assertTrue(animation.isFinished());
    }

    @Test
    void zeroDurationCompletesOnTheNextUpdate() {
        List<Float> values = new ArrayList<>();
        Animation animation = new Animation(2, 4, 0, Easing.LINEAR, values::add);

        animation.update(0);

        assertEquals(4, values.get(0), 0.0001f);
        assertTrue(animation.isFinished());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new Animation(Float.NaN, 1, 1, Easing.LINEAR, value -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> new Animation(0, 1, -1, Easing.LINEAR, value -> { }));
        assertThrows(NullPointerException.class,
                () -> new Animation(0, 1, 1, null, value -> { }));
        assertThrows(NullPointerException.class,
                () -> new Animation(0, 1, 1, Easing.LINEAR, null));

        Animation animation = new Animation(0, 1, 1, Easing.LINEAR, value -> { });
        assertThrows(IllegalArgumentException.class, () -> animation.setDelay(-1));
        assertThrows(IllegalArgumentException.class, () -> animation.update(-0.1f));
        assertThrows(IllegalArgumentException.class, () -> animation.update(Float.NaN));
    }
}
