
package com.atakmap.android.data;

import java.util.List;

/**
 * Interface for URI content that supports content queries
 */
public interface URIQueryInterface {

    /**
     * Query a list of content using specific parameters
     * @param params Query parameters
     * @return List of matching content
     */
    List<URIContentHandler> query(URIQueryParameters params);
}
