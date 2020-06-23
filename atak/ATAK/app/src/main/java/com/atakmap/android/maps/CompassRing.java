
package com.atakmap.android.maps;

import com.atakmap.coremap.maps.coords.GeoPoint;

public class CompassRing extends PointMapItem {
    public CompassRing(final GeoPoint point,
            final String uid) {
        this(MapItem.createSerialId(), new DefaultMetaDataHolder(), point, uid);
    }

    public CompassRing(final long serialId,
            final MetaDataHolder metadata,
            final String uid) {
        this(serialId, metadata, POINT_DEFAULT, uid);
    }

    private CompassRing(final long serialId,
            final MetaDataHolder metadata,
            final GeoPoint point,
            final String uid) {
        super(serialId, metadata, point, uid);
        this.setMetaBoolean("addToObjList", false);
    }
}
