
package com.atakmap.android.maps;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * 
 */
public class MetaMapPoint extends PointMapItem {

    public MetaMapPoint(final GeoPointMetaData point,
            final String uid) {
        super(point, uid);
        setMetaBoolean("addToObjList", false);
    }

    public MetaMapPoint(final long serialId,
            final MetaDataHolder metadata,
            final String uid) {
        this(serialId, metadata, POINT_DEFAULT, uid);
    }

    private MetaMapPoint(final long serialId,
            final MetaDataHolder metadata,
            final GeoPoint point,
            final String uid) {
        super(serialId, metadata, point, uid);
        this.setMetaBoolean("addToObjList", false);
    }

}
