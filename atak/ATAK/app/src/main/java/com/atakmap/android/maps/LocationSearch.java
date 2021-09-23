
package com.atakmap.android.maps;

import java.util.SortedMap;

public interface LocationSearch {

    /**
     * Tries to find the location of some point of interest in the overlay based on search terms.
     * 
     * @param term The search terms as plain text.
     * @return A {@link SortedMap} of results. The keys are confidence values, ranging from
     *         <code>0.0f</code> to <code>1.0f</code>; values are the associated locations.
     */
    SortedMap<Float, ILocation> findLocation(String term);
}
