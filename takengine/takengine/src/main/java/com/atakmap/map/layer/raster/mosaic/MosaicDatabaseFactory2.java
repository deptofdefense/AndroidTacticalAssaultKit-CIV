package com.atakmap.map.layer.raster.mosaic;

import java.util.HashMap;
import java.util.Map;

public final class MosaicDatabaseFactory2 {

    private static Map<String, MosaicDatabaseSpi2> spis = new HashMap<String, MosaicDatabaseSpi2>();
    
    private MosaicDatabaseFactory2() {}
    
    public synchronized static MosaicDatabase2 create(String provider) {
        final MosaicDatabaseSpi2 spi = spis.get(provider);
        if(spi == null)
            return null;
        return spi.createInstance();
    }
    
    public synchronized static boolean canCreate(String provider) {
        final MosaicDatabaseSpi2 spi = spis.get(provider);
        return (spi != null);
    }

    public synchronized static void register(MosaicDatabaseSpi2 spi) {
        spis.put(spi.getName(), spi);
    }
    
    public synchronized static void unregister(MosaicDatabaseSpi2 spi) {
        spis.values().remove(spi);
    }
}
