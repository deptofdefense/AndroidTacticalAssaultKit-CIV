
package com.atakmap.android.maps.graphics;

import com.atakmap.android.maps.SimpleRectangle;
import com.atakmap.android.maps.SimpleRectangle.OnRectFillTypeChangedListener;
import com.atakmap.android.maps.SimpleRectangle.OnRectPropertiesChangedListener;
import com.atakmap.map.MapRenderer;

public class GLRectangle extends GLPolyline implements
        OnRectPropertiesChangedListener,
        OnRectFillTypeChangedListener {

    public GLRectangle(MapRenderer surface, SimpleRectangle subject) {
        super(surface, subject);
        this.needsProjectVertices = false;
    }

    @Override
    public void onRectPropertiesChanged(SimpleRectangle rectangle,
            int changed) {
    }

    @Override
    public void onRectFillTypeChanged(SimpleRectangle rectangle) {
    }
}
