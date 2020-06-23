
package com.atakmap.android.data;

/**
 * Listener for content changes
 * To be registered by content managers and called by content handlers
 */
public interface URIContentListener {

    /**
     * Called when content is finished being imported
     * @param handler URI content handler
     */
    void onContentImported(URIContentHandler handler);

    /**
     * Called when content is finished being locally deleted
     * @param handler URI content handler
     */
    void onContentDeleted(URIContentHandler handler);

    /**
     * Called when content has changed in some way
     * @param handler URI content handler
     */
    void onContentChanged(URIContentHandler handler);
}
