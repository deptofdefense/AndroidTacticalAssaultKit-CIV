package com.atakmap.map.elevation;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.interop.Pointer;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.mosaic.FilterMosaicDatabaseCursor2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2.Cursor;
import com.atakmap.map.layer.raster.mosaic.MultiplexingMosaicDatabaseCursor2;
import com.atakmap.spi.PriorityServiceProviderRegistry2;
import com.atakmap.util.Filter;

public final class ElevationManager {

    private final static Map<MosaicDatabase2, ElevationSource> dbs = new IdentityHashMap<MosaicDatabase2, ElevationSource>();
    private final static PriorityServiceProviderRegistry2<ElevationData, ImageInfo, ElevationDataSpi> dataSpiRegistry = new PriorityServiceProviderRegistry2<ElevationData, ImageInfo, ElevationDataSpi>();

    private ElevationManager() {}

    // XXX - single image registration? 

    /**
     * Registers a catalog of elevation data. The supplied interface only
     * needs to implement the {@link MosaicDatabase2#query(MosaicDatabase2.QueryParameters)}
     * method.
     *
     * @param mosaic
     */
    public static synchronized void registerElevationSource(MosaicDatabase2 mosaic) {
        if(dbs.containsKey(mosaic))
            return;
        ElevationSource src = Adapter.adapt("MosaicDatabase2@" + mosaic.hashCode(), mosaic, false);
        dbs.put(mosaic, src);
        ElevationSourceManager.attach(src);
    }
    
    public static synchronized void unregisterElevationSource(MosaicDatabase2 mosaic) {
        ElevationSource src = dbs.remove(mosaic);
        if(src == null)
            return;
        ElevationSourceManager.detach(src);
    }

    /**
     *
     * @param params
     * @return
     *
     * @deprecated use {@link #queryElevationSources(ElevationSource.QueryParameters)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static synchronized MosaicDatabase2.Cursor queryElevationData(QueryParameters params) {
        MosaicDatabase2.QueryParameters mparams = null;
        Collection<Filter<MosaicDatabase2.Cursor>> filters = null;
        boolean isReject = false;
        if(params != null) {
            mparams = new MosaicDatabase2.QueryParameters();
            mparams.minGsd = params.minResolution;
            mparams.maxGsd = params.maxResolution;
            mparams.spatialFilter = params.spatialFilter;
            mparams.types = params.types;
            
            // terrain/surface filtering
            if(params.elevationModel != (ElevationData.MODEL_SURFACE|ElevationData.MODEL_TERRAIN)) {
                switch (params.elevationModel) {
                    case 0:
                        isReject = true;
                        break;
                    case ElevationData.MODEL_SURFACE:
                        filters = Collections.singleton(ElevationModelFilter.SURFACE);
                        break;
                    case ElevationData.MODEL_TERRAIN:
                        filters = Collections.singleton(ElevationModelFilter.TERRAIN);
                        break;
                    default:
                        filters = Collections.<Filter<MosaicDatabase2.Cursor>>singleton(new ElevationModelFilter(params.elevationModel));
                        break;
                }
            }
        }
        
        Collection<MosaicDatabase2.Cursor> results = new LinkedList<MosaicDatabase2.Cursor>();
        if(!isReject) {
            for(MosaicDatabase2 db : dbs.keySet())
                results.add(db.query(mparams));
        }
        MosaicDatabase2.Cursor retval = new MultiplexingMosaicDatabaseCursor2(results, null);
        if(filters != null)
            retval = FilterMosaicDatabaseCursor2.filter(retval, filters);
        return retval;
    }

    public static ElevationSource.Cursor queryElevationSources(ElevationSource.QueryParameters params) {
        Pointer cparams = null;
        try {
            if(params != null) {
                cparams = NativeElevationSource.QueryParameters_create();
                NativeElevationSource.QueryParameters_adapt(params, cparams.raw);
            }

            Pointer retval = queryElevationSources((cparams != null) ? cparams.raw : 0L);
            if(retval == null)
                throw new IllegalStateException();
            return new NativeElevationSourceCursor(retval, null);
        } finally {
            if(cparams != null)
                NativeElevationSource.QueryParameters_destruct(cparams);
        }
    }

    public static int queryElevationSourcesCount(ElevationSource.QueryParameters params) {
        Pointer cparams = null;
        try {
            if(params != null) {
                cparams = NativeElevationSource.QueryParameters_create();
                NativeElevationSource.QueryParameters_adapt(params, cparams.raw);
            }

            return queryElevationSourcesCount((cparams != null) ? cparams.raw : 0L);
        } finally {
            if(cparams != null)
                NativeElevationSource.QueryParameters_destruct(cparams);
        }
    }

    /**************************************************************************/
    
