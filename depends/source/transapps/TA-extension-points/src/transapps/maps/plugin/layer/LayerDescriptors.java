package transapps.maps.plugin.layer;

import java.util.List;


/**
 * An extension point that allows an extension to provide multiple, dynamic layers.
 * 
 * @author mriley
 * @see LayerDescriptor
 * @see Layer
 * @deprecated In NW SDK 1.0.34, Use multiple {@link MapLayerDescriptor} instances instead
 */
public interface LayerDescriptors {

    /**
     * @return The list of {@link LayerDescriptor}s
     */
    List<LayerDescriptor> getLayerDescriptors();
}
