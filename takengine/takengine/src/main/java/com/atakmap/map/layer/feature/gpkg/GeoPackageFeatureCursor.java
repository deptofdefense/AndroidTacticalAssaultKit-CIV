package com.atakmap.map.layer.feature.gpkg;

import com.atakmap.database.RowIterator;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;

public interface GeoPackageFeatureCursor extends RowIterator {
    public AttributeSet getAttributes();
    public Geometry getGeometry();
    public long getID();
    public String getName();
    public Style getStyle();
    public long getVersion();
}
