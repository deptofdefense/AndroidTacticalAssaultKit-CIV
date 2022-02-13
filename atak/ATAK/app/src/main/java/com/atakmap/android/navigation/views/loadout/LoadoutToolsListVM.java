
package com.atakmap.android.navigation.views.loadout;

import com.atakmap.android.navigation.models.NavButtonModel;

/**
 * View model for the loadout tools list
 */
public class LoadoutToolsListVM extends LoadoutToolsVM {
    public LoadoutToolsListVM(NavButtonModel button, boolean inUse,
            boolean editMode, boolean hidden) {
        super(button, inUse, editMode, hidden);
    }
}
