
package com.atakmap.android.hierarchy.action;

/**
 * Interface for deleting an item
 */
public interface Delete extends Action {

    /**
     * Perform deletion on this item
     * @return True if deletion executed successfully
     */
    boolean delete();
}
