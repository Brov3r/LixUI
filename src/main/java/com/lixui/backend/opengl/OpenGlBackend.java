package com.lixui.backend.opengl;

import com.lixui.backend.CommandClassifier;
import com.lixui.backend.MaskAction;
import com.lixui.backend.QuadGeometry;
import com.lixui.backend.ShaderPaint;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.DrawList;
import com.lixui.render.command.ImageCommand;
import com.lixui.render.ImageData;
import com.lixui.render.command.MaskBeginCommand;
import com.lixui.render.command.MaskEndCommand;
import com.lixui.render.command.MaskTextureBeginCommand;
import com.lixui.render.command.MaskTextureEndCommand;
import com.lixui.render.command.MeshCommand;
import com.lixui.render.MeshVertex;
import com.lixui.render.RenderBackend;
import com.lixui.render.command.ShapeCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.render.Texture;
import com.lixui.render.TextureFilter;
import com.lixui.render.TextureOptions;
import com.lixui.render.TextureWrap;
import com.lixui.render.Viewport;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.MemoryStack;

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
 * Use {@link OpenGlLayer} to isolate UI attachments and preserve the host application's GL state.
 */
public final class OpenGlBackend implements RenderBackend {
    private static final Rect FULL_UV = new Rect(0, 0, 1, 1);
    private static final float MESH_HALF_SIZE = 1_000_000f;
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
    private int paintLocation;
    private Viewport viewport;
    private int floatCount;
    private int drawCallCount;

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
            paintLocation = GL20.glGetUniformLocation(program, "uPaint");
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
     * Enables fragment-shader evaluation of shapes and gradients.
     *
     * @return true because analytic shapes are supported
     */
    @Override
    public boolean supportsAnalyticShapes() {
        return true;
    }

    /**
     * Stores the logical and physical viewport for the next draw.
     *
     * @param viewport target viewport
     * @throws NullPointerException if {@code viewport} is null
     */
    @Override
    public void resize(Viewport viewport) {
        this.viewport = Objects.requireNonNull(viewport, "viewport");
    }

    /**
     * Creates a texture from RGBA8 image data with default sampling options in the current
     * OpenGL context.
     *
     * @param image image data with a top-left origin
     * @return the created texture
     * @throws NullPointerException if {@code image} is null
     */
    @Override
    public Texture createTexture(ImageData image) {
        return createTexture(image, TextureOptions.DEFAULT);
    }

