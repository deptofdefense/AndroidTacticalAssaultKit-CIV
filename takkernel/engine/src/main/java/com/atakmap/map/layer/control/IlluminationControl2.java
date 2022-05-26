package com.atakmap.map.layer.control;

import com.atakmap.map.MapControl;

public interface IlluminationControl2 extends MapControl {
    /**
     * Check the global status of the time to use for illumination.
     * @return the time to use for illumination as a long from epoch.
     */
    long getTime();

    /**
     * Sets the time to use for illumination.
     * @param millis  the time to use for illumination from epoch.
     */
    void setTime(long millis);

    /**
     * Enable or disable sun/moon illumination calculations.
     * When disabled, illumination calculation will assume that light source angle
     * is coincident with the normal of the tangent plane at the scene focus point.
     *
     * @param enabled Boolean value indicating whether or not illumination calculations are enabled.
     */
    void setEnabled(boolean enabled);

    /**
     * Enabled status of the sun/moon illumination calculations.
     *
     * @return Boolean value indicating whether or not illumination calculations are enabled.
     */
    boolean getEnabled();
}
