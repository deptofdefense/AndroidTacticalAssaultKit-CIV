package com.atakmap.map.layer.feature;

/**
 * The definition of a feature. Feature properties may be recorded as raw,
 * unprocessed data of several well-defined types. Utilization of
 * unprocessed data may yield significant a performance advantage depending
 * on the intended storage.
 *
 * @author Developer
 */
public interface FeatureDefinition3 extends FeatureDefinition2 {
    /**
     * Returns the AltitudeMode associated with the feature.
     *
     * @return
     */
    public Feature.AltitudeMode getAltitudeMode();

    /**
     * Returns the Extrude value associated with the feature.
     *
     * @return
     */
    public double getExtrude();

} // FeatureDefinition3
