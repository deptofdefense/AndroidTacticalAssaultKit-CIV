
package com.atakmap.android.maps.visibility;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;

/**
 * Visibility listeners on startup
 */
public class VisibilityMapComponent extends AbstractMapComponent {

    private MapItemVisibilityListener _mapListener;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        _mapListener = new MapItemVisibilityListener(view);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        _mapListener.dispose();
    }
}
