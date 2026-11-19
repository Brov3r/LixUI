package com.lixui.backend.opengl;

import com.lixui.widget.testsupport.TestViewports;
import com.lixui.backend.BatchScenario;
import com.lixui.backend.MipmapScenario;
import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.graphics.GradientStop;
import com.lixui.graphics.Paint;
import com.lixui.render.RenderManager;
import com.lixui.render.ImageData;
import com.lixui.render.Texture;
import com.lixui.render.TextureFilter;
import com.lixui.render.Viewport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGlBackendTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    void rendersAnalyticPaintAndAntialiasedEdgesOnTheGpu(int samples) throws ReflectiveOperationException {
        Assumptions.assumeTrue(GLFW.glfwInit(), "GLFW display unavailable.");
        long window = 0;
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, 8);
            GLFW.glfwWindowHint(GLFW.GLFW_ALPHA_BITS, 8);
            GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, samples);
            window = GLFW.glfwCreateWindow(128, 128, "LixUI GPU test", 0, 0);
            Assumptions.assumeTrue(window != 0, "OpenGL context unavailable.");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            try (OpenGlBackend backend = new OpenGlBackend()) {
                RenderManager renderer = new RenderManager(backend);
                renderer.resize(TestViewports.FRAMEBUFFER);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 8);
                Texture rows = backend.createTexture(new ImageData(1, 2, new byte[]{
                        -1, 0, 0, -1, 0, 0, -1, -1}));
                assertEquals(8, GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT));
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                clear();
                renderer.begin();
                renderer.image(rows, new Rect(0, 0, 32, 32));
                renderer.end();
                assertPixel(16, 2, Color.rgb(255, 0, 0), 1);
                assertPixel(16, 29, Color.rgb(0, 0, 255), 1);
                verifyGradients(renderer);
                Texture nearest = MipmapScenario.stripes(renderer, TextureFilter.NEAREST);
                Texture linear = MipmapScenario.stripes(renderer, TextureFilter.LINEAR);
                clear();
                renderer.begin();
                MipmapScenario.draw(renderer, nearest, linear);
                renderer.end();
                assertPixel(4, 4, Color.rgb(191, 191, 191), 1);
                assertPixel(2, 14, Color.rgb(128, 128, 128), 1);
                verifyEllipses(renderer);
                verifyEdgeCoverage(renderer, 1);
                renderer.resize(new Viewport(128, 128, 64, 64));
                verifyEdgeCoverage(renderer, 2);
                renderer.resize(TestViewports.FRAMEBUFFER);
                Texture texture = backend.createTexture(new ImageData(1, 1,
                        new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255}));
                clear();
                renderer.begin();
                BatchScenario.draw(renderer, texture);
                renderer.end();
                BatchScenario.verify(backend);
                assertPixel(25, 25, Color.BLACK, 1);
                assertPixel(15, 15, Color.WHITE, 1);
                assertPixel(42, 42, Color.WHITE, 1);
                assertEquals(0, pixel(70, 70, 3));
                assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            }
        } finally {
            GL.setCapabilities(null);
            if (window != 0) {
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
        }
    }

    private static void verifyGradients(RenderManager renderer) {
        Paint linear = Paint.linear(new Point(10, 0), new Point(110, 0),
                new GradientStop(0, Color.rgb(255, 0, 0)),
                new GradientStop(0.5f, Color.WHITE),
                new GradientStop(0.5f, Color.BLACK),
                new GradientStop(1, Color.rgb(0, 0, 255)));
        clear();
        renderer.begin();
        renderer.fillRect(new Rect(10, 10, 100, 30), linear);
        renderer.end();
        for (int x : new int[]{20, 59, 60, 100}) {
            assertPixel(x, 20, linear.colorAt(new Point(x + 0.5f, 20.5f)), 3);
        }

        Paint radial = Paint.radial(new Point(64, 64), 40,
                new GradientStop(0, new Color(1, 0, 0, 0.5f)),
                new GradientStop(1, new Color(0, 0, 1, 0.5f)));
        clear();
        renderer.begin();
        renderer.fillRoundedRect(new Rect(10, 10, 108, 108), 12, radial);
        renderer.end();
        assertPixel(64, 64, radial.colorAt(new Point(64.5f, 64.5f)), 3);
        assertPixel(90, 64, radial.colorAt(new Point(90.5f, 64.5f)), 3);

        clear();
        renderer.begin();
        renderer.withTransform(new Transform(0, -1, 100, 1, 0, 0),
                () -> renderer.fillRect(new Rect(20, 20, 40, 20), linear));
        renderer.end();
        assertPixel(70, 40, linear.colorAt(new Point(40.5f, 29.5f)), 3);
    }

    private static void verifyEllipses(RenderManager renderer) {
        clear();
        renderer.begin();
        renderer.fillEllipse(new Point(64, 64), 24, 16, Paint.solid(Color.WHITE));
        renderer.end();
        assertPixel(64, 64, Color.WHITE, 1);
        assertEquals(0, pixel(40, 48, 3));
        int partial = 0;
        for (int x = 40; x <= 88; x++) {
            int alpha = pixel(x, 52, 3);
            if (alpha > 0 && alpha < 255) {
                partial++;
            }
        }
        assertTrue(partial > 0, "Ellipse edges must contain partial coverage.");
    }

    private static void verifyEdgeCoverage(RenderManager renderer, int scale) {
        clear();
        renderer.begin();
        renderer.rect(new Rect(20.75f / scale, 10f / scale, 30f / scale, 30f / scale), Color.WHITE);
        renderer.end();
        assertEquals(64, pixel(20, 20, 3), 5, "Coverage must extend outside the geometric bounds.");
        assertPixel(25, 20, Color.WHITE, 1);
        assertEquals(0, pixel(19, 20, 3));
    }

    private static void clear() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glClearColor(0, 0, 0, 0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    private static void assertPixel(int x, int y, Color color, int tolerance) {
        assertEquals(Math.round(color.r() * color.a() * 255), pixel(x, y, 0), tolerance);
        assertEquals(Math.round(color.g() * color.a() * 255), pixel(x, y, 1), tolerance);
        assertEquals(Math.round(color.b() * color.a() * 255), pixel(x, y, 2), tolerance);
        assertEquals(Math.round(color.a() * 255), pixel(x, y, 3), tolerance);
    }

    private static int pixel(int x, int y, int channel) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixel = stack.malloc(4);
            GL11.glReadPixels(x, 127 - y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            return Byte.toUnsignedInt(pixel.get(channel));
        }
    }
}
