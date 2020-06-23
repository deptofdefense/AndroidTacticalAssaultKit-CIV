package transapps.maps.plugin.layer.search;

import java.util.ArrayList;
import java.util.List;
import transapps.geom.BoundingBoxE6;
import transapps.mapi.MapView;
import transapps.maps.plugin.layer.model.LayerModel;


/**
 * This is a simple object which provides parameters for querying the items in a {@link LayerModel}.
 * To create this class you will need to make use of the {@link Builder} object.
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public class QueryParams
{
    /**
     * This provides the bounding box where items need to at least partially be inside. If this is
     * null the location of the item should not be taken into account for searches.
     */
    public final BoundingBoxE6 searchArea;

    /**
     * This is the map view the query is taking place on
     */
    public final MapView mapView;

    /**
     * This stores the list of search criteria for the query
     */
    private List<SearchCriteria> searchCriteria = new ArrayList<SearchCriteria>( );

    private QueryParams( Builder builder )
    {
        mapView = builder.mapView;
        if( builder.searchArea != null )
        {
            searchArea = new BoundingBoxE6( );
            searchArea.setCoords( builder.searchArea.getLeft( ), builder.searchArea.getTop( ),
                builder.searchArea.getRight( ), builder.searchArea.getBottom( ) );
        }
        else
        {
            searchArea = null;
        }
        searchCriteria.addAll( builder.searchCriteria );
    }

    /**
     * This checks to see if the params have any specific search criteria
     * 
     * @return true if there is at least one search criteria connected to the params
     */
    public boolean hasSearchCriteria( )
    {
        return ( searchCriteria.size( ) > 0 );
    }

    /**
     * This will return the number of specific search criteria which is included in the query
     * 
     * @return The number of search criteria which are part of the query
     */
    public int getSearchCriteriaSize( )
    {
        return searchCriteria.size( );
    }

    /**
     * This will return the specific search criteria for the query under the given index
     * 
     * @param index
     *            The index of the search criteria to return which needs to be in the range [0,
     *            {@link #getSearchCriteriaSize()}
     * @return The search criteria or null if the index is out of the range
     */
    public SearchCriteria getSearchCriteria( int index )
    {
        SearchCriteria criteria = null;

        if( ( 0 <= index ) && ( index < searchCriteria.size( ) ) )
        {
            criteria = searchCriteria.get( index );
        }

        return criteria;
    }

    /**
     * This will return a list of all the search criteria which are part of the query
     * 
     * @return A list of all the queries search criteria
     */
    public List<SearchCriteria> getSearchCriteria( )
    {
        return new ArrayList<SearchCriteria>( searchCriteria );
    }

    /**
     * This is a builder object which needs to be used to create a {@link QueryParams} object.
     * 
     * @author SRA
     * @since NW SDK 1.0.34
     */
    public static class Builder
    {
        /**
         * This provides the bounding box where items need to at least partially be inside. If this
         * is null the location of the item should not be taken into account for searches.
         */
        private BoundingBoxE6 searchArea = null;

        /**
         * This is the map view the query is taking place on
         */
        private MapView mapView = null;

        /**
         * This stores the list of search criteria to be included in the query params
         */
        private List<SearchCriteria> searchCriteria = new ArrayList<SearchCriteria>( );

        /**
         * This will set the bounding box where items need to at least partially be inside. If this
         * is null the location of the item should not be taken into account for searches. The
         * search area will be null by default.
         * 
         * @param searchArea
         *            The bounding box for the search area.
         */
        public void setSearchArea( BoundingBoxE6 searchArea )
        {
            this.searchArea = searchArea;
        }

        /**
         * This is the map view the query is taking place on
         * 
         * @param mapView
         *            The map view of the search
         */
        public void setMapView( MapView mapView )
        {
            this.mapView = mapView;
        }

        /**
         * This will add specific search criteria to be included with the query
         * 
         * @param searchCriteria
         *            The search criteria to add
         */
        public void addSearchCriteria( SearchCriteria searchCriteria )
        {
            if( searchCriteria != null )
            {
                this.searchCriteria.add( searchCriteria );
            }
        }

        /**
         * This will create a new instance of the {@link QueryParams} with the current state of the
         * build object
         * 
         * @return The instance of the {@link QueryParams} which was just built
         */
        public QueryParams build( )
        {
            return new QueryParams( this );
        }
    }
}
