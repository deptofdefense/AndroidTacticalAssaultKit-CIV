package com.atakmap.map.layer.raster;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Objects;

public class ImageInfo {
    public final String path;
    public final String type;
    public final boolean precisionImagery;
    public final GeoPoint upperLeft;
    public final GeoPoint upperRight;
    public final GeoPoint lowerRight;
    public final GeoPoint lowerLeft;
    public final double maxGsd;
    public final int width;
    public final int height;
    public final int srid;

    public ImageInfo(String path,
                     String type,
                     boolean precisionImagery,
                     GeoPoint upperLeft,
                     GeoPoint upperRight,
                     GeoPoint lowerRight,
                     GeoPoint lowerLeft,
                     double maxGsd,
                     int width,
                     int height,
                     int srid) {

        this.path = path;
        this.type = type;
        this.precisionImagery = precisionImagery;
        this.upperLeft = upperLeft;
        this.upperRight = upperRight;
        this.lowerRight = lowerRight;
        this.lowerLeft = lowerLeft;
        this.maxGsd = maxGsd;
        this.width = width;
        this.height = height;
        this.srid = srid;
    }

    @Override
    public int hashCode() {
        return this.path.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof ImageInfo))
            return false;
        return Objects.equals(this.path, ((ImageInfo)o).path);
    }
}
