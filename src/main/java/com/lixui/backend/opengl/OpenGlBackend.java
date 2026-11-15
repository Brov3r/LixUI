package com.lixui.backend.opengl;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.render.DrawCommand;
import com.lixui.render.DrawList;
import com.lixui.render.ImageCommand;
import com.lixui.render.ImageData;
import com.lixui.render.RenderBackend;
import com.lixui.render.Texture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An OpenGL 3.3+ Core render backend for rounded rectangles and textured quads.
 * The backend batches consecutive commands that share a clip rectangle and texture, applies the clip
 * with a scissor test, and blends straight-alpha colors and textures with premultiplied output.
 * The caller must make the target framebuffer current before {@link #draw(DrawList)}.
 */
public final class OpenGlBackend implements RenderBackend {
    private static final Rect FULL_UV = new Rect(0, 0, 1, 1);
    private static final int FLOATS_PER_VERTEX = 13;
    private static final int VERTICES_PER_QUAD = 6;
    private static final int FLOATS_PER_QUAD = FLOATS_PER_VERTEX * VERTICES_PER_QUAD;
    private static final int INITIAL_CAPACITY = 4096;

    private final List<Batch> batches = new ArrayList<>();
    private final List<GlTexture> textures = new ArrayList<>();
    private FloatBuffer vertices = MemoryUtil.memAllocFloat(INITIAL_CAPACITY);
    private GlTexture whiteTexture;
    private int program;
    private int vertexArray;
    private int vertexBuffer;
    private int viewportLocation;
    private int textureLocation;
    private int framebufferWidth;
    private int framebufferHeight;
    private int floatCount;

    /**
     * Creates the backend for the current OpenGL context.
     * The context must support OpenGL 3.3 Core or newer.
     *
     * @throws IllegalStateException if shader compilation or program linking fails
     */
    public OpenGlBackend() {
        try {
            program = createProgram();
            viewportLocation = GL20.glGetUniformLocation(program, "uViewport");
            textureLocation = GL20.glGetUniformLocation(program, "uTexture");
            createVertexArray();
            whiteTexture = (GlTexture) createTexture(new ImageData(1, 1, new byte[] {
                    (byte) 255, (byte) 255, (byte) 255, (byte) 255
            }));
        } catch (RuntimeException | Error exception) {
            close();
            throw exception;
        }
    }

    /**
     * Stores the target size in pixels for the next draw.
     *
     * @param framebufferWidth target width in pixels
     * @param framebufferHeight target height in pixels
     * @throws IllegalArgumentException if a dimension is not positive
     */
    @Override
    public void resize(int framebufferWidth, int framebufferHeight) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            throw new IllegalArgumentException("Framebuffer dimensions must be positive.");
        }
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
    }

    /**
     * Creates a texture from RGBA8 image data in the current OpenGL context.
     *
     * @param image image data with a top-left origin
     * @return the created texture
     * @throws NullPointerException if {@code image} is null
     */
    @Override
    public Texture createTexture(ImageData image) {
        Objects.requireNonNull(image, "image");
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        ByteBuffer pixels = MemoryUtil.memAlloc(image.pixels().length);
        try {
            pixels.put(image.pixels()).flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, image.width(), image.height(), 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        } finally {
            MemoryUtil.memFree(pixels);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }

        GlTexture texture = new GlTexture(id, image.width(), image.height(), true);
        textures.add(texture);
        return texture;
    }

    /**
     * Wraps an existing OpenGL texture owned by the host application.
     * The backend only samples the texture and never deletes it.
     * Texture coordinates use a top-left origin, so the host texture should be uploaded accordingly.
     *
     * @param textureId existing OpenGL texture name
     * @param width texture width in pixels
     * @param height texture height in pixels
     * @return a non-owning texture handle
     * @throws IllegalArgumentException if the texture name or a dimension is not positive
     */
    public Texture wrapTexture(int textureId, int width, int height) {
        if (textureId <= 0) {
            throw new IllegalArgumentException("Texture name must be positive.");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Texture dimensions must be positive.");
        }
        return new GlTexture(textureId, width, height, false);
    }

    /**
     * Deletes a texture created by this backend. Wrapped host textures are left untouched.
     *
     * @param texture texture to release
     * @throws NullPointerException if {@code texture} is null
     * @throws IllegalArgumentException if the texture belongs to another backend
     */
    @Override
    public void destroyTexture(Texture texture) {
        GlTexture glTexture = asGlTexture(texture);
        if (!glTexture.owned()) {
            return;
        }
        GL11.glDeleteTextures(glTexture.id());
        textures.remove(glTexture);
        if (whiteTexture == glTexture) {
            whiteTexture = null;
        }
    }

    /**
     * Draws all commands into the currently bound framebuffer.
     *
     * @param drawList commands to render
     * @throws NullPointerException if {@code drawList} is null
     * @throws IllegalStateException if {@link #resize(int, int)} has not been called
     */
    @Override
    public void draw(DrawList drawList) {
        Objects.requireNonNull(drawList, "drawList");
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            throw new IllegalStateException("resize must be called before draw.");
        }

        collect(drawList);
        if (batches.isEmpty()) {
            return;
        }

        GL11.glViewport(0, 0, framebufferWidth, framebufferHeight);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        GL30.glBindVertexArray(vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
        vertices.position(0).limit(floatCount);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STREAM_DRAW);
        vertices.clear();

        GL20.glUseProgram(program);
        GL20.glUniform2f(viewportLocation, framebufferWidth, framebufferHeight);
        GL20.glUniform1i(textureLocation, 0);
        for (Batch batch : batches) {
            if (!applyClip(batch.clip())) {
                continue;
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, batch.texture().id());
            GL11.glDrawArrays(GL11.GL_TRIANGLES, batch.firstVertex(), batch.vertexCount());
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL20.glUseProgram(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    /**
     * Deletes owned textures, the program, vertex array and vertex buffer.
     */
    @Override
    public void close() {
        batches.clear();
        for (GlTexture texture : textures) {
            if (texture.owned()) {
                GL11.glDeleteTextures(texture.id());
            }
        }
        textures.clear();
        whiteTexture = null;
        if (vertexBuffer != 0) {
            GL15.glDeleteBuffers(vertexBuffer);
            vertexBuffer = 0;
        }
        if (vertexArray != 0) {
            GL30.glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
        if (vertices != null) {
            MemoryUtil.memFree(vertices);
            vertices = null;
        }
    }

    private void createVertexArray() {
        vertexArray = GL30.glGenVertexArrays();
        vertexBuffer = GL15.glGenBuffers();
        GL30.glBindVertexArray(vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);

        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L * Float.BYTES);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 4L * Float.BYTES);
        GL20.glEnableVertexAttribArray(3);
        GL20.glVertexAttribPointer(3, 1, GL11.GL_FLOAT, false, stride, 6L * Float.BYTES);
        GL20.glEnableVertexAttribArray(4);
        GL20.glVertexAttribPointer(4, 4, GL11.GL_FLOAT, false, stride, 7L * Float.BYTES);
        GL20.glEnableVertexAttribArray(5);
        GL20.glVertexAttribPointer(5, 2, GL11.GL_FLOAT, false, stride, 11L * Float.BYTES);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    private void collect(DrawList drawList) {
        floatCount = 0;
        batches.clear();
        if (vertices.capacity() < FLOATS_PER_QUAD) {
            growCapacity(FLOATS_PER_QUAD);
        }

        Rect currentClip = null;
        GlTexture currentTexture = null;
        int batchStartVertex = 0;
        int batchVertexCount = 0;
        for (DrawCommand command : drawList.commands()) {
            GlTexture texture = asGlTexture(command instanceof ImageCommand image
                    ? image.texture() : whiteTexture);
            if (!command.clip().equals(currentClip) || texture != currentTexture) {
                if (batchVertexCount > 0) {
                    batches.add(new Batch(batchStartVertex, batchVertexCount, currentClip, currentTexture));
                }
                currentClip = command.clip();
                currentTexture = texture;
                batchStartVertex = floatCount / FLOATS_PER_VERTEX;
                batchVertexCount = 0;
            }
            ensureCapacity(FLOATS_PER_QUAD);
            appendQuad(command);
            batchVertexCount += VERTICES_PER_QUAD;
        }
        if (batchVertexCount > 0) {
            batches.add(new Batch(batchStartVertex, batchVertexCount, currentClip, currentTexture));
        }
    }

    private void appendQuad(DrawCommand command) {
        Rect bounds = command.bounds();
        Rect uv = command instanceof ImageCommand image ? image.uv() : FULL_UV;
        float halfWidth = bounds.width() / 2;
        float halfHeight = bounds.height() / 2;
        float left = bounds.x();
        float top = bounds.y();
        float right = bounds.right();
        float bottom = bounds.bottom();
        float radius = command.radius();
        Color color = command.color();
        float u0 = uv.x();
        float v0 = uv.y();
        float u1 = uv.right();
        float v1 = uv.bottom();

        putVertex(left, top, -halfWidth, -halfHeight, halfWidth, halfHeight, radius, color, u0, v0);
        putVertex(right, top, halfWidth, -halfHeight, halfWidth, halfHeight, radius, color, u1, v0);
        putVertex(right, bottom, halfWidth, halfHeight, halfWidth, halfHeight, radius, color, u1, v1);
        putVertex(left, top, -halfWidth, -halfHeight, halfWidth, halfHeight, radius, color, u0, v0);
        putVertex(right, bottom, halfWidth, halfHeight, halfWidth, halfHeight, radius, color, u1, v1);
        putVertex(left, bottom, -halfWidth, halfHeight, halfWidth, halfHeight, radius, color, u0, v1);
    }

    private void putVertex(float x, float y, float localX, float localY,
                           float halfWidth, float halfHeight, float radius, Color color,
                           float u, float v) {
        vertices.put(x).put(y)
                .put(localX).put(localY)
                .put(halfWidth).put(halfHeight)
                .put(radius)
                .put(color.r()).put(color.g()).put(color.b()).put(color.a())
                .put(u).put(v);
        floatCount += FLOATS_PER_VERTEX;
    }

    private void ensureCapacity(int additionalFloats) {
        int required = floatCount + additionalFloats;
        if (required > vertices.capacity()) {
            growCapacity(required);
        }
    }

    private void growCapacity(int required) {
        int capacity = vertices.capacity();
        while (capacity < required) {
            capacity *= 2;
        }
        FloatBuffer grown = MemoryUtil.memAllocFloat(capacity);
        vertices.flip();
        grown.put(vertices);
        grown.position(floatCount);
        MemoryUtil.memFree(vertices);
        vertices = grown;
    }

    private boolean applyClip(Rect clip) {
        float left = Math.max(clip.x(), 0);
        float top = Math.max(clip.y(), 0);
        float right = Math.min(clip.right(), framebufferWidth);
        float bottom = Math.min(clip.bottom(), framebufferHeight);
        if (right <= left || bottom <= top) {
            return false;
        }

        int scissorX = (int) Math.floor(left);
        int scissorY = (int) Math.floor(top);
        int scissorWidth = (int) Math.ceil(right) - scissorX;
        int scissorHeight = (int) Math.ceil(bottom) - scissorY;
        GL11.glScissor(scissorX, framebufferHeight - scissorY - scissorHeight, scissorWidth, scissorHeight);
        return true;
    }

    private static GlTexture asGlTexture(Texture texture) {
        Objects.requireNonNull(texture, "texture");
        if (!(texture instanceof GlTexture glTexture)) {
            throw new IllegalArgumentException("Texture belongs to a different render backend.");
        }
        return glTexture;
    }

    private static int createProgram() {
        int vertexShader = createShader(GL20.GL_VERTEX_SHADER, loadShader("rounded_rect.vert"));
        int fragmentShader;
        try {
            fragmentShader = createShader(GL20.GL_FRAGMENT_SHADER, loadShader("rounded_rect.frag"));
        } catch (RuntimeException | Error exception) {
            GL20.glDeleteShader(vertexShader);
            throw exception;
        }

        int linkedProgram = GL20.glCreateProgram();
        try {
            GL20.glAttachShader(linkedProgram, vertexShader);
            GL20.glAttachShader(linkedProgram, fragmentShader);
            GL20.glLinkProgram(linkedProgram);
            if (GL20.glGetProgrami(linkedProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("Failed to link OpenGL program: "
                        + GL20.glGetProgramInfoLog(linkedProgram));
            }
            return linkedProgram;
        } catch (RuntimeException | Error exception) {
            GL20.glDeleteProgram(linkedProgram);
            throw exception;
        } finally {
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
        }
    }

    private static int createShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Failed to compile OpenGL shader: " + log);
        }
        return shader;
    }

    private static String loadShader(String name) {
        try (InputStream input = OpenGlBackend.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing shader resource: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load shader resource: " + name, exception);
        }
    }

    private record Batch(int firstVertex, int vertexCount, Rect clip, GlTexture texture) {
    }
}
