
package com.atakmap.android.gdal.layers.graphics;

import android.net.Uri;
import android.util.Pair;

import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.DefaultDatasetProjection2;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.gdal.GdalTileReader;
import com.atakmap.map.layer.raster.gdal.opengl.GLGdalMapLayer2;
import com.atakmap.map.layer.raster.opengl.GLMapLayer3;
import com.atakmap.map.layer.raster.opengl.GLMapLayerSpi3;
import com.atakmap.map.layer.raster.tilereader.AndroidTileReader;
import com.atakmap.map.layer.raster.tilereader.TileReader;

import com.atakmap.coremap.locale.LocaleUtil;

public class GLGdalKmzMapLayer2 extends GLGdalMapLayer2 {
    public final static GLMapLayerSpi3 SPI = new GLMapLayerSpi3() {
        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLMapLayer3 create(Pair<MapRenderer, DatasetDescriptor> arg) {
            final MapRenderer surface = arg.first;
            final DatasetDescriptor info = arg.second;
            if (!info.getDatasetType().equals("kmz"))
                return null;
            return new GLGdalKmzMapLayer2(surface, info);
        }
    };

    public GLGdalKmzMapLayer2(MapRenderer surface, DatasetDescriptor info) {
        super(surface, info);
    }

    @Override
    protected DatasetProjection2 createDatasetProjection() {
        final ImageDatasetDescriptor image = (ImageDatasetDescriptor) this.info;

        return new DefaultDatasetProjection2(image.getSpatialReferenceID(),
                image.getWidth(), image.getHeight(),
                image.getUpperLeft(),
                image.getUpperRight(),
                image.getLowerRight(),
                image.getLowerLeft());
    }

    @Override
    protected TileReader createTileReader() {
        if (this.getLayerUri().toLowerCase(LocaleUtil.getCurrent())
                .endsWith("jpg")) {
            try {
                Uri uri = Uri.parse(this.getLayerUri());
                return new AndroidTileReader(new ZipVirtualFile(uri.getPath()),
                        512, 512,
                        this.info.getExtraData("tilecache"),
                        this.asyncio);
            } catch (Throwable e) {
                Log.e("GLGdalKmzMapLayer",
                        "Failed to create Android KMZ tile reader.", e);
            }
        }
        return new GdalTileReader(this.dataset,
                this.dataset.GetDescription(), 512, 512,
                info.getExtraData("tilecache"), this.asyncio);
    }
}
