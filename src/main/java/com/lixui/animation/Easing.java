package com.lixui.animation;

/**
 * A normalized timing curve used by animations.
 * Implementations receive a progress value in {@code [0, 1]} and return the eased progress, which
 * may overshoot for curves such as {@link #BACK_OUT} or {@link #ELASTIC_OUT}.
 */
@FunctionalInterface
public interface Easing {
    /** Linear interpolation. */
    Easing LINEAR = t -> t;

    /** A cubic curve that starts slowly. */
    Easing EASE_IN = t -> t * t * t;
    /** A cubic curve that ends slowly. */
    Easing EASE_OUT = t -> 1 - (1 - t) * (1 - t) * (1 - t);
    /** A cubic curve that starts and ends slowly. */
    Easing EASE_IN_OUT = t -> t < 0.5f
            ? 4 * t * t * t
            : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;

    /** Quadratic acceleration. */
    Easing QUAD_IN = t -> t * t;
    /** Quadratic deceleration. */
    Easing QUAD_OUT = t -> 1 - (1 - t) * (1 - t);
    /** Quadratic acceleration then deceleration. */
    Easing QUAD_IN_OUT = t -> t < 0.5f
            ? 2 * t * t
            : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;

    /** Cubic acceleration. */
    Easing CUBIC_IN = EASE_IN;
    /** Cubic deceleration. */
    Easing CUBIC_OUT = EASE_OUT;
    /** Cubic acceleration then deceleration. */
    Easing CUBIC_IN_OUT = EASE_IN_OUT;

    /** Quartic acceleration. */
    Easing QUART_IN = t -> t * t * t * t;
    /** Quartic deceleration. */
    Easing QUART_OUT = t -> 1 - (float) Math.pow(1 - t, 4);
    /** Quartic acceleration then deceleration. */
    Easing QUART_IN_OUT = t -> t < 0.5f
            ? 8 * t * t * t * t
            : 1 - (float) Math.pow(-2 * t + 2, 4) / 2;

    /** Quintic acceleration. */
    Easing QUINT_IN = t -> t * t * t * t * t;
    /** Quintic deceleration. */
    Easing QUINT_OUT = t -> 1 - (float) Math.pow(1 - t, 5);
    /** Quintic acceleration then deceleration. */
    Easing QUINT_IN_OUT = t -> t < 0.5f
            ? 16 * t * t * t * t * t
            : 1 - (float) Math.pow(-2 * t + 2, 5) / 2;

    /** Sine acceleration. */
    Easing SINE_IN = t -> 1 - (float) Math.cos(t * Math.PI / 2);
    /** Sine deceleration. */
    Easing SINE_OUT = t -> (float) Math.sin(t * Math.PI / 2);
    /** Sine acceleration then deceleration. */
    Easing SINE_IN_OUT = t -> (1 - (float) Math.cos(Math.PI * t)) / 2;

    /** Exponential acceleration. */
    Easing EXPO_IN = t -> t == 0 ? 0 : (float) Math.pow(2, 10 * t - 10);
    /** Exponential deceleration. */
    Easing EXPO_OUT = t -> t == 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
    /** Exponential acceleration then deceleration. */
    Easing EXPO_IN_OUT = t -> {
        if (t == 0 || t == 1) {
            return t;
        }
        return t < 0.5f
                ? (float) Math.pow(2, 20 * t - 10) / 2
                : (2 - (float) Math.pow(2, -20 * t + 10)) / 2;
    };

    /** Circular acceleration. */
    Easing CIRC_IN = t -> 1 - (float) Math.sqrt(1 - t * t);
    /** Circular deceleration. */
    Easing CIRC_OUT = t -> (float) Math.sqrt(1 - (t - 1) * (t - 1));
    /** Circular acceleration then deceleration. */
    Easing CIRC_IN_OUT = t -> t < 0.5f
            ? (1 - (float) Math.sqrt(1 - 4 * t * t)) / 2
            : ((float) Math.sqrt(1 - (float) Math.pow(-2 * t + 2, 2)) + 1) / 2;

