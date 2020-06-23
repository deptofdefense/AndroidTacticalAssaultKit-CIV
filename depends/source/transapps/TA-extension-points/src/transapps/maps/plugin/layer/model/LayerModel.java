package transapps.maps.plugin.layer.model;

import java.util.List;
import transapps.maps.plugin.layer.MapLayer;
import transapps.maps.plugin.layer.MapLayerSelectionItem;
import transapps.maps.plugin.layer.search.ModelSearcher;


/**
 * This object represents a model which provides access to the items which are shown on a
 * {@link MapLayer}.
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public interface LayerModel
{
    /**
     * This returns a string that tells the data type of the item. A common usage is to use the
     * class.getName() method on the DAO object the item comes from.
     * 
     * @see MapLayerSelectionItem#getDataType()
     * @return
     *         The data type of the map selection item
     */
    public String getDataType( );

    /**
     * Gets a copy of the list of all map items for the map model.
     * 
     * @return A copy of the list of all map items for the map model.
     */
    public List<? extends MapLayerSelectionItem> getAllItemList( );

    /**
     * This will return the object that should be used for searching the model. This allows a
     * special implementation of the model searcher to be used if needed. Otherwise any instance of
     * the ModelSearcher object can be returned. In the case that null is returned the base
     * the default instance of the ModelSearcher will be used for the searching.
     * 
     * @return The specific model searcher object that should be used or null if the default
     *         instance of the ModelSearcher should be used
     */
    public ModelSearcher getModelSearcher( );

    /**
     * This adds a layer model listener to the layer model
     * 
     * @param listener
     *            The listener to add
     */
    public void addLayerModelListener( LayerModelListener listener );

    /**
     * This removes the model listener from the layer model
     * 
     * @param listener
     *            The listener to remove
     */
    public void removeLayerModelListener( LayerModelListener listener );
}
