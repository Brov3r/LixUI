package com.lixui.demo;

import com.lixui.render.ImageData;

/**
 * Generates image data used by the demos.
 */
final class DemoTextures {
    private static final int SIZE = 64;
    private static final int CELL = 8;

    private DemoTextures() {
    }

    /**
     * Creates a checkerboard with a red top-left marker and a green bottom-right marker,
     * which makes texture orientation and UV regions easy to verify.
     *
     * @return an RGBA8 checkerboard image
     */
    static ImageData checkerboard() {
        byte[] pixels = new byte[SIZE * SIZE * 4];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean light = ((x / CELL) + (y / CELL)) % 2 == 0;
                int offset = (y * SIZE + x) * 4;
                pixels[offset] = (byte) (light ? 0x5B : 0x2C);
                pixels[offset + 1] = (byte) (light ? 0x8C : 0x30);
                pixels[offset + 2] = (byte) (light ? 0xFF : 0x3A);
                pixels[offset + 3] = (byte) 0xFF;
            }
        }
        fill(pixels, 0, 0, 12, 12, 0xFF, 0x40, 0x40);
        fill(pixels, SIZE - 12, SIZE - 12, 12, 12, 0x40, 0xFF, 0x40);
        return new ImageData(SIZE, SIZE, pixels);
    }

    private static void fill(byte[] pixels, int startX, int startY, int width, int height,
                             int red, int green, int blue) {
        for (int y = startY; y < startY + height; y++) {
            for (int x = startX; x < startX + width; x++) {
                int offset = (y * SIZE + x) * 4;
                pixels[offset] = (byte) red;
                pixels[offset + 1] = (byte) green;
                pixels[offset + 2] = (byte) blue;
                pixels[offset + 3] = (byte) 0xFF;
            }
        }
    }
}
