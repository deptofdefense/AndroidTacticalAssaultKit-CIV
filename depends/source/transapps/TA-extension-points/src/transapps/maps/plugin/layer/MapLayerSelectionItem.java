package transapps.maps.plugin.layer;

import java.util.List;
import transapps.geom.GeoPoint;


/**
 * This object represents an item which is selectable on a map layer.
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public interface MapLayerSelectionItem extends SelectionEntity
{
    /**
     * This is the name of the meta data to tell if an item is tetherable or not. The data needs to
     * be a Boolean where null will be interpreted as false (not tetherable)
     */
    public static final String TETHERABLE = "Tetherable";

    /**
     * This is the name of the meta data to tell if an item is selected/highlighted on the map. The
     * data needs to be a Boolean where null will be interpreted as false (not selected)
     */
    public static final String SELECTED = "Selected";

    /**
     * This is the name of the meta data to provide access to all the points that make up the item.
     * The data needs to be a {@link List} of {@link GeoPoint} objects.
     */
    public static final String POINTS = "Points";

    /**
     * This is the name of the meta data to provide access to the geographic bounding box of the
     * item. The data needs to be a {@link transapps.geom.BoundingBoxE6} object.
     *
     * @since NW SDK 1.1.15.4
     */
    public static final String BOUNDS = "Bounds";

    /**
     * This is the name of the meta data that should be used to provide access to the activity
     * context. The data needs to be a {@link android.content.Context} object which needs to be
     * the activity context opposed to a plugin or application context.<br/>
     * Please note that storing a context in a selection item could cause memory leaks and should
     * only be done with care.
     *
     * @since NW SDK 1.1.15.4
     */
    public static final String ACTIVITY = "Activity";

    /**
     * This is the meta data name for providing a visibility value for the item. The
     * value needs to be a Boolean where true means visible and false means hidden.
     * If this value is not provided it is assumed the specific item can not be
     * specifically hidden from the map view.
     *
     * @since NW SDK 1.1.15.12
     */
    public static final String VISIBLE = "Visible";

    /**
     * This is the meta data name for accessing an array of unique identifiers for the path.
     * This is used with the {@link #NAVIGATE_PATH_MODELS} meta data which defines the class name
     * {@link Class#getName()} of the model that owns the item.
     * With the {@link #NAVIGATE_PATH_UIDS} and {@link #NAVIGATE_PATH_MODELS} meta data, if
     * both exist and are valid then navigation can exist between the items in the list
     * going from the first item in the array to the last.<br/>
     * The data type needs to be a {@link String[]}
     *
     * @since NW SDK 1.1.15.19
     */
    public static final String NAVIGATE_PATH_UIDS = "NavigatePathUIDs";

    /**
     * This is the meta data name for the class name of the model owner of the unique identifiers
     * provided in the {@link #NAVIGATE_PATH_UIDS} meta data. If an array is provided it must
     * be the same size as the UID array where the index in one array matches the item of the
     * same index in the other array. If a String (an not an array) is provided then it means that
     * all the UIDs share the same model.<br/>
     * The data type needs to be a {@link String}[] or just a {@link String}
     *
     * @since NW SDK 1.1.15.19
     */
    public static final String NAVIGATE_PATH_MODELS = "NavigatePathModels";

    /**
     * This checks to see if the item has meta data for the given name
     * 
     * @param dataName
     *            The name of the meta data to check
     * @return true if the item has meta data false if it does not
     */
    public boolean hasMetaData( String dataName );

    /**
     * This will get the meta data for the given name
     * 
     * @param dataName
     *            The name of the meta data to get
     * @return The meta data for the given key or null if no meta data exist for the key
     */
    public Object getMetaData( String dataName );

    /**
     * This will set the meta data for the given name
     * 
     * @param dataName
     *            The name of the meta data to set
     * @param data
     *            The meta data or null to remove meta data which was previously stored
     * @return true if the meta data was updated, the meta data might not be updated for a few
     *         reasons which include the case where the data is read only
     */
    public boolean setMetaData( String dataName, Object data );

    /**
     * This method checks to see if the specific meta data is read only and thus can not be set from
     * an external source using the {@link #setMetaData(String, Object)} method.
     * 
     * @param dataName
     *            The name of the meta data to check
     * @return true if the meta data is read only
     */
    public boolean isMetaDataReadOnly( String dataName );

    /**
     * This gets the type of item this object represents
     * 
     * @return The best match for the type of the item
     * @see ItemType
     */
    public ItemType getItemType( );

    /**
     * This enumeration defines the type of the item and how it is represented on the map
     * 
     * @author SRA
     * @since NW SDK 1.0.34
     */
    public static enum ItemType
    {
        /**
         * The item is represented by a single point and most likely is shown as an icon on the map
         */
        POINT,

        /**
         * The item represents a set of points which generally connect to each other to form a line
         * or path
         */
        LINES,

        /**
         * The item represents a set of points which define an area or region on the map
         */
        AREA,

        /**
         * The item represents a few points which define a symbol which doesn't fall into the
         * {@link #LINES} or {@link #AREA} definition. An example of this type would be something
         * like the 2525 "Attack by Fire" symbol.
         */
        DRAWING;
    }
}
