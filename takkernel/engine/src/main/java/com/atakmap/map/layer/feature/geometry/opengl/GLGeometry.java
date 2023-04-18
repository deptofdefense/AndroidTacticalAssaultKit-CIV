
package com.atakmap.map.layer.feature.geometry.opengl;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public final class GLGeometry {
    public final static int VERTICES_PROJECTED = 0;
    public final static int VERTICES_PIXEL = 1;
    public final static int VERTICES_BATCH = 2;
}
