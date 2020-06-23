package transapps.maps.plugin;

import android.graphics.drawable.Drawable;

/**
 * Base interface for something that will be a member of a list
 * 
 * @author mriley
 */
public interface Describable {
    /**
     * @return the name for this tool/layer/item
     */
    String getDescription();

    /**
     * @return The icon for this tool/layer/item
     */
    Drawable getIcon();
}
