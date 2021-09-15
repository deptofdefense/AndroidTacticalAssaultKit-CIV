
package com.atakmap.android.user.filter;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import org.simpleframework.xml.Attribute;

/**
 * Filter to match in this order:
 *      on map item type
 *      on meta string
 *          If value is specified match CoT/Marker against string literal
 *          If not, then match CoT/Marker against pref string
 * 
 * 
 */
public class MapOverlayFilter {

    /**
     * CoT/2525C type
     */
    @Attribute(name = "type", required = false)
    private String type;

    /**
     * Meta string name to match on
     */
    @Attribute(name = "itemStringName", required = false)
    private String itemStringName;

    /**
     * Meta string value to match. If empty, then match that value for prefStringName
     */
    @Attribute(name = "itemStringValue", required = false)
    private String itemStringValue;

    /**
     * Pref string name to match
     */
    @Attribute(name = "prefStringName", required = false)
    private String prefStringName;

    /**
     * Require type 1 of 3 valid cases
     * @return
     */
    public boolean isValid() {
        return hasType() ||
                (hasItemStringName() && hasItemStringValue()) ||
                (hasItemStringName() && hasPrefStringName());
    }

    public boolean hasType() {
        return !FileSystemUtils.isEmpty(type);
    }

    public String getType() {
        return type;
    }

    public boolean hasItemStringName() {
        return !FileSystemUtils.isEmpty(itemStringName);
    }

    public String getItemStringName() {
        return itemStringName;
    }

    public boolean hasItemStringValue() {
        return !FileSystemUtils.isEmpty(itemStringValue);
    }

    public String getItemStringValue() {
        return itemStringValue;
    }

    public boolean hasPrefStringName() {
        return !FileSystemUtils.isEmpty(prefStringName);
    }

    public String getPrefStringName() {
        return prefStringName;
    }
}
