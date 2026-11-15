package com.lixui.demo;

import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.FontStyle;
import com.lixui.text.SystemFonts;

/**
 * Regular, bold, italic and bold-italic variants of the demo font.
 *
 * @param regular regular face
 * @param bold synthetic bold face
 * @param italic synthetic italic face
 * @param boldItalic synthetic bold-italic face
 */
record DemoFonts(Font regular, Font bold, Font italic, Font boldItalic) {
    /**
     * Loads all demo styles from the default system font.
     *
     * @param renderer drawing manager that owns the font atlases
     * @return the font set, or null when no system font is available
     */
    static DemoFonts load(RenderManager renderer) {
        return SystemFonts.findDefault()
                .map(data -> new DemoFonts(
                        renderer.createFont(data, 24, FontStyle.REGULAR),
                        renderer.createFont(data, 24, FontStyle.BOLD, 0.8f),
                        renderer.createFont(data, 24, FontStyle.ITALIC),
                        renderer.createFont(data, 24, FontStyle.BOLD_ITALIC, 0.5f)))
                .orElse(null);
    }
}
