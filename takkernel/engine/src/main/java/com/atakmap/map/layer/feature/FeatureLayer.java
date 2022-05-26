package com.atakmap.map.layer.feature;

import java.util.LinkedList;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.layer.AbstractLayer;

/**
 * {@link com.atakmap.map.layer.Layer Layer} subinterface for feature (point and
 * vector) data.
 * 
 * <H2>Services</H2>
 * 
 * <P>The service may provide optional functions for the layer that are not
 * expressly part of the API. The method,
 * {@link FeatureLayer#getService(Class)}, provides a mechanism for other
 * users to acquire access to that functionality for a layer.
 * 
 * <P>The service pattern provides layer implementors with the flexibility to
 * distribute well-defined functionality outside of the model domain.
 * Specifically, it can provide a pluggable point for functionality that may be
 * within the domain of the renderer; the application would normally have no
 * means to communicate with the renderer. It also allows for delegation of
 * model domain functionality that can be more efficiently serviced by the
 * renderer. Well-defined service interfaces that are part of the SDK may be
 * found in the <code>com.atakmap.map.layer.feature.service</code> package.
 *  
 * @author Developer
 * 
 * @deprecated use {@link FeatureLayer3}
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class FeatureLayer extends AbstractLayer {

    /**
     * Interface for the concept of a service for the {@link FeatureLayer}. An
     * overview can be found in the class documentation for
     * {@link FeatureLayer}. 
     *  
     * @author Developer
     * 
     * @see com.atakmap.map.layer.feature.FeatureLayer
     * @see com.atakmap.map.layer.feature.FeatureLayer#getService(Class)
     */
    public static interface Service {}

    /**************************************************************************/
    
    protected FeatureDataStore dataStore;
    protected LinkedList<Service> services;

    public FeatureLayer(String name, FeatureDataStore dataStore) {
        super(name);
        
        this.dataStore = dataStore;
        this.services = new LinkedList<Service>();
    }

    /**
     * Returns the {@link FeatureDataStore} that contains this layer's content.
     *
     * @return  The {@link FeatureDataStore} that contains this layer's content.
     */
    public FeatureDataStore getDataStore() {
        return this.dataStore;
    }

    /**
     * This method should generally not be invoked by the application.
     * 
     * <P>Registers the specified service on the layer. {@link FeatureLayer}
     * implementors may use this method to install services on their layer.
     * 
     * @param service   The service to be registered
     */
    public synchronized void registerService(Service service) {
        this.services.addFirst(service);
    }
    
    /**
     * This method should generally not be invoked by the application.
     * 
     * <P>Unregisters the specified service on the layer.
     * 
     * @param service   The service to be registered
     */
    public synchronized void unregisterService(Service service) {
        this.services.remove(service);
    }
    
    /**
     * Returns the specified {@link Service} for this layer.
     * 
     * @param clazz The {@link Service} class
     * 
     * @return  An instance of the specified {@link Service} or
     *          <code>null</code> if no services of the specified type are
     *          available for the layer.
     */
    public synchronized <T extends Service> T getService(Class<T> clazz) {
        for(Service s : this.services) {
            if(clazz.isAssignableFrom(s.getClass()))
                return clazz.cast(s);
        }
        return null;
    }

} // FeatureLayer
