
package com.atakmap.android.maps.graphics;

import com.atakmap.android.maps.Shape;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapView;

/**
 * @deprecated Use {@link com.atakmap.android.maps.graphics.GLShape2}
 */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public abstract class GLShape extends GLShape2 {

    public GLShape(final MapRenderer surface, final Shape subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SURFACE);
    }
}
