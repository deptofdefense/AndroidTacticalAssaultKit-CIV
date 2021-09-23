
package com.atakmap.android.maps;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

public final class LocationSearchManager {

    private static final Set<LocationSearch> searchEngines = Collections
            .newSetFromMap(new IdentityHashMap<>());

    private LocationSearchManager() {
    }

    public static synchronized void register(LocationSearch search) {
        searchEngines.add(search);
    }

    public static synchronized void unregister(LocationSearch search) {
        searchEngines.remove(search);
    }

    public static synchronized SortedSet<ILocation> find(String searchTerms) {
        SortedSet<ILocation> retval = new TreeSet<>();

        SortedMap<Float, ILocation> results;
        for (LocationSearch search : searchEngines) {
            results = search.findLocation(searchTerms);
            for (Map.Entry<Float, ILocation> entry : results.entrySet())
                retval.add(
                        new RankedLocation(entry.getValue(), entry.getKey()));
        }

        return retval;
    }

    private final static class RankedLocation implements ILocation,
            Comparable<RankedLocation> {
        private final ILocation filter;
        private final float confidence;

        RankedLocation(ILocation filter, float confidence) {
            this.filter = filter;
            this.confidence = confidence;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            RankedLocation that = (RankedLocation) o;

            if (Float.compare(that.confidence, confidence) != 0)
                return false;
            return filter != null ? filter.equals(that.filter)
                    : that.filter == null;

        }

        @Override
        public int hashCode() {
            int result = filter != null ? filter.hashCode() : 0;
            result = 31
                    * result
                    + (confidence != +0.0f ? Float.floatToIntBits(confidence)
                            : 0);
            return result;
        }

        @Override
        public int compareTo(RankedLocation other) {
            if (this.confidence > other.confidence)
                return -1;
            else if (this.confidence < other.confidence)
                return 1;
            if (this.filter instanceof HierarchyListItem) {
                if (other.filter instanceof HierarchyListItem) {
                    String t1 = ((HierarchyListItem) this.filter).getTitle();
                    String t2 = ((HierarchyListItem) other.filter).getTitle();
                    return t1 != null && t2 != null ? t1.compareTo(t2)
                            : (t1 != null ? -1 : 0);
                }
                return -1;
            } else if (other.filter instanceof HierarchyListItem)
                return 1;
            return 0;
        }

        @Override
        public GeoPoint getPoint(GeoPoint point) {
            return this.filter.getPoint(point);
        }

        @Override
        public GeoBounds getBounds(MutableGeoBounds bounds) {
            return this.filter.getBounds(bounds);
        }
    }
}
