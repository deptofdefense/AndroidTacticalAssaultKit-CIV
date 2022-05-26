
package com.atakmap.android.menu;

import com.atakmap.android.action.MapAction;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;

import gov.tak.api.widgets.IMapMenuButtonWidget;

final class MapActionAdapter
        implements IMapMenuButtonWidget.OnButtonClickHandler {
    final MapAction impl;

    MapActionAdapter(MapAction impl) {
        this.impl = impl;
    }

    @Override
    public boolean isSupported(Object opaque) {
        return (opaque == null) || (opaque instanceof MapItem);
    }

    @Override
    public void performAction(Object opaque) {
        if (this.impl != null && isSupported(opaque))
            this.impl.performAction(MapView.getMapView(), (MapItem) opaque);
    }

    public MapAction getMapAction() {
        return impl;
    }
}
