package com.atakmap.map.layer.raster.nativeimagery;

import java.io.File;

import com.atakmap.map.layer.raster.mosaic.FilterMosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.FilterMosaicDatabaseCursor2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;

final class ResolvedPathMosaicDatabase extends FilterMosaicDatabase2 {

    private final String baseUri;

    public ResolvedPathMosaicDatabase(MosaicDatabase2 impl, String baseUri) {
        super(impl);
        
        this.baseUri = baseUri;
    }

    @Override
    public void open(File f) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Cursor query(QueryParameters params) {
        return new ResolvedPathCursor(this.impl.query(params));
    }

    class ResolvedPathCursor extends FilterMosaicDatabaseCursor2 {

        public ResolvedPathCursor(Cursor impl) {
            super(impl);
        }

        @Override
        public String getPath() {
            return ResolvedPathMosaicDatabase.this.baseUri + File.separator + super.getPath();
        }
        
        @Override
        public Frame asFrame() {
            return new Frame(this);
        }
    }
}
