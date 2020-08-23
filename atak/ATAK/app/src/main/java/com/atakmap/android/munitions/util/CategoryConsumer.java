
package com.atakmap.android.munitions.util;

import java.util.Map;

/**
 * Used by {@link MunitionsHelper} for looping through weapon categories
 */
public interface CategoryConsumer {

    /**
     * Called for each category in the target's weapon metadata
     * @param categoryName Category
     * @param map Weapon mapping
     */
    void forCategory(String categoryName, Map<String, Object> map);
}
