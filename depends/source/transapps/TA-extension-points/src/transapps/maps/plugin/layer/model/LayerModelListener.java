package transapps.maps.plugin.layer.model;

import java.util.List;
import transapps.maps.plugin.layer.MapLayerSelectionItem;


/**
 * This is the listener for changes to the {@link LayerModel}.
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public interface LayerModelListener
{
    /**
     * General alert that the model has changed.
     * 
     * @param items
     *            The full list of MapLayerSelectionItems in the model
     * @param model
     *            The model which has changed
     */
    public void onModelChanged( List<MapLayerSelectionItem> items, LayerModel model );

    /**
     * General alert that a map item in the model has changed.
     * 
     * @param item
     *            The only item that has changed in the model.
     * @param model
     *            The model which the item was added or updated in
     */
    public void onMapItemChanged( MapLayerSelectionItem item, LayerModel model );

    /**
     * General alert that a map item in the model has been removed. This is different than
     * onMapItemChanged because we need to know that it was deleted and not updated
     * 
     * @param item
     *            The item that was removed from the model
     * @param model
     *            The model which the item was removed from
     */
    public void onMapItemRemoved( MapLayerSelectionItem item, LayerModel model );
}
