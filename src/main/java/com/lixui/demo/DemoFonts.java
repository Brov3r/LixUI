package com.lixui.demo;

import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.FontStyle;
import com.lixui.text.SystemFonts;

import java.util.List;
import java.util.Optional;

/**
 * Regular, bold, italic and bold-italic variants of the demo font.
 * Installed faces are preferred; missing styles fall back to synthetic rasterization.
 *
 * @param regular regular face
 * @param bold bold face
 * @param italic italic face
 * @param boldItalic bold-italic face
 */
record DemoFonts(Font regular, Font bold, Font italic, Font boldItalic) {
    static final List<String> FAMILIES = List.of(
            "Segoe UI", "Arial", "Helvetica Neue", "Helvetica",
            "DejaVu Sans", "Liberation Sans", "Noto Sans", "Tahoma", "Verdana");

    /**
     * Loads all demo styles from the first installed family that provides a regular face.
     * Real bold and italic faces are used when installed; otherwise synthetic styles are baked.
     *
     * @param renderer drawing manager that owns the font atlases
     * @return the font set, or null when no system font is available
     */
    static DemoFonts load(RenderManager renderer) {
        for (String family : FAMILIES) {
            Optional<FontData> regularData = SystemFonts.find(family);
            if (regularData.isEmpty()) {
                continue;
            }
            FontData data = regularData.get();
            Font regular = renderer.createFont(data, 24);
            Font bold = renderer.createFont(family, 24, FontStyle.BOLD)
                    .orElseGet(() -> renderer.createFont(data, 24, FontStyle.BOLD, 0.8f));
            Font italic = renderer.createFont(family, 24, FontStyle.ITALIC)
                    .orElseGet(() -> renderer.createFont(data, 24, FontStyle.ITALIC));
            Font boldItalic = renderer.createFont(family, 24, FontStyle.BOLD_ITALIC)
                    .orElseGet(() -> renderer.createFont(data, 24, FontStyle.BOLD_ITALIC, 0.5f));
            return new DemoFonts(regular, bold, italic, boldItalic);
        }
        return SystemFonts.findDefault()
                .map(data -> new DemoFonts(
                        renderer.createFont(data, 24),
                        renderer.createFont(data, 24, FontStyle.BOLD, 0.8f),
                        renderer.createFont(data, 24, FontStyle.ITALIC),
                        renderer.createFont(data, 24, FontStyle.BOLD_ITALIC, 0.5f)))
                .orElse(null);
    }
}
