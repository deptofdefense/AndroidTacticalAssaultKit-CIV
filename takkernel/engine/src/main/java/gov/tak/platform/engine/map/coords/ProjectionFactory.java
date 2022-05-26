package gov.tak.platform.engine.map.coords;

import com.atakmap.map.projection.ECEFProjection;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.WebMercatorProjection;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import gov.tak.api.engine.map.coords.IProjection;
import gov.tak.platform.marshal.MarshalManager;

public final class ProjectionFactory {
    private static Map<Integer, WeakReference<IProjection>> cache = new HashMap<>();

    private ProjectionFactory() {}

    public static IProjection getProjection(int srid) {
        // short-circuit for most frequently used projections
        if(srid == ProjectionImpl.ECEF.getSpatialReferenceID())
            return ProjectionImpl.ECEF;
        else if(srid == ProjectionImpl.EQUIRECTANGULAR.getSpatialReferenceID())
            return ProjectionImpl.EQUIRECTANGULAR;
        else if(srid == ProjectionImpl.WEB_MERCATOR.getSpatialReferenceID())
            return ProjectionImpl.WEB_MERCATOR;

        synchronized (cache) {
            WeakReference<IProjection> cached = cache.get(srid);
            IProjection impl;
            do {
                if(cached == null)
                    break;
                impl = cached.get();
                if(impl != null)
                    return impl;
            } while(false);
            final com.atakmap.map.projection.Projection p =
                    com.atakmap.map.projection.ProjectionFactory.getProjection(srid);
            if(p == null) return null;
            impl = new ProjectionImpl(p);
            cache.put(srid, new WeakReference<>(impl));
            return impl;
        }
    }

    final static class ProjectionImpl implements IProjection {
        final static IProjection EQUIRECTANGULAR = new ProjectionImpl(EquirectangularMapProjection.INSTANCE);
        final static IProjection WEB_MERCATOR = new ProjectionImpl(WebMercatorProjection.INSTANCE);
        final static IProjection ECEF = new ProjectionImpl(ECEFProjection.INSTANCE);

        final com.atakmap.map.projection.Projection _impl;

        ProjectionImpl(com.atakmap.map.projection.Projection impl) {
            _impl = impl;
        }

        @Override
        public boolean forward(gov.tak.api.engine.map.coords.GeoPoint g, gov.tak.api.engine.math.PointD p) {
            final com.atakmap.math.PointD result = _impl.forward(MarshalManager.marshal(g, gov.tak.api.engine.map.coords.GeoPoint.class, com.atakmap.coremap.maps.coords.GeoPoint.class), null);
            if (result == null) return false;
            p.x = result.x;
            p.y = result.y;
            p.z = result.z;
            return true;
        }

        @Override
        public boolean inverse(gov.tak.api.engine.math.PointD p, gov.tak.api.engine.map.coords.GeoPoint g) {
            com.atakmap.coremap.maps.coords.GeoPoint.createMutable();
            com.atakmap.coremap.maps.coords.GeoPoint legacy =_impl.inverse(new com.atakmap.math.PointD(p.x, p.y, p.z), null);
            if(legacy == null)  return false;
            g.set(legacy.getLatitude(),
                    legacy.getLongitude(),
                    legacy.getAltitude(),
                    MarshalManager.marshal(legacy.getAltitudeReference(), com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference.class, gov.tak.api.engine.map.coords.GeoPoint.AltitudeReference.class),
                    legacy.getCE(),
                    legacy.getLE());
            return true;
        }

        @Override
        public int getSpatialReferenceID() {
            return _impl.getSpatialReferenceID();
        }
    }
}
