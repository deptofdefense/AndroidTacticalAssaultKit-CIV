
package com.atakmap.android.gpkg;

import android.graphics.drawable.Drawable;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.spatial.file.FeatureDbContentHandler;

import java.io.File;

/**
 * Content handler for GeoPackage (.gpkg) files
 */
public class GeoPackageContentHandler extends FeatureDbContentHandler {

    GeoPackageContentHandler(MapView mapView, File file,
            FeatureDataStore db, Envelope bounds) {
        super(mapView, file, db, bounds);
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.gpkg);
    }

    @Override
    public String getContentType() {
        return GeoPackageImporter.CONTENT_TYPE;
    }

    @Override
    public String getMIMEType() {
        return GeoPackageImporter.MIME_TYPE;
    }
}
