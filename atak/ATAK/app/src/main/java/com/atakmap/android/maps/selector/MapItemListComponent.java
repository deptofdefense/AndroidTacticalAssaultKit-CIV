
package com.atakmap.android.maps.selector;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;

/**
 * Map component used for displaying instances of {@link MapItemList}
 */
public final class MapItemListComponent extends AbstractMapComponent {

    private MapItemListOverlay _overlay;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        _overlay = new MapItemListOverlay(view);
        view.getMapOverlayManager().addOverlay(_overlay);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        view.getMapOverlayManager().removeOverlay(_overlay);
        _overlay.dispose();
    }
}
