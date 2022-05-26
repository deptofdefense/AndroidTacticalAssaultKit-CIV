
package com.atakmap.android.navigation.views.loadout;

import com.atakmap.android.navigation.models.LoadoutItemModel;
import com.atakmap.android.util.MappingVM;

/**
 * View model for the list of available loadouts
 */
public class LoadoutListVM extends MappingVM {

    private final LoadoutItemModel loadout;
    private LoadoutMode mode;

    public LoadoutListVM(LoadoutItemModel loadout) {
        this.loadout = loadout;
        this.mode = LoadoutMode.SELECT;
    }

    public LoadoutItemModel getLoadout() {
        return loadout;
    }

    public String getTitle() {
        return loadout.getTitle();
    }

    public String getUID() {
        return loadout.getUID();
    }

    public LoadoutMode getMode() {
        return mode;
    }

    public void setMode(LoadoutMode mode) {
        this.mode = mode;
    }
}
