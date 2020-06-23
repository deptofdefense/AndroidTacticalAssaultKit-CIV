
package com.atakmap.android.maps;

/**
 * Like MetaMapPoint, represents a shape without a direct visual presence on the map.
 * 
 * 
 */
public abstract class MetaShape extends Shape {

    protected MetaShape(final String uid) {
        super(uid);
    }

    protected MetaShape(final long serialId, final MetaDataHolder metadata,
            final String uid) {
        super(serialId, metadata, uid);
    }

}
