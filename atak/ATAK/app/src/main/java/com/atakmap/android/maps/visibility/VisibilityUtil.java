
package com.atakmap.android.maps.visibility;

import java.util.List;

/**
 * Helper methods related to visibility conditions
 */
public class VisibilityUtil {

    /**
     * Execute a list of visibility conditions on an object
     * @param object Object to check against
     * @param conditions List of visibility conditions
     * @return Overall visibility state where results are ANDed together
     *         i.e. if a single condition returns INVISIBLE, the object is
     *         considered invisible
     */
    public static int checkConditions(Object object,
            List<VisibilityCondition> conditions) {
        int ret = VisibilityCondition.IGNORE;
        for (VisibilityCondition cond : conditions) {
            int v = cond.isVisible(object);

            // Ignore this condition
            if (v == VisibilityCondition.IGNORE)
                continue;

            // Update visibility
            ret = v;

            // If a single condition returns invisible, then break
            if (v == VisibilityCondition.INVISIBLE)
                break;
        }
        return ret;
    }
}
