package com.atakmap.map.elevation;

import com.atakmap.database.RowIterator;
import com.atakmap.lang.Objects;
import com.atakmap.map.layer.feature.AbstractFeatureDataStore2;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Rectangle;
import com.atakmap.util.Collections2;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public abstract class ElevationSource {
    public static interface OnContentChangedListener {
        public void onContentChanged(ElevationSource source);
    }

    public final static Comparator<Cursor> RESOLUTION_ASC = new Comparator<Cursor>() {
        @Override
        public int compare(Cursor lhs, Cursor rhs) {
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
    public final static Comparator<Cursor> RESOLUTION_DESC = new Comparator<Cursor>() {
        @Override
        public int compare(Cursor lhs, Cursor rhs) {
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
    public final static Comparator<Cursor> CE_ASC = new Comparator<Cursor>() {
        @Override
        public int compare(Cursor lhs, Cursor rhs) {
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
    public final static Comparator<Cursor> CE_DESC = new Comparator<Cursor>() {
        @Override
        public int compare(Cursor lhs, Cursor rhs) {
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
    public final static Comparator<Cursor> LE_ASC = new Comparator<Cursor>() {
        @Override
        public int compare(Cursor lhs, Cursor rhs) {
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
    public final static Comparator<Cursor> LE_DESC = new Comparator<Cursor>() {
        @Override
        public int compare(Cursor lhs, Cursor rhs) {
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

    ElevationSource() {}

    public abstract String getName();
    public abstract Cursor query(QueryParameters params);
    public abstract Envelope getBounds();
    public abstract void addOnContentChangedListener(OnContentChangedListener l);
    public abstract void removeOnContentChangedListener(OnContentChangedListener l);

    public static boolean accept(Cursor cursor, QueryParameters params) {
        if(params == null)
            return true;

        if(params.authoritative != null && params.authoritative.booleanValue() != cursor.isAuthoritative())
            return false;

        if(params.flags != null && ((params.flags.intValue()&cursor.getFlags()) == 0))
            return false;

        // note that comparison with possibly NaN cursor CE will always return
        // false, which is the desired behavior
        if(!Double.isNaN(params.minCE) && params.minCE < cursor.getCE())
            return false;

        // note that comparison with possibly NaN cursor CE will always return
        // false, which is the desired behavior
        if(!Double.isNaN(params.minLE) && params.minLE < cursor.getLE())
            return false;

        if(params.spatialFilter != null) {
            Envelope test = params.spatialFilter.getEnvelope();

            Geometry geom = cursor.getBounds();

            // the geometry is bad, return false so that it will never be accepted.
            if (geom == null)
                return false;

            Envelope c = geom.getEnvelope();
            if(!Rectangle.intersects(test.minX, test.minY, test.maxX, test.maxY, c.minX, c.minY, c.maxX, c.maxY))
                return false;
        }

        if(params.types != null && !AbstractFeatureDataStore2.matches(params.types, cursor.getType(), '*'))
            return false;

        if(!Double.isNaN(params.minResolution) && params.minResolution < cursor.getResolution())
            return false;

        if(!Double.isNaN(params.maxResolution) && params.maxResolution > cursor.getResolution())
            return false;

        return true;
    }

    public static interface Cursor extends RowIterator {
        public final static Cursor EMPTY = new Cursor() {
            @Override
            public boolean moveToNext() { return false; }
            @Override
            public void close() { }
            @Override
            public boolean isClosed() { return false; }
            @Override
            public ElevationChunk get() { return null; }
            @Override
            public double getResolution() { return Double.NaN; }
            @Override
            public boolean isAuthoritative() { return false; }
            @Override
            public double getCE() { return Double.NaN; }
            @Override
            public double getLE() { return Double.NaN; }
            @Override
            public String getUri() { return null; }
            @Override
            public String getType() { return null; }
            @Override
            public Geometry getBounds() { return null; }
            @Override
            public int getFlags() { return 0; }
        };

        public ElevationChunk get();
        public double getResolution();
        public boolean isAuthoritative();
        public double getCE();
        public double getLE();
        public String getUri();
        public String getType();
        public Geometry getBounds();
        public int getFlags();
    }

    public final static class QueryParameters {
        public enum Order {
            /** low to high resolution */
            ResolutionAsc,
            /** high to low resolution */
            ResolutionDesc,
            /** low to high error */
            CEAsc,
            /** high to low error */
            CEDesc,
            /** low to high error */
            LEAsc,
            /** high to low error */
            LEDesc,
        }

        public Geometry spatialFilter = null;
        public double maxResolution = Double.NaN;
        public double minResolution = Double.NaN;
        /**
         * If specified, defines the desired nominal resolution to satisfy the
         * operation for which the query was launched.
         */
        public double targetResolution = Double.NaN;
        public Set<String> types = null;
        public Boolean authoritative = null;
        public double minCE = Double.NaN;
        public double minLE = Double.NaN;
        public List<Order> order = null;
        public Integer flags = null;

        @Override
        public boolean equals(Object o) {
            if(!(o instanceof QueryParameters))
                return false;
            final QueryParameters other = (QueryParameters)o;
            return Objects.equals(spatialFilter, other.spatialFilter) &&
                  MathUtils.equals(targetResolution, other.targetResolution) &&
                  MathUtils.equals(minResolution, other.minResolution) &&
                  MathUtils.equals(maxResolution, other.maxResolution) &&
                  Collections2.equals(types, other.types) &&
                  Objects.equals(authoritative, other.authoritative) &&
                  MathUtils.equals(minCE, other.minCE) &&
                  MathUtils.equals(minLE, other.minLE) &&
                  Collections2.equals(order, other.order) &&
                  Objects.equals(flags, other.flags);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(spatialFilter, maxResolution, minResolution, targetResolution,
                    types, authoritative, minCE, minLE, order, flags);
        }
    }
}
