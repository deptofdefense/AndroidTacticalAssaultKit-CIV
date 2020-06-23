package com.atakmap.map.layer.feature.geometry.opengl;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;

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
