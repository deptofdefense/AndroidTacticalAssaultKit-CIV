
package com.atakmap.android.navigation.views.loadout;

import android.content.Context;

import com.atakmap.android.util.MappingAdapter;

/**
 * Adapter for loadout tools
 */
public class LoadoutToolsAdapter extends MappingAdapter {
    public LoadoutToolsAdapter(Context context) {
        super(context);
        addMapping(LoadoutToolsListVM.class, LoadoutToolsListVH.class);
        addMapping(LoadoutToolsGridVM.class, LoadoutToolsGridVH.class);
    }
}
