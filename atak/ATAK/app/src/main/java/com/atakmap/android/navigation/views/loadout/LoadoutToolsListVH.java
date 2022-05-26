
package com.atakmap.android.navigation.views.loadout;

import com.atakmap.android.util.MappingAdapterEventReceiver;
import com.atakmap.app.R;

import android.annotation.SuppressLint;
import android.view.ViewGroup;

/**
 * View handler for the loadout tools list
 */
@SuppressLint("ViewConstructor")
public class LoadoutToolsListVH extends LoadoutToolsVH<LoadoutToolsListVM> {
    public LoadoutToolsListVH(ViewGroup parent) {
        super(parent, R.layout.loadout_tool_list_item);
    }

    @Override
    public void onBind(final LoadoutToolsListVM viewModel,
            final MappingAdapterEventReceiver<LoadoutToolsListVM> receiver) {
        super.onBind(viewModel, receiver);
        this.hideIcon.setVisible(!viewModel.isHidden());
    }
}
