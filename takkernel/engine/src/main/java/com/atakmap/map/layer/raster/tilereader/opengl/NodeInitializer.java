package com.atakmap.map.layer.raster.tilereader.opengl;

import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;

public interface NodeInitializer {
    class Result {
        public TileReader reader;
        public DatasetProjection2 imprecise;
        public DatasetProjection2 precise;
        public Throwable error;
    }

    Result init(ImageInfo info, TileReaderFactory.Options opts);
    void dispose(Result result);
}
