
package com.atakmap.android.routes.animations;

public abstract class SpeedInterpolationProvider implements
        MapWidgetAnimationInterpolatorInterface {

    //-------------------- Fields and Properties ---------------------------
    private static final float epsilon = (float) 1E-6;

    private final float speed;
    private final float to;
    private long lastReceivedTime = 0;
    private final boolean isComplete = false;

    //-------------------- CTOR ---------------------------

    public SpeedInterpolationProvider(float to, float speed) {
        this.to = to;
        this.speed = speed;
    }

    //-------------------- Abstract Members ---------------------------

    /**
     * Obtains the current value as a floating point number that represents an interpolation of the
     * current speed.
     * @return the current value.
     */
    public abstract float getCurrentValue();

    /**
     * Allows for a new value to be added into the interpolation provider to be considered.
     * @param newValue the value to add in for consideration.
     */
    public abstract void setNewValue(float newValue);

    //-------------------- Interface Implementation ---------------------------

    @Override
    public boolean interpolate(long timeInMs) {

        if (isComplete)
            return false;

        float currentValue = getCurrentValue();

        if (isClose(to, currentValue)) {
            //We're finished so make sure we have the right to value set and return false.
            setNewValue(to);
            return false;
        }

        long elapsedTime = timeInMs - lastReceivedTime;
        int direction = currentValue < to ? 1 : -1;
        float magnitude = elapsedTime * speed;
        float newValue = currentValue + (direction * magnitude);

        //Make sure we don't overshoot
        if (to < currentValue) {
            newValue = Math.max(newValue, to);
        } else {
            newValue = Math.min(newValue, to);
        }

        setNewValue(newValue);

        lastReceivedTime = timeInMs;
        return !isClose(to, newValue);
    }

    protected boolean isClose(float value1, float value2) {
        return value1 == value2 ||
                Math.abs(value1 - value2) <= epsilon;
    }
}
