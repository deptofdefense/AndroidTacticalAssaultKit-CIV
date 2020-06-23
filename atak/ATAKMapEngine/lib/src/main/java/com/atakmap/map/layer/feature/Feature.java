package com.atakmap.map.layer.feature;

import com.atakmap.interop.Pointer;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyleParser;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.util.ReadWriteLock;

/**
 * A map feature composed of geometry, style and attributes (metadata).
 *  
 * @author Developer
 */
public final class Feature {

    public static enum AltitudeMode {
        Relative,
        Absolute,
        ClampToGround,
    };

    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;

    public Feature(FeatureDefinition def) {
        this(FeatureDataStore2.FEATURESET_ID_NONE,
             FeatureDataStore2.FEATURE_ID_NONE,
             def.getName(),
             getGeometry(def),
             getStyle(def),
             def.getAttributes(),
             getTimestamp(def),
             FeatureDataStore2.FEATURE_VERSION_NONE);
    }

    /**
     * Creates a new instance. The instance is considered orphaned and will not
     * have an ID or parent feature set ID.
     * 
     * @param name          The name of the feature
     * @param geom          The geometry of the feature
     * @param style         The style of the feature
     * @param attributes    The attributes of the feature
     */
    public Feature(String name, Geometry geom, Style style, AttributeSet attributes) {
        this(FeatureDataStore.FEATURESET_ID_NONE,
             FeatureDataStore.FEATURE_ID_NONE,
             name,
             geom,
             style,
             attributes,
             FeatureDataStore2.TIMESTAMP_NONE,
             FeatureDataStore.FEATURE_VERSION_NONE);
    }

    public Feature(long fsid, long fid, String name, Geometry geom, Style style, AttributeSet attributes, long timestamp, long version) {
        this(create(fsid, fid, name, (geom != null) ? Interop.getPointer(geom).raw : 0L, (style != null) ? Interop.getPointer(style).raw : 0L, (attributes != null) ? attributes.pointer.raw : 0L, timestamp, version));
    }

    Feature(Pointer pointer) {
        this.pointer = pointer;
    }
   
    /**
     * Returns the ID of the parent feature set.
     * 
     * @return  The ID of the parent feature set.
     */
    public long getFeatureSetId() {
        this.rwlock.acquireRead();
        try {
            return getFeatureSetId(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the ID of the feature.
     * 
     * @return  The version of the feature.
     */
    public long getId() {
        this.rwlock.acquireRead();
        try {
            return getId(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the version of the feature.
     * 
     * @return  The version of the feature.
     */
    public long getVersion() {
        this.rwlock.acquireRead();
        try {
            return getVersion(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    /**
     * Returns the name of the feature.
     * 
     * @return  The name of the feature.
     */
    public String getName() {
        this.rwlock.acquireRead();
        try {
            return getName(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * The geometry of the feature.
     * 
     * @return  The geometry of the feature.
     */
    public Geometry getGeometry() {
        this.rwlock.acquireRead();
        try {
            return Interop.createGeometry(getGeometry(this.pointer.raw), this);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the style of the feature.
     * 
     * @return  The style of the feature.
     */
    public Style getStyle() {
        this.rwlock.acquireRead();
        try {
            return Interop.createStyle(getStyle(this.pointer.raw), this);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the attributes of the feature.
     * 
     * @return  The attributes of the feature.
     */
    public AttributeSet getAttributes() {
        this.rwlock.acquireRead();
        try {
            final Pointer attrs = getAttributes(this.pointer.raw);
            if(attrs == null)
                return null;
            return new AttributeSet(attrs, this);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public long getTimestamp() {
        this.rwlock.acquireRead();
        try {
            return getTimestamp(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * 
     * @param a
     * @param b
     * @return
     */
    public static boolean isSame(Feature a, Feature b) {
        return (a.getId() == b.getId()) &&
               (a.getVersion() == b.getVersion()) &&
               (a.getId() != FeatureDataStore.FEATURE_ID_NONE) &&
               (a.getId() != FeatureDataStore.FEATURE_ID_NONE);
    }

    static Geometry getGeometry(FeatureDefinition def) {
        Geometry geom = null;
        switch(def.getGeomCoding()) {
            case FeatureDefinition.GEOM_SPATIALITE_BLOB :
                geom = GeometryFactory.parseSpatiaLiteBlob((byte[])def.getRawGeometry());
                break;
            case FeatureDefinition.GEOM_WKB :
                geom = GeometryFactory.parseWkb((byte[])def.getRawGeometry());
                break;
            case FeatureDefinition.GEOM_WKT :
                geom = GeometryFactory.parseWkt((String)def.getRawGeometry());
                break;
            case FeatureDefinition.GEOM_ATAK_GEOMETRY :
                geom = (Geometry)def.getRawGeometry();
                break;
            default :
                throw new UnsupportedOperationException();
        }
        return geom;
    }

    static Style getStyle(FeatureDefinition def) {
        Style style = null;
        switch(def.getStyleCoding()) {
            case FeatureDefinition.STYLE_OGR :
                style = FeatureStyleParser.parse2((String)def.getRawStyle());
                break;
            case FeatureDefinition.STYLE_ATAK_STYLE :
                style = (Style)def.getRawStyle();
                break;
            default :
                throw new UnsupportedOperationException();
        }
        return style;
    }

    private static long getTimestamp(FeatureDefinition def) {
        if(def instanceof FeatureDefinition2)
            return ((FeatureDefinition2)def).getTimestamp();
        else
            return FeatureDataStore2.TIMESTAMP_NONE;
    }

    static native Pointer create(long fsid, long fid, String name, long geom, long style, long attributes, long timestamp, long version);
    static native void destruct(Pointer pointer);

    static native long getFeatureSetId(long ptr);
    static native long getId(long ptr);
    static native long getVersion(long ptr);
    static native String getName(long ptr);
    static native Pointer getGeometry(long ptr);
    static native Pointer getStyle(long ptr);
    static native Pointer getAttributes(long ptr);
    static native long getTimestamp(long ptr);
    
} // Feature
