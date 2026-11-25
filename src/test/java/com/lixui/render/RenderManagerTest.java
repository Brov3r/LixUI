package com.lixui.render;

import com.lixui.widget.testsupport.TestViewports;
import com.lixui.widget.testsupport.TestTexture;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.graphics.GradientStop;
import com.lixui.graphics.Paint;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.ImageCommand;
import com.lixui.render.command.MaskBeginCommand;
import com.lixui.render.command.MaskEndCommand;
import com.lixui.render.command.MaskTextureBeginCommand;
import com.lixui.render.command.MaskTextureEndCommand;
import com.lixui.render.command.MeshCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.command.ShapeCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.FontStyle;
import com.lixui.text.Glyph;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.SystemFonts;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderManagerTest {
    private static final Viewport VIEWPORT = TestViewports.HIDPI;
    private static final Rect LOGICAL = new Rect(0, 0, 800, 600);
    private static final Color RED = Color.rgb(255, 0, 0);

    @Test
    void withOpacityMultipliesColorsAndRestoresTheScope() {
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);

        manager.begin();
        manager.withOpacity(0.5f, () -> {
            manager.rect(new Rect(0, 0, 10, 10), new Color(1, 0, 0, 1));
            manager.withOpacity(0.5f, () -> manager.rect(new Rect(10, 0, 10, 10),
                    new Color(0, 1, 0, 1)));
            manager.mesh(List.of(new MeshVertex(0, 0, Color.WHITE),
                    new MeshVertex(1, 0, Color.WHITE), new MeshVertex(0, 1, Color.WHITE)));
            manager.pushMaskRect(new Rect(0, 0, 4, 4));
            manager.popMask();
            manager.fillRect(new Rect(0, 0, 10, 10), Paint.linear(new Point(0, 0),
                    new Point(10, 0), new GradientStop(0, Color.WHITE),
                    new GradientStop(1, Color.WHITE)));
        });
        manager.rect(new Rect(20, 0, 10, 10), new Color(0, 0, 1, 1));
        manager.end();

        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertEquals(0.5f, assertInstanceOf(RoundedRectCommand.class, commands.get(0)).color().a(),
                0.0001f);
        assertEquals(0.25f, assertInstanceOf(RoundedRectCommand.class, commands.get(1)).color().a(),
                0.0001f);
        MeshCommand mesh = assertInstanceOf(MeshCommand.class, commands.get(2));
        assertEquals(0.5f, mesh.vertices().getFirst().color().a(), 0.0001f);
        MaskBeginCommand mask = assertInstanceOf(MaskBeginCommand.class, commands.get(3));
        assertEquals(1, mask.vertices().getFirst().color().a(), 0.0001f);
        MeshCommand gradient = assertInstanceOf(MeshCommand.class, commands.get(5));
        assertEquals(0.5f, gradient.vertices().getFirst().color().a(), 0.0001f);
        assertEquals(1, assertInstanceOf(RoundedRectCommand.class, commands.get(6)).color().a(),
                0.0001f);

        assertThrows(IllegalArgumentException.class, () -> manager.withOpacity(-0.1f, () -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> manager.withOpacity(Float.NaN, () -> { }));
        assertThrows(NullPointerException.class, () -> manager.withOpacity(0.5f, null));
        assertThrows(IllegalStateException.class, () -> manager.withOpacity(0.5f, () -> { }));
    }

    @Test
    void resizeDelegatesAndExposesTheLogicalViewport() {
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);

        assertSame(Rect.EMPTY, manager.viewport());

        manager.resize(VIEWPORT);

        assertSame(VIEWPORT, backend.viewport());
        assertEquals(LOGICAL, manager.viewport());
    }

    @Test
    void beginDrawEndSubmitsTheCompletedList() {
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);

        manager.begin();
        manager.rect(new Rect(10, 20, 100, 50), RED);
        manager.roundedRect(new Rect(20, 30, 100, 50), 8, RED);
        manager.end();

        assertEquals(1, backend.drawCalls());
        assertEquals(2, backend.lastDrawList().commands().size());
        assertEquals(LOGICAL, backend.lastDrawList().viewport());
        assertEquals(LOGICAL, backend.lastDrawList().commands().get(0).clip());
    }

    @Test
    void withClipScopesCommandsAndRestoresTheClip() {
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        Rect clip = new Rect(0, 0, 100, 100);

        manager.begin();
        manager.withClip(clip, () -> manager.rect(new Rect(10, 10, 50, 50), RED));
        manager.rect(new Rect(200, 200, 50, 50), RED);
        manager.end();

        assertEquals(clip, backend.lastDrawList().commands().get(0).clip());
        assertEquals(LOGICAL, backend.lastDrawList().commands().get(1).clip());
    }

    @Test
    void beginResetsCommandsFromThePreviousFrame() {
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);

        manager.begin();
        manager.rect(new Rect(10, 10, 10, 10), RED);
        manager.end();

        manager.begin();
        manager.end();

        assertTrue(backend.lastDrawList().commands().isEmpty());
    }

    @Test
    void rejectsFrameMisuse() {
        TestBackend backend = new TestBackend();
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
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        ImageData image = new ImageData(2, 2, new byte[16]);

        Texture texture = manager.createTexture(image);
        manager.destroyTexture(texture);

        assertSame(image, backend.lastImage());
        assertSame(texture, backend.lastDestroyedTexture());
    }

    @Test
    void fillRectUsesSolidSdfAndGradientMesh() {
        TestBackend backend = new TestBackend();
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

        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertEquals(2, commands.size());
        assertTrue(commands.get(0) instanceof RoundedRectCommand);
        assertTrue(commands.get(1) instanceof MeshCommand);
        assertEquals(0, ((MeshCommand) commands.get(1)).vertices().size() % 3);
    }

    @Test
    void shapeApiEmitsMeshesForCurvesStrokesAndPolygons() {
        TestBackend backend = new TestBackend();
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

        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertEquals(13, commands.size());
        assertTrue(commands.get(0) instanceof RoundedRectCommand);
        for (int index = 1; index < commands.size(); index++) {
            assertTrue(commands.get(index) instanceof MeshCommand);
            assertTrue(((MeshCommand) commands.get(index)).vertices().size() >= 3);
        }
    }

    @Test
    void fillsRingSectorsWithoutEarClippingEvenAtSubPixelSizes() {
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        Point center = new Point(640.37f, 360.61f);
        float sweep = (float) (Math.PI * 2) / 8 - 0.02f;

        manager.begin();
        manager.fillRingSector(center, 0.04096f, 0.128f,
                (float) (-Math.PI / 2), sweep, Paint.solid(RED));
        assertThrows(IllegalArgumentException.class,
                () -> manager.fillRingSector(center, 10, 5, 0, 1, Paint.solid(RED)));
        assertThrows(IllegalArgumentException.class,
                () -> manager.fillRingSector(center, 1, 2, 0, 0, Paint.solid(RED)));
        manager.end();

        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertEquals(1, commands.size());
        MeshCommand mesh = assertInstanceOf(MeshCommand.class, commands.get(0));
        assertTrue(mesh.vertices().size() >= 6);
        assertEquals(0, mesh.vertices().size() % 3);
    }

    @Test
    void rejectsInvalidShapeArguments() {
        TestBackend backend = new TestBackend();
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
        assertThrows(IllegalArgumentException.class, () -> manager.fillPolygon(
                List.of(new Point(0, 0), new Point(Float.NaN, 0), new Point(1, 1)),
                Paint.solid(RED)));
    }

    @Test
    void emptyPolygonsDrawNothing() {
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);

        manager.begin();
        manager.fillPolygon(List.of(), Paint.solid(RED));
        manager.fillPolygon(List.of(new Point(0, 0), new Point(1, 1)), Paint.solid(RED));
        manager.fillPolygon(List.of(new Point(0, 0), new Point(10, 0), new Point(20, 0)),
                Paint.solid(RED));
        manager.pushMaskPolygon(List.of());
        manager.fillRect(LOGICAL, Paint.solid(RED));
        manager.popMask();
        manager.end();

        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertTrue(commands.stream().noneMatch(MeshCommand.class::isInstance));
    }

    @Test
    void transformsAffectShapeAndMeshGeometry() {
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        manager.setAntialiasing(Antialiasing.OFF);
        Rect bounds = new Rect(0, 0, 100, 50);

        manager.begin();
        manager.withTransform(Transform.translation(10, 20), () -> {
            manager.fillRect(bounds, Paint.solid(RED));
            manager.mesh(List.of(new MeshVertex(0, 0, RED), new MeshVertex(100, 0, RED),
                    new MeshVertex(0, 50, RED)));
        });
        manager.end();

        DrawCommand command = backend.lastDrawList().commands().get(0);
        assertTrue(command instanceof RoundedRectCommand);
        assertEquals(new Rect(10, 20, 100, 50), command.bounds());
        command = backend.lastDrawList().commands().get(1);
        assertTrue(command instanceof MeshCommand);
        assertEquals(new Rect(10, 20, 100, 50), command.bounds());
    }

    @Test
    void translatedRoundedRectPreservesClipColorAndClampedRadius() {
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        Rect clip = new Rect(0, 0, 80, 60);
        Color translucent = new Color(1, 0, 0, 0.5f);

        manager.begin();
        manager.pushClip(clip);
        manager.pushTransform(Transform.translation(30, 40));
        manager.pushTransform(Transform.translation(-10, -15));
        manager.fillRoundedRect(new Rect(2, 3, 100, 50), 100, Paint.solid(translucent));
        manager.popTransform();
        manager.popTransform();
        manager.popClip();
        manager.end();

        RoundedRectCommand command = (RoundedRectCommand) backend.lastDrawList().commands().getFirst();
        assertEquals(new Rect(22, 28, 100, 50), command.bounds());
        assertEquals(25, command.radius());
        assertEquals(translucent, command.color());
        assertEquals(clip, command.clip());
    }

    @Test
    void scalingRotationAndGradientsKeepTheMeshPath() {
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        Rect bounds = new Rect(0, 0, 100, 50);
        Paint gradient = Paint.linear(new Point(0, 0), new Point(100, 0),
                new GradientStop(0, RED), new GradientStop(1, Color.WHITE));

        manager.begin();
        manager.withTransform(Transform.scaling(2, 3),
                () -> manager.fillRoundedRect(bounds, 10, Paint.solid(RED)));
        manager.withTransform(Transform.rotation(0.5f),
                () -> manager.fillRect(bounds, Paint.solid(RED)));
        manager.withTransform(Transform.translation(10, 20),
                () -> manager.fillRect(bounds, gradient));
        manager.end();

        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertEquals(3, commands.size());
        assertTrue(commands.stream().allMatch(MeshCommand.class::isInstance));
        assertEquals(new Rect(0, 0, 200, 150), commands.get(0).bounds());
        assertEquals(new Rect(10, 20, 100, 50), commands.get(2).bounds());
        MeshCommand translated = (MeshCommand) commands.get(2);
        assertTrue(translated.vertices().stream().anyMatch(vertex -> vertex.color().equals(RED)));
        assertTrue(translated.vertices().stream().anyMatch(vertex -> vertex.color().equals(Color.WHITE)));
    }

    @Test
    void transformsApplyToImageCommandsWithTheAtlasRegion() {
        TestBackend backend = new TestBackend();
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

        List<DrawCommand> commands = backend.lastDrawList().commands();
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
        TestBackend backend = new TestBackend();
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
        TestBackend backend = new TestBackend();
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
        TestBackend backend = new TestBackend();
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

        List<DrawCommand> commands = backend.lastDrawList().commands();
        MaskTextureBeginCommand begin = (MaskTextureBeginCommand) commands.get(0);
        assertSame(texture, begin.texture());
        assertEquals(bounds, begin.bounds());
        assertEquals(atlasUv, begin.uv());
        assertEquals(transform, begin.transform());
        assertEquals(transform, ((MaskTextureEndCommand) commands.get(1)).transform());
    }

    @Test
    void maskApiEmitsBeginAndEndCommands() {
        TestBackend backend = new TestBackend();
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

        List<DrawCommand> commands = backend.lastDrawList().commands();
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
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        manager.begin();

        assertThrows(IllegalStateException.class, manager::popMask);
        manager.pushMaskPolygon(List.of(new Point(0, 0), new Point(1, 1)));
        manager.popMask();
        assertThrows(NullPointerException.class, () -> manager.pushMaskTexture(null,
                new Rect(0, 0, 10, 10)));
    }

    @Test
    void antialiasingChangesTessellationDensity() {
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        manager.setAntialiasing(Antialiasing.OFF);
        manager.begin();
        manager.fillCircle(new Point(100, 100), 50, Paint.solid(RED));
        manager.end();
        int coarse = ((MeshCommand) backend.lastDrawList().commands().get(0)).vertices().size();

        manager.setAntialiasing(Antialiasing.HIGH);
        manager.begin();
        manager.fillCircle(new Point(100, 100), 50, Paint.solid(RED));
        manager.end();
        int dense = ((MeshCommand) backend.lastDrawList().commands().get(0)).vertices().size();

        assertTrue(dense > coarse);
    }

    @Test
    void nestedMasksEmitBalancedCommands() {
        TestBackend backend = new TestBackend();
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

        List<DrawCommand> commands = backend.lastDrawList().commands();
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
    void alignsIconTexelsToFramebufferPixelsAfterWidgetTranslation() {
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        manager.begin();
        manager.withTransform(Transform.translation(0.25f, 0.25f),
                () -> manager.icon(new TestTexture(28, 28), new Rect(3.1f, 2.1f, 14, 14),
                        ImageStyle.ICON, Color.WHITE));
        manager.end();
        ImageCommand icon = (ImageCommand) backend.lastDrawList().commands().getFirst();
        assertEquals(new Rect(3.25f, 2.25f, 14, 14), icon.bounds());
    }

    @Test
    void drawsNativeSizeIconWithoutResamplingAtFractionalDensity() {
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(new Viewport(1000, 750, 800, 600));
        manager.begin();
        manager.icon(new TestTexture(18, 18), new Rect(3, 3, 14, 14), ImageStyle.ICON, Color.WHITE);
        manager.end();
        ImageCommand icon = (ImageCommand) backend.lastDrawList().commands().getFirst();
        assertEquals(new Rect(3.2f, 3.2f, 14.4f, 14.4f), icon.bounds());
    }

    @Test
    void createsFontsDrawsTextAndReleasesTheirAtlases() {
        FontData data = SystemFonts.findDefault().orElse(null);
        Assumptions.assumeTrue(data != null, "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        Font font = manager.createFont(data, 20, new int[]{'A', 'B'});
        assertSame(font.atlas(), backend.lastImage());

        manager.begin();
        manager.text(font, "AB", new Point(10, 20), RED);
        assertThrows(NullPointerException.class, () -> manager.text(null, "A", 0, 0, RED));
        assertThrows(NullPointerException.class, () -> manager.text(font, null, 0, 0, RED));
        assertThrows(NullPointerException.class, () -> manager.text(font, "A", 0, 0, (Color) null));
        manager.end();

        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertEquals(1, commands.size());
        TextCommand text = (TextCommand) commands.get(0);
        assertEquals(2, text.glyphs().size());
        assertSame(backend.lastCreatedTexture(), text.texture());
        assertEquals(RED, text.colors().get(0));
        assertEquals(Transform.IDENTITY, text.transform());
        assertEquals(font.glyph('A').uv(), text.glyphs().get(0).uv());
        assertEquals(new Rect(Math.round((10 + font.glyph('A').offsetX()) * 2) / 2f,
                Math.round((20 + font.ascent() + font.glyph('A').offsetY()) * 2) / 2f,
                font.glyph('A').width(), font.glyph('A').height()),
                text.glyphs().get(0).bounds());

        assertThrows(NullPointerException.class, () -> manager.measureText(null, "A"));
        assertThrows(NullPointerException.class, () -> manager.measureText(font, null));
        assertEquals(font.measure("AB").width(), manager.measureText(font, "AB").width(), 0.0001f);
        assertEquals(font.measure("AB").height(), manager.measureText(font, "AB").height(), 0.0001f);

        manager.destroyFont(font);
        assertSame(backend.lastCreatedTexture(), backend.lastDestroyedTexture());
    }

    @Test
    void createsStyledFontVariants() {
        FontData data = SystemFonts.findDefault().orElse(null);
        Assumptions.assumeTrue(data != null, "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);

        Font bold = manager.createFont(data, 20, new int[]{'A'}, FontStyle.BOLD, 1.5f);

        assertEquals(FontStyle.BOLD, bold.style());
        assertEquals(1.5f, bold.letterSpacing(), 0.0001f);
        assertSame(bold.atlas(), backend.lastImage());
    }

    @Test
    void prefersInstalledBoldFacesOverSyntheticStyles() {
        String family = null;
        for (String candidate : List.of("Segoe UI", "Arial", "Helvetica Neue", "Helvetica",
                "DejaVu Sans", "Liberation Sans", "Noto Sans", "Tahoma", "Verdana")) {
            if (SystemFonts.find(candidate, FontStyle.BOLD).isPresent()) {
                family = candidate;
                break;
            }
        }
        Assumptions.assumeTrue(family != null, "No installed bold face is available.");
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);

        Font bold = manager.createFont(family, 20, FontStyle.BOLD).orElseThrow();

        assertEquals(FontStyle.REGULAR, bold.style());
        assertNotNull(bold.glyph('A'));
        assertSame(bold.atlas(), backend.lastImage());
    }

    @Test
    void systemFontLookupReturnsEmptyForMissingFamilies() {
        RenderManager manager = new RenderManager(new TestBackend());

        assertTrue(manager.createFont("lixui-missing-family", 16, FontStyle.BOLD).isEmpty());
        assertThrows(NullPointerException.class,
                () -> manager.createFont((String) null, 16, FontStyle.BOLD));
        assertThrows(NullPointerException.class,
                () -> manager.createFont("Arial", 16, null));
    }

    @Test
    void alignsTextAroundTheAnchor() {
        FontData data = SystemFonts.findDefault().orElse(null);
        Assumptions.assumeTrue(data != null, "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        Font font = manager.createFont(data, 30, new int[]{'A', 'B'});
        Glyph glyph = font.glyph('A');
        float width = font.measure("AB").width();
        float height = font.measure("AB").height();

        manager.begin();
        manager.text(font, "AB", new Point(400, 300), RED,
                HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        assertThrows(NullPointerException.class,
                () -> manager.text(font, "A", 0, 0, RED, null, VerticalAlignment.TOP));
        assertThrows(NullPointerException.class,
                () -> manager.text(font, "A", 0, 0, RED, HorizontalAlignment.LEFT, null));
        manager.end();

        Rect expected = new Rect(Math.round((400 - width / 2 + glyph.offsetX()) * 2) / 2f,
                Math.round((300 - height / 2 + font.ascent() + glyph.offsetY()) * 2) / 2f,
                glyph.width(), glyph.height());
        TextCommand command = (TextCommand) backend.lastDrawList().commands().get(0);
        assertEquals(expected, command.glyphs().get(0).bounds());
    }

    @Test
    void textSupportsWrappedStyles() {
        FontData data = SystemFonts.findDefault().orElse(null);
        Assumptions.assumeTrue(data != null, "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        Font font = manager.createFont(data, 24, new int[]{'A', 'B', ' '});
        TextStyle style = new TextStyle(font.measure("AAA").width() + 1, 0, 2,
                TextOverflow.ELLIPSIS, HorizontalAlignment.LEFT, VerticalAlignment.TOP);

        Size measured = manager.measureText(font, "AAA AAA AAA", style);
        manager.begin();
        manager.text(font, "AAA AAA AAA", 0, 0, RED, style);
        manager.end();

        assertTrue(measured.height() > font.lineHeight());
        assertEquals(1, backend.lastDrawList().commands().size());
    }

    @Test
    void textSupportsPaintGradients() {
        FontData data = SystemFonts.findDefault().orElse(null);
        Assumptions.assumeTrue(data != null, "No readable system font is installed.");
        TestBackend backend = new TestBackend();
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        Font font = manager.createFont(data, 40, new int[]{'A', 'B'});
        Paint gradient = Paint.linear(new Point(0, 0), new Point(200, 0),
                new GradientStop(0, RED), new GradientStop(1, Color.rgb(0, 0, 255)));

        manager.begin();
        manager.text(font, "AB", new Point(0, 0), gradient);
        assertThrows(NullPointerException.class, () -> manager.text(font, "A", 0, 0, (Paint) null));
        manager.end();

        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertEquals(1, commands.size());
        TextCommand command = (TextCommand) commands.get(0);
        assertEquals(2, command.colors().size());
        Color first = command.colors().get(0);
        Color second = command.colors().get(1);
        assertTrue(first.r() > second.r());
        assertTrue(first.b() < second.b());
        assertEquals(1, first.a(), 0.0001f);
        assertEquals(1, second.a(), 0.0001f);
    }

    @Test
    void textRejectsFontsCreatedByAnotherManager() {
        FontData data = SystemFonts.findDefault().orElse(null);
        Assumptions.assumeTrue(data != null, "No readable system font is installed.");
        RenderManager manager = new RenderManager(new TestBackend());
        manager.resize(VIEWPORT);
        Font font = Font.load(data, 16, new int[]{'A'});

        manager.begin();
        assertThrows(IllegalArgumentException.class,
                () -> manager.text(font, "A", new Point(0, 0), RED));
        manager.end();
    }

    @Test
    void rejectsNullArguments() {
        TestBackend backend = new TestBackend();
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


    @Test
    void analyticBackendReceivesShapesWithoutTessellatingGradientsOrEllipses() {
        TestBackend backend = new TestBackend();
        backend.setAnalytic(true);
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        Paint gradient = Paint.radial(new Point(50, 50), 40,
                new GradientStop(0, RED), new GradientStop(1, Color.WHITE));
        Transform transform = Transform.rotation(0.25f).scale(2, 1);

        manager.begin();
        manager.pushTransform(transform);
        manager.fillRoundedRect(new Rect(0, 0, 100, 50), 12, gradient);
        manager.fillEllipse(new Point(50, 50), 40, 20, Paint.solid(RED));
        manager.popTransform();
        manager.end();

        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertEquals(2, commands.size());
        ShapeCommand rectangle = (ShapeCommand) commands.get(0);
        ShapeCommand ellipse = (ShapeCommand) commands.get(1);
        assertSame(gradient, rectangle.paint());
        assertEquals(12, rectangle.radius());
        assertEquals(transform, rectangle.transform());
        assertEquals(LOGICAL, rectangle.clip());
        assertTrue(ellipse.ellipse());
        assertEquals(new Rect(10, 30, 80, 40), ellipse.bounds());
    }

    @Test
    void oversizedGradientsFallBackWithoutDroppingStops() {
        TestBackend backend = new TestBackend();
        backend.setAnalytic(true);
        RenderManager manager = new RenderManager(backend);
        manager.resize(VIEWPORT);
        Paint gradient = Paint.linear(new Point(0, 0), new Point(100, 0),
                new GradientStop(0, RED), new GradientStop(0.25f, Color.WHITE),
                new GradientStop(0.5f, RED), new GradientStop(0.75f, Color.WHITE),
                new GradientStop(1, RED));

        manager.begin();
        manager.fillRect(new Rect(0, 0, 100, 50), gradient);
        manager.end();

        assertTrue(backend.lastDrawList().commands().getFirst() instanceof MeshCommand);
        assertThrows(IllegalArgumentException.class,
                () -> new ShapeCommand(LOGICAL, 0, false, gradient, LOGICAL, Transform.IDENTITY));
    }

}
