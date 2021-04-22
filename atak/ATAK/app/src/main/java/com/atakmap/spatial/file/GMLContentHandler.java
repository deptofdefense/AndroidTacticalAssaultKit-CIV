
package com.atakmap.spatial.file;

import android.graphics.drawable.Drawable;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.geometry.Envelope;

import java.io.File;
import java.util.List;

public class GMLContentHandler extends SpatialDbContentHandler {

    GMLContentHandler(MapView mv, DataSourceFeatureDataStore db,
            File file,
            List<Long> featureSetIds, Envelope bounds) {
        super(mv, db, file, featureSetIds, bounds);
    }

    @Override
    public String getContentType() {
        return GMLSpatialDb.GML_CONTENT_TYPE;
    }

    @Override
    public String getMIMEType() {
        return GMLSpatialDb.GML_FILE_MIME_TYPE;
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_shapefile);
    }
}
