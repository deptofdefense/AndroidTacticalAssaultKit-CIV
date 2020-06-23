package com.atakmap.map.layer.raster;

import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.math.PointD;

public final class PrecisionImageryUtil {
    private PrecisionImageryUtil() {}

    /**
     * Refines the specified coordinate with respect to the specified
     * precision raster data.
     *
     * <P>The refine process consists of obtaining the pixel coordinate for
     * the raster data, as registered on the map
     * (via {@link RasterDataAccess2#groundToImage(GeoPoint, PointD)})
     *
     * @param access the RasterData accessor to use when looking up a coordinate.
     * @param imprecise the imprecise coordinate
     * @param refined the refined coordinate
     *
     * @return  false
     */
    public static boolean refine(RasterDataAccess2 access,
                                 final GeoPointMetaData imprecise,
                                 final GeoPointMetaData refined) {
         return false;
    }
}
