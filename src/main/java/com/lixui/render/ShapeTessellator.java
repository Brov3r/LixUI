package com.lixui.render;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns high-level shapes into colored triangle meshes.
 * Gradients are evaluated per vertex; linear gradients are exact at vertex positions, while curved
 * shapes and radial gradients use enough segments to keep visible banding small.
 */
final class ShapeTessellator {
    private static final float TWO_PI = (float) (Math.PI * 2);
    private static final float EPSILON = 0.0001f;
    private static final int MIN_CIRCLE_SEGMENTS = 12;
    private static final int MAX_CIRCLE_SEGMENTS = 256;
    private static final int MAX_GRADIENT_STEPS = 64;
    private static final int MIN_RINGS = 4;
    private static final int MAX_RINGS = 24;
    private static float quality = 1;

    private ShapeTessellator() {
    }

    static void setQuality(float value) {
        if (!Float.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Tessellation quality must be finite and positive.");
        }
        quality = value;
    }

    static List<MeshVertex> fillRect(Rect bounds, Paint paint) {
        if (bounds.isEmpty()) {
            return List.of();
        }
        List<MeshVertex> vertices = new ArrayList<>();
        if (paint instanceof Paint.Solid) {
            addQuad(vertices,
                    new Point(bounds.x(), bounds.y()),
                    new Point(bounds.right(), bounds.y()),
                    new Point(bounds.right(), bounds.bottom()),
                    new Point(bounds.x(), bounds.bottom()),
                    paint);
            return vertices;
        }
        int steps = gradientSteps(bounds);
        float cellWidth = bounds.width() / steps;
        float cellHeight = bounds.height() / steps;
        for (int row = 0; row < steps; row++) {
            float top = bounds.y() + row * cellHeight;
            float bottom = top + cellHeight;
            for (int column = 0; column < steps; column++) {
                float left = bounds.x() + column * cellWidth;
                float right = left + cellWidth;
                addQuad(vertices,
                        new Point(left, top),
                        new Point(right, top),
                        new Point(right, bottom),
                        new Point(left, bottom),
                        paint);
            }
        }
        return vertices;
    }

    static List<MeshVertex> fillRoundedRect(Rect bounds, float radius, Paint paint) {
        if (bounds.isEmpty()) {
            return List.of();
        }
        List<Point> path = roundedRectPath(bounds, radius);
        Point center = new Point(bounds.x() + bounds.width() / 2,
                bounds.y() + bounds.height() / 2);
        List<MeshVertex> vertices = new ArrayList<>();
        if (paint instanceof Paint.Solid) {
            addClosedFan(vertices, center, path, paint);
        } else {
            addPathRings(vertices, center, path, paint);
        }
        return vertices;
    }

    static List<MeshVertex> fillEllipse(Point center, float radiusX, float radiusY, Paint paint) {
        int segments = ellipseSegments(radiusX, radiusY, TWO_PI);
        List<Point> path = ellipsePath(center, radiusX, radiusY, segments);
        List<MeshVertex> vertices = new ArrayList<>();
        if (paint instanceof Paint.Solid) {
            addClosedFan(vertices, center, path, paint);
        } else {
            addEllipseRings(vertices, center, radiusX, radiusY, 0, TWO_PI, segments, true, paint);
        }
        return vertices;
    }

    static List<MeshVertex> fillArc(Point center, float radiusX, float radiusY,
                                    float startAngle, float sweepAngle, Paint paint) {
        if (isFullCircle(sweepAngle)) {
            return fillEllipse(center, radiusX, radiusY, paint);
        }
        int segments = ellipseSegments(radiusX, radiusY, Math.abs(sweepAngle));
        List<Point> path = arcPath(center, radiusX, radiusY, startAngle, sweepAngle, segments);
        List<MeshVertex> vertices = new ArrayList<>();
        if (paint instanceof Paint.Solid) {
            addOpenFan(vertices, center, path, paint);
        } else {
            addEllipseRings(vertices, center, radiusX, radiusY, startAngle, sweepAngle,
                    segments, false, paint);
        }
        return vertices;
    }

