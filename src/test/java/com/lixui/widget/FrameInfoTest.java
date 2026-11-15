package com.lixui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameInfoTest {
    @Test
    void exposesTimingComponents() {
        FrameInfo frame = new FrameInfo(0.5f, 12.75, 3);

        assertEquals(0.5f, frame.deltaSeconds());
        assertEquals(12.75, frame.elapsedSeconds());
        assertEquals(3, frame.frameIndex());
    }

    @Test
    void zeroDescribesTheInitialFrame() {
        assertEquals(0, FrameInfo.ZERO.deltaSeconds());
        assertEquals(0, FrameInfo.ZERO.elapsedSeconds());
        assertEquals(0, FrameInfo.ZERO.frameIndex());
    }

    @Test
    void rejectsInvalidTiming() {
        assertThrows(IllegalArgumentException.class, () -> new FrameInfo(-0.1f, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new FrameInfo(Float.NaN, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new FrameInfo(Float.POSITIVE_INFINITY, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new FrameInfo(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new FrameInfo(0, Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new FrameInfo(0, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new FrameInfo(0, 0, -1));
    }
}
