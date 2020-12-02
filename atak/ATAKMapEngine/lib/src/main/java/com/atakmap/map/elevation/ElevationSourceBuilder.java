package com.atakmap.map.elevation;

import com.atakmap.database.IteratorCursor;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.util.CascadingComparator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;

// XXX - implement internal chunk container via FeatureDataStore

public final class ElevationSourceBuilder {
    private final static Comparator<ElevationChunk> RESOLUTION_ASC = new Comparator<ElevationChunk>() {
        @Override
        public int compare(ElevationChunk lhs, ElevationChunk rhs) {
            final double a = lhs.getResolution();
            final double b = rhs.getResolution();
            if(a < b)
                return 1;
            else if(a > b)
                return -1;
            else
                return lhs.getUri().compareTo(rhs.getUri());
        }
    };
    private final static Comparator<ElevationChunk> RESOLUTION_DESC = new Comparator<ElevationChunk>() {
        @Override
        public int compare(ElevationChunk lhs, ElevationChunk rhs) {
            final double a = lhs.getResolution();
            final double b = rhs.getResolution();
            if(a < b)
                return -1;
            else if(a > b)
                return 1;
            else
                return lhs.getUri().compareTo(rhs.getUri());
        }
    };
    private final static Comparator<ElevationChunk> CE_ASC = new Comparator<ElevationChunk>() {
        @Override
        public int compare(ElevationChunk lhs, ElevationChunk rhs) {
            final double a = lhs.getCE();
            final double b = rhs.getCE();
            if(a < b)
                return 1;
            else if(a > b)
                return -1;
            else
                return lhs.getUri().compareTo(rhs.getUri());
        }
    };
    private final static Comparator<ElevationChunk> CE_DESC = new Comparator<ElevationChunk>() {
        @Override
        public int compare(ElevationChunk lhs, ElevationChunk rhs) {
            final double a = lhs.getCE();
            final double b = rhs.getCE();
            if(a < b)
                return -1;
            else if(a > b)
                return 1;
            else
                return lhs.getUri().compareTo(rhs.getUri());
        }
    };
    private final static Comparator<ElevationChunk> LE_ASC = new Comparator<ElevationChunk>() {
        @Override
        public int compare(ElevationChunk lhs, ElevationChunk rhs) {
            final double a = lhs.getLE();
            final double b = rhs.getLE();
            if(a < b)
                return 1;
            else if(a > b)
                return -1;
            else
                return lhs.getUri().compareTo(rhs.getUri());
        }
    };
    private final static Comparator<ElevationChunk> LE_DESC = new Comparator<ElevationChunk>() {
        @Override
        public int compare(ElevationChunk lhs, ElevationChunk rhs) {
            final double a = lhs.getLE();
            final double b = rhs.getLE();
            if(a < b)
                return -1;
            else if(a > b)
                return 1;
            else
                return lhs.getUri().compareTo(rhs.getUri());
        }
    };

    private LinkedList<ElevationChunk> chunks;

    public ElevationSourceBuilder() {
        this.chunks = new LinkedList<>();
    }

    public synchronized void add(ElevationChunk chunk) {
        this.chunks.add(chunk);
    }

    public ElevationSource build(String name) {
        return build(name, this.chunks);
    }

    public static ElevationSource build(String name, Collection<ElevationChunk> chunks) {
        if(name == null)
            throw new IllegalArgumentException();
        if(chunks == null)
            throw new IllegalArgumentException();
        return new ElevationSourceImpl(name, chunks);
    }

    final static class ElevationSourceImpl extends ElevationSource {

        String name;
        Collection<ElevationChunk> chunks;
        Envelope bounds;

