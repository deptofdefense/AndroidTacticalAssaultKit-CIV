package com.atakmap.map.layer.raster.mbtiles;

import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabaseSpi2;

public final class MOMAPMosaicDatabase {

    public final static MosaicDatabaseSpi2 SPI = new MosaicDatabaseSpi2() {
        @Override
        public String getName() {
            return "momap";
        }

        @Override
        public MosaicDatabase2 createInstance() {
            return MBTilesMosaicDatabase.SPI.createInstance();
        }
    };
    
    private MOMAPMosaicDatabase() {}
}
