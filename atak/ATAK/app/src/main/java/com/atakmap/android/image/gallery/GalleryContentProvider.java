
package com.atakmap.android.image.gallery;

import android.graphics.drawable.Drawable;
import android.os.Bundle;

import com.atakmap.android.data.URIContentProvider;
import com.atakmap.android.image.GalleryItem;

import java.util.List;

/**
 * Class which provides files for display in the main ATAK gallery
 * Although this implements URIContentProvider, it's used differently than
 * other content providers in that it does not allow you to add content to it
 */
public abstract class GalleryContentProvider implements URIContentProvider {

    public static final String TOOL = "Gallery";

    @Override
    public String getName() {
        return TOOL;
    }

    @Override
    public Drawable getIcon() {
        return null;
    }

    @Override
    public boolean isSupported(String requestTool) {
        return TOOL.equals(requestTool);
    }

    @Override
    public boolean addContent(String requestTool, Bundle extras, Callback cb) {
        // Display only - not supported
        return false;
    }

    /**
     * Get all applicable gallery items
     * @return List of gallery items
     */
    public abstract List<GalleryItem> getItems();
}
