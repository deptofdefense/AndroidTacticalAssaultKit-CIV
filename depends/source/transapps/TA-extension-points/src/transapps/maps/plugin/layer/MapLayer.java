package transapps.maps.plugin.layer;


import android.app.Activity;
import mil.army.nettwarrior.core.actions.Action;
import transapps.mapi.MapView;
import transapps.mapi.canvas.MapCanvasDrawParams;

import java.util.List;


/**
 * This is the object which is used to draw items on the map using the native and optimized drawing
 * functionality
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 * @see MapLayerDescriptor
 */
public abstract class MapLayer
{
    /**
     * This is called when the layer needs to draw itself on the map. This method will be called
     * every frame and the layer needs to continue drawing an item for that item to continue to be
     * shown on the map (assuming that map location it is being drawn to is visible).
     * 
     * @param drawParams
     *            An object which contains a collection of other objects and useful values and
     *            methods which should be used for drawing the contents of the layer
     */
    public abstract void draw( final MapCanvasDrawParams drawParams );

    /**
     * This method will be called when the layer has been attached to the map view
     * 
     * @param mapView
     *            The map view the overlay is being attached to
     */
    public void onAttach( final MapView mapView )
    {
    }

    /**
     * This method will be called when the layer has been detached from the map view.
     * 
     * @param mapView
     *            The map view the overlay is being detached from
     */
    public void onDetach( final MapView mapView )
    {
    }

    /**
     * This method will be called when the map is being shut down. This method is only called if
     * {@link #onAttach(MapView)} has been called at least once. This should be used to free data
     * that needs to be freed and is too expensive to allocate every time the layer is attached.
     */
    public void onDestroy( )
    {
    }

    /**
     * This will be called when the item has been selected on the map or through a list. It will
     * need to return the list of actions which should be shown in the context menu for this item.
     * 
     * @param item
     *            The item from the layer's model which the selection items need to be returned for
     * @param activity
     *            The map activity
     * @return A list of actions which should be available on selection of the item
     */
    public abstract List<Action> getSelectionActions( MapLayerSelectionItem item, Activity activity );
}
