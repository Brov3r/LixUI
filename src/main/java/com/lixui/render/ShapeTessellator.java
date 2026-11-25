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
    private static final double RELATIVE_TOLERANCE = 1e-12;
    private static final int MIN_CIRCLE_SEGMENTS = 12;
    private static final int MAX_CIRCLE_SEGMENTS = 256;
    private static final int MAX_GRADIENT_STEPS = 64;
    private static final int MIN_RINGS = 4;
    private static final int MAX_RINGS = 24;
    private static final float MITER_LIMIT = 4;
    private static final float CORNER_TOLERANCE = 0.1f;
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
        Point center = bounds.center();
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
        double area = signedArea(polygon);
        if (Math.abs(area) < areaTolerance(polygon)) {
            throw new IllegalArgumentException("Polygon has no area.");
        }
        if (area < 0) {
            Collections.reverse(polygon);
        }
        return triangulate(polygon, paint);
    }

    /**
     * Fills the area between two concentric arcs as a single quad strip.
     * Tessellating the strip directly keeps sub-pixel sectors stable, while ear clipping a polygon
     * whose vertices are tiny offsets from a far origin loses the winding to float cancellation.
     *
     * @param center shared arc center
     * @param innerRadius inner arc radius, or zero to fill up to the center
     * @param outerRadius outer arc radius
     * @param startAngle start angle in radians
     * @param sweepAngle signed sweep in radians, at most one full turn
     * @param paint fill paint
     * @return triangle vertices covering only the band between the arcs
     * @throws NullPointerException if {@code center} is null
     * @throws IllegalArgumentException if a radius or angle is invalid
     */
    static List<MeshVertex> fillRingSector(Point center, float innerRadius, float outerRadius,
                                           float startAngle, float sweepAngle, Paint paint) {
        validateRingSector(center, innerRadius, outerRadius, startAngle, sweepAngle);
        if (innerRadius <= EPSILON) {
            return fillArc(center, outerRadius, outerRadius, startAngle, sweepAngle, paint);
        }
        int segments = ellipseSegments(outerRadius, outerRadius, Math.abs(sweepAngle));
        boolean closed = isFullCircle(sweepAngle);
        List<MeshVertex> vertices = new ArrayList<>(segments * 6);
        for (int segment = 0; segment < segments; segment++) {
            int next = closed && segment == segments - 1 ? 0 : segment + 1;
            float firstAngle = angleAt(startAngle, sweepAngle, segment, segments);
            float secondAngle = angleAt(startAngle, sweepAngle, next, segments);
            addQuad(vertices,
                    pointOnCircle(center, outerRadius, firstAngle),
                    pointOnCircle(center, outerRadius, secondAngle),
                    pointOnCircle(center, innerRadius, secondAngle),
                    pointOnCircle(center, innerRadius, firstAngle),
                    paint);
        }
        return vertices;
    }

    static void validateRingSector(Point center, float innerRadius, float outerRadius,
                                   float startAngle, float sweepAngle) {
        if (center == null) {
            throw new NullPointerException("center");
        }
        if (!Float.isFinite(innerRadius) || innerRadius < 0
                || !Float.isFinite(outerRadius) || outerRadius <= 0
                || innerRadius >= outerRadius) {
            throw new IllegalArgumentException(
                    "Ring radii must be finite with 0 <= inner < outer.");
        }
        if (!Float.isFinite(startAngle) || !Float.isFinite(sweepAngle)) {
            throw new IllegalArgumentException("Arc angles must be finite.");
        }
        float magnitude = Math.abs(sweepAngle);
        if (magnitude < EPSILON || magnitude > TWO_PI + EPSILON) {
            throw new IllegalArgumentException("Arc sweep must be non-zero and at most one full turn.");
        }
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
        List<Point> points = removeShortSegments(dedupeConsecutive(input), closed);
        if (points.size() < 2) {
            throw new IllegalArgumentException("A path requires at least two distinct points.");
        }
        if (closed && points.size() < 3) {
            throw new IllegalArgumentException("A closed path requires at least three distinct points.");
        }

        float halfWidth = width / 2;
        List<Point> left = new ArrayList<>(points.size());
        List<Point> right = new ArrayList<>(points.size());
        for (int index = 0; index < points.size(); index++) {
            offsetPoint(points, index, closed, halfWidth, left, right);
        }

        List<MeshVertex> vertices = new ArrayList<>();
        int segmentCount = closed ? points.size() : points.size() - 1;
        for (int index = 0; index < segmentCount; index++) {
            int next = (index + 1) % points.size();
            addQuad(vertices, left.get(index), left.get(next),
                    right.get(next), right.get(index), paint);
        }
        if (!closed) {
            int last = points.size() - 1;
            addCap(vertices, points.get(0), left.get(0), points.get(1), paint);
            addCap(vertices, points.get(last), left.get(last), points.get(last - 1), paint);
        }
        return vertices;
    }

    /**
     * Computes the mitered offsets of one path vertex. Adjacent segments share the same offset
     * points, so the stroke stays a single band without overlapping geometry; overlapping pieces
     * would double-blend translucent paint and show up as stacked rectangles.
     */
    private static void offsetPoint(List<Point> points, int index, boolean closed, float halfWidth,
                                    List<Point> left, List<Point> right) {
        int count = points.size();
        Point current = points.get(index);
        boolean hasPrevious = closed || index > 0;
        boolean hasNext = closed || index < count - 1;

        float previousX = 0;
        float previousY = 0;
        if (hasPrevious) {
            Point previous = points.get((index - 1 + count) % count);
            float length = distance(previous, current);
            previousX = (previous.y() - current.y()) / length;
            previousY = (current.x() - previous.x()) / length;
        }
        float nextX = 0;
        float nextY = 0;
        if (hasNext) {
            Point next = points.get((index + 1) % count);
            float length = distance(current, next);
            nextX = (current.y() - next.y()) / length;
            nextY = (next.x() - current.x()) / length;
        }
        if (!hasPrevious) {
            previousX = nextX;
            previousY = nextY;
        } else if (!hasNext) {
            nextX = previousX;
            nextY = previousY;
        }

        float sumX = previousX + nextX;
        float sumY = previousY + nextY;
        float sumLength = (float) Math.hypot(sumX, sumY);
        float offsetX;
        float offsetY;
        if (sumLength < EPSILON) {
            offsetX = previousX * halfWidth;
            offsetY = previousY * halfWidth;
        } else {
            float miterLength = Math.min(2f * halfWidth / sumLength, MITER_LIMIT * halfWidth);
            offsetX = sumX / sumLength * miterLength;
            offsetY = sumY / sumLength * miterLength;
        }
        left.add(new Point(current.x() + offsetX, current.y() + offsetY));
        right.add(new Point(current.x() - offsetX, current.y() - offsetY));
    }

    /**
     * Fills a round cap with a half disc that only touches the stroke band instead of covering it.
     */
    private static void addCap(List<MeshVertex> vertices, Point center, Point first,
                               Point toward, Paint paint) {
        float radius = distance(center, first);
        float outwardX = center.x() - toward.x();
        float outwardY = center.y() - toward.y();
        float outwardLength = (float) Math.hypot(outwardX, outwardY);
        if (radius < EPSILON || outwardLength < EPSILON) {
            return;
        }
        outwardX /= outwardLength;
        outwardY /= outwardLength;
        float edgeX = (first.x() - center.x()) / radius;
        float edgeY = (first.y() - center.y()) / radius;
        int segments = capSegments(radius);
        List<Point> arc = new ArrayList<>(segments + 1);
        for (int index = 0; index <= segments; index++) {
            float angle = (float) (Math.PI * index / segments);
            float cosine = (float) Math.cos(angle);
            float sine = (float) Math.sin(angle);
            arc.add(new Point(center.x() + radius * (cosine * edgeX + sine * outwardX),
                    center.y() + radius * (cosine * edgeY + sine * outwardY)));
        }
        addOpenFan(vertices, center, arc, paint);
    }

    private static List<Point> removeShortSegments(List<Point> points, boolean closed) {
        List<Point> result = new ArrayList<>(points.size());
        for (Point point : points) {
            if (result.isEmpty() || distance(result.get(result.size() - 1), point) >= EPSILON) {
                result.add(point);
            }
        }
        if (closed && result.size() > 1
                && distance(result.get(0), result.get(result.size() - 1)) < EPSILON) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private static float distance(Point first, Point second) {
        return (float) Math.hypot(second.x() - first.x(), second.y() - first.y());
    }

    private static int capSegments(float radius) {
        return Math.clamp((int) Math.ceil(radius * 2 * quality), 4, 32);
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
        return Math.clamp((int) Math.ceil(size / 10 * quality), MIN_RINGS, MAX_RINGS);
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
        double tolerance = areaTolerance(points);
        int guard = points.size() * points.size();
        while (indices.size() > 3 && guard-- > 0) {
            boolean earFound = false;
            for (int index = 0; index < indices.size(); index++) {
                int previous = indices.get((index - 1 + indices.size()) % indices.size());
                int current = indices.get(index);
                int next = indices.get((index + 1) % indices.size());
                if (!isEar(points, indices, previous, current, next, tolerance)) {
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

    /**
     * Picks an area tolerance relative to the polygon size.
     * A fixed tolerance cannot serve both large polygons, where floating point noise produces
     * non-zero areas, and sub-pixel slivers such as thin arc masks, whose triangles are valid but tiny.
     */
    private static double areaTolerance(List<Point> points) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (Point point : points) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        double extent = Math.max(maxX - (double) minX, maxY - (double) minY);
        return Math.max(1e-12, RELATIVE_TOLERANCE * extent * extent);
    }

    private static boolean isEar(List<Point> points, List<Integer> indices,
                                 int previous, int current, int next, double tolerance) {
        Point a = points.get(previous);
        Point b = points.get(current);
        Point c = points.get(next);
        if (cross(a, b, c) <= tolerance) {
            return false;
        }
        for (int index : indices) {
            if (index == previous || index == current || index == next) {
                continue;
            }
            if (pointInTriangle(points.get(index), a, b, c, tolerance)) {
                return false;
            }
        }
        return true;
    }

    private static boolean pointInTriangle(Point point, Point a, Point b, Point c, double tolerance) {
        double first = cross(a, b, point);
        double second = cross(b, c, point);
        double third = cross(c, a, point);
        return first >= -tolerance && second >= -tolerance && third >= -tolerance;
    }

    private static double cross(Point a, Point b, Point c) {
        return (double) (b.x() - a.x()) * (c.y() - a.y())
                - (double) (b.y() - a.y()) * (c.x() - a.x());
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

    private static double signedArea(List<Point> points) {
        double area = 0;
        for (int index = 0; index < points.size(); index++) {
            Point current = points.get(index);
            Point next = points.get((index + 1) % points.size());
            area += (double) current.x() * next.y() - (double) next.x() * current.y();
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
        int segments = cornerSegments(clamped);
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

    private static float angleAt(float startAngle, float sweepAngle, int index, int segments) {
        return startAngle + sweepAngle * index / segments;
    }

    private static Point pointOnCircle(Point center, float radius, float angle) {
        return new Point(center.x() + (float) Math.cos(angle) * radius,
                center.y() + (float) Math.sin(angle) * radius);
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
        return Math.clamp(steps, 4, MAX_GRADIENT_STEPS);
    }

    /**
     * Keeps a quarter circle under the sagitta tolerance, so small corner radii no longer turn
     * into two or three visible facets.
     */
    private static int cornerSegments(float radius) {
        float ratio = Math.min(CORNER_TOLERANCE / (Math.max(radius, EPSILON) * quality), 0.9f);
        double step = 2 * Math.acos(1 - ratio);
        int segments = (int) Math.ceil(Math.PI / 2 / step);
        return Math.clamp(segments, 3, 48);
    }

    private static int ellipseSegments(float radiusX, float radiusY, float sweep) {
        float circumference = (float) (Math.PI * (radiusX + radiusY));
        float length = circumference * (sweep / TWO_PI);
        int segments = (int) Math.ceil(length / 6 * quality);
        return Math.clamp(segments, MIN_CIRCLE_SEGMENTS, MAX_CIRCLE_SEGMENTS);
    }

    private static boolean isFullCircle(float sweep) {
        return Math.abs(sweep) >= TWO_PI - EPSILON;
    }

}
