package com.atakmap.map.layer.feature;

import com.atakmap.annotations.DeprecatedApi;
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
 * 
 * @deprecated use {@link FeatureLayer3}
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public final class FeatureLayer2 extends AbstractLayer {

    /**************************************************************************/
    
    protected FeatureDataStore dataStore;

    public FeatureLayer2(String name, FeatureDataStore dataStore) {
        super(name);
        
        this.dataStore = dataStore;
    }

    /**
     * Returns the {@link FeatureDataStore} that contains this layer's content.
     *
     * @return  The {@link FeatureDataStore} that contains this layer's content.
     */
    public FeatureDataStore getDataStore() {
        return this.dataStore;
    }

} // FeatureLayer2
