
package com.atakmap.android.data;

/**
 * Used to resolve which content handler is returned for a given tool name and URI
 */
public interface URIContentResolver {
    /**
     * Given a tool name and content URI, create or obtain a content handler
     * @param tool Tool name (may be null if N/A)
     * @param uri Content URI
     * @return URI content handler or null if not supported
     */
    URIContentHandler getHandler(String tool, String uri);
}
