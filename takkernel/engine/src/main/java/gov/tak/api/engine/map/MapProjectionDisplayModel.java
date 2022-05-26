package gov.tak.api.engine.map;

import com.atakmap.math.GeometryModel;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import gov.tak.api.engine.math.IGeometryModel;
import gov.tak.api.engine.math.PointD;
import gov.tak.api.engine.math.Ray;
import gov.tak.api.engine.math.Vector;
import gov.tak.api.marshal.IMarshal;
import gov.tak.platform.marshal.MarshalManager;

public final class MapProjectionDisplayModel {
    static {
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null)
                    return null;
                else if(in instanceof GeometryModelForwardAdapter)
                    return (T)((GeometryModelForwardAdapter)in)._impl;
                else
                    return (T)new GeometryModelBackwardAdapter((IGeometryModel)in);
            }
        }, IGeometryModel.class, GeometryModel.class);
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null)
                    return null;
                else if(in instanceof GeometryModelBackwardAdapter)
                    return (T)((GeometryModelBackwardAdapter)in)._impl;
                else
                    return (T)new GeometryModelForwardAdapter((GeometryModel)in);
            }
        }, GeometryModel.class, IGeometryModel.class);

        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null) return null;
                synchronized(cache) {
                    do {
                        final WeakReference<MapProjectionDisplayModel> ref = cache.get(in);
                        if (ref == null) break;
                        final MapProjectionDisplayModel value = ref.get();
                        if(value == null) break;

                        return (T)value;
                    } while(false);
                    final MapProjectionDisplayModel marshaled = new MapProjectionDisplayModel(
                            (com.atakmap.map.projection.MapProjectionDisplayModel)in);
                    cache.put((com.atakmap.map.projection.MapProjectionDisplayModel)in, new WeakReference<>(marshaled));
                    return (T)marshaled;
                }
            }
        }, com.atakmap.map.projection.MapProjectionDisplayModel.class, MapProjectionDisplayModel.class);
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null)
                    return null;
                else
                    return (T)((MapProjectionDisplayModel)in)._impl;
            }
        }, MapProjectionDisplayModel.class, com.atakmap.map.projection.MapProjectionDisplayModel.class);
    }

    private final static Map<com.atakmap.map.projection.MapProjectionDisplayModel, WeakReference<MapProjectionDisplayModel>> cache = new WeakHashMap<>();

    /**
     * A representation of the earth, in projection units.
     */
    public final IGeometryModel earth;

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

    final com.atakmap.map.projection.MapProjectionDisplayModel _impl;

    MapProjectionDisplayModel(com.atakmap.map.projection.MapProjectionDisplayModel impl) {
        _impl = impl;

        this.srid = _impl.srid;
        this.earth = MarshalManager.marshal(_impl.earth, GeometryModel.class, IGeometryModel.class);
        this.projectionXToNominalMeters = _impl.projectionXToNominalMeters;
        this.projectionYToNominalMeters = _impl.projectionYToNominalMeters;
        this.projectionZToNominalMeters = _impl.projectionZToNominalMeters;
        this.zIsHeight = _impl.zIsHeight;
    }

    /**************************************************************************/

    public static synchronized MapProjectionDisplayModel getModel(int srid) {
        final com.atakmap.map.projection.MapProjectionDisplayModel impl =
                com.atakmap.map.projection.MapProjectionDisplayModel.getModel(srid);
        WeakReference<MapProjectionDisplayModel> modelRef = cache.get(impl);
        if(modelRef != null) {
            MapProjectionDisplayModel model = modelRef.get();
            if(model != null)
                return model;
            cache.remove(impl);
        }

        // construct from pointer
        final MapProjectionDisplayModel retval = new MapProjectionDisplayModel(impl);
        // add to the registry
        cache.put(impl, new WeakReference<>(retval));

        return retval;
    }

    public static boolean isSupported(int srid) {
        return com.atakmap.map.projection.MapProjectionDisplayModel.isSupported(srid);
    }

    final static class GeometryModelForwardAdapter implements IGeometryModel {
        final GeometryModel _impl;

        GeometryModelForwardAdapter(GeometryModel impl) {
            _impl = impl;
        }


        @Override
        public PointD intersect(Ray ray) {
            com.atakmap.math.PointD result = _impl.intersect(
                    new com.atakmap.math.Ray(
                            new com.atakmap.math.PointD(ray.origin.x, ray.origin.y, ray.origin.z),
                            new com.atakmap.math.Vector3D(ray.direction.x, ray.direction.y, ray.direction.z)
                    ));
            return (result != null) ? new PointD(result.x, result.y, result.z) : null;
        }
    }

    final static class GeometryModelBackwardAdapter implements GeometryModel {
        final IGeometryModel _impl;

        GeometryModelBackwardAdapter(IGeometryModel impl) {
            _impl = impl;
        }


        @Override
        public com.atakmap.math.PointD intersect(com.atakmap.math.Ray ray) {
            PointD result = _impl.intersect(
                    new Ray(
                            new PointD(ray.origin.x, ray.origin.y, ray.origin.z),
                            new Vector(ray.direction.X, ray.direction.Y, ray.direction.Z)
                    ));
            return (result != null) ?
                    new com.atakmap.math.PointD(result.x, result.y, result.z) : null;
        }
    }
}
