package com.lixui.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void identityLeavesPointsAndReportsItself() {
        assertTrue(Transform.IDENTITY.isIdentity());
        assertEquals(new Point(3, -2), Transform.IDENTITY.apply(new Point(3, -2)));
        assertEquals(Transform.IDENTITY, Transform.IDENTITY.multiply(Transform.IDENTITY));
    }

    @Test
    void appliesTranslationScalingAndRotation() {
        assertEquals(new Point(11, 8),
                Transform.translation(10, 5).apply(new Point(1, 3)));
        assertEquals(new Point(2, 9),
                Transform.scaling(2, 3).apply(new Point(1, 3)));

        Point rotated = Transform.rotation((float) (Math.PI / 2)).apply(new Point(1, 0));
        assertEquals(0, rotated.x(), EPSILON);
        assertEquals(1, rotated.y(), EPSILON);
    }

    @Test
    void multipliesInParentThenChildOrder() {
        Transform transform = Transform.translation(10, 0).multiply(Transform.scaling(2, 2));

        assertEquals(new Point(12, 2), transform.apply(new Point(1, 1)));
    }

    @Test
    void chainingAppliesNewOperationsAfterTheExistingTransform() {
        Transform transform = Transform.IDENTITY
                .rotate((float) (Math.PI / 2))
                .translate(10, 0);

        Point point = transform.apply(new Point(1, 0));

        assertEquals(10, point.x(), EPSILON);
        assertEquals(1, point.y(), EPSILON);
        assertFalse(transform.isIdentity());
    }

    @Test
    void rejectsInvalidComponentsAndNulls() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transform(Float.NaN, 0, 0, 0, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Transform.translation(Float.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> Transform.rotation(Float.NaN));
        assertThrows(NullPointerException.class, () -> Transform.IDENTITY.apply(null));
        assertThrows(NullPointerException.class, () -> Transform.IDENTITY.multiply(null));
    }
}
