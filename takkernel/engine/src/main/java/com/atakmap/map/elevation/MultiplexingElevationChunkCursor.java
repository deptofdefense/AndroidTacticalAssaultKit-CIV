package com.atakmap.map.elevation;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.util.CascadingComparator;
import com.atakmap.util.Collections2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MultiplexingElevationChunkCursor implements ElevationSource.Cursor {
    private ArrayList<ElevationSource.Cursor> cursors;
    private Comparator<ElevationSource.Cursor> order;
    private ElevationSource.Cursor row;

    public MultiplexingElevationChunkCursor(Collection<ElevationSource.Cursor> cursors) {
        this(cursors, Collections.<Comparator<ElevationSource.Cursor>>singletonList(ElevationSource.RESOLUTION_DESC));
    }

    public MultiplexingElevationChunkCursor(Collection<ElevationSource.Cursor> cursors, Comparator<ElevationSource.Cursor> order) {
        this(cursors, Collections.<Comparator<ElevationSource.Cursor>>singletonList(order));
    }

    public MultiplexingElevationChunkCursor(Collection<ElevationSource.Cursor> cursors, List<Comparator<ElevationSource.Cursor>> order) {
        Comparator<ElevationSource.Cursor> corder;
        if(order.size() == 1) {
            this.order = Collections2.first(order);
        } else if(order.isEmpty()) {
            this.order = ElevationSource.RESOLUTION_DESC;
        } else {
            this.order = new CascadingComparator<ElevationSource.Cursor>(order);
        }


        this.cursors = new ArrayList<ElevationSource.Cursor>(cursors.size());
        for(ElevationSource.Cursor c : cursors) {
            if(c.moveToNext())
                this.cursors.add(c);
        }
        Collections.sort(this.cursors, this.order);
    }

    @Override
    public ElevationChunk get() {
        return this.row.get();
    }

    @Override
    public double getResolution() {
        return this.row.getResolution();
    }

    @Override
    public boolean isAuthoritative() {
        return this.row.isAuthoritative();
    }

    @Override
    public double getCE() {
        return this.row.getCE();
    }

    @Override
    public double getLE() {
        return this.row.getLE();
    }

    @Override
    public String getUri() {
        return this.row.getUri();
    }

    @Override
    public String getType() {
        return this.row.getType();
    }

    @Override
    public Geometry getBounds() {
        return this.row.getBounds();
    }

    @Override
    public int getFlags() {
        return this.row.getFlags();
    }

    @Override
    public boolean moveToNext() {
        if(this.row != null) {
            if(!this.row.moveToNext())
                this.cursors.remove(0);
            Collections.sort(this.cursors, this.order);
        }
        if(this.cursors.isEmpty())
            return false;
        this.row = this.cursors.get(0);
        return true;
    }

    @Override
    public void close() {
        if(this.cursors.isEmpty())
            return;
        for(ElevationSource.Cursor c : this.cursors)
            c.close();
        this.cursors.clear();
        this.row = null;
    }

    @Override
    public boolean isClosed() {
        return this.cursors.isEmpty();
    }
}
