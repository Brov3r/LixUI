package com.lixui.backend;

/**
 * Stencil operation required by a draw command.
 */
public enum MaskAction {
    /** Draws content normally. */
    DRAW,
    /** Writes the command to the stencil buffer and hides color. */
    PUSH,
    /** Removes the command from the stencil buffer and hides color. */
    POP
}
