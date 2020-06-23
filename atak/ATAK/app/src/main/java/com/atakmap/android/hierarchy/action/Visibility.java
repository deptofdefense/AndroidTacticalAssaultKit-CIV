
package com.atakmap.android.hierarchy.action;

public interface Visibility extends Action {

    /**
     * Encapsulation of the visibility toggle implementation as an action.
     * @param visible boolean true is visible, false invisible.
     * @return success or failure of setting the visibility.
     */
    boolean setVisible(boolean visible);

    /**
     * Gets the current visibility state.
     * @return true if the item is visible, false if invisible.
     */
    boolean isVisible();
}
