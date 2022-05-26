
package com.atakmap.android.maps.graphics;

import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.map.MapRenderer;

public class GLRangeAndBearingMapItemCompat {
    public static GLRangeAndBearingMapItem newInstance(MapRenderer surface,
            RangeAndBearingMapItem arrow) {
        return new GLRangeAndBearingMapItem(surface, arrow);
    }
}