    /** Back acceleration. */
    Easing BACK_IN = t -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return c3 * t * t * t - c1 * t * t;
    };
    /** Back deceleration. */
    Easing BACK_OUT = t -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    };
    /** Back acceleration then deceleration. */
    Easing BACK_IN_OUT = t -> {
        float c1 = 1.70158f * 1.525f;
        return t < 0.5f
                ? (float) Math.pow(2 * t, 2) * ((c1 + 1) * 2 * t - c1) / 2
                : ((float) Math.pow(2 * t - 2, 2) * ((c1 + 1) * (t * 2 - 2) + c1) + 2) / 2;
    };

    /** Elastic acceleration. */
    Easing ELASTIC_IN = t -> {
        if (t == 0 || t == 1) {
            return t;
        }
        float c4 = (float) (2 * Math.PI / 3);
        return -(float) Math.pow(2, 10 * t - 10) * (float) Math.sin((t * 10 - 10.75f) * c4);
    };
    /** Elastic deceleration. */
    Easing ELASTIC_OUT = t -> {
        if (t == 0 || t == 1) {
            return t;
        }
        float c4 = (float) (2 * Math.PI / 3);
        return (float) Math.pow(2, -10 * t) * (float) Math.sin((t * 10 - 0.75f) * c4) + 1;
    };
    /** Elastic acceleration then deceleration. */
    Easing ELASTIC_IN_OUT = t -> {
        if (t == 0 || t == 1) {
            return t;
        }
        float c5 = (float) (2 * Math.PI / 4.5);
        return t < 0.5f
                ? -((float) Math.pow(2, 20 * t - 10) * (float) Math.sin((20 * t - 11.125f) * c5)) / 2
                : ((float) Math.pow(2, -20 * t + 10) * (float) Math.sin((20 * t - 11.125f) * c5)) / 2 + 1;
    };

    /** Bounce acceleration. */
    Easing BOUNCE_IN = t -> 1 - bounceOut(1 - t);
    /** Bounce deceleration. */
    Easing BOUNCE_OUT = Easing::bounceOut;
    /** Bounce acceleration then deceleration. */
    Easing BOUNCE_IN_OUT = t -> t < 0.5f
            ? (1 - bounceOut(1 - 2 * t)) / 2
            : (1 + bounceOut(2 * t - 1)) / 2;

    /** A step that jumps to the end value immediately. */
    Easing STEP_START = t -> t > 0 ? 1 : 0;
    /** A step that stays at the start value until the end. */
    Easing STEP_END = t -> t >= 1 ? 1 : 0;

    /**
     * Applies the curve to normalized progress.
     *
     * @param t progress in {@code [0, 1]}
     * @return the eased progress
     */
    float apply(float t);

    /**
     * Creates a custom cubic Bezier easing curve with fixed endpoints {@code (0, 0)} and {@code (1, 1)}.
     * Control point X coordinates must be in {@code [0, 1]}; Y coordinates may overshoot to create
     * anticipation or overshoot effects.
     *
     * @param x1 first control point X
     * @param y1 first control point Y
     * @param x2 second control point X
     * @param y2 second control point Y
     * @return the custom easing curve
     * @throws IllegalArgumentException if a coordinate is non-finite or an X coordinate is outside
     *                                  {@code [0, 1]}
     */
    static Easing cubicBezier(float x1, float y1, float x2, float y2) {
        if (!Float.isFinite(x1) || !Float.isFinite(y1)
                || !Float.isFinite(x2) || !Float.isFinite(y2)) {
            throw new IllegalArgumentException("Bezier control points must be finite.");
        }
        if (x1 < 0 || x1 > 1 || x2 < 0 || x2 > 1) {
            throw new IllegalArgumentException("Bezier control point X must be in [0, 1].");
        }
        return t -> cubicBezierValue(t, x1, y1, x2, y2);
    }

    private static float cubicBezierValue(float x, float x1, float y1, float x2, float y2) {
        if (x <= 0 || x >= 1) {
            return x;
        }
        float t = solveForX(x, x1, x2);
        float inverse = 1 - t;
        return 3 * inverse * inverse * t * y1 + 3 * inverse * t * t * y2 + t * t * t;
    }

    private static float solveForX(float x, float x1, float x2) {
        float t = x;
        for (int iteration = 0; iteration < 8; iteration++) {
            float error = bezierCoordinate(t, x1, x2) - x;
            if (Math.abs(error) < 0.0001f) {
                return t;
            }
            float derivative = bezierDerivative(t, x1, x2);
            if (Math.abs(derivative) < 0.0001f) {
                break;
            }
            t -= error / derivative;
            if (t < 0 || t > 1) {
                break;
            }
        }
        float low = 0;
        float high = 1;
        t = x;
        for (int iteration = 0; iteration < 16; iteration++) {
            float value = bezierCoordinate(t, x1, x2);
            if (Math.abs(value - x) < 0.0001f) {
                break;
            }
            if (value < x) {
                low = t;
            } else {
                high = t;
            }
            t = (low + high) / 2;
        }
        return t;
    }

    private static float bezierCoordinate(float t, float first, float second) {
        float inverse = 1 - t;
        return 3 * inverse * inverse * t * first + 3 * inverse * t * t * second + t * t * t;
    }

    private static float bezierDerivative(float t, float first, float second) {
        float inverse = 1 - t;
        return 3 * inverse * inverse * first
                + 6 * inverse * t * (second - first)
                + 3 * t * t * (1 - second);
    }

    private static float bounceOut(float t) {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (t < 1 / d1) {
            return n1 * t * t;
        }
        if (t < 2 / d1) {
            t -= 1.5f / d1;
            return n1 * t * t + 0.75f;
        }
        if (t < 2.5f / d1) {
            t -= 2.25f / d1;
            return n1 * t * t + 0.9375f;
        }
        t -= 2.625f / d1;
        return n1 * t * t + 0.984375f;
    }
}
