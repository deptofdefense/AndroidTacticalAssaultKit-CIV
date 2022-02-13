package com.atakmap.map.layer.feature;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.Style;

/**
 * The definition of a feature. Feature properties may be recorded as raw,
 * unprocessed data of several well-defined types. Utilization of
 * unprocessed data may yield significant a performance advantage depending
 * on the intended storage. 
 *  
 * @author Developer
 */
public interface FeatureDefinition {
    /**
     * {@link #rawGeom} is the a {@link String} that is the OGC WKT
     * representation for geometry.
     */
    public static final int GEOM_WKT = 0;
    
    /**
     * {@link #rawGeom} is the a <code>byte[]</code> that is the OGC WKB
     * representation for geometry.
     */
    public static final int GEOM_WKB = 1;
    
    /**
     * {@link #rawGeom} is the a <code>byte[]</code> that is the SpataiLite
     * blob representation for geometry.
     */
    public static final int GEOM_SPATIALITE_BLOB = 2;
    
    /**
     * {@link #rawGeom} is the a {@link Geometry} object..
     */
    public static final int GEOM_ATAK_GEOMETRY = 3;
    
    /**
     * {@link #rawGeom} is the a {@link String} that adheres toe the OGR
     * Style Specification.
     */
    public static final int STYLE_OGR = 0;
    
    /**
     * {@link #rawGeom} is the a {@link Style} object..
     */
    public static final int STYLE_ATAK_STYLE = 1;
    
    /**
     * The raw geometry data.
     */
    public Object getRawGeometry();
    
    /**
     * The coding of the geometry data.
     */
    public int getGeomCoding();
    
    /**
     * The name of the feature.
     */
    public String getName();
    
    /**
     * The coding of the style data.
     */
    public int getStyleCoding();
    
    /**
     * The raw style data.
     */
    public Object getRawStyle();
    
    /**
     * The feature attributes.
     */
    public AttributeSet getAttributes();

    public Feature get();

} // FeatureDefinition
