
package com.atakmap.android.hierarchy;

import com.atakmap.android.dropdown.DropDown;

/**
 * List navigation events within Overlay manager
 * The {@link HierarchyListItem} sub-class must implement this interface to use
 * Due to this requirement, methods can assume the list in question is 'this'
 * Several of these events may be handled/suppressed by returning 'true'
 *
 * For more general Overlay Manager events see {@link HierarchyStateListener}
 */
public interface HierarchyListStateListener {

    /**
     * The list is about to be opened (pushed to the front of the stack)
     * Event can be suppressed (i.e. user cannot open the list)
     *
     * @param adapter Overlay Manager adapter
     * @return True if event handled, false to allow the list to open normally
     */
    default boolean onOpenList(HierarchyListAdapter adapter) {
        return false;
    }

    /**
     * The list is about to be closed (popped off the stack or pushed beneath)
     * Event can be suppressed unless forceClose is true
     *
     * @param adapter Overlay Manager adapter
     * @param forceClose True if the list is being forcibly closed
     * @return True if event handled, false to allow the list to close normally
     */
    default boolean onCloseList(HierarchyListAdapter adapter,
            boolean forceClose) {
        return false;
    }

    /**
     * The list's on-screen visibility state has changed
     * Tied to {@link DropDown.OnStateListener#onDropDownVisible(boolean)}
     * Not directly affected by list open or close events
     * Event cannot be suppressed
     *
     * @param adapter Overlay Manager adapter
     * @param visible True if the drop-down is visible
     */
    default void onListVisible(HierarchyListAdapter adapter, boolean visible) {
    }

    /**
     * A back button was pressed while on the list
     * Event can be suppressed
     *
     * @param adapter Overlay Manager adapter
     * @param deviceBack True if the device back button was pressed
     *                   False if the list's inline back button was pressed
     * @return True if event handled, false for default back button behavior
     */
    default boolean onBackButton(HierarchyListAdapter adapter,
            boolean deviceBack) {
        return false;
    }
}
