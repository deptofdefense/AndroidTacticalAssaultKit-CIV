package com.atakmap.map.layer.feature;

import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyleParser;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

/**
 * A map feature composed of geometry, style and attributes (metadata).
 *  
 * @author Developer
 */
public final class Feature implements Disposable {

    final static NativePeerManager.Cleaner CLEANER = new NativePeerManager.Cleaner() {
        @Override
        protected void run(Pointer pointer, Object opaque) {
            destruct(pointer);
        }
    };

    public enum AltitudeMode {
        ClampToGround(0),
        Relative(1),
        Absolute(2);

        final private int val;

        AltitudeMode(final int v) {
            this.val = v;
        }

        public int value() {
            return val;
        }

        /**
         * Given a altitude value as expressed by an integer, return the appropriate enum.
         * @param value a value of either 0, 1, 2
         * @return returns the corresponding altitude mode, if the value passed in is invalid
         * it will return ClampToGround.
         */
        public static AltitudeMode from(int value) {
            switch(value) {
                case 1:
                    return Relative;
                case 2:
                    return Absolute;
                default:
                    return ClampToGround;
            }
        }

    };

    final ReadWriteLock rwlock = new ReadWriteLock();
    Pointer pointer;
    Cleaner cleaner;

    public Feature(FeatureDefinition def) {
        this(FeatureDataStore2.FEATURESET_ID_NONE,
             FeatureDataStore2.FEATURE_ID_NONE,
             def.getName(),
             getGeometry(def),
             getStyle(def),
             def.getAttributes(),
             getAltitudeMode(def),
             getExtrude(def),
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

    /**
     * Creates a new instance. The instance is considered orphaned and will not
     * have an ID or parent feature set ID.
     *
     * @param fsid          The id of the feature
     * @param name          The name of the feature
     * @param geom          The geometry of the feature
     * @param style         The style of the feature
     * @param attributes    The attributes of the feature
     * @param altMode       The altitude mode of the feature
     * @param extrude       The extrusion
     */
    public Feature(long fsid, String name, Geometry geom, Style style, AttributeSet attributes, AltitudeMode altMode, double extrude) {
        this(fsid,
                FeatureDataStore.FEATURE_ID_NONE,
                name,
                geom,
                style,
                attributes,
                altMode,
                extrude,
                FeatureDataStore2.TIMESTAMP_NONE,
                FeatureDataStore.FEATURE_VERSION_NONE);
    }

    /**
     * Creates a new instance. The instance is is considered a member of the FeatureSet but will be
     * assigned an id.
     * @param fsid          The feature set id
     * @param name          The name of the feature
     * @param geom          The geometry of the feature
     * @param style         The style of the feature
     * @param attributes    The attributes of the feature
     */
    public Feature(long fsid, String name, Geometry geom, Style style, AttributeSet attributes) {
        this(fsid,
                FeatureDataStore.FEATURE_ID_NONE,
                name,
                geom,
                style,
                attributes,
                FeatureDataStore2.TIMESTAMP_NONE,
                FeatureDataStore.FEATURE_VERSION_NONE);
    }

    /**
     * Creates a new instance.
     *
     * @param fsid          The featureset id
     * @param fid           The feature id
     * @param name          The name of the feature
     * @param geom          The geometry of the feature
     * @param style         The style of the feature
     * @param attributes    The attributes of the feature
     */
    public Feature(long fsid, long fid,  String name, Geometry geom, Style style, AttributeSet attributes) {
        this(fsid,
                fid,
                name,
                geom,
                style,
                attributes,
                FeatureDataStore2.TIMESTAMP_NONE,
                FeatureDataStore.FEATURE_VERSION_NONE);
    }
    public Feature(long fsid, long fid, String name, Geometry geom, Style style, AttributeSet attributes, long timestamp, long version) {
        this(fsid, fid, name, geom, style, attributes, AltitudeMode.ClampToGround, 0.0, timestamp, version);
    }

    public Feature(long fsid, long fid, String name, Geometry geom, Style style, AttributeSet attributes, AltitudeMode altitudeMode, double extrude, long timestamp, long version) {
        this(create(fsid, fid, name, (geom != null) ? Interop.getPointer(geom).raw : 0L, altitudeMode.value(), extrude, (style != null) ? Interop.getPointer(style).raw : 0L, (attributes != null) ? attributes.pointer.raw : 0L, timestamp, version));
    }

    Feature(Pointer pointer) {
        NativePeerManager.register(this, pointer, rwlock, null, CLEANER);
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

    public AltitudeMode getAltitudeMode() {
        this.rwlock.acquireRead();
        try {
            return AltitudeMode.from(getAltitudeMode(this.pointer.raw));
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public double getExtrude() {
        this.rwlock.acquireRead();
        try {
            return getExtrude(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void dispose() {
        if(cleaner != null)
            cleaner.clean();
    }

    /**
     * Compare two features for similarity.
     * @param a the first feature
     * @param b the second feature
     * @return if the features share the same ID and version
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
                final String rawStyle = (String)def.getRawStyle();
                if (rawStyle != null)
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

    private static Feature.AltitudeMode getAltitudeMode(FeatureDefinition def) {
        if(def instanceof FeatureDefinition3)
            return ((FeatureDefinition3)def).getAltitudeMode();
        else
            return AltitudeMode.ClampToGround;
    }

    private static double getExtrude(FeatureDefinition def) {
        if(def instanceof FeatureDefinition3)
            return ((FeatureDefinition3)def).getExtrude();
        else
            return 0d;
    }


    static native Pointer create(long fsid, long fid, String name, long geom, int attributeMode, double extrude, long style, long attributes, long timestamp, long version);
    static native void destruct(Pointer pointer);

    static native long getFeatureSetId(long ptr);
    static native long getId(long ptr);
    static native long getVersion(long ptr);
    static native String getName(long ptr);
    static native Pointer getGeometry(long ptr);
    static native Pointer getStyle(long ptr);
    static native Pointer getAttributes(long ptr);
    static native long getTimestamp(long ptr);
    static native int getAltitudeMode(long ptr);
    static native double getExtrude(long ptr);
    
} // Feature
