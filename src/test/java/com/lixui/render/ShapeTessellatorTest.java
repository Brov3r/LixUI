package com.lixui.render;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.graphics.GradientStop;
import com.lixui.graphics.Paint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeTessellatorTest {
    private static final Color RED = Color.rgb(255, 0, 0);
    private static final Color BLUE = Color.rgb(0, 0, 255);
    private static final Paint SOLID = Paint.solid(RED);
    private static final Paint GRADIENT = Paint.linear(new Point(0, 0), new Point(100, 0),
            new GradientStop(0, RED), new GradientStop(1, BLUE));

    @BeforeEach
    void resetQuality() {
        ShapeTessellator.setQuality(1);
    }

    @Test
    void fillsRectangleGradientsWithTrianglesInsideBounds() {
        Rect bounds = new Rect(0, 0, 100, 50);

        List<MeshVertex> vertices = ShapeTessellator.fillRect(bounds, GRADIENT);

        assertTrue(vertices.size() >= 6);
        assertEquals(0, vertices.size() % 3);
        for (MeshVertex vertex : vertices) {
            assertTrue(vertex.x() >= bounds.x() && vertex.x() <= bounds.right());
            assertTrue(vertex.y() >= bounds.y() && vertex.y() <= bounds.bottom());
        }
    }

    @Test
    void triangulatesSimplePolygons() {
        List<Point> square = List.of(
                new Point(0, 0),
                new Point(100, 0),
                new Point(100, 100),
                new Point(0, 100));

        List<MeshVertex> vertices = ShapeTessellator.fillPolygon(square, SOLID);

        assertEquals(6, vertices.size());
    }

    @Test
    void triangulatesThinSectorPolygons() {
        List<Point> sector = new ArrayList<>();
        sector.add(new Point(0, 0));
        int segments = 8;
        float sweep = (float) (Math.PI * 2 * 0.002);
        for (int index = 0; index <= segments; index++) {
            float angle = (float) (-Math.PI / 2) + sweep * index / segments;
            sector.add(new Point((float) Math.cos(angle) * 50, (float) Math.sin(angle) * 50));
        }

        List<MeshVertex> vertices = ShapeTessellator.fillPolygon(sector, SOLID);

        assertTrue(vertices.size() >= 6);
        assertEquals(0, vertices.size() % 3);
    }

    @Test
    void buildsCurvedFillsAndRoundedRectangles() {
        List<MeshVertex> circle = ShapeTessellator.fillEllipse(
                new Point(50, 50), 40, 30, GRADIENT);
        List<MeshVertex> arc = ShapeTessellator.fillArc(
                new Point(50, 50), 40, 30, 0, (float) (Math.PI / 2), SOLID);
        List<MeshVertex> rounded = ShapeTessellator.fillRoundedRect(
                new Rect(0, 0, 100, 60), 12, GRADIENT);

        assertTrue(circle.size() >= 6);
        assertTrue(arc.size() >= 6);
        assertTrue(rounded.size() >= 6);
        assertEquals(0, circle.size() % 3);
        assertEquals(0, arc.size() % 3);
        assertEquals(0, rounded.size() % 3);
    }

    @Test
    void buildsStrokesForLinesAndCurves() {
        List<MeshVertex> line = ShapeTessellator.strokeLine(
                new Point(0, 0), new Point(100, 100), 4, SOLID);
        List<MeshVertex> arc = ShapeTessellator.strokeArc(
                new Point(50, 50), 40, 30, 0, (float) Math.PI, 4, GRADIENT);
        List<MeshVertex> polygon = ShapeTessellator.strokePolygon(
                List.of(new Point(0, 0), new Point(100, 0), new Point(50, 100)), 4, SOLID);

        assertTrue(line.size() >= 6);
        assertTrue(arc.size() >= 6);
        assertTrue(polygon.size() >= 6);
        assertEquals(0, line.size() % 3);
        assertEquals(0, arc.size() % 3);
        assertEquals(0, polygon.size() % 3);
    }

    @Test
    void curvedGradientsUseAdditionalRings() {
        List<MeshVertex> solid = ShapeTessellator.fillEllipse(
                new Point(50, 50), 100, 80, SOLID);
        List<MeshVertex> gradient = ShapeTessellator.fillEllipse(
                new Point(50, 50), 100, 80, GRADIENT);

        assertTrue(gradient.size() > solid.size());
        assertEquals(0, gradient.size() % 3);
    }

    @Test
    void strokesCoverThePathBandWithoutStackedOverlaps() {
        List<Point> square = List.of(
                new Point(0, 0),
                new Point(100, 0),
                new Point(100, 100),
                new Point(0, 100));

        List<MeshVertex> vertices = ShapeTessellator.strokePolygon(square, 10, SOLID);

        assertEquals(24, vertices.size());
        assertEquals(4000, meshArea(vertices), 1);
    }

    @Test
    void strokesOpenPathsWithRoundCapsThatDoNotOverlapTheBand() {
        List<MeshVertex> vertices = ShapeTessellator.strokeLine(
                new Point(0, 0), new Point(100, 0), 10, SOLID);

        double area = meshArea(vertices);
        assertTrue(area > 1000, "A 100x10 band must be covered.");
        assertTrue(area < 1100, "Round caps must not be drawn twice.");
        for (MeshVertex vertex : vertices) {
            assertTrue(vertex.x() >= -5.001f && vertex.x() <= 105.001f);
            assertTrue(Math.abs(vertex.y()) <= 5.001f);
        }
    }

    @Test
    void fillsSubPixelRingSectorsFarFromTheOriginWithoutPaintingTheCenter() {
        Point center = new Point(640.37f, 360.61f);
        float inner = 0.04096f;
        float outer = 0.128f;
        float sweep = (float) (Math.PI * 2) / 8 - 0.02f;

        List<MeshVertex> vertices = ShapeTessellator.fillRingSector(
                center, inner, outer, (float) (-Math.PI / 2), sweep, SOLID);

        assertTrue(vertices.size() >= 6);
        assertEquals(0, vertices.size() % 3);
        double expected = 0.5 * ((double) outer * outer - (double) inner * inner) * sweep;
        assertEquals(expected, meshArea(vertices), expected * 0.01);
        for (MeshVertex vertex : vertices) {
            double distance = Math.hypot(vertex.x() - center.x(), vertex.y() - center.y());
            assertTrue(distance >= inner * 0.98,
                    "The inner disc must stay transparent, but a vertex sits at " + distance);
        }
    }

    @Test
    void triangulatesTinyRingPolygonsFarFromTheOrigin() {
        Point center = new Point(640.37f, 360.61f);
        float inner = 0.04096f;
        float outer = 0.128f;
        float sweep = (float) (Math.PI * 2) / 8 - 0.02f;
        List<Point> polygon = ringSectorPolygon(center, inner, outer,
                (float) (-Math.PI / 2), sweep);

        List<MeshVertex> vertices = ShapeTessellator.fillPolygon(polygon, SOLID);

        assertTrue(vertices.size() >= 6);
        assertEquals(0, vertices.size() % 3);
        double expected = 0.5 * ((double) outer * outer - (double) inner * inner) * sweep;
        assertEquals(expected, meshArea(vertices), expected * 0.01);
    }

    @Test
    void fillsRingSectorsAcrossAnimatedMenuParameters() {
        float[] radii = {20, 108, 128, 140, 400};
        float[] ratios = {0.2f, 0.32f, 0.4f, 0.8f};
        float[] amounts = {0.001f, 0.0012f, 0.0018f, 0.002f, 0.01f, 0.5f, 1f};
        float[] lifts = {0, 4};
        Point center = new Point(640.37f, 360.61f);
        for (int items = 2; items <= 8; items++) {
            float baseSweep = (float) (Math.PI * 2) / items;
            float gap = Math.min(0.02f, baseSweep * 0.2f);
            float sweep = baseSweep - gap;
            float start = (float) (-Math.PI / 2) - baseSweep / 2 + gap / 2;
            for (float radius : radii) {
                for (float ratio : ratios) {
                    for (float amount : amounts) {
                        float outer = radius * amount;
                        for (float lift : lifts) {
                            List<MeshVertex> vertices = ShapeTessellator.fillRingSector(
                                    center, outer * ratio, outer + lift, start, sweep, SOLID);
                            assertEquals(0, vertices.size() % 3);
                        }
                    }
                }
            }
        }
    }

    private static List<Point> ringSectorPolygon(Point center, float inner, float outer,
                                                 float startAngle, float sweep) {
        int steps = Math.max(2, (int) Math.ceil(sweep / (Math.PI * 2) * 96));
        List<Point> points = new ArrayList<>((steps + 1) * 2);
        for (int step = 0; step <= steps; step++) {
            float angle = startAngle + sweep * step / steps;
            points.add(new Point(center.x() + (float) Math.cos(angle) * outer,
                    center.y() + (float) Math.sin(angle) * outer));
        }
        for (int step = steps; step >= 0; step--) {
            float angle = startAngle + sweep * step / steps;
            points.add(new Point(center.x() + (float) Math.cos(angle) * inner,
                    center.y() + (float) Math.sin(angle) * inner));
        }
        return points;
    }

    private static double meshArea(List<MeshVertex> vertices) {
        double area = 0;
        for (int index = 0; index < vertices.size(); index += 3) {
            MeshVertex first = vertices.get(index);
            MeshVertex second = vertices.get(index + 1);
            MeshVertex third = vertices.get(index + 2);
            area += Math.abs((second.x() - first.x()) * (third.y() - first.y())
                    - (second.y() - first.y()) * (third.x() - first.x())) / 2;
        }
        return area;
    }

    @Test
    void skipsEmptyPolygonsInsteadOfFailing() {
        List<List<Point>> emptyPolygons = List.of(
                List.of(),
                List.of(new Point(10, 10)),
                List.of(new Point(0, 0), new Point(10, 0)),
                List.of(new Point(5, 5), new Point(5, 5), new Point(5, 5)),
                List.of(new Point(0, 0), new Point(10, 0), new Point(20, 0)),
                List.of(new Point(640.37f, 360.61f), new Point(640.37f, 360.61f)));

        for (List<Point> polygon : emptyPolygons) {
            assertTrue(ShapeTessellator.fillPolygon(polygon, SOLID).isEmpty(),
                    "An empty polygon must not produce geometry: " + polygon);
        }
    }

    @Test
    void rejectsNonFinitePolygonCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> ShapeTessellator.fillPolygon(
                        List.of(new Point(0, 0), new Point(Float.NaN, 0), new Point(1, 1)),
                        SOLID));
        assertThrows(IllegalArgumentException.class,
                () -> ShapeTessellator.fillPolygon(
                        List.of(new Point(0, 0), new Point(1, 0), new Point(1, Float.POSITIVE_INFINITY)),
                        SOLID));
        assertThrows(NullPointerException.class,
                () -> ShapeTessellator.fillPolygon(
                        List.of(new Point(0, 0), new Point(1, 0), null), SOLID));
        assertThrows(NullPointerException.class,
                () -> ShapeTessellator.fillPolygon(null, SOLID));
    }

    @Test
    void rejectsDegeneratePaths() {
        assertThrows(IllegalArgumentException.class,
                () -> ShapeTessellator.strokePolyline(
                        List.of(new Point(0, 0), new Point(0, 0)), 4, SOLID));
    }
}
