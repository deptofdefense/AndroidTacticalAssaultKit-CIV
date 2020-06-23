package transapps.maps.plugin.layer.search;

import transapps.maps.plugin.layer.MapLayerSelectionItem;


/**
 * This object is for a positive match in a search where the score is included
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 */
public class ScoredMatch implements Comparable<ScoredMatch>
{
    /**
     * The score of the specific match. The higher the score the better the match. A number lower
     * than 0 will only be used if {@link #mapItem} is null
     */
    public final int score;

    /**
     * The map item which was successfully matched with the search and had the resulting score
     */
    public final MapLayerSelectionItem mapItem;

    /**
     * This is the constructor for the score match. If the score is less than 1 it will be increased
     * to 1 which is the lowest possible score which results in a positive match
     * 
     * @param mapItem
     *            The map item which was matched
     * @param itemScore
     *            The score of the matched item
     */
    public ScoredMatch( MapLayerSelectionItem mapItem, int itemScore )
    {
        if( itemScore <= 0 )
        {
            itemScore = 1;
        }
        if( mapItem == null )
        {
            itemScore = 0;
        }
        score = itemScore;
        this.mapItem = mapItem;
    }

    @Override
    public int hashCode( )
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( mapItem == null ) ? 0 : mapItem.hashCode( ) );
        result = prime * result + score;
        return result;
    }

    @Override
    public boolean equals( Object obj )
    {
        boolean equal = true;
        if( this != obj )
        {
            if( ( obj != null ) && ( getClass( ) == obj.getClass( ) ) )
            {
                ScoredMatch other = (ScoredMatch) obj;
                if( mapItem == null )
                {
                    equal = ( other.mapItem == null );
                }
                else
                {
                    equal = ( mapItem.equals( other.mapItem ) );
                }

                if( equal )
                {
                    equal = ( score == other.score );
                }
            }
        }
        return equal;
    }

    @Override
    public int compareTo( ScoredMatch other )
    {
        int result = -1;
        if( other != null )
        {
            result = ( other.score - score );
            if( result == 0 )
            {
                String name1 = null;
                String name2 = null;
                if( mapItem != null )
                {
                    name1 = mapItem.getDescription( );
                }
                if( other.mapItem != null )
                {
                    name2 = other.mapItem.getDescription( );
                }

                if( name1 != name2 )
                {
                    if( name1 == null )
                    {
                        result = 1;
                    }
                    else if( name2 == null )
                    {
                        result = -1;
                    }
                    else
                    {
                        result = name1.compareTo( name2 );
                    }
                }
            }
        }

        return result;
    }
}
