
package com.atak.plugins.impl;

import android.view.View;

import com.atakmap.android.maps.MapView;
import com.atakmap.annotations.DeprecatedApi;

import transapps.geom.Projection;

/*
 * Wrapper class for the implementation that carries the real ATAK MapView through to to plugins using
 * the transapps MapView class.     The two are not related.
 * @deprecated Implementatino detail. Class will be refactored to package private and renamed
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
final public class AtakMapView implements transapps.mapi.MapView {
    public static final String TAG = "AtakMapView";

    private final MapView impl;

    /**
     * @param impl the map view implementation from ATAK.
     */
    public AtakMapView(MapView impl) {
        this.impl = impl;
    }

    public View getView() {
        return impl;
    }

    @Override
    public Projection getProjection() {
        throw new UnsupportedOperationException();
    }

}
