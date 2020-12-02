
package com.atakmap.android.data;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import androidx.annotation.Nullable;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import com.atakmap.android.maps.MetaDataHolder;

/**
 * The interface used to define actions for URI content
 */
public abstract class URIContentHandler implements URIContent {

    protected final String uri;

    protected URIContentHandler(String uri) {
        this.uri = uri;
    }

    @Override
    public String getURI() {
        return this.uri;
    }

    /**
     * Import content into ATAK
     * This may kick off an import task or simply do nothing if
     * the content is already imported or import is N/A
     */
    public abstract void importContent();

    /**
     * Delete content from ATAK
     * This may kick off a delete task or do nothing if the content
     * isn't imported or deletion is N/A
     */
    public abstract void deleteContent();

    /**
     * Get a title that represents this content
     * @return Content title (may be null if not imported or N/A)
     */
    @Override
    public abstract String getTitle();

    /**
     * Get an icon that represents this content
     * @return Icon drawable
     */
    public abstract Drawable getIcon();

    @Override
    public Drawable getIconDrawable() {
        return getIcon();
    }

    /**
     * Get the icon color filter (white by default)
     * @return Color multiply filter
     */
    @Override
    public int getIconColor() {
        return Color.WHITE;
    }

    /**
     * Create/get a view that represents this content
     * Arguments are optional and may be null (for use with recycler views)
     * @param convertView The existing content view
     * @param parent The parent view
     * @return Content view or null if not supported
     */
    public View getView(@Nullable View convertView,
            @Nullable ViewGroup parent) {
        return null;
    }

    /**
     * Create/get a menu for any actions associated with this content
     * The menu may be used as a container for menu items/actions and not
     * necessarily displayed as-is to the user. Avoid setting click listeners
     * here since they may be ignored in this case.
     *
     * If this handler overrides {@link OnMenuItemClickListener#onMenuItemClick}
     * then overriders shouldn't assume the menu item IDs will be consistent
     * due to potential context mismatch issues. Consider checking actions
     * using {@link MenuItem#getTitle()}
     *
     * @param anchor View anchor where this menu will be displayed
     *               May be null if the caller is using the menu as a container
     * @return Menu containing a list of actions or null if not supported
     */
    public PopupMenu getMenu(@Nullable View anchor) {
        return null;
    }

    /**
     * Allow for retrieval name/value dataset associated with this content view
     *
     * @return the MetaDataHolder containing the information associated with the 
     * current state of the View.   The return value is assumed to be mutable by 
     * the view at any time but changes are guaranteed when the 
     * URIContentListener::onContentChanged is fired.  Can be null if not implemented.
     * The view that is returned can be reused by setViewState without issue.
     */
    public MetaDataHolder getViewState() {
        return null;
    }

    /**
     * Allow for setting of the name/value dataset associated with this content view
     *
     * @param metaData the metaData used to populate the view.   The view is only populated 
     * when this method is called.   Subsequent changes to this holder will have no effect
     * on the visual content of the view unless the setViewState is called again.   The view
     * can be recycled from the view returned from getViewState();
     */
    public void setViewState(final MetaDataHolder metaData) {
    }

    /**
     * Check if an action with a given class is supported by this handler
     * Besides the "instance of" check, this call may return false if a handler
     * only supports a given action under other conditions
     * i.e. visibility toggle is only supported if content exists on map
     *
     * @param action Action class
     * @return True if supported
     */
    public boolean isActionSupported(Class<?> action) {
        return action != null && action.isInstance(this);
    }
}
