package com.lixui.demo;

import com.lixui.render.Texture;

import java.util.function.Consumer;

/**
 * Loads a bundled demo icon and assigns the resulting texture to a target.
 */
@FunctionalInterface
interface DemoIconBinder {
    /**
     * Binds one icon.
     *
     * @param name bundled icon resource name
     * @param size requested icon size in logical UI units
     * @param assign consumer receiving the loaded texture
     */
    void bind(String name, float size, Consumer<Texture> assign);
}
