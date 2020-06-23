package com.atakmap.map.layer.feature.cursor;

import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;

public class FeatureCursorWrapper implements FeatureCursor {
    protected  FeatureCursor impl;
    
    protected FeatureCursorWrapper(FeatureCursor impl) {
        this.impl = impl;
    }

    @Override
    public boolean moveToNext() {
        return this.impl.moveToNext();
    }

    @Override
    public void close() {
        this.impl.close();
    }

    @Override
    public boolean isClosed() {
        return this.impl.isClosed();
    }

    @Override
    public Object getRawGeometry() {
        return this.impl.getRawGeometry();
    }

    @Override
    public int getGeomCoding() {
        return this.impl.getGeomCoding();
    }

    @Override
    public String getName() {
        return this.impl.getName();
    }

    @Override
    public int getStyleCoding() {
        return this.impl.getStyleCoding();
    }

    @Override
    public Object getRawStyle() {
        return this.impl.getRawStyle();
    }

    @Override
    public AttributeSet getAttributes() {
        return this.impl.getAttributes();
    }

    @Override
    public Feature get() {
        return this.impl.get();
    }

    @Override
    public long getId() {
        return this.impl.getId();
    }

    @Override
    public long getVersion() {
        return this.impl.getVersion();
    }

    @Override
    public long getFsid() {
        return this.impl.getFsid();
    }
}
