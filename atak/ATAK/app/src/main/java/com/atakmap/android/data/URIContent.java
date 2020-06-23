
package com.atakmap.android.data;

import android.graphics.drawable.Drawable;

/**
 * Generic URI-based content with a title and drawable icon
 */
public interface URIContent {

    /**
     * Get a URI that represents this content
     * @return Content URI
     */
    String getURI();

    /**
     * Title for this content
     * @return Title
     */
    String getTitle();

    /**
     * Icon for this content
     * @return Icon drawable
     */
    Drawable getIconDrawable();

    /**
     * Get the color of the icon
     * @return Icon color (usually white)
     */
    int getIconColor();
}
