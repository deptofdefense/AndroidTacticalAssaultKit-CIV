
package com.atakmap.android.util;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;

/**
 * Abstract view holder
 * @param <T> View model (see {@link MappingVM})
 */
public abstract class MappingVH<T> extends FrameLayout {

    public MappingVH(ViewGroup parent, @LayoutRes int layoutResource) {
        super(parent.getContext());
        initView(parent, layoutResource);
    }

    private void initView(ViewGroup parent, @LayoutRes int layoutResource) {
        View.inflate(parent.getContext(), layoutResource, this);
    }

    /**
     * Called when a view needs to be updated
     * @param viewModel View model
     * @param receiver Event receiver
     */
    public abstract void onBind(T viewModel,
            MappingAdapterEventReceiver<T> receiver);
}
