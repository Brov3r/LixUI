package com.lixui.backend;

import com.lixui.render.Texture;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.ImageCommand;
import com.lixui.render.command.MaskBeginCommand;
import com.lixui.render.command.MaskEndCommand;
import com.lixui.render.command.MaskTextureBeginCommand;
import com.lixui.render.command.MaskTextureEndCommand;
import com.lixui.render.command.MeshCommand;
import com.lixui.render.command.TextCommand;

/**
 * Backend-independent inspection of draw commands used by both render backends.
 */
public final class CommandClassifier {
    private static final int VERTICES_PER_QUAD = 6;

    /**
     * Prevents instantiation of the utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private CommandClassifier() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    /**
     * Returns the stencil operation required by a command.
     *
     * @param command command to inspect
     * @return the stencil action
     */
    public static MaskAction maskAction(DrawCommand command) {
        if (command instanceof MaskBeginCommand || command instanceof MaskTextureBeginCommand) {
            return MaskAction.PUSH;
        }
        if (command instanceof MaskEndCommand || command instanceof MaskTextureEndCommand) {
            return MaskAction.POP;
        }
        return MaskAction.DRAW;
    }

    /**
     * Returns the texture sampled by a command, or the supplied fallback texture.
     *
     * @param command command to inspect
     * @param whiteTexture texture used by untextured commands
     * @return the sampled texture
     */
    public static Texture textureOf(DrawCommand command, Texture whiteTexture) {
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

    /**
     * Returns the number of vertices emitted by a command.
     *
     * @param command command to inspect
     * @return the vertex count
     */
    public static int vertexCount(DrawCommand command) {
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
}
