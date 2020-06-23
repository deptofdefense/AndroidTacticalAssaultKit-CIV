
package com.atakmap.android.maps.graphics;

import com.atakmap.android.maps.AxisOfAdvance;
import com.atakmap.map.MapRenderer;

public class GLAxisOfAdvance extends GLPolyline {

    public GLAxisOfAdvance(MapRenderer surface, AxisOfAdvance subject) {
        super(surface, subject);
        this.needsProjectVertices = false;
    }
}
