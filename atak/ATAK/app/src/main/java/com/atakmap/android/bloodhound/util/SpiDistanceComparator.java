
package com.atakmap.android.bloodhound.util;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;

import java.util.Comparator;

public class SpiDistanceComparator implements Comparator<MapItem> {

    private final PointMapItem _ref;

    public SpiDistanceComparator(PointMapItem ref) {
        _ref = ref;
    }

    @Override
    public int compare(final MapItem lhs, final MapItem rhs) {
        if (lhs == rhs)
            return 0;

        if (!(lhs instanceof PointMapItem))
            return 1;
        else if (!(rhs instanceof PointMapItem))
            return -1;

        PointMapItem plhs = (PointMapItem) lhs;
        PointMapItem prhs = (PointMapItem) rhs;
        if (plhs.getPoint() == null || !plhs.getPoint().isValid())
            return 1;
        else if (prhs.getPoint() == null || !prhs.getPoint().isValid())
            return -1;

        double lhsDist = _ref.getPoint().distanceTo(plhs.getPoint());
        double rhsDist = _ref.getPoint().distanceTo(prhs.getPoint());

        return lhsDist < rhsDist ? -1 : 1;
    }
}
