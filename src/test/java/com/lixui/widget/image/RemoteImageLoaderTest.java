package com.lixui.widget.image;

import com.lixui.input.InputState;
import com.lixui.render.Texture;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteImageLoaderTest {
    private static final URI IMAGE_URI = URI.create("https://example.test/image.png");

    @Test
    void decodesAtTheRequestedSizeOnTheRenderThreadQueue() throws IOException {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RemoteImageLoader loader = loader(manager, png(100, 50));
            try {
                AtomicReference<Texture> ready = new AtomicReference<>();
                AtomicReference<Throwable> error = new AtomicReference<>();
                ImageRequest request = new ImageRequest(IMAGE_URI, 50, 50);
                ImageHandle handle = loader.load(request, ready::set, error::set);

                assertNull(ready.get());
                manager.update(0.016f);

                assertNull(error.get());
                assertNotNull(ready.get());
                assertEquals(50, ready.get().width());
                assertEquals(25, ready.get().height());
                handle.close();
            } finally {
                loader.close();
            }
        }
    }

    @Test
    void downloadsOffTheRenderThread() throws Exception {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            CountDownLatch fetchStarted = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            RemoteImageLoader loader = new RemoteImageLoader(manager.context(),
                    uri -> {
                        fetchStarted.countDown();
                        release.await();
                        return png(20, 20);
                    }, executor);
            try {
                AtomicReference<Texture> ready = new AtomicReference<>();
                AtomicReference<Throwable> error = new AtomicReference<>();
                ImageHandle handle = loader.load(new ImageRequest(IMAGE_URI, 20, 20),
                        ready::set, error::set);

                assertTrue(fetchStarted.await(2, TimeUnit.SECONDS));
                manager.update(0.016f);
                assertNull(ready.get());

                release.countDown();
                long deadline = System.nanoTime() + 2_000_000_000L;
                while (ready.get() == null && error.get() == null
                        && System.nanoTime() < deadline) {
                    manager.update(0.016f);
                    Thread.sleep(5);
                }
                assertNull(error.get(), () -> String.valueOf(error.get()));
                assertNotNull(ready.get());
                handle.close();
            } finally {
                release.countDown();
                loader.close();
            }
        }
    }

    @Test
    void deduplicatesConcurrentRequestsForTheSameKey() throws IOException {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            AtomicInteger fetches = new AtomicInteger();
            RemoteImageLoader loader = new RemoteImageLoader(manager.context(),
                    uri -> {
                        fetches.incrementAndGet();
                        return png(40, 20);
                    }, new DirectExecutorService());
            try {
                AtomicReference<Texture> firstTexture = new AtomicReference<>();
                AtomicReference<Texture> secondTexture = new AtomicReference<>();
                ImageRequest request = new ImageRequest(IMAGE_URI, 40, 20);
                ImageHandle first = loader.load(request, firstTexture::set, error -> {
                });
                ImageHandle second = loader.load(request, secondTexture::set, error -> {
                });

                manager.update(0.016f);
                assertEquals(1, fetches.get());
                assertSame(firstTexture.get(), secondTexture.get());
                first.close();
                second.close();
            } finally {
                loader.close();
            }
        }
    }

    @Test
    void closingAHandleSuppressesItsCallback() throws IOException {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RemoteImageLoader loader = loader(manager, png(20, 20));
            try {
                AtomicReference<Texture> ready = new AtomicReference<>();
                ImageRequest request = new ImageRequest(IMAGE_URI, 20, 20);
                ImageHandle handle = loader.load(request, ready::set, error -> {
                });
                handle.close();
                manager.update(0.016f);
                assertNull(ready.get());
            } finally {
                loader.close();
            }
        }
    }

    @Test
    void reportsFetchFailures() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RemoteImageLoader loader = new RemoteImageLoader(manager.context(),
                    uri -> {
                        throw new IOException("fetch failed");
                    }, new DirectExecutorService());
            try {
                AtomicReference<Texture> ready = new AtomicReference<>();
                AtomicReference<Throwable> error = new AtomicReference<>();
                ImageRequest request = new ImageRequest(IMAGE_URI, 20, 20);
                ImageHandle handle = loader.load(request, ready::set, error::set);
                manager.update(0.016f);

                assertNull(ready.get());
                assertNotNull(error.get());
                assertTrue(error.get() instanceof IOException);
                handle.close();
            } finally {
                loader.close();
            }
        }
    }

    private static RemoteImageLoader loader(WidgetManager manager, byte[] bytes) {
        return new RemoteImageLoader(manager.context(), uri -> bytes, new DirectExecutorService());
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(TestViewports.LOGICAL);
        return manager;
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new RejectedExecutionException();
            }
            command.run();
        }
    }
}