    static List<MeshVertex> fillPolygon(List<Point> points, Paint paint) {
        List<Point> polygon = normalizePolygon(points);
        float area = signedArea(polygon);
        if (Math.abs(area) < EPSILON) {
            throw new IllegalArgumentException("Polygon has no area.");
        }
        if (area < 0) {
            Collections.reverse(polygon);
        }
        return triangulate(polygon, paint);
    }

    static List<MeshVertex> strokeLine(Point start, Point end, float width, Paint paint) {
        return strokePath(List.of(start, end), false, width, paint);
    }

    static List<MeshVertex> strokeRect(Rect bounds, float width, Paint paint) {
        return strokePath(rectPath(bounds), true, width, paint);
    }

    static List<MeshVertex> strokeRoundedRect(Rect bounds, float radius, float width, Paint paint) {
        return strokePath(roundedRectPath(bounds, radius), true, width, paint);
    }

    static List<MeshVertex> strokeEllipse(Point center, float radiusX, float radiusY,
                                          float width, Paint paint) {
        int segments = ellipseSegments(radiusX, radiusY, TWO_PI);
        return strokePath(ellipsePath(center, radiusX, radiusY, segments), true, width, paint);
    }

    static List<MeshVertex> strokeArc(Point center, float radiusX, float radiusY,
                                      float startAngle, float sweepAngle, float width, Paint paint) {
        if (isFullCircle(sweepAngle)) {
            return strokeEllipse(center, radiusX, radiusY, width, paint);
        }
        int segments = ellipseSegments(radiusX, radiusY, Math.abs(sweepAngle));
        List<Point> path = arcPath(center, radiusX, radiusY, startAngle, sweepAngle, segments);
        return strokePath(path, false, width, paint);
    }

    static List<MeshVertex> strokePolyline(List<Point> points, float width, Paint paint) {
        return strokePath(points, false, width, paint);
    }

    static List<MeshVertex> strokePolygon(List<Point> points, float width, Paint paint) {
        return strokePath(points, true, width, paint);
    }

    static void validateWidth(float width) {
        if (!Float.isFinite(width) || width <= 0) {
            throw new IllegalArgumentException("Stroke width must be finite and positive.");
        }
    }

    static void validateRadius(float radius) {
        if (!Float.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("Radius must be finite and non-negative.");
        }
    }

    static void validateEllipse(Point center, float radiusX, float radiusY) {
        if (center == null) {
            throw new NullPointerException("center");
        }
        if (!Float.isFinite(radiusX) || radiusX <= 0 || !Float.isFinite(radiusY) || radiusY <= 0) {
            throw new IllegalArgumentException("Ellipse radii must be finite and positive.");
        }
    }

    static void validateArc(Point center, float radiusX, float radiusY,
                            float startAngle, float sweepAngle) {
        validateEllipse(center, radiusX, radiusY);
        if (!Float.isFinite(startAngle) || !Float.isFinite(sweepAngle)) {
            throw new IllegalArgumentException("Arc angles must be finite.");
        }
        float magnitude = Math.abs(sweepAngle);
        if (magnitude < EPSILON || magnitude > TWO_PI + EPSILON) {
            throw new IllegalArgumentException("Arc sweep must be non-zero and at most one full turn.");
        }
    }

    private static List<MeshVertex> strokePath(List<Point> input, boolean closed,
                                               float width, Paint paint) {
        validateWidth(width);
        List<Point> points = dedupeConsecutive(input);
        if (points.size() < 2) {
            throw new IllegalArgumentException("A path requires at least two distinct points.");
        }
        if (closed && points.size() > 1 && points.get(0).equals(points.get(points.size() - 1))) {
            points = new ArrayList<>(points.subList(0, points.size() - 1));
        }
        if (closed && points.size() < 3) {
            throw new IllegalArgumentException("A closed path requires at least three distinct points.");
        }

        float halfWidth = width / 2;
        List<MeshVertex> vertices = new ArrayList<>();
        int segmentCount = closed ? points.size() : points.size() - 1;
        for (int index = 0; index < segmentCount; index++) {
            Point start = points.get(index);
            Point end = points.get((index + 1) % points.size());
            addSegment(vertices, start, end, halfWidth, paint);
        }
        for (Point point : points) {
            addDisc(vertices, point, halfWidth, paint);
        }
        return vertices;
    }

