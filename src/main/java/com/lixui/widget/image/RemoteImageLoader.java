package com.lixui.widget.image;

import com.lixui.render.ImageData;
import com.lixui.render.Texture;
import com.lixui.render.TextureOptions;
import com.lixui.widget.UiContext;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * An HTTP image loader with request deduplication, background decoding and a bounded texture cache.
 * Downloads and decoding run on worker threads. Texture upload and all callbacks run on the render
 * thread through {@link UiContext#post(Runnable)}. Requests with the same URI, target size and
 * sampling options share one download and one cached texture.
 * Instances own a thread pool and an HTTP client, so the creator must close the loader when the UI
 * lifecycle ends.
 */
public final class RemoteImageLoader implements ImageLoader {
    private static final int MAX_WORKERS = 2;
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final long DEFAULT_MAX_CACHE_BYTES = 64L * 1024 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final UiContext context;
    private final Fetcher fetcher;
    private final ExecutorService executor;
    private final HttpClient client;
    private final long maxCacheBytes;
    private final Object lock = new Object();
    private final LinkedHashMap<ImageRequest, Entry> entries =
            new LinkedHashMap<>(16, 0.75f, true);

    private long cacheBytes;
    private boolean closed;

    /**
     * Creates a loader that downloads images over HTTP with the JDK client.
     *
     * @param context context that owns the loader and creates the uploaded textures
     * @throws NullPointerException if {@code context} is null
     */
    public RemoteImageLoader(UiContext context) {
        this.context = Objects.requireNonNull(context, "context");
        this.client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.fetcher = this::fetchHttp;
        this.executor = createExecutor();
        this.maxCacheBytes = DEFAULT_MAX_CACHE_BYTES;
    }

    RemoteImageLoader(UiContext context, Fetcher fetcher, ExecutorService executor) {
        this(context, fetcher, executor, DEFAULT_MAX_CACHE_BYTES);
    }

    RemoteImageLoader(UiContext context, Fetcher fetcher, ExecutorService executor,
                      long maxCacheBytes) {
        this.context = Objects.requireNonNull(context, "context");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.client = null;
        if (maxCacheBytes < 0) {
            throw new IllegalArgumentException("Maximum cache size must be non-negative.");
        }
        this.maxCacheBytes = maxCacheBytes;
    }

    /**
     * Requests an image and registers render-thread callbacks.
     *
     * @param request image request used as the cache key
     * @param onReady callback receiving the uploaded texture
     * @param onError callback receiving a network, decoding or upload failure
     * @return a handle that releases this request when closed
     * @throws NullPointerException if an argument is null
     * @throws IllegalStateException if the loader is closed
     */
    @Override
    public ImageHandle load(ImageRequest request, Consumer<Texture> onReady,
                            Consumer<Throwable> onError) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(onReady, "onReady");
        Objects.requireNonNull(onError, "onError");
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("Image loader is closed.");
            }
            Entry entry = entries.get(request);
            if (entry == null) {
                entry = new Entry(request);
                entries.put(request, entry);
                start(entry);
            }
            entry.references++;
            LoadCallback callback = new LoadCallback(onReady, onError);
            if (entry.error != null) {
                Throwable error = entry.error;
                post(() -> deliverError(callback, error));
            } else if (entry.texture != null) {
                Texture texture = entry.texture;
                post(() -> deliverReady(callback, texture));
            } else {
                entry.callbacks.add(callback);
            }
            return new Handle(entry, callback);
        }
    }

    /**
     * Stops pending requests and releases every cached texture.
     */
    @Override
    public void close() {
        List<Texture> textures = new ArrayList<>();
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            for (Entry entry : entries.values()) {
                entry.removed = true;
                if (entry.future != null) {
                    entry.future.cancel(true);
                }
                if (entry.texture != null) {
                    textures.add(entry.texture);
                    entry.texture = null;
                }
                entry.callbacks.clear();
            }
            entries.clear();
            cacheBytes = 0;
        }
        for (Texture texture : textures) {
            try {
                context.destroyTexture(texture);
            } catch (RuntimeException ignored) {
                // Texture destruction failures must not keep the loader open.
            }
        }
        executor.shutdownNow();
        if (client != null) {
            client.close();
        }
    }

    private void start(Entry entry) {
        try {
            entry.future = executor.submit(() -> {
                try {
                    byte[] bytes = fetcher.fetch(entry.request.uri());
                    ImageData image = decode(bytes, entry.request);
                    post(() -> finish(entry, image, null));
                } catch (Throwable throwable) {
                    post(() -> finish(entry, null, throwable));
                }
            });
        } catch (RejectedExecutionException exception) {
            post(() -> finish(entry, null, exception));
        }
    }

    private void finish(Entry entry, ImageData image, Throwable failure) {
        List<LoadCallback> ready = new ArrayList<>();
        List<LoadCallback> failed = new ArrayList<>();
        synchronized (lock) {
            entry.future = null;
            if (entry.removed || closed) {
                return;
            }
            if (failure != null) {
                entry.error = failure;
                failed.addAll(entry.callbacks);
                entry.callbacks.clear();
            } else {
                if (entry.references == 0) {
                    entries.remove(entry.request);
                    entry.removed = true;
                    return;
                }
                Texture texture;
                try {
                    texture = upload(image, entry.request.options());
                } catch (RuntimeException exception) {
                    entry.error = exception;
                    failed.addAll(entry.callbacks);
                    entry.callbacks.clear();
                    texture = null;
                }
                if (texture != null) {
                    entry.texture = texture;
                    entry.bytes = (long) texture.width() * texture.height() * 4;
                    cacheBytes += entry.bytes;
                    ready.addAll(entry.callbacks);
                    entry.callbacks.clear();
                    trimCache();
                }
            }
        }
        for (LoadCallback callback : ready) {
            deliverReady(callback, entry.texture);
        }
        for (LoadCallback callback : failed) {
            deliverError(callback, entry.error);
        }
    }

    private Texture upload(ImageData image, TextureOptions options) {
        try {
            return context.createTexture(image, options);
        } catch (UnsupportedOperationException unsupportedSampling) {
            return context.createTexture(image);
        }
    }

    private void release(Entry entry, LoadCallback callback) {
        synchronized (lock) {
            callback.cancelled = true;
            entry.callbacks.remove(callback);
            if (entry.references > 0) {
                entry.references--;
            }
            if (entry.references == 0 && entry.texture == null) {
                entry.removed = true;
                entries.remove(entry.request);
                if (entry.future != null) {
                    entry.future.cancel(true);
                    entry.future = null;
                }
            }
        }
    }

    private void trimCache() {
        if (cacheBytes <= maxCacheBytes) {
            return;
        }
        Iterator<Map.Entry<ImageRequest, Entry>> iterator = entries.entrySet().iterator();
        while (cacheBytes > maxCacheBytes && iterator.hasNext()) {
            Entry candidate = iterator.next().getValue();
            if (candidate.references > 0 || candidate.texture == null) {
                continue;
            }
            Texture texture = candidate.texture;
            iterator.remove();
            candidate.texture = null;
            candidate.removed = true;
            cacheBytes -= candidate.bytes;
            context.destroyTexture(texture);
        }
    }

    private void deliverReady(LoadCallback callback, Texture texture) {
        if (!callback.cancelled) {
            callback.onReady.accept(texture);
        }
    }

    private void deliverError(LoadCallback callback, Throwable error) {
        if (!callback.cancelled) {
            callback.onError.accept(error);
        }
    }

    private void post(Runnable task) {
        try {
            context.post(task);
        } catch (IllegalStateException ignored) {
            // The UI context closed while a background request was finishing.
        }
    }

    private byte[] fetchHttp(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "image/*")
                .GET()
                .build();
        HttpResponse<InputStream> response =
                client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Image request failed with HTTP " + response.statusCode() + ".");
            }
            return readLimited(body);
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                throw new IOException("Image exceeds the " + MAX_BYTES + " byte limit.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static ImageData decode(byte[] bytes, ImageRequest request) throws IOException {
        if (bytes.length == 0) {
            throw new IOException("Image response is empty.");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IOException("Unsupported image stream.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                float scale = Math.min((float) request.targetWidth() / width,
                        (float) request.targetHeight() / height);
                int subsampling = scale < 1 ? Math.max(1, (int) (1 / scale)) : 1;
                ImageReadParam parameters = reader.getDefaultReadParam();
                if (subsampling > 1) {
                    parameters.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }
                BufferedImage image = reader.read(0, parameters);
                return ImageData.fromBufferedImage(scaleDown(image,
                        request.targetWidth(), request.targetHeight()));
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage scaleDown(BufferedImage image, int maxWidth, int maxHeight) {
        if (image.getWidth() <= maxWidth && image.getHeight() <= maxHeight) {
            return image;
        }
        float scale = Math.min((float) maxWidth / image.getWidth(),
                (float) maxHeight / image.getHeight());
        int width = Math.max(1, Math.round(image.getWidth() * scale));
        int height = Math.max(1, Math.round(image.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static ExecutorService createExecutor() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "lixui-image-loader");
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(MAX_WORKERS, factory);
    }

    /**
     * Fetches the raw bytes for an image URI.
     */
    @FunctionalInterface
    interface Fetcher {
        /**
         * Downloads the image bytes.
         *
         * @param uri image URI
         * @return raw image bytes
         * @throws Exception when the image cannot be downloaded
         */
        byte[] fetch(URI uri) throws Exception;
    }

    private static final class Entry {
        private final ImageRequest request;
        private final List<LoadCallback> callbacks = new ArrayList<>();
        private Future<?> future;
        private Texture texture;
        private Throwable error;
        private int references;
        private long bytes;
        private boolean removed;

        private Entry(ImageRequest request) {
            this.request = request;
        }
    }

    private static final class LoadCallback {
        private final Consumer<Texture> onReady;
        private final Consumer<Throwable> onError;
        private boolean cancelled;

        private LoadCallback(Consumer<Texture> onReady, Consumer<Throwable> onError) {
            this.onReady = onReady;
            this.onError = onError;
        }
    }

    private final class Handle implements ImageHandle {
        private final Entry entry;
        private final LoadCallback callback;
        private boolean closed;

        private Handle(Entry entry, LoadCallback callback) {
            this.entry = entry;
            this.callback = callback;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            release(entry, callback);
        }
    }
}
