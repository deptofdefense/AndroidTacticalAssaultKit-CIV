package transapps.maps.plugin.layer.search;

import transapps.maps.plugin.layer.MapLayerSelectionItem;


/**
 * This interface defines the ability for a class to search {@link MapLayerSelectionItem} objects
 * based on some underlining search criteria
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public interface SearchCriteria
{
    /**
     * This method needs to provide a score for the map item based on how it matches the search
     * criteria
     * 
     * @param mapItem
     *            The map item to provide a score of
     * @param fullScore
     *            true if a full score needs to be computed, if false a simple 0 and 1 can be
     *            returned to tell if the item meets the search criteria or not
     * @return The score of 0 or less is used for no match, a positive number represents a passing
     *         score where the higher scores are better
     */
    public int scoreMapItem( MapLayerSelectionItem mapItem, boolean fullScore );
}
