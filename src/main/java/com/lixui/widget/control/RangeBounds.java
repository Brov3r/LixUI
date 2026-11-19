package com.lixui.widget.control;

/**
 * Shared minimum, maximum and step state for slider controls.
 */
final class RangeBounds {
    private final String stepName;
    private float minimum;
    private float maximum = 1;
    private float step;

    /**
     * Creates an empty range over {@code [0, 1]}.
     *
     * @param stepName control name used in step validation messages
     */
    RangeBounds(String stepName) {
        this.stepName = stepName;
    }

    /**
     * Returns the range start.
     *
     * @return the minimum value
     */
    float minimum() {
        return minimum;
    }

    /**
     * Returns the range end.
     *
     * @return the maximum value
     */
    float maximum() {
        return maximum;
    }

    /**
     * Returns the step size.
     *
     * @return the step size, or zero for a continuous range
     */
    float step() {
        return step;
    }

    /**
     * Replaces the range bounds.
     *
     * @param newMinimum range start
     * @param newMaximum range end
     */
    void setRange(float newMinimum, float newMaximum) {
        SliderSupport.validateRange(newMinimum, newMaximum);
        minimum = newMinimum;
        maximum = newMaximum;
    }

    /**
     * Replaces the step size.
     *
     * @param newStep non-negative step size
     */
    void setStep(float newStep) {
        if (!Float.isFinite(newStep) || newStep < 0) {
            throw new IllegalArgumentException(
                    stepName + " step must be finite and non-negative.");
        }
        step = newStep;
    }

    /**
     * Clamps and snaps a value to the configured range and step grid.
     *
     * @param value value to quantize
     * @return the quantized value
     */
    float quantize(float value) {
        return SliderSupport.quantize(value, minimum, maximum, step);
    }
}
