package com.atakmap.map.layer.raster.mosaic;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.RowIterator;
import com.atakmap.database.RowIteratorWrapper;

import com.atakmap.coremap.log.Log;

public final class MultiplexingMosaicDatabaseCursor2 extends RowIteratorWrapper implements MosaicDatabase2.Cursor {

    static public final String TAG = "MultiplexingMosaicDatabaseCursor2";

    private final static Map<MosaicDatabase2.QueryParameters.Order, Comparator<Entry>> ORDER_COMPARATORS = new HashMap<>();
    static {
        ORDER_COMPARATORS.put(MosaicDatabase2.QueryParameters.Order.MaxGsdDesc, new Comparator<Entry>() {
            @Override
            public int compare(Entry lhs, Entry rhs) {
                if(lhs.maxGsd < rhs.maxGsd)
                    return -1;
                else if(lhs.maxGsd > rhs.maxGsd)
                    return 1;
                // XXX - should consider something faster than string comparison
                return lhs.path.compareTo(rhs.path);
            }
        });
        ORDER_COMPARATORS.put(MosaicDatabase2.QueryParameters.Order.MaxGsdAsc, new Comparator<Entry>() {
            @Override
            public int compare(Entry lhs, Entry rhs) {
                if(lhs.maxGsd < rhs.maxGsd)
                    return 1;
                else if(lhs.maxGsd > rhs.maxGsd)
                    return -1;
                // XXX - should consider something faster than string comparison
                return lhs.path.compareTo(rhs.path);
            }
        });
        ORDER_COMPARATORS.put(MosaicDatabase2.QueryParameters.Order.MinGsdDesc, new Comparator<Entry>() {
            @Override
            public int compare(Entry lhs, Entry rhs) {
                if(lhs.minGsd < rhs.minGsd)
                    return -1;
                else if(lhs.minGsd > rhs.minGsd)
                    return 1;
                // XXX - should consider something faster than string comparison
                return lhs.path.compareTo(rhs.path);
            }
        });
        ORDER_COMPARATORS.put(MosaicDatabase2.QueryParameters.Order.MinGsdAsc, new Comparator<Entry>() {
            @Override
            public int compare(Entry lhs, Entry rhs) {
                if(lhs.minGsd < rhs.minGsd)
                    return 1;
                else if(lhs.minGsd > rhs.minGsd)
                    return -1;
                // XXX - should consider something faster than string comparison
                return lhs.path.compareTo(rhs.path);
            }
        });
    }

    private final Impl impl;

    public MultiplexingMosaicDatabaseCursor2(Collection<MosaicDatabase2.Cursor> cursors, MosaicDatabase2.QueryParameters.Order order) {
        super(new Impl(cursors, order));
        
        this.impl = (Impl)this.filter;
    }

    @Override
    public GeoPoint getUpperLeft() {
        return this.impl.current.getUpperLeft();
    }

    @Override
    public GeoPoint getUpperRight() {
        return this.impl.current.getUpperRight();
    }

    @Override
    public GeoPoint getLowerRight() {
        return this.impl.current.getLowerRight();
    }

    @Override
    public GeoPoint getLowerLeft() {
        return this.impl.current.getLowerLeft();
    }

    @Override
    public double getMinLat() {
        return this.impl.current.getMinLat();
    }

    @Override
    public double getMinLon() {
        return this.impl.current.getMinLon();
    }

    @Override
    public double getMaxLat() {
        return this.impl.current.getMaxLat();
    }

    @Override
    public double getMaxLon() {
        return this.impl.current.getMaxLon();
    }

    @Override
    public String getPath() {
        return this.impl.current.getPath();
    }

    @Override
    public String getType() {
        return this.impl.current.getType();
    }

    @Override
    public double getMinGSD() {
        return this.impl.current.getMinGSD();
    }

    @Override
    public double getMaxGSD() {
        return this.impl.current.getMaxGSD();
    }

    @Override
    public int getWidth() {
        return this.impl.current.getWidth();
    }

    @Override
    public int getHeight() {
        return this.impl.current.getHeight();
    }

    @Override
    public int getId() {
        // XXX - 
        return this.impl.current.getId();
    }
    
    @Override
    public MosaicDatabase2.Frame asFrame() {
        return this.impl.current.asFrame();
    }
    
    @Override
    public int getSrid() {
        return this.impl.current.getSrid();
    }
    
    @Override
    public boolean isPrecisionImagery() {
        return this.impl.current.isPrecisionImagery();
    }

    /**************************************************************************/
    
    private static class Impl implements RowIterator {
        private final Collection<MosaicDatabase2.Cursor> cursors;
        public MosaicDatabase2.Cursor current;

        private SortedSet<Entry> pendingResults;
        private Collection<MosaicDatabase2.Cursor> invalid;

        public Impl(Collection<MosaicDatabase2.Cursor> cursors, MosaicDatabase2.QueryParameters.Order order) {
            this.cursors = cursors;
            this.current = null;
            if(order == null)
                order = MosaicDatabase2.QueryParameters.Order.MaxGsdDesc;
            this.pendingResults = new TreeSet<Entry>(ORDER_COMPARATORS.get(order));
            this.invalid = new LinkedList<MosaicDatabase2.Cursor>(this.cursors);
        }

        @Override
        public boolean moveToNext() {
            // update entries for any cursors marked 'invalid'
            for(MosaicDatabase2.Cursor cursor : this.invalid) {
                try { 
                    if(cursor.moveToNext())
                        this.pendingResults.add(new Entry(cursor));
                } catch (Exception e) { 
                    Log.e(TAG, "bad cursor encountered, will be cleared", e);
                }
            }
            // all cursors are now valid
            this.invalid.clear();
            
            // if there are no pending results, we've exhausted the input
            if(this.pendingResults.isEmpty())
                return false;
            
            // remove the cursor pointing at the frame with the highest
            // resolution (numerically lowest GSD value)
            Entry entry =  this.pendingResults.first();
            this.pendingResults.remove(entry);
            // now removed, we want to re-evaluate the next call to 'moveToNext'
            // so place it in the invalid list
            this.invalid.add(entry.cursor);
            // reset our current pointer
            this.current = entry.cursor;
            
            return true;
        }

        @Override
        public void close() {
            for(MosaicDatabase2.Cursor cursor : this.cursors)
                cursor.close();
            this.cursors.clear();
            
            this.invalid.clear();
            this.pendingResults.clear();
        }
        @Override
        public boolean isClosed() {
            return this.cursors.isEmpty();
        }
    }

    private final static class Entry {
        final double minGsd;
        final double maxGsd;
        final String path;
        final MosaicDatabase2.Cursor cursor;
        
        public Entry(MosaicDatabase2.Cursor cursor) {
            this.minGsd = cursor.getMinGSD();
            this.maxGsd = cursor.getMaxGSD();
            this.path = cursor.getPath();
            this.cursor = cursor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Entry entry = (Entry) o;

            if (Double.compare(entry.maxGsd, maxGsd) != 0) return false;
            if (path != null ? !path.equals(entry.path) : entry.path != null) return false;
            return cursor != null ? cursor.equals(entry.cursor) : entry.cursor == null;
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            temp = Double.doubleToLongBits(maxGsd);
            result = (int) (temp ^ (temp >>> 32));
            result = 31 * result + (path != null ? path.hashCode() : 0);
            result = 31 * result + (cursor != null ? cursor.hashCode() : 0);
            return result;
        }
    }
}
