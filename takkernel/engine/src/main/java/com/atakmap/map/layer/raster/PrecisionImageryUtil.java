package com.atakmap.map.layer.raster;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/** @deprecated Will not be replaced */
@Deprecated
@DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
public final class PrecisionImageryUtil {
    private PrecisionImageryUtil() {}

    public static boolean refine(RasterDataAccess2 ignored1,
                                 final GeoPointMetaData ignored2,
                                 final GeoPointMetaData ignored3) {
         return false;
    }

    public static boolean unrefine(RasterDataAccess2 ignored1,
                                  final GeoPoint ignored2,
                                  final GeoPoint ignored3) {
         return false;
    }
}
