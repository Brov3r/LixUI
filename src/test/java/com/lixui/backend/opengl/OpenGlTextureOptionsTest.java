package com.lixui.backend.opengl;

import com.lixui.render.ImageData;
import com.lixui.render.Texture;
import com.lixui.render.TextureFilter;
import com.lixui.render.TextureOptions;
import com.lixui.render.TextureWrap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenGlTextureOptionsTest {
    @Test
    void appliesFilterWrapAndMipmapsToUploadedTextures() {
        Assumptions.assumeTrue(GLFW.glfwInit(), "GLFW display unavailable.");
        long window = 0;
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            window = GLFW.glfwCreateWindow(64, 64, "LixUI texture options", 0, 0);
            Assumptions.assumeTrue(window != 0, "OpenGL context unavailable.");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            verifyTextureOptions();
        } finally {
            GL.setCapabilities(null);
            if (window != 0) {
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
        }
    }

    private static void verifyTextureOptions() {
        TextureOptions options = new TextureOptions(
                TextureFilter.NEAREST, TextureWrap.REPEAT, TextureWrap.MIRROR, true);
        ImageData image = new ImageData(4, 4, new byte[64]);
        try (OpenGlLayer layer = new OpenGlLayer()) {
            Texture texture = layer.createTexture(image, options);
            assertEquals(options, texture.options());

            int id = ((GlTexture) texture).id();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            try {
                assertEquals(GL11.GL_NEAREST_MIPMAP_NEAREST,
                        GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER));
                assertEquals(GL11.GL_NEAREST,
                        GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER));
                assertEquals(GL11.GL_REPEAT,
                        GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S));
                assertEquals(GL14.GL_MIRRORED_REPEAT,
                        GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T));
                assertEquals(2, GL11.glGetTexLevelParameteri(
                        GL11.GL_TEXTURE_2D, 1, GL11.GL_TEXTURE_WIDTH));
                assertEquals(2, GL11.glGetTexLevelParameteri(
                        GL11.GL_TEXTURE_2D, 1, GL11.GL_TEXTURE_HEIGHT));
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }

            layer.destroyTexture(texture);
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        }
    }
}
