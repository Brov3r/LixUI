package com.lixui.backend.opengl;

import com.lixui.widget.testsupport.TestViewports;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.render.ImageData;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGlLayerTest {
    @Test
    void compositesPremultipliedUiWithoutChangingHostDepthStencilOrState() {
        Assumptions.assumeTrue(GLFW.glfwInit(), "GLFW display unavailable.");
        long window = 0;
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, 8);
            GLFW.glfwWindowHint(GLFW.GLFW_DEPTH_BITS, 24);
            window = GLFW.glfwCreateWindow(128, 128, "LixUI layer isolation", 0, 0);
            Assumptions.assumeTrue(window != 0, "OpenGL context unavailable.");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            verifyIsolation();
        } finally {
            GL.setCapabilities(null);
            if (window != 0) {
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
        }
    }

    private static void verifyIsolation() {
        int framebuffer = GL30.glGenFramebuffers();
        int color = GL30.glGenRenderbuffers();
        int depthStencil = GL30.glGenRenderbuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, color);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_RGBA8, 128, 128);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL30.GL_RENDERBUFFER, color);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencil);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, 128, 128);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                GL30.GL_RENDERBUFFER, depthStencil);
        assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE, GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER));
        GL11.glClearColor(0, 0, 1, 1);
        GL11.glClearStencil(7);
        GL11.glClearDepth(0.25);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer depth = stack.mallocFloat(1);
            GL11.glReadPixels(25, 102, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "Host depth read must be valid.");
            assertEquals(0.25f, depth.get(0), 0.00001f, "Host depth must be initialized.");
        }
        int program = OpenGlBackend.createProgram("layer.vert", "layer.frag");
        int vao = GL30.glGenVertexArrays();
        int buffer = GL15.glGenBuffers();
        int texture = GL11.glGenTextures();
        int sampler = GL33.glGenSamplers();
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL33.glBindSampler(0, sampler);
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        GL11.glViewport(3, 4, 70, 80);
        GL11.glScissor(5, 6, 7, 8);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_NEVER, 3, 0x5A);
        GL11.glStencilMask(0x3C);
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_ZERO, GL11.GL_INVERT);
        GL11.glColorMask(false, true, false, true);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_SUBTRACT, GL14.GL_FUNC_REVERSE_SUBTRACT);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 17);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 2);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 3);
        int[] expected = hostState();

        try (OpenGlLayer layer = new OpenGlLayer()) {
            assertArrayEquals(expected, hostState(), "Constructor must preserve host state.");
            Texture icon = layer.createTexture(new ImageData(1, 1,
                    new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255}));
            assertArrayEquals(expected, hostState(), "Texture uploads must preserve host state.");
            RenderManager renderer = new RenderManager(layer);
            renderer.resize(TestViewports.FRAMEBUFFER);
            renderer.begin();
            renderer.pushMaskRect(new Rect(20, 20, 30, 30));
            renderer.rect(new Rect(0, 0, 100, 100), new Color(1, 0, 0, 0.5f));
            renderer.popMask();
            renderer.image(icon, new Rect(80, 80, 10, 10));
            renderer.end();

            assertArrayEquals(expected, hostState(), "Drawing must preserve host state.");
            assertPixel(25, 25, 128, 0, 127);
            assertPixel(10, 10, 0, 0, 255);
            assertPixel(85, 85, 255, 255, 255);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer stencil = stack.malloc(4);
                GL11.glReadPixels(25, 102, 1, 1, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, stencil);
                assertEquals(7, Byte.toUnsignedInt(stencil.get(0)));
                FloatBuffer depth = stack.mallocFloat(1);
                GL11.glReadPixels(25, 102, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
                assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "Layer depth read must be valid.");
                assertEquals(0.25f, depth.get(0), 0.00001f);
            }
            layer.destroyTexture(icon);
            assertTrue(layer.drawCallCount() > 1);
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GL20.glUseProgram(0);
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL20.glDeleteProgram(program);
            GL30.glDeleteVertexArrays(vao);
            GL15.glDeleteBuffers(buffer);
            GL11.glDeleteTextures(texture);
            GL33.glDeleteSamplers(sampler);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL30.glDeleteFramebuffers(framebuffer);
            GL30.glDeleteRenderbuffers(color);
            GL30.glDeleteRenderbuffers(depthStencil);
        }
    }

    private static int[] hostState() {
        int[] viewport = new int[4];
        int[] scissor = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        return new int[]{
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING), GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
                GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL11.GL_STENCIL_FUNC), GL11.glGetInteger(GL11.GL_STENCIL_REF),
                GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK), GL11.glGetInteger(GL11.GL_STENCIL_FAIL),
                GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL),
                GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST) ? 1 : 0, GL11.glIsEnabled(GL11.GL_CULL_FACE) ? 1 : 0,
                GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) ? 1 : 0, GL11.glIsEnabled(GL11.GL_STENCIL_TEST) ? 1 : 0,
                viewport[0], viewport[1], viewport[2], viewport[3], scissor[0], scissor[1], scissor[2], scissor[3],
                GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH), GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS),
                GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        };
    }

    private static void assertPixel(int x, int y, int red, int green, int blue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixel = stack.malloc(4);
            GL11.glReadPixels(x, 127 - y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            assertEquals(red, Byte.toUnsignedInt(pixel.get(0)), 1);
            assertEquals(green, Byte.toUnsignedInt(pixel.get(1)), 1);
            assertEquals(blue, Byte.toUnsignedInt(pixel.get(2)), 1);
        }
    }
}
