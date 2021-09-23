
package com.atakmap.android.maps.graphics;

import com.atakmap.android.widgets.AutoSizeAngleOverlayShape;
import com.atakmap.map.MapRenderer;

public class GLAngleOverlay2Compat {
    public static GLAngleOverlay2 newInstance(MapRenderer surface,
            AutoSizeAngleOverlayShape subject) {
        return new GLAngleOverlay2(surface, subject);
    }
}
