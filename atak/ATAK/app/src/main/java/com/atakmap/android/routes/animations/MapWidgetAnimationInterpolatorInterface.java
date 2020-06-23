
package com.atakmap.android.routes.animations;

public interface MapWidgetAnimationInterpolatorInterface {

    /**
     * Implementation of navigation interpolation.
     * @param timeInMs provides a new time in ms and attempts to compute an interpolated value.
     * @return true if interpolation was performed.
     */
    boolean interpolate(long timeInMs);

}
