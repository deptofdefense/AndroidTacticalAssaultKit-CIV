package transapps.maps.plugin.layer.search;

import transapps.maps.plugin.layer.MapLayerSelectionItem;


/**
 * This search criteria is used to make sure the item is visible by using the
 * {@link MapLayerSelectionItem#VISIBLE} value were it is considered hidden if the
 * value is a Boolean equal to false otherwise it is treated as visible
 *
 * @since NW SDK 1.1.15.12
 */
public class VisibleSearchCriteria implements SearchCriteria
{
    /**
     * {@inheritDoc}
     */
    @Override
    public int scoreMapItem( MapLayerSelectionItem item, boolean exact )
    {
        int score = 1;

        Object visible = item.getMetaData( MapLayerSelectionItem.VISIBLE );
        if( ( visible instanceof Boolean ) && ( ( (Boolean) visible ) == false ) )
        {
            score = 0;
        }

        return score;
    }
}
