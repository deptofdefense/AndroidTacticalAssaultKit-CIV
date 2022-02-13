
package com.atakmap.map.layer.raster.gdal.opengl;

import android.util.Pair;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.DefaultDatasetProjection2;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.opengl.GLMapLayer3;
import com.atakmap.map.layer.raster.opengl.GLMapLayerSpi3;

public class GLGdalPriMapLayer2 extends GLGdalMapLayer2 {
    public final static GLMapLayerSpi3 SPI = new GLMapLayerSpi3() {
        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLMapLayer3 create(Pair<MapRenderer, DatasetDescriptor> arg) {
            final MapRenderer surface = arg.first;
            final DatasetDescriptor info = arg.second;
            if (!info.getDatasetType().equals("PRI"))
                return null;
            return new GLGdalPfiMapLayer2(surface, info);
        }
    };

    public GLGdalPriMapLayer2(MapRenderer surface, DatasetDescriptor info) {
        super(surface, info);
    }

    @Override
    protected DatasetProjection2 createDatasetProjection() {
        final ImageDatasetDescriptor image = (ImageDatasetDescriptor)this.info;
        
        return new DefaultDatasetProjection2(image.getSpatialReferenceID(),
                                             image.getWidth(), image.getHeight(),
                                             image.getUpperLeft(),
                                             image.getUpperRight(),
                                             image.getLowerRight(),
                                             image.getLowerLeft());
    }
}
