package com.lixui.geometry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PositionTest {
    @Test
    void changesCoordinatesInPlaceWithoutAffectingOtherInstances() {
        Position position = new Position();
        Position other = new Position();
        assertSame(position, position.set(10, 20));
        assertSame(position, position.setX(-5.25f));
        assertEquals(20, position.y());
        assertSame(position, position.setY(-30.5f));
        assertEquals(-5.25f, position.x());
        assertEquals(-30.5f, position.y());
        assertEquals(0, other.x());
        assertEquals(0, other.y());
    }

    @Test
    void offsetsSignedFractionalCoordinatesInPlace() {
        Position position = new Position(-10.5f, 20.25f);
        assertSame(position, position.offset(5.25f, -30.5f));
        assertEquals(-5.25f, position.x());
        assertEquals(-10.25f, position.y());
    }

    @Test
    void acceptsFiniteCoordinateExtremes() {
        Position position = new Position(-Float.MAX_VALUE, Float.MAX_VALUE);
        position.offset(Float.MAX_VALUE, -Float.MAX_VALUE);
        assertEquals(0.0f, position.x());
        assertEquals(0.0f, position.y());
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY})
    void rejectsInvalidCoordinatesWithoutPartialChanges(float value) {
        assertThrows(IllegalArgumentException.class, () -> new Position(value, 0));
        assertThrows(IllegalArgumentException.class, () -> new Position(0, value));
        Position position = new Position(10, 20);
        assertThrows(IllegalArgumentException.class, () -> position.set(value, 30));
        assertEquals(10, position.x());
        assertEquals(20, position.y());
        assertThrows(IllegalArgumentException.class, () -> position.set(30, value));
        assertEquals(10, position.x());
        assertEquals(20, position.y());
        assertThrows(IllegalArgumentException.class, () -> position.setX(value));
        assertThrows(IllegalArgumentException.class, () -> position.setY(value));
        assertThrows(IllegalArgumentException.class, () -> position.offset(value, 5));
        assertThrows(IllegalArgumentException.class, () -> position.offset(5, value));
        assertEquals(10, position.x());
        assertEquals(20, position.y());
    }

    @ParameterizedTest
    @ValueSource(floats = {-Float.MAX_VALUE, Float.MAX_VALUE})
    void rejectsDisplacementOverflowWithoutPartialChanges(float value) {
        Position position = new Position(value, value);
        assertThrows(IllegalArgumentException.class, () -> position.offset(value, -value));
        assertEquals(value, position.x());
        assertEquals(value, position.y());
        assertThrows(IllegalArgumentException.class, () -> position.offset(-value, value));
        assertEquals(value, position.x());
        assertEquals(value, position.y());
    }

    @Test
    void normalizesNegativeZeroDuringConstructionAndMutation() {
        Position position = new Position(-0.0f, -0.0f);
        assertEquals(0.0f, position.x());
        assertEquals(0.0f, position.y());
        position.set(10, 20).setX(-0.0f).setY(-0.0f);
        assertEquals(0.0f, position.x());
        assertEquals(0.0f, position.y());
    }
}
