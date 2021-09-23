package com.atakmap.map.layer.raster.tilematrix;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class TileClientFactory {
    private static Set<TileClientSpi> spis = Collections.newSetFromMap(new IdentityHashMap<TileClientSpi, Boolean>());
    
    private TileClientFactory() {}
    
    public static synchronized TileClient create(String path, String offlineCache, TileClientSpi.Options opts) {
        for(TileClientSpi spi : spis) {
            final TileClient retval = spi.create(path, offlineCache, opts);
            if(retval != null)
                return retval;
        }
        return null;
    }
    
    public static synchronized void registerSpi(TileClientSpi spi) {
        spis.add(spi);
    }
    
    public static synchronized void unregisterSpi(TileClientSpi spi) {
        spis.remove(spi);
    }
}
