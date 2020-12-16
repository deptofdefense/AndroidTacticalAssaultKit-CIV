
package com.atakmap.android.maps.visibility;

/**
 * Decides whether an object is visible or not, outside of its own visibility flag
 */
public interface VisibilityCondition {

    // Visibility states
    int IGNORE = -1;
    int INVISIBLE = 0;
    int VISIBLE = 1;

    /**
     * Check if an object is visible
     * @param o Object to check
     * @return Visibility state constant (IGNORE, INVISIBLE, or VISIBLE)
     */
    int isVisible(Object o);
}
