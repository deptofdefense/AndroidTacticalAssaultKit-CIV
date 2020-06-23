
package com.atakmap.spatial.file.export;

import com.atakmap.coremap.maps.coords.GeoPoint;

import org.gdal.ogr.Geometry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Wrapper for exporting map items to OGR
 * 
 * 
 */
public class OGRFeatureExportWrapper {

    /**
     * Wrap a geometry to give it a name
     */
    public static class NamedGeometry {
        private final Geometry geometry;
        private final String name;

        public NamedGeometry(Geometry geometry, String name) {
            super();
            this.geometry = geometry;
            this.name = name;
        }

        public Geometry getGeometry() {
            return geometry;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * Map feature type to list of geometries
     */
    private final Map<Integer, List<NamedGeometry>> geometries;
    private final String groupName;

    public OGRFeatureExportWrapper(String name) {
        geometries = new HashMap<>();
        this.groupName = name;
    }

    public OGRFeatureExportWrapper(String name, Integer featureType,
            NamedGeometry geometry) {
        geometries = new HashMap<>();
        List<NamedGeometry> geom = new ArrayList<>();
        geom.add(geometry);
        geometries.put(featureType, geom);
        this.groupName = name;
    }

    public String getName() {
        return groupName;
    }

    public Map<Integer, List<NamedGeometry>> getGeometries() {
        return geometries;
    }

    public void addGeometries(Integer featureType,
            List<NamedGeometry> geometries) {
        if (geometries == null || geometries.size() < 1)
            return;

        List<NamedGeometry> g = this.geometries.get(featureType);
        if (g == null) {
            g = new ArrayList<>();
            this.geometries.put(featureType, g);
        }

        g.addAll(geometries);
    }

    public void addGeometries(OGRFeatureExportWrapper itemFolder) {
        if (itemFolder == null || itemFolder.isEmpty())
            return;

        for (Entry<Integer, List<NamedGeometry>> e : itemFolder.geometries
                .entrySet()) {
            if (e == null || e.getValue() == null || e.getValue().size() < 1)
                continue;

            addGeometries(e.getKey(), e.getValue());
        }
    }

    public boolean isEmpty() {
        return this.geometries == null || this.geometries.size() < 1;
    }

    public static void addPoint(Geometry geo, GeoPoint p, double unwrap) {
        double x = p.getLongitude();
        double y = p.getLatitude();
        if (unwrap > 0 && x < 0 || unwrap < 0 && x > 0)
            x += unwrap;
        geo.AddPoint(x, y);
    }
}