    private static void addSegment(List<MeshVertex> vertices, Point start, Point end,
                                   float halfWidth, Paint paint) {
        float dx = end.x() - start.x();
        float dy = end.y() - start.y();
        float length = (float) Math.hypot(dx, dy);
        if (length < EPSILON) {
            return;
        }
        float normalX = -dy / length * halfWidth;
        float normalY = dx / length * halfWidth;
        addQuad(vertices,
                new Point(start.x() + normalX, start.y() + normalY),
                new Point(end.x() + normalX, end.y() + normalY),
                new Point(end.x() - normalX, end.y() - normalY),
                new Point(start.x() - normalX, start.y() - normalY),
                paint);
    }

    private static void addDisc(List<MeshVertex> vertices, Point center, float radius, Paint paint) {
        int segments = circleSegments(radius);
        List<Point> path = ellipsePath(center, radius, radius, segments);
        addClosedFan(vertices, center, path, paint);
    }

    private static void addClosedFan(List<MeshVertex> vertices, Point center,
                                     List<Point> path, Paint paint) {
        if (path.size() < 3) {
            return;
        }
        for (int index = 0; index < path.size(); index++) {
            addTriangle(vertices, center, path.get(index), path.get((index + 1) % path.size()), paint);
        }
    }

    private static void addOpenFan(List<MeshVertex> vertices, Point center,
                                   List<Point> path, Paint paint) {
        for (int index = 0; index < path.size() - 1; index++) {
            addTriangle(vertices, center, path.get(index), path.get(index + 1), paint);
        }
    }

    private static void addPathRings(List<MeshVertex> vertices, Point center,
                                     List<Point> path, Paint paint) {
        int rings = ringCount(path.size());
        List<Point> previous = null;
        for (int ring = 1; ring <= rings; ring++) {
            float fraction = 1f - (ring - 1f) / rings;
            List<Point> current = new ArrayList<>(path.size());
            for (Point point : path) {
                current.add(new Point(center.x() + (point.x() - center.x()) * fraction,
                        center.y() + (point.y() - center.y()) * fraction));
            }
            if (previous != null) {
                connectRings(vertices, previous, current, true, paint);
            }
            previous = current;
        }
        if (previous != null) {
            addCenterFan(vertices, center, previous, true, paint);
        }
    }

    private static void addEllipseRings(List<MeshVertex> vertices, Point center,
                                        float radiusX, float radiusY,
                                        float startAngle, float sweepAngle,
                                        int segments, boolean closed, Paint paint) {
        int rings = ringCount(Math.max(radiusX, radiusY));
        List<Point> previous = null;
        for (int ring = 1; ring <= rings; ring++) {
            float fraction = 1f - (ring - 1f) / rings;
            List<Point> current;
            if (closed) {
                current = ellipsePath(center, radiusX * fraction, radiusY * fraction, segments);
            } else {
                current = arcPath(center, radiusX * fraction, radiusY * fraction,
                        startAngle, sweepAngle, segments);
            }
            if (previous != null) {
                connectRings(vertices, previous, current, closed, paint);
            }
            previous = current;
        }
        if (previous != null) {
            addCenterFan(vertices, center, previous, closed, paint);
        }
    }

    private static void connectRings(List<MeshVertex> vertices, List<Point> outer, List<Point> inner,
                                     boolean closed, Paint paint) {
        int edgeCount = closed ? outer.size() : outer.size() - 1;
        for (int index = 0; index < edgeCount; index++) {
            int next = (index + 1) % outer.size();
            addQuad(vertices, outer.get(index), outer.get(next), inner.get(next), inner.get(index), paint);
        }
    }

    private static void addCenterFan(List<MeshVertex> vertices, Point center, List<Point> ring,
                                     boolean closed, Paint paint) {
        if (closed) {
            for (int index = 0; index < ring.size(); index++) {
                addTriangle(vertices, center, ring.get(index), ring.get((index + 1) % ring.size()), paint);
            }
        } else {
            for (int index = 0; index < ring.size() - 1; index++) {
                addTriangle(vertices, center, ring.get(index), ring.get(index + 1), paint);
            }
        }
    }

