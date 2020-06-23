package transapps.maps.plugin.layer;

import java.io.Serializable;
import transapps.maps.plugin.Describable;



/**
 * Represents a layer item within a list of layers. This
 * is just enough info to display the layer option in a
 * list. This is the interface used as the extension point
 * through which layers may be provided to the map.
 * 
 * @author mriley
 * @see LayerDescriptors
 * @see Layer
 * @deprecated in NW SDK 1.0.34 use {@link MapLayerDescriptor} instead
 */
public interface LayerDescriptor extends Describable, Serializable {
    
    /**
     * @return The layer for this layer
     */
    Layer getLayer();
}
