
package com.atakmap.android.maps.graphics;

import com.atakmap.android.maps.Ellipse;
import com.atakmap.map.MapRenderer;

public class GLEllipse extends GLPolyline {

    public GLEllipse(MapRenderer surface, Ellipse subject) {
        super(surface, subject);
        this.needsProjectVertices = false;
    }
}
