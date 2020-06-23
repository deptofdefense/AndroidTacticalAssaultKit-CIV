
package com.atakmap.android.hierarchy;

import com.atakmap.android.dropdown.DropDown;

/**
 * Overlay Manager event listener
 * Use {@link HierarchyListReceiver#registerListener(HierarchyStateListener)}
 *
 * For list-specific events see {@link HierarchyListStateListener}
 */
public interface HierarchyStateListener {

    /**
     * Overlay Manager has been opened
     *
     * @param adapter Overlay Manager adapter
     */
    void onOpened(HierarchyListAdapter adapter);

    /**
     * Overlay Manager has been closed
     *
     * @param adapter Overlay Manager adapter
     */
    void onClosed(HierarchyListAdapter adapter);

    /**
     * Overlay Manager drop-down visibility state changed
     * Tied to {@link DropDown.OnStateListener#onDropDownVisible(boolean)}
     *
     * @param adapter Overlay Manager adapter
     * @param visible True if visible
     */
    void onVisible(HierarchyListAdapter adapter, boolean visible);

    /**
     * The current on-screen list has been changed
     *
     * @param adapter Overlay Manager adapter
     * @param oldList The previous current list
     * @param newList The new current list
     */
    void onCurrentListChanged(HierarchyListAdapter adapter,
            HierarchyListItem oldList, HierarchyListItem newList);
}
