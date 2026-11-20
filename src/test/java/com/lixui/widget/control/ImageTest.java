package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.graphics.GradientStop;
import com.lixui.graphics.Paint;
import com.lixui.input.InputState;
import com.lixui.render.ImageData;
import com.lixui.render.ImageFit;
import com.lixui.render.ImageStyle;
import com.lixui.render.Texture;
import com.lixui.render.command.ImageCommand;
import com.lixui.render.command.ShapeCommand;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.image.ImageHandle;
import com.lixui.widget.image.ImageLoader;
import com.lixui.widget.image.ImageRequest;
import com.lixui.widget.image.ImageSource;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageTest {
    @Test
    void drawsTextureWithFitTintAndRoundedCorners() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Texture texture = texture(manager, 4, 2);
            Image image = new Image(texture);
            image.setBounds(new Rect(10, 20, 100, 80));
            image.setImageStyle(ImageStyle.COVER);
            image.setCornerRadius(12);
            image.setTint(Color.rgb(255, 0, 0));
            manager.add(image);
            manager.render();

            ImageCommand command = imageCommand(backend, texture);
            assertEquals(new Rect(0, 0, 100, 80), command.bounds());
            assertEquals(12, command.radius(), 0.001f);
            assertEquals(Color.rgb(255, 0, 0), command.color());
        }
    }

    @Test
    void supportsUvRegionsAndContainFit() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Texture texture = texture(manager, 8, 2);
            Image image = new Image(texture);
            image.setBounds(new Rect(0, 0, 100, 100));
            image.setFit(ImageFit.CONTAIN);
            image.setUv(new Rect(0.5f, 0, 0.5f, 1));
            manager.add(image);
            manager.render();

            ImageCommand command = imageCommand(backend, texture);
            assertEquals(new Rect(0.5f, 0, 0.5f, 1), command.uv());
            assertEquals(new Rect(0, 25, 100, 50), command.bounds());
        }
    }

    @Test
    void circularClippingUsesHalfOfTheSmallerSide() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Texture texture = texture(manager, 2, 2);
            Image image = new Image(texture);
            image.setBounds(new Rect(0, 0, 100, 60));
            image.setCircular(true);
            manager.add(image);
            manager.render();

            ImageCommand command = imageCommand(backend, texture);
            assertEquals(new Rect(20, 0, 60, 60), command.bounds());
            assertEquals(30, command.radius(), 0.001f);
        }
    }

    @Test
    void circularBorderFeatherCarriesItsOwnColorInsteadOfBlack() {
        TestBackend backend = new TestBackend();
        backend.setAnalytic(true);
        try (WidgetManager manager = manager(backend)) {
            Image image = new Image(texture(manager, 2, 2));
            image.setBounds(new Rect(0, 0, 100, 60));
            image.setCircular(true);
            image.setBorderColor(Color.rgb(200, 100, 50));
            image.setBorderWidth(4);
            manager.add(image);
            manager.render();

            List<Paint.RadialGradient> gradients = backend.lastDrawList().commands().stream()
                    .filter(ShapeCommand.class::isInstance)
                    .map(ShapeCommand.class::cast)
                    .map(ShapeCommand::paint)
                    .filter(Paint.RadialGradient.class::isInstance)
                    .map(Paint.RadialGradient.class::cast)
                    .toList();

            assertFalse(gradients.isEmpty());
            for (Paint.RadialGradient gradient : gradients) {
                Color edge = gradient.stops().getLast().color();
                for (GradientStop stop : gradient.stops()) {
                    if (stop.color().a() == 0) {
                        assertEquals(edge.toRgb(), stop.color().toRgb());
                    }
                }
            }
        }
    }

    @Test
    void sharesBundledIconsAndReleasesTheLastReference() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Image first = new Image();
            first.setIcon("star.svg");
            first.setBounds(new Rect(0, 0, 32, 32));
            Image second = new Image();
            second.setIcon("star.svg");
            second.setBounds(new Rect(40, 0, 32, 32));
            manager.add(first);
            manager.add(second);
            manager.render();

            assertEquals(1, backend.created().size());
            assertEquals(32, first.displayedTexture().width());
            assertSame(first.displayedTexture(), second.displayedTexture());

            manager.remove(first);
            assertTrue(backend.destroyed().isEmpty());
            manager.remove(second);
            assertEquals(1, backend.destroyed().size());
        }
    }

    @Test
    void autoSizeUsesTheIntrinsicTextureSize() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Texture texture = texture(manager, 20, 10);
            Image image = new Image(texture);
            image.setAutoSize(true);
            manager.add(image);
            manager.render();

            assertEquals(20, image.bounds().width(), 0.001f);
            assertEquals(10, image.bounds().height(), 0.001f);
        }
    }

    @Test
    void switchesBetweenTextureAndIconSources() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Texture texture = texture(manager, 2, 2);
            Image image = new Image();
            image.setIcon("star.svg");
            image.setTexture(texture);

            assertSame(texture, image.texture());
            assertNull(image.icon());

            image.setIcon("star.svg");
            assertNull(image.texture());
            assertEquals("star.svg", image.icon());

            image.clearIcon();
            assertNull(image.icon());
            assertNull(image.displayedTexture());
        }
    }

    @Test
    void usesTheInjectedLoaderForRemoteSourcesAndRefreshesTheRequest() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Texture placeholder = texture(manager, 2, 2);
            Texture remote = texture(manager, 30, 20);
            ManualImageLoader loader = new ManualImageLoader();
            Image image = new Image();
            image.setImageLoader(loader);
            image.setPlaceholder(placeholder);
            image.setSource(ImageSource.remote("https://example.test/image.png"));
            image.setBounds(new Rect(0, 0, 100, 50));
            manager.add(image);
            manager.render();

            ImageRequest first = new ImageRequest(URI.create("https://example.test/image.png"),
                    100, 50);
            assertEquals(first, loader.request);
            assertTrue(hasImageCommand(backend, placeholder));

            loader.onReady.accept(remote);
            manager.render();
            assertTrue(hasImageCommand(backend, remote));

            image.setBounds(new Rect(0, 0, 200, 80));
            manager.render();
            assertEquals(new ImageRequest(URI.create("https://example.test/image.png"), 200, 80),
                    loader.request);
            assertEquals(1, loader.closeCount);
        }
    }

    @Test
    void rejectsInvalidArgumentsAndSettings() {
        Image image = new Image();
        assertThrows(NullPointerException.class, () -> image.setIcon(null));
        assertThrows(NullPointerException.class, () -> image.setImageStyle(null));
        assertThrows(NullPointerException.class, () -> image.setFit(null));
        assertThrows(NullPointerException.class, () -> image.setTint(null));
        assertThrows(NullPointerException.class, () -> image.setBorderColor(null));
        assertThrows(IllegalArgumentException.class, () -> image.setCornerRadius(-1));
        assertThrows(IllegalArgumentException.class, () -> image.setBorderWidth(-1));
    }

    private static Texture texture(WidgetManager manager, int width, int height) {
        return manager.context().createTexture(new ImageData(width, height,
                new byte[width * height * 4]));
    }

    private static ImageCommand imageCommand(TestBackend backend, Texture texture) {
        return backend.lastDrawList().commands().stream()
                .filter(ImageCommand.class::isInstance)
                .map(ImageCommand.class::cast)
                .filter(command -> command.texture() == texture)
                .findFirst().orElseThrow();
    }

    private static boolean hasImageCommand(TestBackend backend, Texture texture) {
        return backend.lastDrawList().commands().stream()
                .filter(ImageCommand.class::isInstance)
                .map(ImageCommand.class::cast)
                .anyMatch(command -> command.texture() == texture);
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(TestViewports.LOGICAL);
        return manager;
    }

    private static final class ManualImageLoader implements ImageLoader {
        private ImageRequest request;
        private Consumer<Texture> onReady;
        private int closeCount;

        @Override
        public ImageHandle load(ImageRequest request, Consumer<Texture> onReady,
                                Consumer<Throwable> onError) {
            this.request = request;
            this.onReady = onReady;
            return () -> closeCount++;
        }
    }
}
