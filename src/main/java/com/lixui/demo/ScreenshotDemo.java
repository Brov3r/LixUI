package com.lixui.demo;

import com.lixui.backend.opengl.OpenGlLayer;
import com.lixui.glfw.GlfwPlatform;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders the curated showcase scenes with OpenGL 3.3 Core and optionally writes one to a PNG.
 *
 * <p>Interactive mode shows a centered 1280x720 window. Keys 1-4 switch scenes and T toggles the
 * theme. Capture mode keeps the window hidden, renders into an offscreen framebuffer and exits
 * after writing the requested image.</p>
 */
public final class ScreenshotDemo {
    private static final float CAPTURE_DELTA_SECONDS = 0.35f;
    private static final float MAX_LIVE_DELTA_SECONDS = 0.05f;

    private ScreenshotDemo() {
    }

    /**
     * Runs the showcase demo.
     * Supported arguments are {@code --scene=1..4}, {@code --theme=dark|light},
     * {@code --capture=<file.png>} and {@code --frames=<count>}.
     *
     * @param args command-line arguments
     * @throws IOException if a capture image cannot be written
     * @throws IllegalArgumentException if a known argument has an invalid value
     * @throws IllegalStateException if the OpenGL context or capture target cannot be created
     */
    public static void main(String[] args) throws IOException {
        ScreenshotOptions options = ScreenshotOptions.parse(args);
        InputState input = new InputState();
        try (DemoWindow window = new DemoWindow("LixUI Showcase - OpenGL",
                GLFW.GLFW_OPENGL_API, DemoPlacement.CENTERED);
                DemoInput inputAdapter = new DemoInput(window.handle(), input);
                GlfwPlatform platform = GlfwPlatform.install(window.handle())) {
            try {
                GLFW.glfwMakeContextCurrent(window.handle());
                GL.createCapabilities();
                try (OpenGlLayer backend = new OpenGlLayer();
                        ScreenshotShowcase showcase = new ScreenshotShowcase(input, backend, platform,
                                options.theme())) {
                    if (options.capturePath() != null) {
                        capture(showcase, options);
                    } else {
                        GLFW.glfwSwapInterval(1);
                        window.show();
                        run(window, showcase, options.scene());
                    }
                }
            } finally {
                GL.setCapabilities(null);
            }
        }
    }

    private static void run(DemoWindow window, ScreenshotShowcase showcase, int initialScene) {
        boolean initialized = false;
        boolean[] previous = new boolean[5];
        double previousTime = GLFW.glfwGetTime();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer logicalWidth = stack.mallocInt(1);
            IntBuffer logicalHeight = stack.mallocInt(1);
            while (!window.shouldClose()) {
                GLFW.glfwPollEvents();
                double currentTime = GLFW.glfwGetTime();
                float deltaSeconds = (float) Math.min(MAX_LIVE_DELTA_SECONDS,
                        currentTime - previousTime);
                previousTime = currentTime;
                window.framebufferSize(width, height);
                window.windowSize(logicalWidth, logicalHeight);
                int framebufferWidth = width.get(0);
                int framebufferHeight = height.get(0);
                if (framebufferWidth <= 0 || framebufferHeight <= 0
                        || logicalWidth.get(0) <= 0 || logicalHeight.get(0) <= 0) {
                    continue;
                }
                Viewport viewport = new Viewport(framebufferWidth, framebufferHeight,
                        ScreenshotShowcase.WIDTH, ScreenshotShowcase.HEIGHT);
                showcase.resize(viewport);
                if (!initialized) {
                    showcase.select(initialScene);
                    initialized = true;
                }
                if (scenePressed(window, previous, 0)) {
                    showcase.select(1);
                } else if (scenePressed(window, previous, 1)) {
                    showcase.select(2);
                } else if (scenePressed(window, previous, 2)) {
                    showcase.select(3);
                } else if (scenePressed(window, previous, 3)) {
                    showcase.select(4);
                }
                if (scenePressed(window, previous, 4)) {
                    showcase.toggleTheme();
                }
                showcase.update(deltaSeconds);
                clear(showcase.clearColor());
                showcase.render();
                GLFW.glfwSwapBuffers(window.handle());
            }
        }
    }

    private static boolean scenePressed(DemoWindow window, boolean[] previous, int slot) {
        int key = switch (slot) {
            case 0 -> GLFW.GLFW_KEY_1;
            case 1 -> GLFW.GLFW_KEY_2;
            case 2 -> GLFW.GLFW_KEY_3;
            case 3 -> GLFW.GLFW_KEY_4;
            default -> GLFW.GLFW_KEY_T;
        };
        boolean down = GLFW.glfwGetKey(window.handle(), key) == GLFW.GLFW_PRESS;
        boolean pressed = down && !previous[slot];
        previous[slot] = down;
        return pressed;
    }

    private static void capture(ScreenshotShowcase showcase, ScreenshotOptions options)
            throws IOException {
        int width = ScreenshotShowcase.WIDTH;
        int height = ScreenshotShowcase.HEIGHT;
        int framebuffer = GL30.glGenFramebuffers();
        int colorBuffer = GL30.glGenRenderbuffers();
        try {
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, colorBuffer);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_RGBA8, width, height);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL30.GL_RENDERBUFFER, colorBuffer);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                    != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Capture framebuffer is incomplete.");
            }
            GL11.glViewport(0, 0, width, height);
            showcase.resize(new Viewport(width, height, width, height));
            showcase.select(options.scene());
            for (int frame = 0; frame < options.frames(); frame++) {
                showcase.update(CAPTURE_DELTA_SECONDS);
                clear(showcase.clearColor());
                showcase.render();
            }
            BufferedImage image = readFramebuffer(width, height);
            writePng(image, options.capturePath());
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL30.glDeleteRenderbuffers(colorBuffer);
            GL30.glDeleteFramebuffers(framebuffer);
        }
    }

    private static BufferedImage readFramebuffer(int width, int height) {
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
        try {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA,
                    GL11.GL_UNSIGNED_BYTE, pixels);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                int sourceRow = height - 1 - y;
                int offset = sourceRow * width * 4;
                for (int x = 0; x < width; x++) {
                    int index = offset + x * 4;
                    int blue = pixels.get(index) & 0xFF;
                    int green = pixels.get(index + 1) & 0xFF;
                    int red = pixels.get(index + 2) & 0xFF;
                    int alpha = pixels.get(index + 3) & 0xFF;
                    row[x] = alpha << 24 | red << 16 | green << 8 | blue;
                }
                image.setRGB(0, y, width, 1, row, 0, width);
            }
            return image;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private static void writePng(BufferedImage image, String capturePath) throws IOException {
        Path path = Path.of(capturePath);
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("No PNG writer is available for " + path);
        }
    }

    private static void clear(Color color) {
        GL11.glClearColor(color.r(), color.g(), color.b(), color.a());
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }
}
