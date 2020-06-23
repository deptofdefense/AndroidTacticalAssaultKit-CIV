package transapps.maps.plugin.layer;

import java.util.Collections;
import java.util.List;

import transapps.geom.BoundingBoxE6;
import transapps.mapi.MapView;
import android.app.Activity;
import android.graphics.Canvas;
import android.view.ViewGroup;


/**
 * Similar to the osmdroid Overlay, except this guy is restricted to only a few specific things.
 * 
 * @author mriley
 * @deprecated in NW SDK 1.0.34 use {@link MapLayer} instead
 */
public abstract class Layer {
    
    /**
     * Activate this layer.  The descriptor is provided in case this layer represents multiple.
     */
    public void onEnabled( LayerDescriptor descriptor ) { }
    
    /**
     * Deactivate this layer.  The descriptor is provided in case this layer represents multiple.
     */
    public void onDisabled( LayerDescriptor descriptor ) { }
    
    /**
     * Get the items at the spot.  Maps will collect these from all of the layers and display a list
     * to the user, if needed.  Once an item is selected from the list the selection will be passed 
     * to the appropriate layer via {@link Layer#onSelected(SelectionItem)}.  <b>This means that you 
     * must override {@link Layer#onSelected(SelectionItem)} if you override this</b>.
     * 
     * @param selectedSpot the selected spot
     * @param mapView the mapView
     * @return the selected items
     */
    public List<? extends SelectionItem> getSelection(BoundingBoxE6 selectedSpot, MapView mapView) {
        return Collections.emptyList();
    }
    
    /**
     * When an item is selected from the list, the said item came from this layer the item will be 
     * passed back to the layer to handle the selection
     * 
     * @param item the item that you provided and was selected
     */
    public void onSelected(SelectionItem item, Activity activity, ViewGroup viewRoot) {
        throw new UnsupportedOperationException("If you implement getSelection, you gotta implement onSelected!");
    }
    
    /**
     * When a layer item is longPressed SelectionController passes the 
     * item back to the layer for management of the long press.  
     * @param item
     * @param activity
     * @param viewRoot
     */
    public void onLongPressSelected(SelectionItem item, Activity activity,ViewGroup viewRoot) {
    }
    /**
     * Actually draw the layer on the map
     * 
     * @param c the canvas
     * @param osmv the map view
     */
    public abstract void draw(final Canvas c, final MapView osmv);
    
    /**
     * This specifies if the layer is very important and should always be on top of other layers.
     * This value is false by default and should only return true for layers which are very
     * important for users to see on the map such as the user's current position. This method also
     * needs to return a constant value since having the method change the value returned by this
     * method at runtime could result in unexpected behavior with the ordering of the layer.<br/>
     * <br/>
     * When a layer specifies that it is a top layer it gains two specific features compared to the
     * non-top layers.<br/>
     * 1. The layer will be ordered with other top layers which will always be above non-top layers.
     * This also means that if there are more than one top layer a top layer might not be on top of
     * all other layers.<br/>
     * 2. The user will not be able to turn off the visibility of the layer. Also the layer
     * visibility can be turned on at any time even if the plugin specifically turns the off the
     * visibility of the layer.<br/>
     * <br/>
     * <b>Note:</b> Having a layer return true could result in a plugin being rejected if it is
     * decided that the layer is not important enough to be the top most layer.
     * 
     * @return <code>false</code> if it is a normal layer and <code>true</code> if the layer is very
     *         important to the user and needs to be one of the top most layers.
     */
    public boolean isTopLayer(){
        return false;
    }
}
