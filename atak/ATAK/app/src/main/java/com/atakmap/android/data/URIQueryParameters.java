
package com.atakmap.android.data;

import com.atakmap.android.hierarchy.filters.FOVFilter;

/**
 * Query parameters for URI-based content
 */
public class URIQueryParameters {

    // Content name/title
    public String name;

    // Content type
    public String contentType;

    // Spatial FOV filter
    public FOVFilter fov;

    // Visible content only
    public boolean visibleOnly;
}
