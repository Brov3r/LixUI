package com.lixui.backend.opengl;

import com.lixui.render.DrawList;
import com.lixui.render.ImageData;
import com.lixui.render.RenderBackend;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.Objects;

/**
 * Renders UI into a private transparent color/stencil target and composites it over the host frame.
 * The host's depth and stencil contents are untouched. Drawing and resource creation restore the
 * OpenGL 3.3 state they change. Call from the host render thread with its context current, outside
 * active transform feedback and conditional-render scopes.
 */
public final class OpenGlLayer implements RenderBackend {
    private OpenGlBackend backend;
    private Viewport viewport;
    private int framebuffer;
    private int colorTexture;
    private int stencilBuffer;
    private int width;
    private int height;
    private int program;
    private int vertexArray;
    private int drawCalls;
    private boolean closed;

    /**
     * Creates an isolated UI renderer in the current OpenGL 3.3 context.
     *
     * @throws IllegalStateException if shader or backend initialization fails
     */
    public OpenGlLayer() {
        try (OpenGlState state = new OpenGlState()) {
            state.prepareUpload();
            try {
                backend = new OpenGlBackend();
                program = OpenGlBackend.createProgram("layer.vert", "layer.frag");
                vertexArray = GL30.glGenVertexArrays();
                GL20.glUseProgram(program);
                GL20.glUniform1i(GL20.glGetUniformLocation(program, "uTexture"), 0);
            } catch (RuntimeException | Error exception) {
                close();
                throw exception;
            }
        }
    }

    /**
     * Enables analytic shapes in the isolated renderer.
     *
     * @return true
     */
    @Override
    public boolean supportsAnalyticShapes() {
        return true;
    }

    /**
     * Sets the UI dimensions; the private target is resized lazily on the next draw.
     *
     * @param viewport physical and logical dimensions of the host target
     * @throws NullPointerException if the viewport is null
     * @throws IllegalStateException if the layer is closed
     */
    @Override
    public void resize(Viewport viewport) {
        ensureOpen();
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        backend.resize(viewport);
    }

    /**
     * Renders a transparent UI layer and blends it over the currently bound draw framebuffer.
     * The host framebuffer must match the physical viewport dimensions. Its color is loaded,
     * and the UI's premultiplied alpha is applied exactly once.
     *
     * @param drawList UI commands
     * @throws NullPointerException if the draw list is null
     * @throws IllegalStateException if the layer is closed, not resized, or its target cannot be created
     */
    @Override
    public void draw(DrawList drawList) {
        ensureOpen();
        Objects.requireNonNull(drawList, "drawList");
        if (viewport == null) {
            throw new IllegalStateException("resize must be called before draw.");
        }
        drawCalls = 0;
        if (drawList.commands().isEmpty()) {
            return;
        }
        try (OpenGlState state = new OpenGlState()) {
            state.prepareUpload();
            state.prepareDrawing();
            ensureTarget();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL11.glViewport(0, 0, width, height);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            backend.draw(drawList);
            drawCalls = backend.drawCallCount();

            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer());
            state.prepareDrawing();
            GL11.glViewport(0, 0, width, height);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vertexArray);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            drawCalls++;
        }
    }

    /**
     * Returns UI batch draws plus the final composition draw.
     *
     * @return draws submitted by the last call
     */
    @Override
    public int drawCallCount() {
        return drawCalls;
    }

    /**
     * Uploads an icon or image while preserving the host's texture and pixel-unpack bindings.
     *
     * @param image straight-alpha RGBA8 source pixels
     * @return the uploaded texture
     * @throws NullPointerException if the image is null
     * @throws IllegalStateException if the layer is closed
     */
    @Override
    public Texture createTexture(ImageData image) {
        ensureOpen();
        try (OpenGlState state = new OpenGlState()) {
            state.prepareUpload();
            return backend.createTexture(image);
        }
    }

    /**
     * Releases an image created by this layer.
     *
     * @param texture image to release
     * @throws NullPointerException if the texture is null
     * @throws IllegalArgumentException if the texture belongs to another backend type
     * @throws IllegalStateException if the layer is closed
     */
    @Override
    public void destroyTexture(Texture texture) {
        ensureOpen();
        backend.destroyTexture(texture);
    }

    /**
     * Releases the layer's GPU resources in the current context. Repeated calls are harmless.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        destroyTarget();
        if (backend != null) {
            backend.close();
        }
        GL20.glDeleteProgram(program);
        GL30.glDeleteVertexArrays(vertexArray);
        program = 0;
        vertexArray = 0;
    }

    private void ensureTarget() {
        int nextWidth = viewport.framebufferWidth();
        int nextHeight = viewport.framebufferHeight();
        if (width == nextWidth && height == nextHeight && framebuffer != 0) {
            return;
        }
        destroyTarget();
        try {
            colorTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, nextWidth, nextHeight, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
            stencilBuffer = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, stencilBuffer);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, nextWidth, nextHeight);
            framebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, colorTexture, 0);
            GL30.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, stencilBuffer);
            if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Cannot create the isolated UI framebuffer.");
            }
            width = nextWidth;
            height = nextHeight;
        } catch (RuntimeException | Error exception) {
            destroyTarget();
            throw exception;
        }
    }

    private void destroyTarget() {
        GL30.glDeleteFramebuffers(framebuffer);
        GL30.glDeleteRenderbuffers(stencilBuffer);
        GL11.glDeleteTextures(colorTexture);
        framebuffer = 0;
        stencilBuffer = 0;
        colorTexture = 0;
        width = 0;
        height = 0;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("UI layer is closed.");
        }
    }
}
