
package com.atakmap.spatial.file;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.atakmap.android.data.FileContentHandler;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.maps.ILocation;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.layer.feature.geometry.Envelope;

import java.io.File;

/**
 * Abstract handler for a file overlay with optional map bounds
 */
public abstract class FileOverlayContentHandler extends FileContentHandler
        implements ILocation, GoTo, FOVFilter.Filterable {

    protected final MapView _mapView;
    protected final Context _context;
    protected GeoBounds _bounds;

    protected FileOverlayContentHandler(MapView mapView, File file,
            Envelope bounds) {
        super(file);
        _mapView = mapView;
        _context = mapView.getContext();
        if (bounds != null)
            _bounds = new MutableGeoBounds(bounds.minY, bounds.minX,
                    bounds.maxY, bounds.maxX);
        else
            _bounds = null;
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_menu_overlays);
    }

    @Override
    public GeoPoint getPoint(GeoPoint point) {
        if (_bounds == null)
            return GeoPoint.ZERO_POINT;
        return _bounds.getCenter(point);
    }

    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        if (bounds != null) {
            if (_bounds == null)
                bounds.clear();
            else
                bounds.set(_bounds);
            return bounds;
        } else if (_bounds != null)
            return new GeoBounds(_bounds);
        return null;
    }

    @Override
    public boolean goTo(boolean select) {
        if (_bounds != null) {
            MapMenuReceiver.getInstance().hideMenu();
            ATAKUtilities.scaleToFit(this);
            return true;
        }
        return false;
    }

    @Override
    public boolean accept(FOVFilter.MapState fov) {
        return _bounds != null && fov.intersects(_bounds);
    }

    @Override
    public boolean isActionSupported(Class<?> action) {
        if (_bounds == null && (action.equals(ILocation.class)
                || action.equals(GoTo.class)))
            return false;
        return super.isActionSupported(action);
    }
}
