package com.lixui.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkScenarioTest {
    @Test
    void buildersCreateTheRequestedNumberOfElements() {
        int count = 37;

        assertEquals(count, BenchmarkScenario.TEXT.create(count).children().size());
        assertEquals(count, BenchmarkScenario.CONTROLS.create(count).children().size());
        assertEquals(count, BenchmarkScenario.GRAPHICS.create(count).children().size());
        assertEquals(count, BenchmarkScenario.MIXED.create(count).children().size());

        BenchmarkDataContent data = assertInstanceOf(BenchmarkDataContent.class,
                BenchmarkScenario.DATA.create(count));
        assertEquals(count, data.requestedRows());
        assertEquals(count, data.table().rows().size());
        assertEquals(count, data.list().itemCount());
        assertEquals(count, data.tree().roots().size());
    }

    @Test
    void treeNodesAreCappedWhileOtherDataStoresUseTheFullCount() {
        int count = BenchmarkDataContent.TREE_NODE_LIMIT + 17;

        BenchmarkDataContent data = assertInstanceOf(BenchmarkDataContent.class,
                BenchmarkScenario.DATA.create(count));
        assertEquals(count, data.table().rows().size());
        assertEquals(count, data.list().itemCount());
        assertEquals(BenchmarkDataContent.TREE_NODE_LIMIT, data.tree().roots().size());
    }

    @Test
    void scenarioCountsStayInsideTheirLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> BenchmarkScenario.GRAPHICS.create(5_000));
        assertThrows(IllegalArgumentException.class,
                () -> BenchmarkScenario.MIXED.create(-1));
        assertTrue(BenchmarkScenario.DATA.counts().contains(10_000));
    }

    @Test
    void statisticsUseNearestRankAndMedianAverages() {
        BenchmarkStats stats = BenchmarkStats.of(new double[]{4, 1, 3, 2});

        assertEquals(1, stats.min());
        assertEquals(2.5, stats.median());
        assertEquals(4, stats.p95());
        assertEquals(2.5, stats.mean());
        assertEquals(4, stats.max());
        assertEquals(4, stats.count());
    }

    @Test
    void formattingIsLocaleIndependentAndReadable() {
        assertEquals("1.50", BenchmarkFormat.millis(1_500_000));
        assertEquals("250", BenchmarkFormat.integer(250.4));
        assertEquals("+25.0%", BenchmarkFormat.percentChange(125, 100));
        assertEquals("-50.0%", BenchmarkFormat.percentChange(50, 100));
        assertEquals("—", BenchmarkFormat.percentChange(10, 0));
        assertEquals("100.0", BenchmarkFormat.fps(10_000_000));
    }

    @Test
    void liveCaptureIgnoresHiddenScenesAndStopsAtTheLimit() {
        BenchmarkLiveCapture capture = new BenchmarkLiveCapture(3);
        assertEquals(3, capture.frameLimit());
        assertTrue(capture.metrics() == null);
        assertTrue(!capture.start(false));

        assertTrue(capture.start(true));
        assertTrue(capture.addFrame(true, 0.010f, 11));
        assertTrue(capture.addFrame(true, 0.020f, 12));
        assertTrue(!capture.addFrame(false, 0.030f, 13));
        assertTrue(!capture.isRunning());
        assertTrue(capture.wasStoppedByHiddenScene());
        assertEquals(2, capture.frameCount());

        assertTrue(capture.start(true));
        assertTrue(capture.addFrame(true, 0.010f, 21));
        assertTrue(capture.addFrame(true, 0.020f, 22));
        assertTrue(capture.addFrame(true, 0.030f, 23));
        assertTrue(!capture.isRunning());

        BenchmarkLiveMetrics metrics = capture.stop();
        assertEquals(3, metrics.frames());
        assertEquals(3, metrics.frameLimit());
        assertEquals(10_000_000, metrics.frameNanos().min(), 1);
        assertEquals(20_000_000, metrics.frameNanos().median(), 1);
        assertEquals(23, metrics.drawCalls().max());
    }
}
