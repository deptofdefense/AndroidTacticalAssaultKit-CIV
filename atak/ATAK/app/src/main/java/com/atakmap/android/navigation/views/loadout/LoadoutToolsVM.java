
package com.atakmap.android.navigation.views.loadout;

import com.atakmap.android.navigation.NavButtonManager;
import com.atakmap.android.navigation.models.NavButtonIntentAction;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.util.MappingVM;

/**
 * Abstract view model for the loadout tools
 */
abstract class LoadoutToolsVM extends MappingVM {

    static final int NONE = -1, TOP = 0, BOTTOM = 1, LEFT = 2, RIGHT = 3;

    private final NavButtonModel button;
    private boolean inUse;
    private final boolean editMode;
    private boolean hidden;
    private int dragPosition = NONE;

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

    public void setDragPosition(int position) {
        dragPosition = position;
    }

    public int getDragPosition() {
        return dragPosition;
    }
}
