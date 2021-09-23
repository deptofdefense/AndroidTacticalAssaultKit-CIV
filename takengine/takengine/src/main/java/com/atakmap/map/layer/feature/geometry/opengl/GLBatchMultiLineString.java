package com.atakmap.map.layer.feature.geometry.opengl;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;

public class GLBatchMultiLineString extends GLBatchGeometryCollection {

    public GLBatchMultiLineString(GLMapSurface surface) {
        this(surface.getGLMapView());
    }
    
    public GLBatchMultiLineString(MapRenderer surface) {
        super(surface, 12, 2, GLMapView.RENDER_PASS_SURFACE);
    }
}
