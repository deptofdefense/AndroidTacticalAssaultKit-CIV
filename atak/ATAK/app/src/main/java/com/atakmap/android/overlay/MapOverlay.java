
package com.atakmap.android.overlay;

import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;

public interface MapOverlay {

    /**
     * Returns the application unique identifier for the overlay.
     * 
     * @return The application unique identifier for the overlay.
     */
    String getIdentifier();

    /**
     * Returns the name of the overlay.
     * 
     * @return The name of the overlay.
     */
    String getName();

    /**
     * Returns the root {@link com.atakmap.android.maps.MapGroup} which will contain all
     * subgroups and {@link com.atakmap.android.maps.MapItem} instances for the overlay. This
     * method may return <code>null</code> to indicate that the overlay does not contain any content
     * that may be selected or interact with other map items.
     * <P>
     * 
     * @return The root group for the overlay, or <code>null</code> if the overlay does not contain
     *         any selectable content.
     */
    MapGroup getRootGroup();

    DeepMapItemQuery getQueryFunction();

    /**
     * Returns the model that may be used to build a UI to interact with the overlay. The returned
     * item represents the root node to be displayed in the list. If <code>null</code>, the overlay
     * will not appear in the list.
     * 
     * @param adapter A reference to the adapter that will be responsible for displaying the list.
     *            The overlay may invoke {@link android.widget.BaseAdapter#notifyDataSetChanged()}
     *            if the list content is externally modified and the adapter should update.
     * @param preferredSort The preferred sorting order to be used when constructing the list view.
     *            This argument is a <I>hint</I> and may be ignored in the event that executing the
     *            specified sort will compromise performance. An ascending alphabetic sort is
     *            recommended as the default.
     * @return The list model used to build the UI for the overlay or <code>null</code> if the
     *         overlay should not be included in the list.
     */
    HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities,
            HierarchyListItem.Sort preferredSort);

}
