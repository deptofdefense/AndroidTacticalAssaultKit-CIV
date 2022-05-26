
package com.atakmap.android.navigation.views.loadout;

import com.atakmap.android.util.MappingAdapterEventReceiver;
import com.atakmap.app.R;

import android.annotation.SuppressLint;
import android.view.ViewGroup;

/**
 * View handler for the loadout tools grid
 */
@SuppressLint("ViewConstructor")
public class LoadoutToolsGridVH extends LoadoutToolsVH<LoadoutToolsGridVM> {

    public LoadoutToolsGridVH(ViewGroup parent) {
        super(parent, R.layout.loadout_tool_grid_item);
    }

    @Override
    public void onBind(final LoadoutToolsGridVM viewModel,
            final MappingAdapterEventReceiver<LoadoutToolsGridVM> receiver) {
        super.onBind(viewModel, receiver);

        this.hideView.setImageResource(viewModel.isHidden()
                ? R.drawable.nav_tool_hidden
                : R.drawable.nav_tool_shown);
    }
}
