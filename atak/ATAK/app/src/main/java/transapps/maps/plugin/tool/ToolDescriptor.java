
package transapps.maps.plugin.tool;

import android.graphics.drawable.Drawable;

public interface ToolDescriptor {

    /**
     * @return the short name for this tool.
     * return getDescription() if unsure
     */
    String getShortDescription();

    /**
     * @return The tool
     */
    Tool getTool();

    /**
     * @return The icon for this tool/layer/item
     */
    Drawable getIcon();

    /**
     * @return the name for this tool/layer/item
     */
    String getDescription();

    /**
     * Unused in ATAK, will always return just GENERAL.
     * @return an array of length one of { Group.GENERAL }
     */
    Group[] getGroups();

}
