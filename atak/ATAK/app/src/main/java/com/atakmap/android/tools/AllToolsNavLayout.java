
package com.atakmap.android.tools;

import com.atakmap.android.tools.menu.AtakActionBarMenuData;

/**
 * 
 */
public class AllToolsNavLayout {

    private final AtakActionBarMenuData menu;
    private final int icon;

    public AllToolsNavLayout(AtakActionBarMenuData menu, int icon) {
        this.menu = menu;
        this.icon = icon;
    }

    public AtakActionBarMenuData getMenu() {
        return this.menu;
    }

    public int getIcon() {
        return this.icon;
    }
}
