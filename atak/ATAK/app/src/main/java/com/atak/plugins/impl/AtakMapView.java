
package com.atak.plugins.impl;

import android.view.View;

import com.atakmap.android.maps.MapView;
import com.atakmap.annotations.DeprecatedApi;

/*
 * Wrapper class for the implementation that carries the real ATAK MapView through to to plugins using
 * the transapps MapView class.     The two are not related.
 * @deprecated implementation detail. Class will be refactored to package private and renamed
 * The removal was bumped to 4.5 since it will require refactoring all plugins by migrating all
 * implement Lifecycle -> extends AbstractPluginLifecycle to reduce as much boilerplate code.
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.5")
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

}
