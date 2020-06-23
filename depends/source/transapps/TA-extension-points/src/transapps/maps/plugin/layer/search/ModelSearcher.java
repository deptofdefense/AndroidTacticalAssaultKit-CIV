package transapps.maps.plugin.layer.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import transapps.geom.BoundingBoxE6;
import transapps.geom.GeoPoint;
import transapps.maps.plugin.layer.MapLayerSelectionItem;
import transapps.maps.plugin.layer.MapLayerSelectionItem.ItemType;
import transapps.maps.plugin.layer.model.LayerModel;


/**
 * This object is used to search for items in a {@link LayerModel}. It is used to provide the
 * ability to search for in either a ranked or unranked order map items from any arbitrary model.
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public class ModelSearcher
{

    /**
     * This method needs to return all the layer items which match the search parameters
     * 
     * @param searchParams
     *            The parameters to use in the search
     * @param model
     *            The layer model that should be searched
     * @return A list of all the layer items which match the search parameters
     */
    public List<? extends MapLayerSelectionItem> searchAll( QueryParams searchParams,
                                                            LayerModel model )
    {
        List<MapLayerSelectionItem> items = new ArrayList<MapLayerSelectionItem>( );

        if( model != null )
        {
            List<? extends MapLayerSelectionItem> allItems = model.getAllItemList( );
            if( allItems != null )
            {
                for( MapLayerSelectionItem item : allItems )
                {
                    if( scoreQueryMatch( item, searchParams, false ) > 0 )
                    {
                        items.add( item );
                    }
                }
            }
        }


        return items;
    }

    /**
     * This method will score the given map item
     *
     * @param item
     *             The item to score
     * @param searchParams
     *             The parameters to use in the search
     * @param exactScore
     *             true if the exact score should be computed otherwise 0 and 1 can be used to represent a no
     *             match and match respectively
     * @return The score where zero means it matched nothing in the search params while a positive
     * integer means there was some match and the higher the number is a better score
     * @since NW SDK 1.1.15.12
     */
    public int scoreItem( MapLayerSelectionItem item, QueryParams searchParams, boolean exactScore )
    {
        return scoreQueryMatch( item, searchParams, exactScore );
    }

    /**
     * This method should be overloaded to provide more searching functionality
     * 
     * @param item
     *            The item to score the match with
     * @param searchParams
     *            The parameters to use in the search
     * @param exactScore
     *            true if the exact score should be computed (to support
     *            {@link #searchAndRankAll(QueryParams, LayerModel)} and
     *            {@link #searchForBest(QueryParams, LayerModel)}) otherwise 0 and 1 can be used to represent a no
     *            match and match respectively
     * @return 0 if the item doesn't match the query and a positive integer if it does match where
     *         the higher the number the better the match
     */
    protected int scoreQueryMatch( MapLayerSelectionItem item, QueryParams searchParams,
                                   boolean exactScore )
    {
        int score = scoreAreaMatch( item, searchParams.searchArea, exactScore );

        if( ( score > 0 ) && ( searchParams.hasSearchCriteria( ) ) )
        {
            int size = searchParams.getSearchCriteriaSize( );
            SearchCriteria criteria;
            int criteriaScore;
            for( int index = 0; index < size; index++ )
            {
                criteria = searchParams.getSearchCriteria( index );
                criteriaScore = criteria.scoreMapItem( item, exactScore );
                if( criteriaScore > 0 )
                {
                    score += criteriaScore;
                    if( score < 0 )
                    {
                        score = Integer.MAX_VALUE;
                    }
                }
                else
                {
                    score = 0;
                    break;
                }
            }
        }

        return score;
    }

    /**
     * This method should be overloaded to provide more complex area match scoring functionality.<br/>
     * <br/>
     * By default it will score a match based on the items location where 100 is an exact match to
     * the center of the search area and the score will decrease down to 1 the farther away from the
     * center. 0 will be returned in the case it is not inside the search area
     * 
     * @param item
     *            The item to score the match with
     * @param searchArea
     *            The search area
     * @param exactScore
     *            true if the exact score should be computed (to support
     *            {@link #searchAndRankAll(QueryParams, LayerModel)} and
     *            {@link #searchForBest(QueryParams, LayerModel)}) otherwise 0 and 1 can be used to
     *            represent a no match and match respectively
     * @return 0 if the item is not inside the search area and a positive integer if it is in the
     *         search area where a larger number means it a better match to the search area
     */
    protected int scoreAreaMatch( MapLayerSelectionItem item, BoundingBoxE6 searchArea,
                                  boolean exactScore )
    {
        int score = 0;

        List<?> points = null;
        if( ( searchArea != null ) && ( item.getItemType( ) != ItemType.POINT ) )
        {
            Object pointValue = item.getMetaData( MapLayerSelectionItem.POINTS );
            if( pointValue instanceof List<?> )
            {
                points = (List<?>) pointValue;
            }
        }
        if( points != null )
        {
            int matches = 0;
            int pointScore = 0;
            int pointCount = 0;
            for( Object point : points )
            {
                if( point instanceof GeoPoint )
                {
                    pointCount++;
                    pointScore = scoreSingleLocation( (GeoPoint) point, searchArea, exactScore );
                    if( pointScore > 0 )
                    {
                        if( pointScore > score )
                        {
                            score = pointScore;
                        }
                        if( exactScore == false )
                        {
                            break;
                        }

                        matches++;
                    }
                }
            }
            if( matches > 1 )
            {
                score +=
                    (int) ( ( 100 - score ) * ( matches / (float) ( pointCount + pointCount ) ) );
                if( score > 100 )
                {
                    score = 100;
                }
            }
        }

        GeoPoint location = item.getLocation( );
        if( ( score == 0 ) && ( location != null )
            && ( ( searchArea == null ) || searchArea.contains( location ) ) )
        {
            score = scoreSingleLocation( location, searchArea, exactScore );
        }

        return score;
    }

    /**
     * This method should be overloaded to provide more complex area match scoring functionality for
     * a single point.<br/>
     * <br/>
     * By default it will score a match based on location where 100 is an exact match to the center
     * of the search area and the score will decrease down to 1 the farther away from the center. 0
     * will be returned in the case it is not inside the search area
     * 
     * @param location
     *            The location to score the match with
     * @param searchArea
     *            The search area
     * @param exactScore
     *            true if the exact score should be computed (to support
     *            {@link #searchAndRankAll(QueryParams, LayerModel)} and
     *            {@link #searchForBest(QueryParams, LayerModel)}) otherwise 0 and 1 can be used to
     *            represent a no match and match respectively
     * @return 0 if the item is not inside the search area and a positive integer if it is in the
     *         search area where a larger number means it a better match to the search area
     */
    protected int scoreSingleLocation( GeoPoint location, BoundingBoxE6 searchArea,
                                       boolean exactScore )
    {
        int score = 0;

        if( ( location != null ) && ( ( searchArea == null ) || searchArea.contains( location ) ) )
        {
            if( ( exactScore ) && ( searchArea != null ) )
            {
                GeoPoint center = searchArea.getCenter( );
                float latDiff = Math.abs( center.getLatitudeE6( ) - location.getLatitudeE6( ) );
                float lonDiff = Math.abs( center.getLongitudeE6( ) - location.getLongitudeE6( ) );
                float centerDistanceSqaured = ( ( latDiff * latDiff ) + ( lonDiff * lonDiff ) );

                float halfLatSpan = searchArea.getLatitudeSpanE6( ) / 2f;
                float halfLonSpan = searchArea.getLongitudeSpanE6( ) / 2f;
                float maxDistanceSqaured =
                    ( ( halfLatSpan * halfLatSpan ) + ( halfLonSpan * halfLonSpan ) );

                if( maxDistanceSqaured == 0 )
                {
                    score = 100;
                }
                else
                {
                    float percentOfMax = centerDistanceSqaured / maxDistanceSqaured;
                    score = (int) ( ( 1 - percentOfMax ) * 100 );
                    if( score > 100 )
                    {
                        score = 100;
                    }
                    else if( score < 1 )
                    {
                        score = 1;
                    }
                }
            }
            else
            {
                score = 1;
            }
        }
        return score;
    }

    /**
     * This method needs to return a ranked list of all the layer items (paired with their score)
     * which satisfy the search parameters
     * 
     * @param searchParams
     *            The parameters to use in the search
     * @param model
     *            The layer model that should be searched
     * @return An ordered list where the first item is the best match of all the layer items which
     *         match the search parameters
     */
    public List<ScoredMatch> searchAndRankAll( QueryParams searchParams, LayerModel model )
    {
        List<ScoredMatch> matches = new ArrayList<ScoredMatch>( );

        if( model != null )
        {
            List<? extends MapLayerSelectionItem> allItems = model.getAllItemList( );
            if( allItems != null )
            {
                int score;
                for( MapLayerSelectionItem item : allItems )
                {
                    score = scoreQueryMatch( item, searchParams, true );
                    if( score > 0 )
                    {
                        matches.add( new ScoredMatch( item, score ) );
                    }
                }
                Collections.sort( matches );
            }
        }

        return matches;
    }

    /**
     * This method needs to return the highest scored layer items which match the search parameters
     * 
     * @param searchParams
     *            The parameters to use in the search
     * @param model
     *            The layer model that should be searched
     * @return The highest scored layer item and score which matched the search parameters or null
     *         if no items matched the search criteria
     */
    public ScoredMatch searchForBest( QueryParams searchParams, LayerModel model )
    {
        MapLayerSelectionItem bestMatch = null;
        int topScore = 0;
        int score;

        if( model != null )
        {
            List<? extends MapLayerSelectionItem> allItems = model.getAllItemList( );
            if( allItems != null )
            {
                for( MapLayerSelectionItem item : allItems )
                {
                    score = scoreQueryMatch( item, searchParams, true );
                    if( score > topScore )
                    {
                        bestMatch = item;
                        topScore = score;
                    }
                }
            }
        }

        ScoredMatch results = null;
        if( bestMatch != null )
        {
            results = new ScoredMatch( bestMatch, topScore );
        }
        return results;
    }
}
