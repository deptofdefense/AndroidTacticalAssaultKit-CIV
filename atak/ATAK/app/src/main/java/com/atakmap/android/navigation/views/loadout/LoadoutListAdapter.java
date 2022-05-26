
package com.atakmap.android.navigation.views.loadout;

import android.content.Context;

import com.atakmap.android.util.MappingAdapter;

/**
 * Adapter for the list of available loadouts
 */
public class LoadoutListAdapter extends MappingAdapter {

    public LoadoutListAdapter(Context context) {
        super(context);
        addMapping(LoadoutListVM.class, LoadoutListVH.class);
        addMapping(LoadoutAllToolsVM.class, LoadoutAllToolsVH.class);
    }
}
