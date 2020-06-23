package transapps.maps.plugin.layer;

import android.content.Context;
import android.graphics.drawable.Drawable;

/**
 * Simple convenience implementation of the SelectionItem interface.
 *
 * It contains a drawable icon and a string description.
 */
public class SimpleSelectionItem implements SelectionItem {

    private Drawable drawable;
    private String description;
    
    /**
     * Main constructor
     * @param drawable the icon for this selected item
     * @param description the String description for this selected item
     */
    public SimpleSelectionItem(Drawable drawable, String description) {
        this.drawable = drawable;
        this.description = description;
    }
    
    /**
     * For if you have resource id.
     * Use the other constructor if you don't have a context.
     * 
     * @param context the context which will be used to load the drawable
     * @param drawable the resource id of the drawable for the icon
     * @param description resource id of the string description to load
     */
    public SimpleSelectionItem(Context context, int drawable, int description) {
        this.drawable = context.getResources().getDrawable(drawable);
        this.description = context.getString(description);
    }


    /**
     * Getter for the description
     *
     * @return String description
     */
    @Override
    public String getDescription() {
        return description;
    }

    /**
     * Getter for the Drawable icon
     *
     * @return Drawable icon for the selection item
     */
    @Override
    public Drawable getIcon() {
        return drawable;
    }
}
