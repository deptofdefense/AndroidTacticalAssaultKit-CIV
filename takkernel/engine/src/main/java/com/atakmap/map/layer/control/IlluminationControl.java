package com.atakmap.map.layer.control;

import com.atakmap.map.MapControl;

import java.util.Calendar;

import gov.tak.api.annotation.DeprecatedApi;

/**
 * See IlluminationControl2
 */
@DeprecatedApi(since="20.0",forRemoval = true)
@Deprecated
public interface IlluminationControl extends MapControl {
    /**
     * Check the global status of the simulated date and time to use for illumination.
     * @return the simulated date and time to use for illumination.
     * @see IlluminationControl2#getTime()
     */
    Calendar getSimulatedDateTime();

    /**
     * Sets the simulated date and time to use for illumination.
     * @param d  the simluated date and time to use for illumination.
     * @see IlluminationControl2#setTime(long)
     */
    void setSimulatedDateTime(Calendar d);

    /**
     * Enable or disable sun/moon illumination calculations.
     * When disabled, illumination calculation will assume that light source angle
     * is coincident with the normal of the tangent plane at the scene focus point.
     *
     * @param enabled Boolean value indicating whether or not illumination calculations are enabled.
     * @see IlluminationControl2#setEnabled(boolean)
     */
    void setEnabled(boolean enabled);

    /**
     * Enabled status of the sun/moon illumination calculations.
     *
     * @return Boolean value indicating whether or not illumination calculations are enabled.
     * @see IlluminationControl2#getEnabled()
     */
    boolean getEnabled();
}
