
package com.atakmap.android.data;

import android.graphics.drawable.Drawable;
import android.os.Bundle;

import java.util.List;

/**
 * Interface which allows a tool to provide a method of adding URI-based content
 * i.e. Tool A has several methods of adding content, including Tool B which
 * implements this interface and provides its own method for adding content
 */
public interface URIContentProvider {

    /**
     * Get the tool name to be displayed to the user
     * @return User-readable tool name
     */
    String getName();

    /**
     * Get an icon that represents this tool
     * @return Icon drawable
     */
    Drawable getIcon();

    /**
     * Check if this content type can be added to the requested tool
     * @return True if supported, false if not
     */
    boolean isSupported(String requestTool);

    /**
     * Request to add content from one tool to another
     * @param requestTool Name of the tool requesting the content
     * @param extras Extra data specific to the request tool
     * @param callback Callback to fire if/when content is ready to be added
     *                 or has already been added
     * @return True if request successful, false on fail
     */
    boolean addContent(String requestTool, Bundle extras, Callback callback);

    interface Callback {

        /**
         * Content is ready to be added (or has just been added)
         * Depends on how Tool B decides to handle the request
         * @param provider The URI content provider
         * @param uris URIs to add
         */
        void onAddContent(URIContentProvider provider, List<String> uris);
    }
}
