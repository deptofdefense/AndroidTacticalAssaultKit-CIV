
package com.atakmap.android.munitions.util;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Data related to a weapon in a munition
 */
public class WeaponData {

    private static final String TAG = "WeaponData";

    public String name, description, category;
    public int prone, standing;

    public WeaponData(String category) {
        this.category = category;
    }

    /**
     * Create weapon data from the corresponding map entry (under category map)
     * @param map Map entry
     */
    public WeaponData(String category, Map<String, Object> map) {
        this(category);
        try {
            this.name = (String) map.get("name");
            this.description = (String) map.get("description");
            this.prone = Integer.parseInt(String.valueOf(map.get("prone")));
            this.standing = Integer
                    .parseInt(String.valueOf(map.get("standing")));
        } catch (Exception e) {
            Log.e(TAG, "Failed to read weapon metadata", e);
        }
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(this.category)
                && !FileSystemUtils.isEmpty(this.name);
    }

    /**
     * Convert this weapon data to a map entry for storing under target metadata
     * @return Map entry
     */
    public Map<String, Object> toMap() {
        Map<String, Object> weapon = new HashMap<>();
        weapon.put("name", this.name);
        weapon.put("description", this.description);
        weapon.put("prone", Integer.toString(this.prone));
        weapon.put("standing", Integer.toString(this.standing));
        return weapon;
    }
}