    private static int ringCount(float size) {
        return clamp((int) Math.ceil(size / 10 * quality), MIN_RINGS, MAX_RINGS);
    }

    private static void addQuad(List<MeshVertex> vertices, Point a, Point b, Point c, Point d,
                                Paint paint) {
        addTriangle(vertices, a, b, c, paint);
        addTriangle(vertices, a, c, d, paint);
    }

    private static void addTriangle(List<MeshVertex> vertices, Point a, Point b, Point c, Paint paint) {
        if (Math.abs(cross(a, b, c)) < EPSILON) {
            return;
        }
        vertices.add(vertex(a, paint));
        vertices.add(vertex(b, paint));
        vertices.add(vertex(c, paint));
    }

    private static MeshVertex vertex(Point point, Paint paint) {
        return new MeshVertex(point.x(), point.y(), paint.colorAt(point));
    }

    private static List<MeshVertex> triangulate(List<Point> points, Paint paint) {
        List<Integer> indices = new ArrayList<>(points.size());
        for (int index = 0; index < points.size(); index++) {
            indices.add(index);
        }

        List<MeshVertex> vertices = new ArrayList<>();
        int guard = points.size() * points.size();
        while (indices.size() > 3 && guard-- > 0) {
            boolean earFound = false;
            for (int index = 0; index < indices.size(); index++) {
                int previous = indices.get((index - 1 + indices.size()) % indices.size());
                int current = indices.get(index);
                int next = indices.get((index + 1) % indices.size());
                if (!isEar(points, indices, previous, current, next)) {
                    continue;
                }
                addTriangle(vertices, points.get(previous), points.get(current), points.get(next), paint);
                indices.remove(index);
                earFound = true;
                break;
            }
            if (!earFound) {
                throw new IllegalArgumentException("Polygon cannot be triangulated.");
            }
        }
        if (indices.size() != 3) {
            throw new IllegalArgumentException("Polygon cannot be triangulated.");
        }
        addTriangle(vertices, points.get(indices.get(0)), points.get(indices.get(1)),
                points.get(indices.get(2)), paint);
        return vertices;
    }

