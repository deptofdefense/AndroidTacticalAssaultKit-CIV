package com.atakmap.map.layer.feature.cursor;

import com.atakmap.database.RowIteratorWrapper;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;

public class BruteForceLimitOffsetFeatureCursor extends RowIteratorWrapper implements FeatureCursor {

    private final FeatureCursor filter;
    private final int offset;
    private final int limit;
    private int pos;
    
    public BruteForceLimitOffsetFeatureCursor(FeatureCursor filter, int offset, int limit) {
        super(filter);
        
        this.filter = filter;

        this.pos = 0;
        this.offset = offset;
        this.limit = (limit > 0) ? limit : Integer.MAX_VALUE;
    }

    @Override
    public boolean moveToNext() {
        // if we've exceeded the limit, return false
        if((this.pos-this.offset) >= this.limit)
            return false;

        // fast forward to the first offset record
        while(this.pos < (this.offset-1)) {
            if(super.moveToNext())
                this.pos++;
            else
                return false;
        }
        
        // input exhausted
        if(!super.moveToNext())
            return false;

        // update the position and make sure we haven't exceeded the limit
        this.pos++;
        return ((this.pos-this.offset) <= this.limit);
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
        return this.filter.getRawStyle();
    }

    @Override
    public AttributeSet getAttributes() {
        return this.filter.getAttributes();
    }

    @Override
    public Feature get() {
        return this.filter.get();
    }

    @Override
    public long getId() {
        return this.filter.getId();
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
