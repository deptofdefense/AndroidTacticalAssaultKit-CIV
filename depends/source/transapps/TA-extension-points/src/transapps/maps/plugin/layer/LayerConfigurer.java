package transapps.maps.plugin.layer;


import transapps.mapi.MapView;
import transapps.maps.plugin.tool.Tool;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;


/**
 * This thing can configure a layer
 * 
 * @author mriley
 * @deprecated in NW SDK 1.1.6.3 use
 *         {@link mil.army.nettwarrior.core.map.plugin.MapPreferenceDescriptor} instead
 */
public abstract class LayerConfigurer extends Tool {
    
    public static final String EXTRA_LAYER_DESCRIPTOR = "EXTRA_LAYER_DESCRIPTOR"; 
    
    @Override
    public void onActivate(Activity activity, MapView mapView, ViewGroup viewRoot, Bundle extras, ToolCallback callback) {
        
        LayerDescriptor desc = (LayerDescriptor) extras.getSerializable(EXTRA_LAYER_DESCRIPTOR);
        if( desc == null ) {
            throw new IllegalArgumentException("Expected to find the layer descriptor in the extras!");
        }
            
        onShowView(mapView, viewRoot, desc);
    }

    /**
     * Method used by the Maps application in order to find the correct LayerConfigurer to handle an Intent
     * received by Maps.  Because LayerConfigurers are Tools, Maps just go through the list of Tools and calls
     * isToolFor. This method forwards those calls to isConfigurerFor which should be overriden by the implementation
     * class for any Configurers that want to handle intents.
     *
     * @param intent the intent received by Maps that it is looking for a handler to process
     *
     * @return boolean Return true to tell maps that this is the appropriate configurer for this
     */
    @Override
    public boolean isToolFor(Intent intent) {
        Bundle extras = intent.getExtras();
        if( extras == null ) {
            return false;
        }
        LayerDescriptor desc = (LayerDescriptor) extras.getSerializable(EXTRA_LAYER_DESCRIPTOR);
        if( desc == null ) {
            return false;
        }
    
        return isConfigurerFor(desc);
    }
    
    /**
     * Return true to tell maps that this is the appropriate configurer for this
     * 
     * @param descriptor
     * @return
     */
    public abstract boolean isConfigurerFor( LayerDescriptor descriptor );

    /**
     * Show your configure view within the given parent view and configure the layer
     * 
     * @param parentView
     * @param descriptor
     */
    public abstract void onShowView( MapView mapView, ViewGroup parentView, LayerDescriptor descriptor );
}
