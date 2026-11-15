package com.lixui.render;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.graphics.GradientStop;
import com.lixui.graphics.Paint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderManagerTest {
    private static final Viewport VIEWPORT = new Viewport(1600, 1200, 800, 600);
    private static final Rect LOGICAL = new Rect(0, 0, 800, 600);
    private static final Color RED = Color.rgb(255, 0, 0);

    @Test
    void resizeDelegatesAndExposesTheLogicalViewport() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);

        assertSame(Rect.EMPTY, manager.viewport());

        manager.resize(VIEWPORT);

        assertSame(VIEWPORT, backend.viewport);
        assertEquals(LOGICAL, manager.viewport());
    }

    @Test
    void beginDrawEndSubmitsTheCompletedList() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);

        manager.begin();
        manager.rect(new Rect(10, 20, 100, 50), RED);
        manager.roundedRect(new Rect(20, 30, 100, 50), 8, RED);
        manager.end();

        assertEquals(1, backend.drawCalls);
        assertEquals(2, backend.lastDrawList.commands().size());
        assertEquals(LOGICAL, backend.lastDrawList.viewport());
        assertEquals(LOGICAL, backend.lastDrawList.commands().get(0).clip());
    }

    @Test
    void withClipScopesCommandsAndRestoresTheClip() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        Rect clip = new Rect(0, 0, 100, 100);

        manager.begin();
        manager.withClip(clip, () -> manager.rect(new Rect(10, 10, 50, 50), RED));
        manager.rect(new Rect(200, 200, 50, 50), RED);
        manager.end();

        assertEquals(clip, backend.lastDrawList.commands().get(0).clip());
        assertEquals(LOGICAL, backend.lastDrawList.commands().get(1).clip());
    }

    @Test
    void beginResetsCommandsFromThePreviousFrame() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);

        manager.begin();
        manager.rect(new Rect(10, 10, 10, 10), RED);
        manager.end();

        manager.begin();
        manager.end();

        assertTrue(backend.lastDrawList.commands().isEmpty());
    }

    @Test
    void rejectsFrameMisuse() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);

        assertThrows(IllegalStateException.class, manager::begin);

        manager.resize(VIEWPORT);
        assertThrows(IllegalStateException.class, () -> manager.rect(LOGICAL, RED));

        manager.begin();
        assertThrows(IllegalStateException.class, manager::begin);
        manager.end();
        assertThrows(IllegalStateException.class, manager::end);
    }

    @Test
    void createsAndDestroysTexturesThroughTheBackend() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        ImageData image = new ImageData(2, 2, new byte[16]);

        Texture texture = manager.createTexture(image);
        manager.destroyTexture(texture);

        assertSame(image, backend.createdImage);
        assertSame(texture, backend.destroyedTexture);
    }

    @Test
    void fillRectUsesSolidSdfAndGradientMesh() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        Rect bounds = new Rect(10, 10, 100, 50);
        Paint gradient = Paint.linear(new Point(10, 10), new Point(110, 10),
                new GradientStop(0, RED), new GradientStop(0.5f, Color.rgb(0, 255, 0)),
                new GradientStop(1, Color.rgb(0, 0, 255)));

        manager.begin();
        manager.fillRect(bounds, Paint.solid(RED));
        manager.fillRect(bounds, gradient);
        manager.end();

        List<DrawCommand> commands = backend.lastDrawList.commands();
        assertEquals(2, commands.size());
        assertTrue(commands.get(0) instanceof RoundedRectCommand);
        assertTrue(commands.get(1) instanceof MeshCommand);
        assertEquals(0, ((MeshCommand) commands.get(1)).vertices().size() % 3);
    }

    @Test
    void shapeApiEmitsMeshesForCurvesStrokesAndPolygons() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        List<Point> square = List.of(
                new Point(0, 0),
                new Point(100, 0),
                new Point(100, 100),
                new Point(0, 100));

        manager.begin();
        manager.fillRoundedRect(new Rect(0, 0, 100, 100), 12, Paint.solid(RED));
        manager.fillCircle(new Point(100, 100), 40, Paint.solid(RED));
        manager.fillEllipse(new Point(200, 100), 60, 30, Paint.solid(RED));
        manager.fillArc(new Point(300, 100), 50, 40, 0, (float) (Math.PI / 2), Paint.solid(RED));
        manager.fillPolygon(square, Paint.solid(RED));
        manager.strokeRect(new Rect(0, 0, 100, 100), 4, Paint.solid(RED));
        manager.strokeRoundedRect(new Rect(0, 0, 100, 100), 12, 4, Paint.solid(RED));
        manager.strokeLine(new Point(0, 0), new Point(100, 100), 4, Paint.solid(RED));
        manager.strokeCircle(new Point(100, 100), 40, 4, Paint.solid(RED));
        manager.strokeEllipse(new Point(200, 100), 60, 30, 4, Paint.solid(RED));
        manager.strokeArc(new Point(300, 100), 50, 40, 0, (float) (Math.PI / 2), 4, Paint.solid(RED));
        manager.strokePolyline(square, 4, Paint.solid(RED));
        manager.strokePolygon(square, 4, Paint.solid(RED));
        manager.end();

        List<DrawCommand> commands = backend.lastDrawList.commands();
        assertEquals(13, commands.size());
        assertTrue(commands.get(0) instanceof RoundedRectCommand);
        for (int index = 1; index < commands.size(); index++) {
            assertTrue(commands.get(index) instanceof MeshCommand);
            assertTrue(((MeshCommand) commands.get(index)).vertices().size() >= 3);
        }
    }

    @Test
    void rejectsInvalidShapeArguments() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        manager.begin();

        assertThrows(IllegalArgumentException.class,
                () -> manager.fillRoundedRect(LOGICAL, -1, Paint.solid(RED)));
        assertThrows(IllegalArgumentException.class,
                () -> manager.fillEllipse(new Point(0, 0), 0, 10, Paint.solid(RED)));
        assertThrows(IllegalArgumentException.class,
                () -> manager.fillArc(new Point(0, 0), 10, 10, 0, 0, Paint.solid(RED)));
        assertThrows(IllegalArgumentException.class,
                () -> manager.strokeLine(new Point(0, 0), new Point(1, 1), 0, Paint.solid(RED)));
        assertThrows(NullPointerException.class, () -> manager.fillPolygon(null, Paint.solid(RED)));
        assertThrows(IllegalArgumentException.class,
                () -> manager.fillPolygon(List.of(new Point(0, 0), new Point(1, 1)), Paint.solid(RED)));
        assertThrows(IllegalArgumentException.class, () -> manager.fillPolygon(
                List.of(new Point(0, 0), new Point(10, 0), new Point(20, 0)), Paint.solid(RED)));
    }

    @Test
    void transformsAffectShapeAndMeshGeometry() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        manager.setAntialiasing(Antialiasing.OFF);
        Rect bounds = new Rect(0, 0, 100, 50);

        manager.begin();
        manager.withTransform(Transform.translation(10, 20),
                () -> manager.fillRect(bounds, Paint.solid(RED)));
        manager.end();

        DrawCommand command = backend.lastDrawList.commands().get(0);
        assertTrue(command instanceof MeshCommand);
        assertEquals(new Rect(10, 20, 100, 50), command.bounds());
    }

    @Test
    void transformsApplyToImageCommandsWithTheAtlasRegion() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        Texture texture = new TestTexture(256, 256);
        Rect bounds = new Rect(0, 0, 64, 32);
        Rect atlasUv = new Rect(0.25f, 0.5f, 0.125f, 0.25f);
        Transform transform = Transform.translation(10, 20).rotate(0.5f);

        manager.begin();
        manager.withTransform(transform, () -> manager.image(texture, bounds, atlasUv));
        manager.image(texture, bounds);
        manager.end();

        List<DrawCommand> commands = backend.lastDrawList.commands();
        ImageCommand transformed = (ImageCommand) commands.get(0);
        assertSame(texture, transformed.texture());
        assertEquals(bounds, transformed.bounds());
        assertEquals(atlasUv, transformed.uv());
        assertEquals(transform, transformed.transform());

        ImageCommand plain = (ImageCommand) commands.get(1);
        assertEquals(Transform.IDENTITY, plain.transform());
    }

    @Test
    void groupTransformsComposeAndResetEachFrame() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);

        manager.begin();
        manager.pushTransform(Transform.translation(10, 0));
        manager.pushTransform(Transform.scaling(2, 2));
        assertEquals(Transform.translation(10, 0).multiply(Transform.scaling(2, 2)),
                manager.currentTransform());
        manager.popTransform();
        assertEquals(Transform.translation(10, 0), manager.currentTransform());
        manager.popTransform();
        assertTrue(manager.currentTransform().isIdentity());
        assertThrows(IllegalStateException.class, manager::popTransform);
        manager.end();

        manager.begin();
        assertTrue(manager.currentTransform().isIdentity());
        manager.end();
        assertThrows(IllegalStateException.class,
                () -> manager.pushTransform(Transform.translation(1, 1)));
    }

    @Test
    void withTransformRestoresThePreviousTransformWhenTheActionFails() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        manager.begin();

        assertThrows(IllegalStateException.class, () -> manager.withTransform(
                Transform.rotation(1), () -> {
                    throw new IllegalStateException("failure");
                }));

        assertTrue(manager.currentTransform().isIdentity());
        manager.end();
    }

    @Test
    void transformsApplyToTextureMasks() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        Texture texture = new TestTexture(64, 64);
        Rect bounds = new Rect(10, 20, 100, 50);
        Rect atlasUv = new Rect(0.25f, 0.5f, 0.5f, 0.25f);
        Transform transform = Transform.translation(5, 7).rotate(0.75f);

        manager.begin();
        manager.withTransform(transform, () -> {
            manager.pushMaskTexture(texture, bounds, atlasUv);
            manager.popMask();
        });
        manager.end();

        List<DrawCommand> commands = backend.lastDrawList.commands();
        MaskTextureBeginCommand begin = (MaskTextureBeginCommand) commands.get(0);
        assertSame(texture, begin.texture());
        assertEquals(bounds, begin.bounds());
        assertEquals(atlasUv, begin.uv());
        assertEquals(transform, begin.transform());
        assertEquals(transform, ((MaskTextureEndCommand) commands.get(1)).transform());
    }

    @Test
    void maskApiEmitsBeginAndEndCommands() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        List<Point> triangle = List.of(
                new Point(0, 0),
                new Point(100, 0),
                new Point(0, 100));

        manager.begin();
        manager.pushMaskRect(new Rect(0, 0, 100, 100));
        manager.fillRect(new Rect(10, 10, 80, 80), Paint.solid(RED));
        manager.popMask();
        manager.pushMaskEllipse(new Point(50, 50), 40, 30);
        manager.popMask();
        manager.pushMaskPolygon(triangle);
        manager.popMask();
        manager.pushMaskTexture(new TestTexture(16, 16), new Rect(0, 0, 100, 100));
        manager.popMask();
        manager.end();

        List<DrawCommand> commands = backend.lastDrawList.commands();
        assertEquals(9, commands.size());
        assertTrue(commands.get(0) instanceof MaskBeginCommand);
        assertTrue(commands.get(1) instanceof RoundedRectCommand);
        assertTrue(commands.get(2) instanceof MaskEndCommand);
        assertTrue(commands.get(3) instanceof MaskBeginCommand);
        assertTrue(commands.get(4) instanceof MaskEndCommand);
        assertTrue(commands.get(5) instanceof MaskBeginCommand);
        assertTrue(commands.get(6) instanceof MaskEndCommand);
        assertTrue(commands.get(7) instanceof MaskTextureBeginCommand);
        assertTrue(commands.get(8) instanceof MaskTextureEndCommand);
    }

    @Test
    void rejectsMaskMisuse() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        manager.begin();

        assertThrows(IllegalStateException.class, manager::popMask);
        assertThrows(IllegalArgumentException.class, () -> manager.pushMaskPolygon(
                List.of(new Point(0, 0), new Point(1, 1))));
        assertThrows(NullPointerException.class, () -> manager.pushMaskTexture(null,
                new Rect(0, 0, 10, 10)));
    }

    @Test
    void antialiasingChangesTessellationDensity() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        manager.setAntialiasing(Antialiasing.OFF);
        manager.begin();
        manager.fillCircle(new Point(100, 100), 50, Paint.solid(RED));
        manager.end();
        int coarse = ((MeshCommand) backend.lastDrawList.commands().get(0)).vertices().size();

        manager.setAntialiasing(Antialiasing.HIGH);
        manager.begin();
        manager.fillCircle(new Point(100, 100), 50, Paint.solid(RED));
        manager.end();
        int dense = ((MeshCommand) backend.lastDrawList.commands().get(0)).vertices().size();

        assertTrue(dense > coarse);
    }

    @Test
    void nestedMasksEmitBalancedCommands() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        List<Point> triangle = List.of(
                new Point(0, 0),
                new Point(100, 0),
                new Point(0, 100));

        manager.begin();
        manager.pushMaskEllipse(new Point(50, 50), 40, 30);
        manager.pushMaskRoundedRect(new Rect(10, 10, 80, 80), 12);
        manager.pushMaskPolygon(triangle);
        manager.fillRect(new Rect(0, 0, 100, 100), Paint.solid(RED));
        manager.popMask();
        manager.popMask();
        manager.popMask();
        manager.end();

        List<DrawCommand> commands = backend.lastDrawList.commands();
        assertEquals(7, commands.size());
        assertTrue(commands.get(0) instanceof MaskBeginCommand);
        assertTrue(commands.get(1) instanceof MaskBeginCommand);
        assertTrue(commands.get(2) instanceof MaskBeginCommand);
        assertTrue(commands.get(3) instanceof RoundedRectCommand);
        assertTrue(commands.get(4) instanceof MaskEndCommand);
        assertTrue(commands.get(5) instanceof MaskEndCommand);
        assertTrue(commands.get(6) instanceof MaskEndCommand);
    }

    @Test
    void rejectsNullArguments() {
        RecordingBackend backend = new RecordingBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        manager.begin();

        assertThrows(NullPointerException.class, () -> new RenderManager(null));
        assertThrows(NullPointerException.class, () -> manager.resize(null));
        assertThrows(NullPointerException.class, () -> manager.rect(null, RED));
        assertThrows(NullPointerException.class, () -> manager.rect(LOGICAL, null));
        assertThrows(NullPointerException.class, () -> manager.roundedRect(LOGICAL, 0, null));
        assertThrows(NullPointerException.class, () -> manager.withClip(null, () -> {
        }));
        assertThrows(NullPointerException.class, () -> manager.withClip(LOGICAL, null));
        assertThrows(NullPointerException.class, () -> manager.createTexture(null));
        assertThrows(NullPointerException.class, () -> manager.destroyTexture(null));
    }

    private record TestTexture(int width, int height) implements Texture {
    }

    private static final class RecordingBackend implements RenderBackend {
        private Viewport viewport;
        private DrawList lastDrawList;
        private int drawCalls;
        private ImageData createdImage;
        private Texture destroyedTexture;

        @Override
        public void resize(Viewport viewport) {
            this.viewport = viewport;
        }

        @Override
        public void draw(DrawList drawList) {
            lastDrawList = drawList;
            drawCalls++;
        }

        @Override
        public Texture createTexture(ImageData image) {
            createdImage = image;
            return new TestTexture(image.width(), image.height());
        }

        @Override
        public void destroyTexture(Texture texture) {
            destroyedTexture = texture;
        }

        @Override
        public void close() {
        }
    }
}
