package com.atakmap.map.layer.feature.geometry.opengl;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public final class GLGeometryCollection extends GLGeometry {

    private List<GLGeometry> geometries;

    public GLGeometryCollection(GeometryCollection collection) {
        super(collection);
        
        this.geometries = new LinkedList<GLGeometry>();
        Collection<Geometry> c = collection.getGeometries();
        for(Geometry geom : c)
            this.geometries.add(GLGeometry.createRenderer(geom));
    }
    
    public Iterator<GLGeometry> iterator() {
        return this.geometries.iterator();
    }
}