    /**
     * Creates a texture from RGBA8 image data with explicit sampling options in the current
     * OpenGL context. Mipmaps are generated on the GPU when requested.
     *
     * @param image image data with a top-left origin
     * @param options filtering, wrapping and mipmap settings applied to the texture
     * @return the created texture
     * @throws NullPointerException if an argument is null
     */
    @Override
    public Texture createTexture(ImageData image, TextureOptions options) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(options, "options");
        try (OpenGlState state = new OpenGlState()) {
            state.prepareUpload();
            return uploadTexture(image, options);
        }
    }

    private Texture uploadTexture(ImageData image, TextureOptions options) {
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        try {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter(options));
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter(options.filter()));
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapMode(options.wrapU()));
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrapMode(options.wrapV()));

            ByteBuffer pixels = MemoryUtil.memAlloc(image.pixels().length);
            try {
                pixels.put(image.pixels()).flip();
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, image.width(), image.height(), 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            } finally {
                MemoryUtil.memFree(pixels);
            }
            if (options.mipmaps()) {
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            }
        } catch (RuntimeException | Error exception) {
            GL11.glDeleteTextures(id);
            throw exception;
        }

        GlTexture texture = new GlTexture(id, image.width(), image.height(), true, options);
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
        return wrapTexture(textureId, width, height, TextureOptions.DEFAULT);
    }

    /**
     * Wraps an existing OpenGL texture owned by the host application and records the sampling
     * options associated with it. The backend never deletes the host texture.
     *
     * @param textureId existing OpenGL texture name
     * @param width texture width in pixels
     * @param height texture height in pixels
     * @param options sampling options that describe the host texture state
     * @return a non-owning texture handle
     * @throws NullPointerException if {@code options} is null
     * @throws IllegalArgumentException if the texture name or a dimension is not positive
     */
    public Texture wrapTexture(int textureId, int width, int height, TextureOptions options) {
        if (textureId <= 0) {
            throw new IllegalArgumentException("Texture name must be positive.");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Texture dimensions must be positive.");
        }
        Objects.requireNonNull(options, "options");
        return new GlTexture(textureId, width, height, false, options);
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
        if (!glTexture.owned() || !textures.remove(glTexture)) {
            return;
        }
        GL11.glDeleteTextures(glTexture.id());
        if (whiteTexture == glTexture) {
            whiteTexture = null;
        }
    }

    /**
     * Draws all commands into the currently bound framebuffer.
     *
     * @param drawList commands to render
     * @throws NullPointerException if {@code drawList} is null
     * @throws IllegalStateException if {@link #resize(Viewport)} has not been called
     */
    @Override
    public void draw(DrawList drawList) {
        Objects.requireNonNull(drawList, "drawList");
        if (viewport == null) {
            throw new IllegalStateException("resize must be called before draw.");
        }

        drawCallCount = 0;
        collect(drawList);
        if (batches.isEmpty()) {
            return;
        }

        GL11.glViewport(0, 0, viewport.framebufferWidth(), viewport.framebufferHeight());
        boolean scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean stencilWasEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean multisampleWasEnabled = GL11.glIsEnabled(GL13.GL_MULTISAMPLE);
        int previousStencilMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        int previousClearStencil = GL11.glGetInteger(GL11.GL_STENCIL_CLEAR_VALUE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL13.glEnable(GL13.GL_MULTISAMPLE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glClearStencil(0);
        GL11.glStencilMask(0xFF);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        GL30.glBindVertexArray(vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
        vertices.position(0).limit(floatCount);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STREAM_DRAW);
        vertices.clear();

        GL20.glUseProgram(program);
        GL20.glUniform2f(viewportLocation, viewport.logicalWidth(), viewport.logicalHeight());
        GL20.glUniform1i(textureLocation, 0);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        Paint previousPaint = null;
        boolean paintSet = false;
        for (Batch batch : batches) {
            if (!applyClip(batch.clip())) {
                continue;
            }
            applyStencil(batch);
            if (!paintSet || !Objects.equals(previousPaint, batch.paint())) {
                applyPaint(batch.paint());
                previousPaint = batch.paint();
                paintSet = true;
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, batch.texture().id());
            GL11.glDrawArrays(GL11.GL_TRIANGLES, batch.firstVertex(), batch.vertexCount());
            drawCallCount++;
        }

        GL11.glColorMask(true, true, true, true);
        GL11.glStencilMask(previousStencilMask);
        GL11.glClearStencil(previousClearStencil);
        if (!stencilWasEnabled) {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
        if (!scissorWasEnabled) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        if (!multisampleWasEnabled) {
            GL11.glDisable(GL13.GL_MULTISAMPLE);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL20.glUseProgram(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    /**
     * Returns the number of draw calls issued by the most recent {@link #draw(DrawList)}.
     *
     * @return the draw call count of the last frame
     */
    @Override
    public int drawCallCount() {
        return drawCallCount;
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
        viewport = null;
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
        Paint currentPaint = null;
        int currentStencilRef = 0;
        MaskAction currentAction = MaskAction.DRAW;
        int stencilDepth = 0;
        int batchStartVertex = 0;
        int batchVertexCount = 0;
        for (DrawCommand command : drawList.commands()) {
            MaskAction action = CommandClassifier.maskAction(command);
            int stencilRef = stencilDepth;
            GlTexture texture = asGlTexture(CommandClassifier.textureOf(command, whiteTexture));
            Paint paint = ShaderPaint.gradient(command);
            if (!command.clip().equals(currentClip) || texture != currentTexture
                    || stencilRef != currentStencilRef || action != currentAction
                    || !Objects.equals(paint, currentPaint)) {
                if (batchVertexCount > 0) {
                    batches.add(new Batch(batchStartVertex, batchVertexCount,
                            currentClip, currentTexture, currentStencilRef, currentAction, currentPaint));
                }
                currentClip = command.clip();
                currentTexture = texture;
                currentPaint = paint;
                currentStencilRef = stencilRef;
                currentAction = action;
                batchStartVertex = floatCount / FLOATS_PER_VERTEX;
                batchVertexCount = 0;
            }
            int commandVertices = CommandClassifier.vertexCount(command);
            ensureCapacity(commandVertices * FLOATS_PER_VERTEX);
            if (command instanceof MaskBeginCommand mask) {
                appendMask(mask.vertices());
            } else if (command instanceof MaskEndCommand mask) {
                appendMask(mask.vertices());
            } else if (command instanceof MaskTextureBeginCommand mask) {
                appendTexturedQuad(mask.bounds(), mask.uv(), mask.transform());
            } else if (command instanceof MaskTextureEndCommand mask) {
                appendTexturedQuad(mask.bounds(), mask.uv(), mask.transform());
            } else if (command instanceof TextCommand text) {
                appendText(text);
            } else if (command instanceof MeshCommand mesh) {
                appendMesh(mesh);
            } else {
                appendQuad(command);
            }
            batchVertexCount += commandVertices;
            if (action == MaskAction.PUSH) {
                stencilDepth++;
            } else if (action == MaskAction.POP) {
                stencilDepth--;
            }
        }
        if (batchVertexCount > 0) {
            batches.add(new Batch(batchStartVertex, batchVertexCount,
                    currentClip, currentTexture, currentStencilRef, currentAction, currentPaint));
        }
    }



    private void applyStencil(Batch batch) {
        GL11.glStencilMask(0xFF);
        GL11.glStencilFunc(GL11.GL_EQUAL, batch.stencilRef(), 0xFF);
        switch (batch.action()) {
            case DRAW -> {
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                GL11.glColorMask(true, true, true, true);
            }
            case PUSH -> {
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);
                GL11.glColorMask(false, false, false, false);
            }
            case POP -> {
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_DECR);
                GL11.glColorMask(false, false, false, false);
            }
            default -> throw new IllegalStateException("Unknown mask action.");
        }
    }

    private void appendQuad(DrawCommand command) {
        if (command instanceof ImageCommand image) {
            appendQuad(image.bounds(), image.radius(), image.color(), image.uv(), image.transform());
        } else if (command instanceof ShapeCommand shape) {
            appendQuad(shape.bounds(), shape.ellipse() ? -1 : shape.radius(), shape.color(),
                    shape.bounds(), shape.transform(), true);
        } else {
            appendQuad(command.bounds(), command.radius(), command.color(), FULL_UV, Transform.IDENTITY, true);
        }
    }

    private void appendQuad(Rect bounds, float radius, Color color, Rect uv, Transform transform) {
        appendQuad(bounds, radius, color, uv, transform, false);
    }

    private void appendQuad(Rect bounds, float radius, Color color, Rect uv,
                            Transform transform, boolean feather) {
        QuadGeometry.append(bounds, radius, color, uv, transform, feather, viewport,
                this::putVertex);
    }

    private void appendTexturedQuad(Rect bounds, Rect uv, Transform transform) {
        appendQuad(bounds, 0, Color.WHITE, uv, transform);
    }

    private void applyPaint(Paint paint) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer parameters = stack.mallocFloat(ShaderPaint.FLOAT_COUNT);
            ShaderPaint.write(paint, parameters);
            parameters.flip();
            GL20.glUniform4fv(paintLocation, parameters);
        }
    }

    private void appendMesh(MeshCommand command) {
        appendMask(command.vertices());
    }

    private void appendText(TextCommand command) {
        for (int index = 0; index < command.glyphs().size(); index++) {
            appendQuad(command.glyphs().get(index).bounds(), 0, command.colors().get(index),
                    command.glyphs().get(index).uv(), command.transform());
        }
    }

    private void appendMask(List<MeshVertex> vertices) {
        for (MeshVertex vertex : vertices) {
            Color color = vertex.color();
            putVertex(vertex.x(), vertex.y(), 0, 0, MESH_HALF_SIZE, MESH_HALF_SIZE, 0,
                    color.r(), color.g(), color.b(), color.a(), 0.5f, 0.5f);
        }
    }

    private void putVertex(float x, float y, float localX, float localY,
                           float halfWidth, float halfHeight, float radius,
                           float red, float green, float blue, float alpha,
                           float u, float v) {
        vertices.put(x).put(y)
                .put(localX).put(localY)
                .put(halfWidth).put(halfHeight)
                .put(radius)
                .put(red).put(green).put(blue).put(alpha)
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
        float scaleX = viewport.scaleX();
        float scaleY = viewport.scaleY();
        float left = Math.max(clip.x() * scaleX, 0);
        float top = Math.max(clip.y() * scaleY, 0);
        float right = Math.min(clip.right() * scaleX, viewport.framebufferWidth());
        float bottom = Math.min(clip.bottom() * scaleY, viewport.framebufferHeight());
        if (right <= left || bottom <= top) {
            return false;
        }

        int scissorX = (int) Math.floor(left);
        int scissorY = (int) Math.floor(top);
        int scissorWidth = (int) Math.ceil(right) - scissorX;
        int scissorHeight = (int) Math.ceil(bottom) - scissorY;
        GL11.glScissor(scissorX,
                viewport.framebufferHeight() - scissorY - scissorHeight,
                scissorWidth,
                scissorHeight);
        return true;
    }

    private static GlTexture asGlTexture(Texture texture) {
        Objects.requireNonNull(texture, "texture");
        if (!(texture instanceof GlTexture glTexture)) {
            throw new IllegalArgumentException("Texture belongs to a different render backend.");
        }
        return glTexture;
    }

    private static int minFilter(TextureOptions options) {
        if (options.filter() == TextureFilter.NEAREST) {
            return options.mipmaps() ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_NEAREST;
        }
        return options.mipmaps() ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR;
    }

    private static int magFilter(TextureFilter filter) {
        return filter == TextureFilter.NEAREST ? GL11.GL_NEAREST : GL11.GL_LINEAR;
    }

    private static int wrapMode(TextureWrap wrap) {
        return switch (wrap) {
            case CLAMP -> GL12.GL_CLAMP_TO_EDGE;
            case REPEAT -> GL11.GL_REPEAT;
            case MIRROR -> GL14.GL_MIRRORED_REPEAT;
        };
    }

    private static int createProgram() {
        return createProgram("rounded_rect.vert", "rounded_rect.frag");
    }

    static int createProgram(String vertexResource, String fragmentResource) {
        int vertexShader = createShader(GL20.GL_VERTEX_SHADER, loadShader(vertexResource));
        int fragmentShader;
        try {
            fragmentShader = createShader(GL20.GL_FRAGMENT_SHADER, loadShader(fragmentResource));
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

    private record Batch(int firstVertex, int vertexCount, Rect clip, GlTexture texture,
                         int stencilRef, MaskAction action, Paint paint) {
    }

}
