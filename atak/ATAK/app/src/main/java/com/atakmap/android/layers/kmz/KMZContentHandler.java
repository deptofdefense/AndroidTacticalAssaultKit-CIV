
package com.atakmap.android.layers.kmz;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.atakmap.android.data.MultiTypeFileContentHandler;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.maps.ILocation;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.spatial.file.KmlFileSpatialDb;

import java.io.File;

/**
 * Content handler for KMZ packages
 */
public class KMZContentHandler extends MultiTypeFileContentHandler
        implements Visibility2, GoTo, ILocation {

    private final Context _context;

    public KMZContentHandler(MapView mapView, File file) {
        super(file);
        _context = mapView.getContext();
    }

    @Override
    public String getContentType() {
        return KMZPackageImporter.CONTENT_TYPE;
    }

    @Override
    public String getMIMEType() {
        return KmlFileSpatialDb.KMZ_FILE_MIME_TYPE;
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_kmz_package);
    }

    @Override
    public boolean goTo(boolean select) {
        GeoBounds bounds = getBounds(null);
        if (bounds != null) {
            ATAKUtilities.scaleToFit(this);
            return true;
        }
        return false;
    }
}
