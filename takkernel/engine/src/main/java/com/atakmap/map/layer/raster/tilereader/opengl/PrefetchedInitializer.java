package com.atakmap.map.layer.raster.tilereader.opengl;

import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory.Options;

public class PrefetchedInitializer implements GLQuadTileNode2.Initializer {

    private final boolean owns;
    private final TileReader reader;
    private final DatasetProjection2 imprecise;
    private final DatasetProjection2 precise;

    public PrefetchedInitializer(TileReader reader, DatasetProjection2 imprecise, boolean owns) {
        this(reader, imprecise, null, owns);
    }

    public PrefetchedInitializer(TileReader reader, DatasetProjection2 imprecise, DatasetProjection2 precise, boolean owns) {
        this.reader = reader;
        this.imprecise = imprecise;
        this.precise = precise;
        this.owns = owns;
    }

    @Override
    public Result init(ImageInfo info, Options opts) {
        Result retval = new Result();
        retval.reader = this.reader;
        retval.imprecise = this.imprecise;
        retval.precise = this.precise;
        return retval;
    }
    
    @Override
    public void dispose(Result result) {
        if(this.owns) {
            if(result.reader != null)
                result.reader.dispose();
            if(result.imprecise != null)
                result.imprecise.release();
            if(result.precise != null)
                result.precise.release();
        }
    }
}
