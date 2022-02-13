package com.atakmap.map.layer.raster.mosaic;

import java.util.Collection;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.RowIterator;
import com.atakmap.database.RowIteratorWrapper;
import com.atakmap.util.Filter;

public abstract class FilterMosaicDatabaseCursor2 extends RowIteratorWrapper implements MosaicDatabase2.Cursor {

    protected MosaicDatabase2.Cursor mosaicFilter;
    
    protected FilterMosaicDatabaseCursor2(MosaicDatabase2.Cursor impl) {
        this(impl, impl);
    }
    
    private FilterMosaicDatabaseCursor2(MosaicDatabase2.Cursor impl, RowIterator subject) {
        super(subject);
        
        this.mosaicFilter = impl;
    }

    @Override
    public GeoPoint getUpperLeft() {
        return this.mosaicFilter.getUpperLeft();
    }

    @Override
    public GeoPoint getUpperRight() {
        return this.mosaicFilter.getUpperRight();
    }

    @Override
    public GeoPoint getLowerRight() {
        return this.mosaicFilter.getLowerRight();
    }

    @Override
    public GeoPoint getLowerLeft() {
        return this.mosaicFilter.getLowerLeft();
    }

    @Override
    public double getMinLat() {
        return this.mosaicFilter.getMinLat();
    }

    @Override
    public double getMinLon() {
        return this.mosaicFilter.getMinLon();
    }

    @Override
    public double getMaxLat() {
        return this.mosaicFilter.getMaxLat();
    }

    @Override
    public double getMaxLon() {
        return this.mosaicFilter.getMaxLon();
    }

    @Override
    public String getPath() {
        return this.mosaicFilter.getPath();
    }

    @Override
    public String getType() {
        return this.mosaicFilter.getType();
    }

    @Override
    public double getMinGSD() {
        return this.mosaicFilter.getMinGSD();
    }

    @Override
    public double getMaxGSD() {
        return this.mosaicFilter.getMaxGSD();
    }

    @Override
    public int getWidth() {
        return this.mosaicFilter.getWidth();
    }

    @Override
    public int getHeight() {
        return this.mosaicFilter.getHeight();
    }

    @Override
    public int getId() {
        return this.mosaicFilter.getId();
    }

    @Override
    public int getSrid() {
        return this.mosaicFilter.getSrid();
    }
    
    @Override
    public boolean isPrecisionImagery() {
        return this.mosaicFilter.isPrecisionImagery();
    }

    @Override
    public MosaicDatabase2.Frame asFrame() {
        return this.mosaicFilter.asFrame();
    }
    
    public static MosaicDatabase2.Cursor filter(MosaicDatabase2.Cursor impl, final Collection<Filter<MosaicDatabase2.Cursor>> filters) {
        return new FilterMosaicDatabaseCursor2(impl) {
            @Override
            public boolean moveToNext() {
                do {
                    if(!this.filter.moveToNext())
                        return false;
                    boolean accepted = true;
                    for(Filter<MosaicDatabase2.Cursor> filter : filters) {
                        accepted &= filter.accept(this.mosaicFilter);
                        if(!accepted)
                            break;
                    }
                    if(!accepted)
                        continue;
                    return true;
                } while(true);
            }
        };
    }
}
