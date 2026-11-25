package com.lixui.widget.container;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerExited;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.platform.Cursor;
import com.lixui.render.Texture;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.image.ImageHandle;
import com.lixui.widget.image.ImageLoader;
import com.lixui.widget.image.ImageRequest;
import com.lixui.widget.image.ImageSource;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageGalleryTest {
    @Test
    void rejectsInvalidConfiguration() {
        ImageGallery gallery = new ImageGallery();
        ImageSource icon = ImageSource.icon("star.svg");

        assertThrows(NullPointerException.class, () -> gallery.addItem(null));
        assertThrows(NullPointerException.class, () -> new ImageGallery.Item(null, "Caption"));
        assertThrows(NullPointerException.class, () -> new ImageGallery.Item(icon, null));
        assertThrows(NullPointerException.class, () -> gallery.addItem(null, "Caption"));
        assertThrows(NullPointerException.class, () -> gallery.addItem(icon, null));
        assertThrows(IllegalArgumentException.class, () -> gallery.setColumns(0));
        assertThrows(IllegalArgumentException.class, () -> gallery.setThumbnailSize(0));
        assertThrows(IllegalArgumentException.class, () -> gallery.setThumbnailSize(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> gallery.setGap(-1));
        assertThrows(IllegalArgumentException.class, () -> gallery.setGap(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> gallery.setPadding(-1));
        assertThrows(NullPointerException.class, () -> gallery.setSelectionMode(null));
        assertThrows(NullPointerException.class, () -> gallery.setSelectedIndices(null));
        assertThrows(IllegalArgumentException.class, () -> gallery.select(0));
        assertThrows(IllegalArgumentException.class, () -> gallery.scrollToItem(0));
        assertThrows(IllegalArgumentException.class, () -> gallery.setScrollY(Float.NaN));
    }

    @Test
    void clickSelectsSingleItemAndEnterActivatesIt() {
        ImageGallery gallery = gallery(4);
        List<ImageGallery.Item> activated = new ArrayList<>();
        gallery.setActivationListener(activated::addAll);
        WidgetManager manager = manager();
        manager.add(gallery);
        manager.render();

        click(manager, 20, 20);
        assertEquals(List.of(0), gallery.selectedIndices());
        assertSame(gallery.items().get(0), gallery.selectedItems().get(0));

        click(manager, 160, 20);
        assertEquals(List.of(1), gallery.selectedIndices());

        press(manager, Key.ENTER);
        assertEquals(List.of(gallery.items().get(1)), activated);
    }

    @Test
    void ctrlTogglesAndShiftExtendsMultipleSelection() {
        ImageGallery gallery = gallery(4);
        gallery.setSelectionMode(ImageGallery.SelectionMode.MULTIPLE);
        WidgetManager manager = manager();
        manager.add(gallery);
        manager.render();

        click(manager, 20, 20);
        assertEquals(List.of(0), gallery.selectedIndices());

        click(manager, 20, 130, new Modifiers(false, true, false, false));
        assertEquals(List.of(0, 2), gallery.selectedIndices());

        click(manager, 160, 130, new Modifiers(true, false, false, false));
        assertEquals(List.of(2, 3), gallery.selectedIndices());

        gallery.setSelectionMode(ImageGallery.SelectionMode.SINGLE);
        assertEquals(List.of(3), gallery.selectedIndices());
    }

    @Test
    void hoversCellsWithThePointerCursor() {
        ImageGallery gallery = gallery(4);
        WidgetManager manager = manager();
        manager.add(gallery);
        manager.render();

        manager.handle(new PointerMoved(20, 20));
        assertEquals(Cursor.POINTER, manager.context().cursor());

        manager.handle(new PointerExited(0, 0));
        assertEquals(Cursor.DEFAULT, manager.context().cursor());
    }

    @Test
    void clampsScrollAndRevealsItems() {
        ImageGallery gallery = gallery(20);
        WidgetManager manager = manager();
        manager.add(gallery);
        manager.render();

        float maximum = gallery.maxScrollY();
        assertTrue(maximum > 0);

        gallery.setScrollY(Float.MAX_VALUE);
        assertEquals(maximum, gallery.scrollY(), 0.001f);

        gallery.setScrollY(-10);
        assertEquals(0, gallery.scrollY(), 0.001f);

        gallery.scrollToItem(19);
        assertTrue(gallery.scrollY() > 0);
        assertTrue(gallery.scrollY() <= maximum);

        gallery.scrollToItem(0);
        assertEquals(0, gallery.scrollY(), 0.001f);
    }

    @Test
    void iconThumbnailsReloadAtTheCurrentDisplayScale() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        ImageGallery gallery = gallery(1);
        manager.add(gallery);
        manager.render();

        assertNotNull(backend.lastCreatedTexture());
        assertTrue(backend.created().stream().anyMatch(texture -> texture.width() == 248));

        manager.resize(TestViewports.LOGICAL);
        manager.render();

        assertTrue(backend.created().stream().anyMatch(texture -> texture.width() == 124));
        assertTrue(backend.destroyed().size() >= 2);
    }

    @Test
    void clearingItemsReleasesIconsAndSelection() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        ImageGallery gallery = gallery(1);
        List<Integer> selectionEvents = new ArrayList<>();
        gallery.setSelectionListener(selectionEvents::addAll);
        manager.add(gallery);
        manager.render();
        click(manager, 20, 20);
        assertEquals(List.of(0), gallery.selectedIndices());
        int destroyedBeforeClear = backend.destroyed().size();

        gallery.clearItems();

        assertTrue(gallery.items().isEmpty());
        assertTrue(gallery.selectedIndices().isEmpty());
        assertEquals(destroyedBeforeClear + 1, backend.destroyed().size());
        assertEquals(List.of(0), selectionEvents);
    }

    @Test
    void clearingRemoteItemsClosesTheirRequests() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend);
        ImageGallery gallery = new ImageGallery();
        gallery.setBounds(new Rect(0, 0, 300, 200));
        ManualImageLoader loader = new ManualImageLoader();
        gallery.setImageLoader(loader);
        gallery.addItem(ImageSource.remote("https://example.test/thumb.png"), "Remote");
        manager.add(gallery);
        manager.render();

        assertNotNull(loader.request);
        int closesBeforeClear = loader.closeCount;

        gallery.clearItems();

        assertEquals(closesBeforeClear + 1, loader.closeCount);
    }

    @Test
    void enterDoesNotActivateRepeatedKeyPresses() {
        ImageGallery gallery = gallery(2);
        int[] activations = {0};
        gallery.setActivationListener(items -> activations[0]++);
        WidgetManager manager = manager();
        manager.add(gallery);
        manager.render();
        click(manager, 20, 20);

        press(manager, Key.ENTER, false);
        assertEquals(1, activations[0]);

        press(manager, Key.ENTER, true);
        assertEquals(1, activations[0]);

        press(manager, Key.ENTER, false);
        assertEquals(2, activations[0]);
    }

    @Test
    void selectionListenerMayClearItemsWithoutStaleScrolling() {
        ImageGallery gallery = gallery(3);
        gallery.setSelectionListener(indices -> gallery.clearItems());

        gallery.select(0);

        assertTrue(gallery.items().isEmpty());
        assertTrue(gallery.selectedIndices().isEmpty());

        ImageGallery clicked = gallery(3);
        WidgetManager manager = manager();
        clicked.setSelectionListener(indices -> clicked.clearItems());
        manager.add(clicked);
        manager.render();
        click(manager, 20, 20);

        assertTrue(clicked.items().isEmpty());
        assertTrue(clicked.selectedIndices().isEmpty());
    }

    private static ImageGallery gallery(int itemCount) {
        ImageGallery gallery = new ImageGallery();
        gallery.setBounds(new Rect(0, 0, 300, 200));
        gallery.setColumns(2);
        gallery.setThumbnailSize(80);
        for (int index = 0; index < itemCount; index++) {
            gallery.addItem(ImageSource.icon("star.svg"), "Item " + index);
        }
        return gallery;
    }

    private static WidgetManager manager() {
        return manager(new TestBackend());
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(TestViewports.HIDPI);
        return manager;
    }

    private static void click(WidgetManager manager, float x, float y) {
        click(manager, x, y, Modifiers.NONE);
    }

    private static void click(WidgetManager manager, float x, float y, Modifiers modifiers) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, modifiers));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, modifiers));
    }

    private static void press(WidgetManager manager, Key key) {
        press(manager, key, false);
    }

    private static void press(WidgetManager manager, Key key, boolean repeat) {
        manager.handle(new KeyPressed(key, Modifiers.NONE, repeat));
    }

    private static final class ManualImageLoader implements ImageLoader {
        private ImageRequest request;
        private int closeCount;

        /**
         * Records the request and returns a counting handle.
         *
         * @param request image request
         * @param onReady ready callback
         * @param onError error callback
         * @return a handle that counts close calls
         */
        @Override
        public ImageHandle load(ImageRequest request, Consumer<Texture> onReady,
                                Consumer<Throwable> onError) {
            this.request = request;
            return () -> closeCount++;
        }
    }
}
