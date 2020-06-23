package com.atakmap.map.layer.feature.geometry.opengl;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;

public class GLBatchMultiPolygon extends GLBatchGeometryCollection {

    public GLBatchMultiPolygon(GLMapSurface surface) {
        this(surface.getGLMapView());
    }
    public GLBatchMultiPolygon(MapRenderer surface) {
        super(surface, 13, 3, GLMapView.RENDER_PASS_SURFACE);
    }
}
