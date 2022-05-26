package com.atakmap.map.elevation;

import com.atakmap.coremap.maps.conversion.EGM96;


import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.lang.Unsafe;
import com.atakmap.map.layer.feature.geometry.Envelope;

import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.model.MeshBuilder;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.DefaultDatasetProjection2;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.GeometryModel;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.math.Ray;
import com.atakmap.math.Vector3D;
import com.atakmap.opengl.GLTexture;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReferenceCount;

import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public interface ElevationChunk extends Disposable  {
    @DontObfuscate
    public static class Data {
        public Mesh value;
        public int srid;
        public Matrix localFrame;
        public boolean interpolated;
    }

    /**
     * Returns the URI of the chunk
     * @return
     */
    public String getUri();

    /**
     * Returns the data type of the chunk (informative).
     * @return
     */
    public String getType();

    /**
     * Returns the nominal resolution of the chunk, in meters-per-pixel.
     * @return
     */
    public double getResolution();

    /**
     * Returns the bounds of the chunk. The bounds shall be a quadrilateral
     * polygon, with no holes. The exterior ring shall have <code>5</code>
     * points, where the last point is equal to the first point.
     * @return
     */
    public Polygon getBounds();

    /**
     * Obtains a new copy of the elevation data for the chunk.
     * @return
     */
    public Data createData();

    /**
     * Samples the elevation at the specified location, returning the elevation
     * value in meters HAE. If no elevation value could be sampled at the
     * location, {@link Double#NaN} is returned.
     * @param latitude
     * @param longitude
     * @return
     */
    public double sample(double latitude, double longitude);

    /**
     * Bulk fetch of samples. Returns <code>true</code> if all missing samples
     * were fetched, <code>false</code> otherwise. Elevation will be queried
     * for all HAE values in the array that are {@link Double#NaN}.
     *
     * @param lla   The point array, in ordered Longitude, Latitude, Altitude HAE triplets.
     * @param off
     * @param len
     * @return
     */
    public boolean sample(double[] lla, int off, int len);

    /**
     * Get the circular error described by the elevation chunk.
     * XXX should this be for a specific lat, lon?
     * @return double in meters or Double.NaN if not valid
     */
    public double getCE();

    /**
     * Get the linear error described by the elevation chunk
     * @return double in meters or Double.NaN if not valid
     */
    public double getLE();

    /**
     * Is the elevation data considered authoritative.
     * @return boolean true if the data is authoritative.
     */
    public boolean isAuthoritative();

    /**
     * Get the flags set on the elevation chunk.
     * XXX where are the flags defined?
     */
    public int getFlags();

    public final static class Factory {
        public static interface DataLoader extends Disposable {
            public Data createData();
        }

        public static abstract class Sampler implements Disposable {
            public abstract double sample(double latitude, double longitude);
            public boolean sample(double[] lla, int off, int len) {
                boolean retval = true;
                for(int i = 0; i < len; i++) {
                    final int idx = (i+off)*3;
                    if(Double.isNaN(lla[idx+2])) {
                        final double el = sample(lla[idx+1], lla[idx]);
                        if(Double.isNaN(el))
                            retval = false;
                        else
                            lla[idx + 2] = el;
                    }
                }
                return retval;
            }
        }

        private Factory() {}

        public static ElevationChunk create(final String type, final String uri, final int flags, final double resolution, final Polygon bounds, double ce, double le, boolean authoritative, final DataLoader dataLoader) {
            return new DataElevationChunk(type, uri, flags, resolution, bounds, ce, le, authoritative, dataLoader);
        }

        public static ElevationChunk create(final String type, final String uri, final int flags, final double resolution, final Polygon bounds, double ce, double le, boolean authoritative,  final Sampler sampler) {
            return new SampledElevationChunk(type, uri, flags, resolution, bounds, ce, le, authoritative, sampler);
        }

        public static ElevationChunk makeShared(final ElevationChunk chunk) {
            ReferenceCount<ElevationChunk> ref;
            if(chunk instanceof SharedElevationChunk) {
                ref = ((SharedElevationChunk)chunk).impl;
            } else {
                ref = new ReferenceCount<ElevationChunk>(chunk, false) {
                    @Override
                    protected void onDereferenced() {
                        chunk.dispose();
                    }
                };
            }
            return new SharedElevationChunk(ref);
        }
    }

    static final class SharedElevationChunk implements ElevationChunk {
        final ReferenceCount<ElevationChunk> impl;

        SharedElevationChunk(ReferenceCount<ElevationChunk> impl) {
            impl.reference();
            this.impl = impl;
        }

        @Override
        public String getUri() {
            return impl.value.getUri();
        }

        @Override
        public String getType() {
            return impl.value.getType();
        }

        @Override
        public double getResolution() {
            return impl.value.getResolution();
        }

        @Override
        public Polygon getBounds() {
            return impl.value.getBounds();
        }

        @Override
        public Data createData() {
            return impl.value.createData();
        }

        @Override
        public double sample(double latitude, double longitude) {
            return impl.value.sample(latitude, longitude);
        }

        @Override
        public boolean sample(double[] lla, int off, int len) {
            return impl.value.sample(lla, off, len);
        }

        @Override
        public double getCE() {
            return impl.value.getCE();
        }

        @Override
        public double getLE() {
            return impl.value.getLE();
        }

        @Override
        public boolean isAuthoritative() {
            return impl.value.isAuthoritative();
        }

        @Override
        public int getFlags() {
            return impl.value.getFlags();
        }

        @Override
        public void dispose() {
            impl.dereference();
        }
    }

    static abstract class AbstractElevationChunk implements ElevationChunk {
        private final String uri;
        private final String type;
        private final int flags;
        private final double resolution;
        private final Polygon bounds;
        private final double ce;
        private final double le;
        private final boolean authoritative;


        /**
         * Abstract ElevationChunk Implementation
         * @param type the type of elevation data.
         * @param uri the uri for the data.
         * @param flags the flag describing the elevation data @see ElevationData MODEL_SURFACE or MODEL_TERAIN
         * @param resolution the resolution of the data
         * @param bounds the bounds of the polygon
         * @param ce the CE90 of the data
         * @param le the LE90 of the data
         * @param authoritative if the data is considered authoratative in that it has not been subject to internal degredation
         */
        AbstractElevationChunk(String type, String uri, int flags, double resolution, Polygon bounds, double ce, double le, boolean authoritative) {
            Polygon pbounds = (Polygon)bounds;
            if(!pbounds.getInteriorRings().isEmpty())
                throw new IllegalArgumentException("interior ring is not empty");
            if(pbounds.getExteriorRing().getNumPoints() != 5)
                throw new IllegalArgumentException("exterior ring != 5");

            if((flags&(ElevationData.MODEL_SURFACE|ElevationData.MODEL_TERRAIN)) == 0)
                throw new IllegalArgumentException("elevationData flags not set properly");

            this.uri = uri;
            this.type = type;
            this.flags = flags;
            this.resolution = resolution;
            this.bounds = bounds;
            this.ce = ce;
            this.le = le;
            this.authoritative = authoritative;

        }

        @Override
        public int getFlags() {
            return this.flags;
        }

        @Override
        public String getUri() {
            return this.uri;
        }

        @Override
        public String getType() {
            return this.type;
        }

        @Override
        public double getResolution() {
            return this.resolution;
        }

        @Override
        public Polygon getBounds() {
            return this.bounds;
        }

        @Override
        public double getCE() {
            return this.ce;
        }

        @Override
        public double getLE() {
            return this.le;
        }

        @Override
        public boolean isAuthoritative() {
            return this.authoritative;
        }

        @Override
        public boolean sample(double[] lla, int off, int len) {
            boolean retval = true;
            for(int i = 0; i < len; i++) {
                final int idx = (i+off)*3;
                if(Double.isNaN(lla[idx+2])) {
                    final double el = sample(lla[idx+1], lla[idx]);
                    if(Double.isNaN(el))
                        retval = false;
                    else
                        lla[idx + 2] = el;
                }
            }
            return retval;
        }
    }

    final static class DataElevationChunk extends AbstractElevationChunk {

        private final Factory.DataLoader dataLoader;
        private Data data;
        private GeometryModel geomModel;
        private Projection proj;

        DataElevationChunk(String type, String uri, int flags, double resolution, Polygon bounds, double ce, double le, boolean authoritative, Factory.DataLoader dataLoader) {
            super(type, uri, flags, resolution, bounds, ce, le, authoritative);
            this.dataLoader = dataLoader;
            this.data = null;
            this.geomModel = null;
            this.proj = null;
        }

        @Override
        public synchronized Data createData() {
            return this.dataLoader.createData();
        }

        @Override
        public synchronized  double sample(double latitude, double longitude) {
            if(this.geomModel == null) {
                this.data = this.createData();
                if(data == null || data.value == null)
                    return Double.NaN;
                if(data.srid != 4326) {
                    this.proj = ProjectionFactory.getProjection(data.srid);
                    if(this.proj == null)
                        return Double.NaN;
                }
                this.geomModel = Models.createGeometryModel(data.value, data.localFrame);
                if(this.geomModel == null)
                    return Double.NaN;
            }

            PointD rayOrg;
            PointD rayTgt;
            Ray ray;
            if(this.proj != null) {
                rayOrg = proj.forward(new GeoPoint(longitude, latitude, 30000d), null);
                rayTgt = proj.forward(new GeoPoint(longitude, latitude), null);
            } else {
                rayOrg = new PointD(longitude, latitude, 30000d);
                rayTgt = new PointD(longitude, latitude, 0d);
            }

            PointD isect = this.geomModel.intersect(new Ray(rayOrg, new Vector3D(rayTgt.x-rayOrg.x, rayTgt.y-rayOrg.y, rayTgt.z-rayOrg.z)));
            if(isect == null)
                return Double.NaN;
            double el;
            if(this.proj != null) {
                GeoPoint result = proj.inverse(isect, null);
                if(result == null)
                    return Double.NaN;
                el = result.isAltitudeValid() ? EGM96.getHAE(result) : Double.NaN;
            } else {
                el = isect.z;
            }
            return el;
        }

        @Override
        public boolean sample(double[] lla, int off, int len) {
            synchronized(this) {
                if (this.geomModel == null) {
                    this.data = this.createData();
                    if (data == null || data.value == null)
                        return false;
                    this.geomModel = Models.createGeometryModel(data.value, data.localFrame);
                    if(this.geomModel == null)
                        return false;
                }
            }

            return super.sample(lla, off, len);
        }

        @Override
        public synchronized void dispose() {
            if (this.data != null) {
                if (this.data.value != null)
                    this.data.value.dispose();
                this.data = null;
            }
            this.geomModel = null;
            this.dataLoader.dispose();
        }
    }

    final static class SampledElevationChunk extends AbstractElevationChunk {

        private final Factory.Sampler sampler;
        private Data data;

        SampledElevationChunk(String type, String uri, int flags, double resolution, Polygon bounds, double ce, double le, boolean authoritative, Factory.Sampler sampler) {
            super(type, uri, flags, resolution, bounds, ce, le, authoritative);
            this.sampler = sampler;
            this.data = null;
        }

        @Override
        public synchronized Data createData() {
            if (this.data == null) {
                Envelope aabb = this.getBounds().getEnvelope();
                final double centroidX = (aabb.minX + aabb.maxX) / 2d;
                final double centroidY = (aabb.minY + aabb.maxY) / 2d;

                LineString bounds = ((Polygon) this.getBounds()).getExteriorRing();

                // approximate number of posts based on resolution
                final int samplesX;
                final int samplesY;
                if ((aabb.maxX - aabb.minX) <= 180d) {
                    final double dx1 = GeoCalculations.distanceTo(new GeoPoint(bounds.getY(0), bounds.getY(0)), new GeoPoint(bounds.getY(1), bounds.getY(1)));
                    final double dx2 = GeoCalculations.distanceTo(new GeoPoint(bounds.getY(2), bounds.getY(2)), new GeoPoint(bounds.getY(3), bounds.getY(3)));
                    final double dy1 = GeoCalculations.distanceTo(new GeoPoint(bounds.getY(1), bounds.getY(1)), new GeoPoint(bounds.getY(2), bounds.getY(2)));
                    final double dy2 = GeoCalculations.distanceTo(new GeoPoint(bounds.getY(3), bounds.getY(3)), new GeoPoint(bounds.getY(0), bounds.getY(0)));

                    samplesX = Math.max((int) Math.ceil(Math.max(dx1, dx2) / getResolution()), 2);
                    samplesY = Math.max((int) Math.ceil(Math.max(dy1, dy2) / getResolution()), 2);
                } else {
                    // approximate meters-per-degree
                    final double rlat = Math.toRadians(centroidY);
                    final double metersDegLat = 111132.92 - 559.82 * Math.cos(2 * rlat) + 1.175 * Math.cos(4 * rlat);
                    final double metersDegLng = 111412.84 * Math.cos(rlat) - 93.5 * Math.cos(3 * rlat);

                    final double dx1 = estimateDistance(
                            metersDegLat, metersDegLng,
                            bounds.getY(0), bounds.getX(0),
                            bounds.getY(1), bounds.getX(1));
                    final double dx2 = estimateDistance(
                            metersDegLat, metersDegLng,
                            bounds.getY(2), bounds.getX(2),
                            bounds.getY(3), bounds.getX(3));
                    final double dy1 = estimateDistance(
                            metersDegLat, metersDegLng,
                            bounds.getY(1), bounds.getX(1),
                            bounds.getY(2), bounds.getX(2));
                    final double dy2 = estimateDistance(
                            metersDegLat, metersDegLng,
                            bounds.getY(3), bounds.getX(3),
                            bounds.getY(0), bounds.getX(0));

                    samplesX = Math.max((int) Math.ceil(Math.max(dx1, dx2) / getResolution()), 2);
                    samplesY = Math.max((int) Math.ceil(Math.max(dy1, dy2) / getResolution()), 2);
                }

                // construct function to convert between post and lat/lon

                DatasetProjection2 proj = new DefaultDatasetProjection2(
                        4326,
                        samplesX, samplesY,
                        new GeoPoint(bounds.getY(0), bounds.getX(0)),
                        new GeoPoint(bounds.getY(1), bounds.getX(1)),
                        new GeoPoint(bounds.getY(2), bounds.getX(2)),
                        new GeoPoint(bounds.getY(3), bounds.getX(3)));


                // construct the model by sampling the coverage
                MeshBuilder builder = new MeshBuilder(Mesh.VERTEX_ATTR_POSITION, true, Mesh.DrawMode.TriangleStrip);
                builder.setWindingOrder(Mesh.WindingOrder.CounterClockwise);

                PointD img = new PointD(0d, 0d, 0d);
                GeoPoint geo = GeoPoint.createMutable();
                for (int y = 0; y < samplesY; y++) {
                    for (int x = 0; x < samplesX; x++) {
                        img.x = x;
                        img.y = y;
                        proj.imageToGround(img, geo);

                        double ela = this.sampler.sample(geo.getLatitude(), geo.getLongitude());
                        builder.addVertex(geo.getLongitude() - centroidX, geo.getLatitude() - centroidY, ela,
                                0f, 0f,
                                0f, 0f, 1f,
                                1f, 1f, 1f, 1f);
                    }
                }

                ShortBuffer indices = Unsafe.allocateDirect(GLTexture.getNumQuadMeshIndices(samplesX - 1, samplesY - 1) * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
                GLTexture.createQuadMeshIndexBuffer(samplesX - 1, samplesY - 1, indices);
                indices.flip();
                builder.addIndices(indices);

                this.data = new Data();
                this.data.value = builder.build();
                this.data.srid = 4326;
                this.data.interpolated = true;
                this.data.localFrame = Matrix.getTranslateInstance(centroidX, centroidY);
            }

            Data retval = new Data();
            retval.value = Models.transform(data.value, data.value.getVertexDataLayout());
            retval.srid = data.srid;
            retval.interpolated = true;
            if(data.localFrame != null) {
                retval.localFrame = Matrix.getIdentity();
                retval.localFrame.concatenate(data.localFrame);
            }
            return retval;
        }

        @Override
        public synchronized double sample(double latitude, double longitude) {
            return this.sampler.sample(latitude, longitude);
        }

        @Override
        public synchronized boolean sample(double[] lla, int off, int len) {
            return this.sampler.sample(lla, off, len);
        }

        @Override
        public synchronized void dispose() {
            if (this.data != null) {
                if (this.data.value != null)
                    this.data.value.dispose();
                this.data = null;
            }
            this.sampler.dispose();
        }

        private static double estimateDistance(double metersDegLat, double metersDegLng, double lat1, double lng1, double lat2, double lng2) {
            final double dlat = metersDegLat * (lat2 - lat1);
            final double dlng = metersDegLng * (lng2 - lng1);
            return Math.sqrt(dlat * dlat + dlng * dlng);
        }
    }
}
