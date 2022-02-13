
package com.atakmap.android.navigation.views.loadout;

import com.atakmap.android.navigation.NavButtonManager;
import com.atakmap.android.navigation.models.NavButtonIntentAction;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.util.MappingVM;

/**
 * Abstract view model for the loadout tools
 */
abstract class LoadoutToolsVM extends MappingVM {

    private final NavButtonModel button;
    private boolean inUse;
    private final boolean editMode;
    private boolean hidden;

    // Event receiver comm vars
    // The last action triggered via press or long-press
    NavButtonIntentAction action;

    public LoadoutToolsVM(NavButtonModel button, boolean inUse,
            boolean editMode, boolean hidden) {
        this.button = button;
        this.inUse = inUse;
        this.editMode = editMode;
        this.hidden = hidden;
    }

    public NavButtonModel getButton() {
        return button;
    }

    public String getName() {
        return button.getName();
    }

    public int getItemIndex() {
        return NavButtonManager.getInstance().indexOfModel(button);
    }

    public boolean isEditMode() {
        return editMode;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isInUse() {
        return this.inUse;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }
}
