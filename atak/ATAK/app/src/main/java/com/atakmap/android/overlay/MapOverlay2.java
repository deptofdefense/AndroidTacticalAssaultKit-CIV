
package com.atakmap.android.overlay;

import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;

public interface MapOverlay2 extends MapOverlay {

    /**
     * Returns the model that may be used to build a UI to interact with the overlay. The returned
     * item represents the root node to be displayed in the list. If <code>null</code>, the overlay
     * will not appear in the list.
     *
     * @param adapter A reference to the adapter that will be responsible for displaying the list.
     *            The overlay may invoke {@link BaseAdapter#notifyDataSetChanged()}
     *            if the list content is externally modified and the adapter should update.
     * @param preferredFilter The preferred filter to be used when constructing the list view.
     *            This argument is a <I>hint</I> and may be ignored in the event that executing the
     *            specified filter will compromise performance. A filter with an ascending alphabetic sort is
     *            recommended as the default.
     * @return The list model used to build the UI for the overlay or <code>null</code> if the
     *         overlay should not be included in the list.
     */
    HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities,
            HierarchyListFilter preferredFilter);

}
