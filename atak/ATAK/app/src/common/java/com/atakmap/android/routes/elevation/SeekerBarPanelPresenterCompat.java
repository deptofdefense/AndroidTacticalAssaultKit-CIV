
package com.atakmap.android.routes.elevation;

import com.atakmap.android.maps.MapView;

public class SeekerBarPanelPresenterCompat {
    public static SeekerBarPanelPresenter newInstance(MapView mapView) {
        return new SeekerBarPanelPresenter(mapView);
    }
}
