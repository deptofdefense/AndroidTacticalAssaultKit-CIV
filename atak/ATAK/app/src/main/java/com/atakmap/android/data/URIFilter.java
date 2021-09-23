
package com.atakmap.android.data;

/**
 * Interface for filtering content based on URI
 */
public interface URIFilter {

    /**
     * Check if this content should be accepted by the filter
     *
     * @param uri Content URI
     * @return True if acceptable
     */
    boolean accept(String uri);
}
