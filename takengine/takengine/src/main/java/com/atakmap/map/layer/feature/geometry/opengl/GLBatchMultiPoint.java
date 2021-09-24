package com.atakmap.map.layer.feature.geometry.opengl;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;

public class GLBatchMultiPoint extends GLBatchGeometryCollection {

    public GLBatchMultiPoint(GLMapSurface surface) {
        this(surface.getGLMapView());
    }

    public GLBatchMultiPoint(MapRenderer surface) {
        super(surface, 11, 1, GLMapView.RENDER_PASS_SPRITES);
    }
}
