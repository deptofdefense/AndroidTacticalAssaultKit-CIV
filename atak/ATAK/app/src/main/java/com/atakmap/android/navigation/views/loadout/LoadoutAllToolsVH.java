
package com.atakmap.android.navigation.views.loadout;

import android.annotation.SuppressLint;
import android.view.View;
import android.view.ViewGroup;

import com.atakmap.android.util.MappingAdapterEventReceiver;
import com.atakmap.android.util.MappingVH;
import com.atakmap.app.R;

/**
 * The view handler for the "All tools & plugins" button
 */
@SuppressLint("ViewConstructor")
public class LoadoutAllToolsVH extends MappingVH<LoadoutAllToolsVM> {

    public LoadoutAllToolsVH(ViewGroup parent) {
        super(parent, R.layout.loadout_all_item);
    }

    @Override
    public void onBind(final LoadoutAllToolsVM viewModel,
            final MappingAdapterEventReceiver receiver) {
        this.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                receiver.eventReceived(viewModel);
            }
        });
    }
}
