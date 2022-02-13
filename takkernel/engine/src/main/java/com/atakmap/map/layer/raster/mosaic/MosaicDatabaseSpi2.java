package com.atakmap.map.layer.raster.mosaic;

public interface MosaicDatabaseSpi2 {
    public String getName();
    public MosaicDatabase2 createInstance();
}