    /**
     * Returns the elevation, as meters HAE, at the specified location. A value
     * of <code>Double.NaN</code> is returned if no elevation is available.
     * 
     * @param latitude  The latitude
     * @param longitude The longitude
     * 
     * @return  The elevation value at the specified location, in meters HAE, or
     *          <code>Double.NaN</code> if not available.
     */
    public static double getElevation(double latitude, double longitude, QueryParameters filter) {
        return getElevation(latitude, longitude, filter, null);
    }


    /**
     * Returns the elevation, as meters HAE, at the specified location. A value
     * of <code>Double.NaN</code> is returned if no elevation is available.
     * 
     * @param latitude  The latitude
     * @param longitude The longitude
     * @param filter The filter
     * @param geoPointMetaData the geopoint metadata item that represents the query, null if no
     * additional metata is desired. The elevation in the recorded GeoPoint should match the elevation
     * returned.
     * 
     * @return  The elevation value at the specified location, in meters HAE, or
     *          <code>Double.NaN</code> if not available.
     */
    public static double getElevation(double latitude, double longitude, QueryParameters filter, GeoPointMetaData geoPointMetaData) {
        if(filter == null)
            filter = new QueryParameters();
        filter.spatialFilter = new Point(longitude, latitude);
        ElevationSource.QueryParameters params = Adapter.adapt(filter, null);

        Pointer cparams = null;
        try {
            if(params != null) {
                cparams = NativeElevationSource.QueryParameters_create();
                NativeElevationSource.QueryParameters_adapt(params, cparams.raw);
            }

            String[] resultType = (geoPointMetaData != null) ? new String[1] : null;
            
            final double hae;
            if (cparams != null)
                hae = getElevation(latitude, longitude, cparams.raw, resultType);
            else
                hae = Double.NaN;
 
            if(!Double.isNaN(hae) && geoPointMetaData != null) {
                geoPointMetaData.set(new GeoPoint(latitude, longitude, hae));
   
                // perform a check just in case the result type returned from the provider is empty
                if (!FileSystemUtils.isEmpty(resultType))
                    geoPointMetaData.setAltitudeSource(resultType[0]);
                else 
                    geoPointMetaData.setAltitudeSource(GeoPointMetaData.UNKNOWN);

            } else if(geoPointMetaData != null) {
                geoPointMetaData.set(new GeoPoint(latitude,longitude)).setAltitudeSource(GeoPointMetaData.UNKNOWN);
            }
            return hae;
        } finally {
            if(cparams != null)
                NativeElevationSource.QueryParameters_destruct(cparams);
        }
    }
    
    /**
     * Returns elevation values for a set of points.
     * 
     * @param points        The points 
     * @param elevations    Returns the elevation values for the specified
     *                      points
     * @param legacyHints   If non-<code>null</code> specifies a minimum
     *                      bounding box containing all points. The
     *                      implementation may use this information to prefetch
     *                      all data that will be required up front, possibly
     *                      reducing IO.
     * @throws IllegalArgumentException if the points or elevations parameters are incorrect.
     */
    public static boolean getElevation(Iterator<GeoPoint> points, double[] elevations, QueryParameters filter, ElevationData.Hints legacyHints) {

        if(points == null)
            throw new IllegalArgumentException("points iterator cannot be null");
        
        if(!points.hasNext())
            return true;

        if(elevations == null)
            throw new IllegalArgumentException("elevations cannot be null");

        double[] src = new double[elevations.length*3];
        int idx = 0;
        GeoPoint point;
        double north = -90;
        double south = 90;
        double east = -180;
        double west = 180;
        while(points.hasNext()) {
            point = points.next();
            src[idx*3] = point.getLongitude();
            src[idx*3+1] = point.getLatitude();
            src[idx*3+2] = Double.NaN;

            if(idx == elevations.length)
                throw new IllegalArgumentException();
            elevations[idx++] = Double.NaN;

            final double lat = point.getLatitude();
            if(lat > north)
                north = lat;
            if(lat < south)
                south = lat;
            final double lng = point.getLongitude();
            if(lng > east)
                east = lng;
            if(lng < west)
                west = lng;
        }
        final GeoBounds hint = new GeoBounds(north, west, south, east);

        ElevationData.Hints h = legacyHints;
        if (legacyHints == null) {
            h = new ElevationData.Hints();
            h.bounds = new GeoBounds(north, west, south, east);
        } else if (legacyHints.bounds == null) { 
            h = new ElevationData.Hints(legacyHints);
            h.bounds = new GeoBounds(north, west, south, east);
        }
            

        // set up filter
        if(filter != null)
            filter = new QueryParameters(filter);
        else
            filter = new QueryParameters();

        filter.spatialFilter = DatasetDescriptor.createSimpleCoverage(
                new GeoPoint(hint.getNorth(), hint.getWest()),
                new GeoPoint(hint.getNorth(), hint.getEast()),
                new GeoPoint(hint.getSouth(), hint.getEast()),
                new GeoPoint(hint.getSouth(), hint.getWest()));

        final ElevationSource.QueryParameters params = Adapter.adapt(filter, legacyHints);
        Pointer cparams = null;
        try {
            if(params != null) {
                cparams = NativeElevationSource.QueryParameters_create();
                NativeElevationSource.QueryParameters_adapt(params, cparams.raw);
            }

            final boolean done = getElevation(src, idx, (cparams != null) ? cparams.raw : 0L);
            for(int i = 0; i < idx; i++)
                elevations[i] = src[(i*3)+2];
            return done;
        } finally {
            if(cparams != null)
                NativeElevationSource.QueryParameters_destruct(cparams);
        }
    }

