package com.atakmap.map.elevation;

import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.spi.ServiceProvider;

public interface ElevationDataSpi extends ServiceProvider<ElevationData, ImageInfo> {
    public int getPriority();
}
