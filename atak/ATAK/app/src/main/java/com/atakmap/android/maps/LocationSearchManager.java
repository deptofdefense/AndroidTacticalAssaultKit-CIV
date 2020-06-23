
package com.atakmap.android.maps;

import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

public final class LocationSearchManager {

    private static final Set<LocationSearch> searchEngines = Collections
            .newSetFromMap(new IdentityHashMap<LocationSearch, Boolean>());

    private LocationSearchManager() {
    }

    public static synchronized void register(LocationSearch search) {
        searchEngines.add(search);
    }

    public static synchronized void unregister(LocationSearch search) {
        searchEngines.remove(search);
    }

    public static synchronized SortedSet<Location> find(String searchTerms) {
        SortedSet<Location> retval = new TreeSet<>();

        SortedMap<Float, Location> results;
        for (LocationSearch search : searchEngines) {
            results = search.findLocation(searchTerms);
            for (Map.Entry<Float, Location> entry : results.entrySet())
                retval.add(
                        new RankedLocation(entry.getValue(), entry.getKey()));
        }

        return retval;
    }

    private final static class RankedLocation implements Location,
            Comparable<RankedLocation> {
        private final Location filter;
        private final float confidence;

        RankedLocation(Location filter, float confidence) {
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
        public int compareTo(RankedLocation arg0) {
            if (this.confidence > arg0.confidence)
                return -1;
            else if (this.confidence < arg0.confidence)
                return 1;
            // XXX - compare overlay and UID as well
            return this.getFriendlyName().compareTo(arg0.getFriendlyName());
        }

        boolean equals(RankedLocation arg0) {
            return compareTo(arg0) == 0;
        }

        @Override
        public GeoPointMetaData getLocation() {
            return this.filter.getLocation();
        }

        @Override
        public String getFriendlyName() {
            return this.filter.getFriendlyName();
        }

        @Override
        public String getUID() {
            return this.filter.getUID();
        }

        @Override
        public MapOverlay getOverlay() {
            return this.filter.getOverlay();
        }
    }
}
