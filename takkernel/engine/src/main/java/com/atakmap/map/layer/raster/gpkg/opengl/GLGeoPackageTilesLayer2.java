package com.atakmap.map.layer.raster.gpkg.opengl;

import android.util.Pair;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.MosaicDatasetDescriptor;
import com.atakmap.map.layer.raster.mosaic.opengl.GLMosaicMapLayer;
import com.atakmap.map.layer.raster.opengl.GLMapLayer3;
import com.atakmap.map.layer.raster.opengl.GLMapLayerSpi3;

public final class GLGeoPackageTilesLayer2 {

    public final static GLMapLayerSpi3 SPI = new GLMapLayerSpi3() {
        @Override
        public int getPriority() {
            return 1;
        }

        @Override
        public GLMapLayer3 create(Pair<MapRenderer, DatasetDescriptor> arg) {
            final MapRenderer surface = arg.first;
            final DatasetDescriptor info = arg.second;
            if(!info.getDatasetType().equals("gpkg"))
                return null;
            if(!(info instanceof MosaicDatasetDescriptor))
                return null;
            return new GLMosaicMapLayer(surface, (MosaicDatasetDescriptor)info);
        }
    };
    
    private GLGeoPackageTilesLayer2() {}
}
