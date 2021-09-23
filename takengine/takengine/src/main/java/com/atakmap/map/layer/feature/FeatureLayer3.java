package com.atakmap.map.layer.feature;

import com.atakmap.map.layer.AbstractLayer;

/**
 * {@link com.atakmap.map.layer.Layer Layer} subinterface for feature (point and
 * vector) data.
 * 
 * <H2>Associated Extensions</H2>
 * 
 * <H2>Associated Controls</H2>
 * 
 * <UL>
 *   <LI>{@link com.atakmap.map.layer.feature.services.FeatureHitTestControl FeatureHitTestControl} - Provides hit-testing mechanism</LI>
 * <UL>
 *  
 * @author Developer
 */
public final class FeatureLayer3 extends AbstractLayer {

    /**************************************************************************/
    
    protected FeatureDataStore2 dataStore;

    public FeatureLayer3(String name, FeatureDataStore2 dataStore) {
        super(name);
        
        this.dataStore = dataStore;
    }

    /**
     * Returns the {@link FeatureDataStore} that contains this layer's content.
     *
     * @return  The {@link FeatureDataStore} that contains this layer's content.
     */
    public FeatureDataStore2 getDataStore() {
        return this.dataStore;
    }

} // FeatureLayer2
