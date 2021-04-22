
package com.atakmap.spatial.file;

import android.graphics.drawable.Drawable;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.geometry.Envelope;

import java.io.File;
import java.util.List;

public class FalconViewContentHandler extends SpatialDbContentHandler {

    private final String _contentType;

    FalconViewContentHandler(MapView mv, DataSourceFeatureDataStore db,
            File file,
            List<Long> featureSetIds, Envelope bounds) {
        super(mv, db, file, featureSetIds, bounds);
        _contentType = FileSystemUtils.checkExtension(_file, "lpt")
                ? FalconViewSpatialDb.LPT
                : FalconViewSpatialDb.DRW;
    }

    @Override
    public String getContentType() {
        return _contentType;
    }

    @Override
    public String getMIMEType() {
        return FalconViewSpatialDb.MIME_TYPE;
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(_contentType.equals(FalconViewSpatialDb.LPT)
                ? R.drawable.ic_falconview_lpt
                : R.drawable.ic_falconview_drw);
    }
}
