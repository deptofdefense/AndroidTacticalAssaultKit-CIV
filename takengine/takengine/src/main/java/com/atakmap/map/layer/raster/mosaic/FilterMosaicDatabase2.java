package com.atakmap.map.layer.raster.mosaic;

import java.io.File;
import java.util.Map;

public abstract class FilterMosaicDatabase2 implements MosaicDatabase2 {

    protected MosaicDatabase2 impl;

    protected FilterMosaicDatabase2(MosaicDatabase2 impl) {
        this.impl = impl;
    }

    @Override
    public String getType() {
        return this.impl.getType();
    }

    @Override
    public void open(File f) {
        this.impl.open(f);
    }

    @Override
    public void close() {
        this.impl.close();
    }

    @Override
    public Coverage getCoverage() {
        return this.impl.getCoverage();
    }

    @Override
    public void getCoverages(Map<String, Coverage> coverages) {
        this.impl.getCoverages(coverages);
    }

    @Override
    public Coverage getCoverage(String type) {
        return this.impl.getCoverage(type);
    }

    @Override
    public Cursor query(QueryParameters params) {
        return this.impl.query(params);
    }
}
