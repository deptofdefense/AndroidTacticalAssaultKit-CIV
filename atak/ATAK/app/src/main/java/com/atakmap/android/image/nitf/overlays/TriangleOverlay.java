
package com.atakmap.android.image.nitf.overlays;

import android.graphics.PointF;

import com.atakmap.android.image.nitf.CGM.PolygonCommand;
import com.atakmap.android.image.nitf.CGM.PolylineCommand;

/**
 * Overlay for triangles
 */
public class TriangleOverlay extends FreeformOverlay {

    public static final String ID = "Triangle";

    public TriangleOverlay(PolylineCommand line) {
        super(line);
        this.name = ID;
    }

    public TriangleOverlay(PolygonCommand shape) {
        super(shape);
        this.name = ID;
    }

    @Override
    public void setup(PointF... points) {
        if (points.length >= 3) {
            _p = new PointF[4];
            for (int i = 0; i < _p.length; i++) {
                if (i < points.length)
                    _p[i] = new PointF(points[i].x, points[i].y);
                else
                    _p[i] = _p[0];
            }
            recalcBounds();
        }
    }
}
