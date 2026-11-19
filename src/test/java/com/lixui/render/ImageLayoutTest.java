package com.lixui.render;

import com.lixui.geometry.Rect;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageLayoutTest {
    private static final Rect BOUNDS = new Rect(0, 0, 100, 100);
    private static final Rect FULL_UV = new Rect(0, 0, 1, 1);

    @Test
    void fillKeepsTheDestinationAndUv() {
        ImageLayout.ImageQuad quad = ImageLayout.resolve(
                BOUNDS, new Rect(0.25f, 0.5f, 0.5f, 0.25f), 64, 32, ImageStyle.FILL);

        assertEquals(BOUNDS, quad.bounds());
        assertEquals(new Rect(0.25f, 0.5f, 0.5f, 0.25f), quad.uv());
    }

    @Test
    void containLetterboxesAndAnchorsTheImage() {
        ImageLayout.ImageQuad centered = ImageLayout.resolve(
                BOUNDS, FULL_UV, 200, 100, ImageStyle.CONTAIN);
        assertEquals(new Rect(0, 25, 100, 50), centered.bounds());
        assertEquals(FULL_UV, centered.uv());

        ImageStyle topLeft = new ImageStyle(ImageFit.CONTAIN, HorizontalAlignment.LEFT,
                VerticalAlignment.TOP, ImageRepeat.NONE);
        assertEquals(new Rect(0, 0, 100, 50),
                ImageLayout.resolve(BOUNDS, FULL_UV, 200, 100, topLeft).bounds());
    }

    @Test
    void coverCropsTheUvRegion() {
        ImageLayout.ImageQuad quad = ImageLayout.resolve(BOUNDS, FULL_UV, 200, 100, ImageStyle.COVER);

        assertEquals(BOUNDS, quad.bounds());
        assertEquals(new Rect(0.25f, 0, 0.5f, 1), quad.uv());
    }

    @Test
    void fitWidthOverflowsTheVerticalAxis() {
        ImageLayout.ImageQuad quad = ImageLayout.resolve(BOUNDS, FULL_UV, 100, 200, ImageStyle.FIT_WIDTH);

        assertEquals(new Rect(0, -50, 100, 200), quad.bounds());
    }

    @Test
    void fitHeightOverflowsTheHorizontalAxis() {
        ImageLayout.ImageQuad quad = ImageLayout.resolve(BOUNDS, FULL_UV, 200, 100, ImageStyle.FIT_HEIGHT);

        assertEquals(new Rect(-50, 0, 200, 100), quad.bounds());
    }

    @Test
    void noneUsesTheTexturePixelSizeInLogicalUnits() {
        ImageLayout.ImageQuad quad = ImageLayout.resolve(BOUNDS, FULL_UV, 64, 32, ImageStyle.NONE);

        assertEquals(new Rect(18, 34, 64, 32), quad.bounds());
        assertEquals(FULL_UV, quad.uv());
    }

    @Test
    void scaleDownOnlyShrinksOversizedImages() {
        assertEquals(new Rect(18, 34, 64, 32),
                ImageLayout.resolve(BOUNDS, FULL_UV, 64, 32, ImageStyle.SCALE_DOWN).bounds());
        assertEquals(new Rect(0, 5, 20, 10),
                ImageLayout.resolve(new Rect(0, 0, 20, 20), FULL_UV, 64, 32,
                        ImageStyle.SCALE_DOWN).bounds());
    }

    @Test
    void repeatsTilesAcrossTheRequestedAxes() {
        ImageStyle both = new ImageStyle(ImageFit.NONE, HorizontalAlignment.CENTER,
                VerticalAlignment.CENTER, ImageRepeat.BOTH);
        ImageLayout.ImageQuad tiled = ImageLayout.resolve(
                new Rect(0, 0, 50, 50), FULL_UV, 10, 10, both);
        assertEquals(new Rect(0, 0, 50, 50), tiled.bounds());
        assertEquals(new Rect(-2, -2, 5, 5), tiled.uv());

        ImageStyle horizontal = new ImageStyle(ImageFit.NONE, HorizontalAlignment.CENTER,
                VerticalAlignment.CENTER, ImageRepeat.X);
        ImageLayout.ImageQuad row = ImageLayout.resolve(
                new Rect(0, 0, 50, 50), FULL_UV, 10, 10, horizontal);
        assertEquals(new Rect(0, 20, 50, 10), row.bounds());
        assertEquals(new Rect(-2, 0, 5, 1), row.uv());
    }

    @Test
    void repeatsTilesProducedByTheFit() {
        ImageStyle both = new ImageStyle(ImageFit.CONTAIN, HorizontalAlignment.CENTER,
                VerticalAlignment.CENTER, ImageRepeat.BOTH);
        ImageLayout.ImageQuad quad = ImageLayout.resolve(BOUNDS, FULL_UV, 20, 10, both);

        assertEquals(new Rect(0, 0, 100, 100), quad.bounds());
        assertEquals(new Rect(0, -0.5f, 1, 2), quad.uv());
    }

    @Test
    void justifyBehavesLikeLeftAlignment() {
        Rect wide = new Rect(0, 0, 200, 100);
        ImageStyle justified = new ImageStyle(ImageFit.CONTAIN, HorizontalAlignment.JUSTIFY,
                VerticalAlignment.CENTER, ImageRepeat.NONE);

        assertEquals(new Rect(0, 0, 100, 100),
                ImageLayout.resolve(wide, FULL_UV, 100, 100, justified).bounds());
    }

    @Test
    void degenerateUvFallsBackToTheDestination() {
        Rect emptyUv = new Rect(0.5f, 0.5f, 0, 0);
        ImageLayout.ImageQuad quad = ImageLayout.resolve(BOUNDS, emptyUv, 64, 32, ImageStyle.CONTAIN);

        assertEquals(BOUNDS, quad.bounds());
        assertEquals(emptyUv, quad.uv());
    }
}
