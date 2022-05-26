
package com.atakmap.android.data;

import android.graphics.drawable.Drawable;

/**
 * Interface which defines a method of sending URI-based content
 * i.e. TAK contact, TAK server upload, FTP, etc.
 */
public interface URIContentSender {

    /**
     * Get the send method name to be displayed to the user
     * @return User-readable name
     */
    String getName();

    /**
     * Get an icon that represents this send method
     * @return Icon drawable
     */
    Drawable getIcon();

    /**
     * Check if this content can be sent with this method based on its URI
     * @return True if supported, false if not
     */
    boolean isSupported(String contentURI);

    /**
     * Request to sent content using this method
     * @param contentURI Content URI
     * @param callback Callback to fire when content has been sent
     * @return True if request successful, false on fail
     */
    boolean sendContent(String contentURI, Callback callback);

    /**
     * Callback invoked when content has been sent
     */
    interface Callback {

        /**
         * Content has been (or attempted to be) sent
         * @param sender The URI content provider
         * @param contentURI URI for content that has been sent
         * @param success True if successful, false if failed
         */
        void onSentContent(URIContentSender sender, String contentURI,
                boolean success);
    }
}
