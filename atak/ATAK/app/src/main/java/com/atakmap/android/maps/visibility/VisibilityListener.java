
package com.atakmap.android.maps.visibility;

import java.util.List;

/**
 * Listen for visibility condition changes
 */
public interface VisibilityListener {

    /**
     * Visibility conditions have changed or should be processed
     * This is where implementations should go through their contents and apply
     * visibility based on these conditions
     *
     * @param conditions List of visibility conditions
     */
    void onVisibilityConditions(List<VisibilityCondition> conditions);
}
