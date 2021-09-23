package com.atakmap.map.layer.raster.opengl;

import android.util.Pair;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.spi.PriorityServiceProviderRegistry2;

public final class GLMapLayerFactory {

    private static PriorityServiceProviderRegistry2<GLMapLayer3, Pair<MapRenderer, DatasetDescriptor>, GLMapLayerSpi3> registry = new PriorityServiceProviderRegistry2<GLMapLayer3, Pair<MapRenderer, DatasetDescriptor>, GLMapLayerSpi3>(); 
    
    private GLMapLayerFactory() {}
    
    public static GLMapLayer3 create3(MapRenderer surface, DatasetDescriptor info) {
        final Pair<MapRenderer, DatasetDescriptor> arg = Pair.create(surface, info);
        return registry.create(arg);
    }
    
    public static void registerSpi(GLMapLayerSpi3 spi) {
        registry.register(spi, spi.getPriority());
    }

    public static void unregisterSpi(GLMapLayerSpi3 spi) {
        registry.unregister(spi);
    }
}
