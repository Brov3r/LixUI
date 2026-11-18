package com.lixui.backend.opengl;

import com.lixui.backend.ShaderPaint;
import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.render.DrawCommand;
import com.lixui.render.DrawList;
import com.lixui.render.ImageCommand;
import com.lixui.render.ImageData;
import com.lixui.render.MaskBeginCommand;
import com.lixui.render.MaskEndCommand;
import com.lixui.render.MaskTextureBeginCommand;
import com.lixui.render.MaskTextureEndCommand;
import com.lixui.render.MeshCommand;
import com.lixui.render.MeshVertex;
import com.lixui.render.RenderBackend;
import com.lixui.render.ShapeCommand;
import com.lixui.render.TextCommand;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
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
            MaskAction action = maskAction(command);
            int stencilRef = stencilDepth;
            GlTexture texture = asGlTexture(textureOf(command));
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
            int commandVertices = vertexCount(command);
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

    private static MaskAction maskAction(DrawCommand command) {
        if (command instanceof MaskBeginCommand || command instanceof MaskTextureBeginCommand) {
            return MaskAction.PUSH;
        }
        if (command instanceof MaskEndCommand || command instanceof MaskTextureEndCommand) {
            return MaskAction.POP;
        }
        return MaskAction.DRAW;
    }

    private Texture textureOf(DrawCommand command) {
        if (command instanceof ImageCommand image) {
            return image.texture();
        }
        if (command instanceof TextCommand text) {
            return text.texture();
        }
        if (command instanceof MaskTextureBeginCommand mask) {
            return mask.texture();
        }
        if (command instanceof MaskTextureEndCommand mask) {
            return mask.texture();
        }
        return whiteTexture;
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

    private void appendQuad(Rect bounds, float radius, Color color, Rect uv, Transform transform, boolean feather) {
        float halfWidth = bounds.width() / 2;
        float halfHeight = bounds.height() / 2;
        float padX = 0;
        float padY = 0;
        float determinant = Math.abs(transform.m00() * transform.m11() - transform.m01() * transform.m10());
        if (feather && determinant > 0) {
            // Reserve a physical pixel outside the contour so the shader can cover both sides of an edge.
            padX = (Math.abs(transform.m11()) / viewport.scaleX()
                    + Math.abs(transform.m01()) / viewport.scaleY()) / determinant;
            padY = (Math.abs(transform.m10()) / viewport.scaleX()
                    + Math.abs(transform.m00()) / viewport.scaleY()) / determinant;
        }
        float localWidth = halfWidth + padX;
        float localHeight = halfHeight + padY;
        float x0 = bounds.x() - padX;
        float y0 = bounds.y() - padY;
        float x1 = bounds.right() + padX;
        float y1 = y0;
        float x2 = x1;
        float y2 = bounds.bottom() + padY;
        float x3 = x0;
        float y3 = y2;
        if (!transform.isIdentity()) {
            Point topLeft = transform.apply(new Point(x0, y0));
            Point topRight = transform.apply(new Point(x1, y1));
            Point bottomRight = transform.apply(new Point(x2, y2));
            Point bottomLeft = transform.apply(new Point(x3, y3));
            x0 = topLeft.x();
            y0 = topLeft.y();
            x1 = topRight.x();
            y1 = topRight.y();
            x2 = bottomRight.x();
            y2 = bottomRight.y();
            x3 = bottomLeft.x();
            y3 = bottomLeft.y();
        }

        float padU = bounds.width() > 0 ? padX * uv.width() / bounds.width() : 0;
        float padV = bounds.height() > 0 ? padY * uv.height() / bounds.height() : 0;
        float u0 = uv.x() - padU;
        float v0 = uv.y() - padV;
        float u1 = uv.right() + padU;
        float v1 = uv.bottom() + padV;

        putVertex(x0, y0, -localWidth, -localHeight, halfWidth, halfHeight, radius, color, u0, v0);
        putVertex(x1, y1, localWidth, -localHeight, halfWidth, halfHeight, radius, color, u1, v0);
        putVertex(x2, y2, localWidth, localHeight, halfWidth, halfHeight, radius, color, u1, v1);
        putVertex(x0, y0, -localWidth, -localHeight, halfWidth, halfHeight, radius, color, u0, v0);
        putVertex(x2, y2, localWidth, localHeight, halfWidth, halfHeight, radius, color, u1, v1);
        putVertex(x3, y3, -localWidth, localHeight, halfWidth, halfHeight, radius, color, u0, v1);
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
            putVertex(vertex.x(), vertex.y(), 0, 0, MESH_HALF_SIZE, MESH_HALF_SIZE, 0,
                    vertex.color(), 0.5f, 0.5f);
        }
    }

    private static int vertexCount(DrawCommand command) {
        if (command instanceof TextCommand text) {
            return text.glyphs().size() * VERTICES_PER_QUAD;
        }
        if (command instanceof MeshCommand mesh) {
            return mesh.vertices().size();
        }
        if (command instanceof MaskBeginCommand mask) {
            return mask.vertices().size();
        }
        if (command instanceof MaskEndCommand mask) {
            return mask.vertices().size();
        }
        return VERTICES_PER_QUAD;
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

    private record Batch(int firstVertex, int vertexCount, Rect clip, GlTexture texture,
                         int stencilRef, MaskAction action, Paint paint) {
    }

    private enum MaskAction {
        /** Draws content using the current stencil reference. */
        DRAW,
        /** Increments the stencil buffer without writing color. */
        PUSH,
        /** Decrements the stencil buffer without writing color. */
        POP
    }
}
