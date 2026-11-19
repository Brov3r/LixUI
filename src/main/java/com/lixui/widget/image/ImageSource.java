package com.lixui.widget.image;

import com.lixui.render.Texture;

import java.net.URI;
import java.util.Objects;

/**
 * Describes where an image widget gets its pixels from.
 * Local textures are caller-owned, bundled icons use the shared context icon cache, and remote
 * sources are resolved through an {@link ImageLoader}.
 */
public sealed interface ImageSource {
    /**
     * Creates a source backed by a caller-owned texture.
     *
     * @param texture texture to draw
     * @return the texture source
     * @throws NullPointerException if {@code texture} is null
     */
    static ImageSource texture(Texture texture) {
        return new TextureSource(texture);
    }

    /**
     * Creates a source backed by a bundled icon.
     *
     * @param name bundled icon resource name
     * @return the icon source
     * @throws NullPointerException if {@code name} is null
     * @throws IllegalArgumentException if the name is blank
     */
    static ImageSource icon(String name) {
        return new IconSource(name);
    }

    /**
     * Creates a source that loads an image over HTTP or HTTPS.
     *
     * @param uri absolute HTTP or HTTPS image URI
     * @return the remote source
     * @throws NullPointerException if {@code uri} is null
     * @throws IllegalArgumentException if the URI is not an absolute HTTP or HTTPS URI with a host
     */
    static ImageSource remote(URI uri) {
        return new RemoteSource(uri);
    }

    /**
     * Creates a source that loads an image over HTTP or HTTPS.
     *
     * @param uri absolute HTTP or HTTPS image URI
     * @return the remote source
     * @throws NullPointerException if {@code uri} is null
     * @throws IllegalArgumentException if the URI is invalid or not an HTTP or HTTPS URI with a host
     */
    static ImageSource remote(String uri) {
        return remote(URI.create(Objects.requireNonNull(uri, "uri")));
    }

    /**
     * A source backed by a caller-owned texture.
     *
     * @param texture texture to draw
     */
    record TextureSource(Texture texture) implements ImageSource {
        /**
         * Creates a texture source.
         *
         * @param texture texture to draw
         * @throws NullPointerException if {@code texture} is null
         */
        public TextureSource {
            Objects.requireNonNull(texture, "texture");
        }
    }

    /**
     * A source backed by a bundled icon.
     *
     * @param name bundled icon resource name
     */
    record IconSource(String name) implements ImageSource {
        /**
         * Creates an icon source.
         *
         * @param name bundled icon resource name
         * @throws NullPointerException if {@code name} is null
         * @throws IllegalArgumentException if the name is blank
         */
        public IconSource {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("Icon name must not be blank.");
            }
        }
    }

    /**
     * A source loaded from an HTTP or HTTPS URI.
     *
     * @param uri absolute image URI
     */
    record RemoteSource(URI uri) implements ImageSource {
        /**
         * Creates a remote image source.
         *
         * @param uri absolute HTTP or HTTPS image URI
         * @throws NullPointerException if {@code uri} is null
         * @throws IllegalArgumentException if the URI is not absolute or has no HTTP(S) host
         */
        public RemoteSource {
            Objects.requireNonNull(uri, "uri");
            if (!uri.isAbsolute() || uri.getHost() == null) {
                throw new IllegalArgumentException("Remote image URI must be absolute and have a host.");
            }
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Remote image URI must use HTTP or HTTPS.");
            }
        }
    }
}