    /**
     * Returns elevation values for a set of points.
     * 
     * @param points        The points 
     * @param elevations    Returns the elevation values for the specified
     *                      points
     * @param params       
     * @throws IllegalArgumentException if the points or elevations parameters are incorrect.
     */
    public static boolean getElevation(Iterator<GeoPoint> points, double[] elevations, ElevationSource.QueryParameters params) {

        if(points == null)
            throw new IllegalArgumentException("points iterator cannot be null");

        if(!points.hasNext())
            return true;

        if(elevations == null)
            throw new IllegalArgumentException("elevations cannot be null");

        double[] pts = new double[elevations.length*3];
        int pointCount = 0;
        while(points.hasNext()) {
            final GeoPoint g = points.next();
            pts[pointCount++] = g.getLongitude();
            pts[pointCount++] = g.getLatitude();
            pts[pointCount++] = Double.NaN;
        }
        if(pointCount == 0)
            return true;
        pointCount /= 3;

        Pointer cparams = null;
        try {
            if(params != null) {
                cparams = NativeElevationSource.QueryParameters_create();
                NativeElevationSource.QueryParameters_adapt(params, cparams.raw);
            }

            final boolean done = getElevation(pts, pointCount, (cparams!=null) ? cparams.raw : 0L);
            for(int i = 0; i < pointCount; i++)
                elevations[i] = pts[(i*3)+2];
            return done;
        } finally {
            if(cparams != null)
                NativeElevationSource.QueryParameters_destruct(cparams);
        }
    }

    /**************************************************************************/
    
    public static void registerDataSpi(ElevationDataSpi spi) {
        dataSpiRegistry.register(spi, spi.getPriority());
    }
    
    public static void unregisterDataSpi(ElevationDataSpi spi) {
        dataSpiRegistry.unregister(spi);
    }
    
    public static ElevationData createData(ImageInfo info) {
        return dataSpiRegistry.create(info);
    }


    /**************************************************************************/

    /**
     * Returns the geoid height at the specified location. Geoid height is the
     * offset between MSL and the ellipsoid surface.
     *
     * <P>Conversions are performed as follows:
     *  <UL>
     *      <LI><code>hae = msl + geoidHeight</code></LI>
     *      <LI><code>msl = hae - geoidHeight</code></LI>
     *  </UL>
     * @param latitude
     * @param longitude
     * @return
     */
    public static native double getGeoidHeight(double latitude, double longitude);

    /**************************************************************************/
    
    public final static class QueryParameters {
        public double minResolution;
        public double maxResolution;
        public Geometry spatialFilter;
        public Set<String> types;
        public int elevationModel;
        public boolean preferSpeed;
        public boolean interpolate;
        
        public QueryParameters() {
            this.minResolution = Double.NaN;
            this.maxResolution = Double.NaN;
            this.spatialFilter = null;
            this.types = null;
            this.elevationModel = ElevationData.MODEL_SURFACE|ElevationData.MODEL_TERRAIN;
        }

        public QueryParameters(QueryParameters other) {
            this.minResolution = other.minResolution;
            this.maxResolution = other.maxResolution;
            this.spatialFilter = other.spatialFilter;
            this.types = (other.types != null) ? new HashSet<String>(other.types) : null;
            this.elevationModel = other.elevationModel;
        }
    }
    
    /**************************************************************************/
    
    private final static class ElevationModelFilter implements Filter<MosaicDatabase2.Cursor> {
        public final static Filter<MosaicDatabase2.Cursor> SURFACE = new ElevationModelFilter(ElevationData.MODEL_SURFACE);
        public final static Filter<MosaicDatabase2.Cursor> TERRAIN = new ElevationModelFilter(ElevationData.MODEL_TERRAIN);
        
        private final int model;
        
        public ElevationModelFilter(int model) {
            this.model = model;
        }

        @Override
        public boolean accept(Cursor arg) {
            final ElevationData data = createData(arg.asFrame());
            if(data == null)
                return false;
            return ((data.getElevationModel()&this.model) != 0);
        }
        
    }

    /*************************************************************************/

    static native Pointer queryElevationSources(long cparams);
    static native int queryElevationSourcesCount(long cparams);
    static native double getElevation(double latitude, double longitude, long cfilter, String[] resultSource);
    static native boolean getElevation(double[] lla, int count, long cparams);
}
