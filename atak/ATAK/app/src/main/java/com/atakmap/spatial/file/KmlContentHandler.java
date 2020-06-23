
package com.atakmap.spatial.file;

import android.graphics.drawable.Drawable;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.coremap.locale.LocaleUtil;

import java.io.File;
import java.util.List;

public class KmlContentHandler extends SpatialDbContentHandler {

    private final boolean _isKMZ;

    KmlContentHandler(MapView mv, DataSourceFeatureDataStore db, File file,
            List<Long> featureSetIds, Envelope bounds) {
        super(mv, db, file, featureSetIds, bounds);
        String fName = file.getName().toLowerCase(LocaleUtil.getCurrent());
        _isKMZ = fName.endsWith(".kmz") || fName.endsWith(".zip");
    }

    @Override
    public String getContentType() {
        return KmlFileSpatialDb.KML_CONTENT_TYPE;
    }

    @Override
    public String getMIMEType() {
        return _isKMZ ? KmlFileSpatialDb.KMZ_FILE_MIME_TYPE
                : KmlFileSpatialDb.KML_FILE_MIME_TYPE;
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(_isKMZ ? R.drawable.ic_kmz
                : R.drawable.ic_kml);
    }
}