        ElevationSourceImpl(String name, Collection<ElevationChunk> chunks) {
            this.name = name;
            this.chunks = chunks;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public Cursor query(QueryParameters params) {
            if(params == null || (params.order == null || params.order.isEmpty())) {
                return new CursorImpl(chunks.iterator(), params);
            } else {
                ArrayList<Comparator<ElevationChunk>> comps = new ArrayList<>(params.order.size());
                for(ElevationSource.QueryParameters.Order order : params.order) {
                    switch(order) {
                        case CEAsc:
                            comps.add(ElevationSourceBuilder.CE_ASC);
                            break;
                        case LEAsc:
                            comps.add(ElevationSourceBuilder.LE_ASC);
                            break;
                        case CEDesc:
                            comps.add(ElevationSourceBuilder.CE_DESC);
                            break;
                        case LEDesc:
                            comps.add(ElevationSourceBuilder.LE_DESC);
                            break;
                        case ResolutionAsc:
                            comps.add(ElevationSourceBuilder.RESOLUTION_ASC);
                            break;
                        case ResolutionDesc:
                            comps.add(ElevationSourceBuilder.RESOLUTION_DESC);
                            break;
                        default :
                            throw new IllegalArgumentException();
                    }
                }

                ArrayList<ElevationChunk> sorted = new ArrayList<ElevationChunk>(chunks);
                Collections.sort(sorted, new CascadingComparator<ElevationChunk>(comps));
                return new CursorImpl(sorted.iterator(), params);
            }
        }

        @Override
        public Envelope getBounds() {
            // Note: this is a race, but considering pretty low-impact since the data is read-only
            if(bounds == null && !chunks.isEmpty()) {
                Envelope mbb = null;
                for(ElevationChunk chunk : chunks) {
                    Geometry cbounds = chunk.getBounds();
                    if(cbounds == null)
                        continue;
                    Envelope cmbb = cbounds.getEnvelope();
                    if(mbb == null) {
                        mbb = cmbb;
                    } else {
                        if(cmbb.minX < mbb.minX)
                            cmbb.minX = mbb.minX;
                        if(cmbb.minY < mbb.minY)
                            cmbb.minY = mbb.minY;
                        if(cmbb.minZ < mbb.minZ)
                            cmbb.minZ = mbb.minZ;
                        if(cmbb.maxX > mbb.maxX)
                            cmbb.maxX = mbb.maxX;
                        if(cmbb.maxY > mbb.maxY)
                            cmbb.maxY = mbb.maxY;
                        if(cmbb.maxZ > mbb.maxZ)
                            cmbb.maxZ = mbb.maxZ;
                    }
                }
                bounds = mbb;
            }
            return bounds;
        }

        @Override
        public void addOnContentChangedListener(OnContentChangedListener l) {
            // no-op; immutable
        }

        @Override
        public void removeOnContentChangedListener(OnContentChangedListener l) {
            // no-op; immutable
        }
    }

    final static class CursorImpl extends IteratorCursor<ElevationChunk> implements ElevationSource.Cursor {

        final ElevationSource.QueryParameters filter;

        CursorImpl(Iterator<ElevationChunk> iter, ElevationSource.QueryParameters filter) {
            super(iter);

            this.filter = filter;
        }

        @Override
        public ElevationChunk get() {
            return this.getRowData();
        }

        @Override
        public double getResolution() {
            return this.getRowData().getResolution();
        }

        @Override
        public boolean isAuthoritative() {
            return this.getRowData().isAuthoritative();
        }

        @Override
        public double getCE() {
            return this.getRowData().getCE();
        }

        @Override
        public double getLE() {
            return this.getRowData().getLE();
        }

        @Override
        public String getUri() {
            return this.getRowData().getUri();
        }

        @Override
        public String getType() {
            return this.getRowData().getType();
        }

        @Override
        public Geometry getBounds() {
            return this.getRowData().getBounds();
        }

        @Override
        public int getFlags() {
            return this.getRowData().getFlags();
        }

        @Override
        public boolean moveToNext() {
            do {
                if(!super.moveToNext())
                    return false;
                if(ElevationSource.accept(this, filter))
                    return true;
            } while(true);
        }
    }
}
