package com.atakmap.map.layer.feature.cursor;

import com.atakmap.database.RowIteratorWrapper;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;

public class OffsetIdFeatureCursor extends RowIteratorWrapper implements FeatureCursor {

    private final long idOffset;

    private final FeatureCursor filter;
    
    public OffsetIdFeatureCursor(FeatureCursor filter, long idOffset) {
        super(filter);
        
        this.filter = filter;
        this.idOffset = idOffset;
    }
    
    @Override
    public Object getRawGeometry() {
        return this.filter.getRawGeometry();
    }

    @Override
    public int getGeomCoding() {
        return this.filter.getGeomCoding();
    }

    @Override
    public String getName() {
        return this.filter.getName();
    }

    @Override
    public int getStyleCoding() {
        return this.filter.getStyleCoding();
    }

    @Override
    public Object getRawStyle() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public AttributeSet getAttributes() {
        return this.filter.getAttributes();
    }

    @Override
    public Feature get() {
        return new Feature(this);
    }

    @Override
    public long getId() {
        return this.filter.getId() + this.idOffset;
    }

    @Override
    public long getVersion() {
        return this.filter.getVersion();
    }
    
    @Override
    public long getFsid() {
        return this.filter.getFsid();
    }

}
