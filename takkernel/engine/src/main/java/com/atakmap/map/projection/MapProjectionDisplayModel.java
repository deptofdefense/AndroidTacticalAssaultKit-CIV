package com.atakmap.map.projection;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import com.atakmap.interop.InteropCleaner;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.map.Interop;
import com.atakmap.math.GeometryModel;
import com.atakmap.math.Plane;
import com.atakmap.math.PointD;
import com.atakmap.math.Vector3D;
import com.atakmap.util.ReadWriteLock;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public final class MapProjectionDisplayModel {

    final static NativePeerManager.Cleaner CLEANER = new InteropCleaner(MapProjectionDisplayModel.class);

    final static Interop<GeometryModel> GeometryModel_interop = Interop.findInterop(GeometryModel.class);

    private final static Map<Long, WeakReference<MapProjectionDisplayModel>> registry = new HashMap<>();

    private final static Plane DEFAULT_PLANE = new Plane(new Vector3D(0, 0, 1), new PointD(0d, 0d));

    /**
     * A representation of the earth, in projection units.
     */
    public final GeometryModel earth;
    
    /**
     * The Spatial Reference ID of the projection.
     */
    public final int srid;
    
    /**
     * If <code>true</code>, the z component of the projected coordinate space
     * corresponds to elevation/height.
     */
    public final boolean zIsHeight;
    
    public final double projectionXToNominalMeters;
    public final double projectionYToNominalMeters;
    public final double projectionZToNominalMeters;

    final ReadWriteLock rwlock = new ReadWriteLock();

    Pointer pointer;
    Object owner;

    MapProjectionDisplayModel(int srid,
                              GeometryModel earth,
                              double projectionXToNominalMeters,
                              double projectionYToNominalMeters,
                              double projectionZToNominalMeters,
                              boolean zIsHeight) {

        final long gmptr = GeometryModel_interop.getPointer(earth);
        if (gmptr != 0L) {
            this.pointer = create(srid, gmptr, projectionXToNominalMeters, projectionYToNominalMeters, projectionZToNominalMeters, zIsHeight);
        } else {
            this.pointer = wrap(srid, earth, projectionXToNominalMeters, projectionYToNominalMeters, projectionZToNominalMeters, zIsHeight);
        }
        NativePeerManager.register(this, this.pointer, rwlock, null, CLEANER);

        this.srid = srid;
        this.earth = earth;
        this.projectionXToNominalMeters = projectionXToNominalMeters;
        this.projectionYToNominalMeters = projectionYToNominalMeters;
        this.projectionZToNominalMeters = projectionZToNominalMeters;
        this.zIsHeight = zIsHeight;
    }

    MapProjectionDisplayModel(Pointer pointer, Object owner) {
        NativePeerManager.register(this, pointer, rwlock, null, CLEANER);

        this.pointer = pointer;
        this.owner = owner;

        this.srid = getSRID(this.pointer.raw);
        this.earth = GeometryModel_interop.create(getEarth(this.pointer.raw), this);
        this.projectionXToNominalMeters = getProjectionXToNominalMeters(this.pointer.raw);
        this.projectionYToNominalMeters = getProjectionYToNominalMeters(this.pointer.raw);
        this.projectionZToNominalMeters = getProjectionZToNominalMeters(this.pointer.raw);
        this.zIsHeight = getZIsHeight(this.pointer.raw);
    }

    /**************************************************************************/

    static synchronized void registerModel(MapProjectionDisplayModel model) {
        if(model.pointer.type != Pointer.SHARED)
            throw new IllegalStateException();

        if(registry.containsKey(model.pointer.raw))
            return;

        registry.put(model.pointer.raw, new WeakReference<>(model));
        registerImpl(model.pointer);
    }
    
    static synchronized void unregisterModel(MapProjectionDisplayModel model) {
        for(Map.Entry<Long, WeakReference<MapProjectionDisplayModel>> entry : registry.entrySet()) {
            if(model == entry.getValue().get()) {
                registry.remove(entry.getKey());

                unregisterImpl(entry.getKey());

                // finalization will destruct the pointer
                break;
            }
        }
    }
    
    public static synchronized MapProjectionDisplayModel getModel(int srid) {
        // XXX - if this method hotspots, we may have to reimplement

        final Pointer pointer = get(srid);
        if(pointer == null)
            return null;

        WeakReference<MapProjectionDisplayModel> modelRef = registry.get(pointer.raw);
        if(modelRef != null) {
            MapProjectionDisplayModel model = modelRef.get();
            if(model != null)
                return model;
            registry.remove(pointer.raw);
        }

        // construct from pointer
        final MapProjectionDisplayModel retval = new MapProjectionDisplayModel(pointer, null);
        // add to the registry
        registry.put(pointer.raw, new WeakReference<>(retval));

        return retval;
    }

    private static GeometryModel createClone(long pointer) {
        // clone the C++ GeometryModel
        Pointer clonePtr = GeometryModel_interop.clone(pointer);
        // derive the corresponding Java object
        return GeometryModel_interop.create(clonePtr);
    }

    public static native boolean isSupported(int srid);

    /**
     * Creates a default model for planar coordinate systems with the horizontal
     * coordinate system specified in degrees Latitude and Longitude and the
     * vertical coordinate system expressed as meters HAE.
     * 
     * @param srid  The spatial reference ID of the projection
     * 
     * @return  The model
     */    
    static MapProjectionDisplayModel createDefaultLLAPlanarModel(int srid) {
        // NOTE: we are using nominal conversions to meters for latitude and
        //       longitude at 0N, derived from
        //https://msi.nga.mil/MSISiteContent/StaticFiles/Calculators/degree.html
        return new MapProjectionDisplayModel(srid,
                                             DEFAULT_PLANE,
                                             111319d,
                                             110574d,
                                             1d,
                                             true);
    }
    
    /**
     * Creates a default model for planar coordinate systems with the horizontal
     * coordinate system specified in meters Easting and Northing and the
     * vertical coordinate system expressed as meters HAE.
     * 
     * @param srid  The spatial reference ID of the projection
     * 
     * @return  The model
     */
    static MapProjectionDisplayModel createDefaultENAPlanarModel(int srid) {
        return new MapProjectionDisplayModel(srid,
                                             DEFAULT_PLANE,
                                             1d,
                                             1d,
                                             1d,
                                             true);
    }

    // Interop<Projection>
    static long getPointer(MapProjectionDisplayModel obj) {
        if(obj == null)
            return 0L;
        return obj.pointer.raw;
    }
    static MapProjectionDisplayModel create(Pointer pointer, Object owner) {
        return new MapProjectionDisplayModel(pointer, owner);
    }
    static Pointer clone(long pointer) {
        return create(getSRID(pointer),
                      getEarth(pointer).raw,
                      getProjectionXToNominalMeters(pointer),
                      getProjectionYToNominalMeters(pointer),
                      getProjectionZToNominalMeters(pointer),
                      getZIsHeight(pointer));
    }
    static native void destruct(Pointer pointer);


    static native void registerImpl(Pointer pointer);
    static native void unregisterImpl(long pointer);

    static native Pointer create(int srid,
                                 long earth,
                                 double projectionXToNominalMeters,
                                 double projectionYToNominalMeters,
                                 double projectionZToNominalMeters,
                                 boolean zIsHeight);
    static native Pointer wrap(int srid,
                               GeometryModel earth,
                               double projectionXToNominalMeters,
                               double projectionYToNominalMeters,
                               double projectionZToNominalMeters,
                               boolean zIsHeight);

    static native Pointer get(int srid);

    /**
     * <P><B>IMPORTANT:</B> The lifetime of the returned pointer is tied to the
     * lifetime of the <code>MapProjectionDisplayModel</code> pointer. If the
     * geometry model will be needed outside of that time, it should be cloned.
     *
     * @param pointer
     * @return
     */
    static native Pointer getEarth(long pointer);
    static native int getSRID(long pointer);
    static native boolean getZIsHeight(long pointer);
    static native double getProjectionXToNominalMeters(long pointer);
    static native double getProjectionYToNominalMeters(long pointer);
    static native double getProjectionZToNominalMeters(long pointer);
}
