package com.lixui.backend.opengl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * Saves the OpenGL 3.3 state touched by UI rendering and texture uploads.
 */
final class OpenGlState implements AutoCloseable {
    private static final int[] CAPABILITIES = {
            GL11.GL_BLEND, GL11.GL_CULL_FACE, GL11.GL_DEPTH_TEST, GL11.GL_SCISSOR_TEST,
            GL11.GL_STENCIL_TEST, GL13.GL_MULTISAMPLE, GL30.GL_RASTERIZER_DISCARD,
            GL11.GL_COLOR_LOGIC_OP, GL30.GL_FRAMEBUFFER_SRGB, GL13.GL_SAMPLE_ALPHA_TO_COVERAGE,
            GL13.GL_SAMPLE_COVERAGE, GL32.GL_SAMPLE_MASK
    };
    private static final int[] UNPACK_PARAMETERS = {
            GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH,
            GL11.GL_UNPACK_SKIP_PIXELS, GL11.GL_UNPACK_SKIP_ROWS
    };
    private final boolean[] enabled = new boolean[CAPABILITIES.length];
    private final int[] unpack = new int[UNPACK_PARAMETERS.length];
    private final boolean[] clipDistances = new boolean[GL11.glGetInteger(GL30.GL_MAX_CLIP_DISTANCES)];
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
    private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int renderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int texture;
    private final int sampler;
    private final int[] viewport = new int[4];
    private final int[] scissor = new int[4];
    private final int[] polygonMode = new int[2];
    private final float[] clearColor = new float[4];
    private final boolean[] colorMask = new boolean[4];
    private final int drawBuffers = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
    private final boolean[][] indexedColorMasks = new boolean[drawBuffers][4];
    private final boolean[] indexedBlend = new boolean[drawBuffers];
    private final int[][] indexedBlendParameters;
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int clearStencil = GL11.glGetInteger(GL11.GL_STENCIL_CLEAR_VALUE);
    private final int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
    private final int blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final Stencil front = new Stencil(false);
    private final Stencil back = new Stencil(true);

    OpenGlState() {
        for (int index = 0; index < CAPABILITIES.length; index++) {
            enabled[index] = GL11.glIsEnabled(CAPABILITIES[index]);
        }
        for (int index = 0; index < unpack.length; index++) {
            unpack[index] = GL11.glGetInteger(UNPACK_PARAMETERS[index]);
        }
        for (int index = 0; index < clipDistances.length; index++) {
            clipDistances[index] = GL11.glIsEnabled(GL30.GL_CLIP_DISTANCE0 + index);
        }
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, polygonMode);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            for (int index = 0; index < 4; index++) {
                colorMask[index] = mask.get(index) != 0;
            }
            for (int buffer = 0; buffer < drawBuffers; buffer++) {
                GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK, buffer, mask);
                for (int index = 0; index < 4; index++) {
                    indexedColorMasks[buffer][index] = mask.get(index) != 0;
                }
                indexedBlend[buffer] = GL30.glIsEnabledi(GL11.GL_BLEND, buffer);
            }
        }
        boolean separateBlend = GL.getCapabilities().OpenGL40 || GL.getCapabilities().GL_ARB_draw_buffers_blend;
        indexedBlendParameters = separateBlend ? new int[drawBuffers][6] : null;
        if (separateBlend) {
            int[] parameters = {GL14.GL_BLEND_SRC_RGB, GL14.GL_BLEND_DST_RGB,
                    GL14.GL_BLEND_SRC_ALPHA, GL14.GL_BLEND_DST_ALPHA,
                    GL20.GL_BLEND_EQUATION_RGB, GL20.GL_BLEND_EQUATION_ALPHA};
            for (int buffer = 0; buffer < drawBuffers; buffer++) {
                for (int index = 0; index < parameters.length; index++) {
                    indexedBlendParameters[buffer][index] = GL30.glGetIntegeri(parameters[index], buffer);
                }
            }
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        sampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
    }

    int drawFramebuffer() {
        return drawFramebuffer;
    }

    void prepareUpload() {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
    }

    void prepareDrawing() {
        for (int capability : CAPABILITIES) {
            GL11.glDisable(capability);
        }
        for (int index = 0; index < clipDistances.length; index++) {
            GL11.glDisable(GL30.GL_CLIP_DISTANCE0 + index);
        }
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL11.glDepthMask(false);
        GL11.glColorMask(true, true, true, true);
        GL11.glEnable(GL11.GL_BLEND);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL33.glBindSampler(0, 0);
    }

    /**
     * Restores the captured host state, including separate front and back stencil settings.
     */
    @Override
    public void close() {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        for (int index = 0; index < unpack.length; index++) {
            GL11.glPixelStorei(UNPACK_PARAMETERS[index], unpack[index]);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL33.glBindSampler(0, sampler);
        GL13.glActiveTexture(activeTexture);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GL11.glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, polygonMode[0]);
        GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        GL11.glDepthMask(depthMask);
        GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        GL11.glClearStencil(clearStencil);
        GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        front.restore(GL11.GL_FRONT);
        back.restore(GL11.GL_BACK);
        for (int index = 0; index < CAPABILITIES.length; index++) {
            setEnabled(CAPABILITIES[index], enabled[index]);
        }
        for (int index = 0; index < clipDistances.length; index++) {
            setEnabled(GL30.GL_CLIP_DISTANCE0 + index, clipDistances[index]);
        }
        for (int buffer = 0; buffer < drawBuffers; buffer++) {
            boolean[] mask = indexedColorMasks[buffer];
            GL30.glColorMaski(buffer, mask[0], mask[1], mask[2], mask[3]);
            if (indexedBlend[buffer]) {
                GL30.glEnablei(GL11.GL_BLEND, buffer);
            } else {
                GL30.glDisablei(GL11.GL_BLEND, buffer);
            }
            if (indexedBlendParameters != null) {
                int[] blend = indexedBlendParameters[buffer];
                if (GL.getCapabilities().OpenGL40) {
                    GL40.glBlendFuncSeparatei(buffer, blend[0], blend[1], blend[2], blend[3]);
                    GL40.glBlendEquationSeparatei(buffer, blend[4], blend[5]);
                } else {
                    ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, blend[0], blend[1], blend[2], blend[3]);
                    ARBDrawBuffersBlend.glBlendEquationSeparateiARB(buffer, blend[4], blend[5]);
                }
            }
        }
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    private record Stencil(int function, int reference, int valueMask, int writeMask,
                           int fail, int depthFail, int pass) {
        private Stencil(boolean back) {
            this(GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_FUNC : GL11.GL_STENCIL_FUNC),
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_REF : GL11.GL_STENCIL_REF),
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_VALUE_MASK : GL11.GL_STENCIL_VALUE_MASK),
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_WRITEMASK : GL11.GL_STENCIL_WRITEMASK),
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_FAIL : GL11.GL_STENCIL_FAIL),
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL : GL11.GL_STENCIL_PASS_DEPTH_FAIL),
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS : GL11.GL_STENCIL_PASS_DEPTH_PASS));
        }

        private void restore(int face) {
            GL20.glStencilFuncSeparate(face, function, reference, valueMask);
            GL20.glStencilMaskSeparate(face, writeMask);
            GL20.glStencilOpSeparate(face, fail, depthFail, pass);
        }
    }
}
