package com.atakmap.map.layer.feature;

/**
 * The definition of a feature. Feature properties may be recorded as raw,
 * unprocessed data of several well-defined types. Utilization of
 * unprocessed data may yield significant a performance advantage depending
 * on the intended storage. 
 *  
 * @author Developer
 */
public interface FeatureDefinition2 extends FeatureDefinition {
    /**
     * Returns the timestamp associated with the feature, expressed as Epoch
     * milliseconds. If no timestamp is associated, a value of
     * {@link FeatureDataStore2#TIMESTAMP_NONE} shall be returned.
     * 
     * @return
     */
    public long getTimestamp();    
} // FeatureDefinition
