package com.lixui.render;

import com.lixui.geometry.Insets;
import com.lixui.geometry.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NinePatchLayoutTest {
    private static final float DELTA = 0.0001f;

    @Test
    void splitsAUniformPatchIntoNineQuads() {
        List<NinePatchLayout.Patch> patches = NinePatchLayout.split(
                new Rect(0, 0, 100, 50), new Rect(0, 0, 1, 1), Insets.uniform(10), 30, 30);

        assertEquals(9, patches.size());
        assertRect(new Rect(0, 0, 10, 10), patches.get(0).bounds());
        assertRect(new Rect(0, 0, 1f / 3, 1f / 3), patches.get(0).uv());
        assertRect(new Rect(10, 10, 80, 30), patches.get(4).bounds());
        assertRect(new Rect(1f / 3, 1f / 3, 1f / 3, 1f / 3), patches.get(4).uv());
        assertRect(new Rect(90, 40, 10, 10), patches.get(8).bounds());
        assertRect(new Rect(2f / 3, 2f / 3, 1f / 3, 1f / 3), patches.get(8).uv());
    }

    @Test
    void shrinksCornersProportionallyWhenTheDestinationIsSmaller() {
        List<NinePatchLayout.Patch> patches = NinePatchLayout.split(
                new Rect(0, 0, 8, 8), new Rect(0, 0, 1, 1), Insets.uniform(10), 30, 30);

        assertEquals(4, patches.size());
        assertRect(new Rect(0, 0, 4, 4), patches.get(0).bounds());
        assertRect(new Rect(4, 0, 4, 4), patches.get(1).bounds());
        assertRect(new Rect(0, 4, 4, 4), patches.get(2).bounds());
        assertRect(new Rect(4, 4, 4, 4), patches.get(3).bounds());
    }

    @Test
    void zeroInsetsProduceASingleStretchedQuad() {
        Rect bounds = new Rect(5, 6, 70, 80);
        Rect uv = new Rect(0.25f, 0.25f, 0.5f, 0.5f);

        List<NinePatchLayout.Patch> patches = NinePatchLayout.split(
                bounds, uv, Insets.NONE, 100, 100);

        assertEquals(1, patches.size());
        assertEquals(bounds, patches.getFirst().bounds());
        assertEquals(uv, patches.getFirst().uv());
    }

    @Test
    void mapsInsetsInsideAnAtlasRegion() {
        List<NinePatchLayout.Patch> patches = NinePatchLayout.split(
                new Rect(0, 0, 100, 100), new Rect(0.5f, 0.5f, 0.2f, 0.2f),
                Insets.uniform(5), 100, 100);

        assertEquals(9, patches.size());
        assertRect(new Rect(0.5f, 0.5f, 0.05f, 0.05f), patches.get(0).uv());
        assertRect(new Rect(0.55f, 0.55f, 0.1f, 0.1f), patches.get(4).uv());
        assertRect(new Rect(0.65f, 0.65f, 0.05f, 0.05f), patches.get(8).uv());
    }

    @Test
    void rejectsInsetsWithoutAPositiveCenter() {
        assertThrows(IllegalArgumentException.class, () -> NinePatchLayout.split(
                new Rect(0, 0, 100, 100), new Rect(0, 0, 1, 1), Insets.uniform(15), 30, 30));
    }

    private static void assertRect(Rect expected, Rect actual) {
        assertEquals(expected.x(), actual.x(), DELTA);
        assertEquals(expected.y(), actual.y(), DELTA);
        assertEquals(expected.width(), actual.width(), DELTA);
        assertEquals(expected.height(), actual.height(), DELTA);
    }
}
