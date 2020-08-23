
package com.atakmap.android.cotdetails.extras;

import android.content.Context;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.atakmap.android.maps.MapItem;

import java.util.List;

/**
 * Default layout for extra details
 */
public class ExtraDetailsLayout extends LinearLayout
        implements ExtraDetailsListener {

    private MapItem _item;

    public ExtraDetailsLayout(Context context) {
        this(context, null);
    }

    public ExtraDetailsLayout(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExtraDetailsLayout(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ExtraDetailsLayout(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        ExtraDetailsManager.getInstance().addListener(this);
        if (_item != null)
            onProviderChanged(null);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ExtraDetailsManager.getInstance().removeListener(this);
    }

    /**
     * Set the map item subject of this layout
     * @param item Map item
     */
    public void setItem(MapItem item) {
        if (_item != item) {
            _item = item;
            onProviderChanged(null);
        }
    }

    @Override
    public void onProviderAdded(ExtraDetailsProvider provider) {
        if (_item == null)
            return;

        View v = provider.getExtraView(_item, null);
        if (v != null) {
            v.setTag(provider);
            addView(v);
        }
    }

    @Override
    public void onProviderChanged(ExtraDetailsProvider provider) {
        if (_item == null)
            return;

        if (provider == null) {
            // No existing provider - refresh all
            List<ExtraDetailsProvider> providers = ExtraDetailsManager
                    .getInstance().getProviders();
            removeAllViews();
            for (ExtraDetailsProvider p : providers) {
                View v = p.getExtraView(_item, null);
                if (v != null) {
                    v.setTag(p);
                    addView(v);
                }
            }
        } else {
            // Update existing provider
            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                View v = getChildAt(i);
                Object tag = v.getTag();
                if (tag == provider) {
                    View updated = provider.getExtraView(_item, v);
                    if (updated != v) {
                        updated.setTag(provider);
                        removeViewAt(i);
                        addView(updated, i);
                    }
                    return;
                }
            }
            // Provider not found - just add it then
            onProviderAdded(provider);
        }
    }

    @Override
    public void onProviderRemoved(ExtraDetailsProvider provider) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View v = getChildAt(i);
            Object tag = v.getTag();
            if (tag == provider) {
                removeViewAt(i);
                return;
            }
        }
    }
}
