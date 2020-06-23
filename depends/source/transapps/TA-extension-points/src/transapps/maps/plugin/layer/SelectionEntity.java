package transapps.maps.plugin.layer;

import transapps.geom.GeoPoint;


/**
 * This object represents an item on the map that has a location and identifier
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public interface SelectionEntity extends SelectionItem
{
    /**
     * This needs to return the location of the selection item which should never return null
     * 
     * @return The location of the item on the map
     */
    public GeoPoint getLocation( );

    /**
     * This is the unique (based on the data type) identifier for the item
     * 
     * @return The unique identifier for the data type
     */
    public String getUid( );

    /**
     * This is a string that tells the data type the item is. A common usage is to use the
     * class.getName() method on the DAO object the item comes from.
     * 
     * @return A data type of the item
     */
    public String getDataType( );
}
