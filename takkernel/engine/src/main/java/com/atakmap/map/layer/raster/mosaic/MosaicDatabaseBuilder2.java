package com.atakmap.map.layer.raster.mosaic;

import com.atakmap.coremap.maps.coords.GeoPoint;

public interface MosaicDatabaseBuilder2 {

    public void insertRow(String path,
                          String type,
                          boolean precision,
                          GeoPoint ul,
                          GeoPoint ur,
                          GeoPoint lr,
                          GeoPoint ll,
                          double minGsd,
                          double maxGsd,
                          int width,
                          int height,
                          int srid);

    public void beginTransaction();
    public void endTransaction();
    public void setTransactionSuccessful();
    
    public void createIndices();

    public void close();
}