    private static boolean isEar(List<Point> points, List<Integer> indices,
                                 int previous, int current, int next) {
        Point a = points.get(previous);
        Point b = points.get(current);
        Point c = points.get(next);
        if (cross(a, b, c) <= EPSILON) {
            return false;
        }
        for (int index : indices) {
            if (index == previous || index == current || index == next) {
                continue;
            }
            if (pointInTriangle(points.get(index), a, b, c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean pointInTriangle(Point point, Point a, Point b, Point c) {
        float first = cross(a, b, point);
        float second = cross(b, c, point);
        float third = cross(c, a, point);
        return first >= -EPSILON && second >= -EPSILON && third >= -EPSILON;
    }

    private static float cross(Point a, Point b, Point c) {
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
    }

    private static List<Point> normalizePolygon(List<Point> input) {
        List<Point> points = dedupeConsecutive(input);
        if (points.size() > 1 && points.get(0).equals(points.get(points.size() - 1))) {
            points = new ArrayList<>(points.subList(0, points.size() - 1));
        }
        if (points.size() < 3) {
            throw new IllegalArgumentException("A polygon requires at least three distinct points.");
        }
        return points;
    }

    private static List<Point> dedupeConsecutive(List<Point> input) {
        if (input == null) {
            throw new NullPointerException("points");
        }
        List<Point> result = new ArrayList<>(input.size());
        for (Point point : input) {
            if (point == null) {
                throw new NullPointerException("point");
            }
            if (result.isEmpty() || !result.get(result.size() - 1).equals(point)) {
                result.add(point);
            }
        }
        return result;
    }

    private static float signedArea(List<Point> points) {
        float area = 0;
        for (int index = 0; index < points.size(); index++) {
            Point current = points.get(index);
            Point next = points.get((index + 1) % points.size());
            area += current.x() * next.y() - next.x() * current.y();
        }
        return area / 2;
    }

    private static List<Point> rectPath(Rect bounds) {
        return List.of(
                new Point(bounds.x(), bounds.y()),
                new Point(bounds.right(), bounds.y()),
                new Point(bounds.right(), bounds.bottom()),
                new Point(bounds.x(), bounds.bottom()));
    }

    private static List<Point> roundedRectPath(Rect bounds, float radius) {
        validateRadius(radius);
        float clamped = Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2);
        if (clamped <= 0) {
            return rectPath(bounds);
        }
        float left = bounds.x();
        float top = bounds.y();
        float right = bounds.right();
        float bottom = bounds.bottom();
        int segments = Math.max(2, Math.min(16, (int) Math.ceil(clamped / 6 * quality)));
        List<Point> points = new ArrayList<>();
        points.add(new Point(left + clamped, top));
        points.add(new Point(right - clamped, top));
        appendArc(points, new Point(right - clamped, top + clamped), clamped, clamped,
                (float) (-Math.PI / 2), (float) (Math.PI / 2), segments);
        points.add(new Point(right, bottom - clamped));
        appendArc(points, new Point(right - clamped, bottom - clamped), clamped, clamped,
                0, (float) (Math.PI / 2), segments);
        points.add(new Point(left + clamped, bottom));
        appendArc(points, new Point(left + clamped, bottom - clamped), clamped, clamped,
                (float) (Math.PI / 2), (float) (Math.PI / 2), segments);
        points.add(new Point(left, top + clamped));
        appendArc(points, new Point(left + clamped, top + clamped), clamped, clamped,
                (float) Math.PI, (float) (Math.PI / 2), segments);
        return dedupeClosed(points);
    }

    private static List<Point> ellipsePath(Point center, float radiusX, float radiusY, int segments) {
        List<Point> points = new ArrayList<>(segments);
        for (int index = 0; index < segments; index++) {
            float angle = TWO_PI * index / segments;
            points.add(new Point(center.x() + (float) Math.cos(angle) * radiusX,
                    center.y() + (float) Math.sin(angle) * radiusY));
        }
        return points;
    }

    private static List<Point> arcPath(Point center, float radiusX, float radiusY,
                                       float startAngle, float sweepAngle, int segments) {
        List<Point> points = new ArrayList<>(segments + 1);
        for (int index = 0; index <= segments; index++) {
            float angle = startAngle + sweepAngle * index / segments;
            points.add(new Point(center.x() + (float) Math.cos(angle) * radiusX,
                    center.y() + (float) Math.sin(angle) * radiusY));
        }
        return points;
    }

    private static void appendArc(List<Point> points, Point center, float radiusX, float radiusY,
                                  float startAngle, float sweepAngle, int segments) {
        for (int index = 0; index <= segments; index++) {
            float angle = startAngle + sweepAngle * index / segments;
            points.add(new Point(center.x() + (float) Math.cos(angle) * radiusX,
                    center.y() + (float) Math.sin(angle) * radiusY));
        }
    }

    private static List<Point> dedupeClosed(List<Point> points) {
        List<Point> result = dedupeConsecutive(points);
        if (result.size() > 1 && result.get(0).equals(result.get(result.size() - 1))) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private static int gradientSteps(Rect bounds) {
        int steps = (int) Math.ceil(Math.max(bounds.width(), bounds.height()) / 16 * quality);
        return clamp(steps, 4, MAX_GRADIENT_STEPS);
    }

    private static int circleSegments(float radius) {
        int segments = (int) Math.ceil(radius * 1.5f * quality);
        return clamp(segments, MIN_CIRCLE_SEGMENTS, MAX_CIRCLE_SEGMENTS);
    }

    private static int ellipseSegments(float radiusX, float radiusY, float sweep) {
        float circumference = (float) (Math.PI * (radiusX + radiusY));
        float length = circumference * (sweep / TWO_PI);
        int segments = (int) Math.ceil(length / 6 * quality);
        return clamp(segments, MIN_CIRCLE_SEGMENTS, MAX_CIRCLE_SEGMENTS);
    }

    private static boolean isFullCircle(float sweep) {
        return Math.abs(sweep) >= TWO_PI - EPSILON;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
