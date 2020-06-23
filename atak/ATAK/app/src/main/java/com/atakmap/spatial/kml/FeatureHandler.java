
package com.atakmap.spatial.kml;

import com.ekito.simpleKML.model.Feature;

/**
 * Interface to process a KML Feature, see KMLUtil.deepFeatures()
 * 
 * 
 * @param <T>
 */
public interface FeatureHandler<T extends Feature> {

    /**
     * Return true to stop processing
     * 
     * @param feature the featue to process
     * @return true if the feature was processed successfully
     */
    boolean process(T feature);
}
