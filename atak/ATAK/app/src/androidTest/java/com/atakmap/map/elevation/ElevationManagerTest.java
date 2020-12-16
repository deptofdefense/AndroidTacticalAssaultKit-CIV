
package com.atakmap.map.elevation;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.math.Rectangle;
import com.atakmap.util.Collections2;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ElevationManagerTest extends ATAKInstrumentedTest {
    private static final Set<ElevationSource> sources = Collections2
            .<ElevationSource> newIdentityHashSet();

    @BeforeClass
    public static void configureElevationManager() {
        // XXX - add some sources

        // several tiles at different resolutions covering same area
        sources.add(createSingleChunkElevationSource("w078n35-lowres", "lowres",
                34.9, -77.9, 34, -77, ElevationData.MODEL_TERRAIN, 90d, 10d,
                30d, true, 12345));
        sources.add(createSingleChunkElevationSource("w078n35-medres", "medres",
                34.9, -77.9, 34, -77, ElevationData.MODEL_TERRAIN, 30d, 5d, 15d,
                true, 23456));
        sources.add(createSingleChunkElevationSource("w078n35-highres",
                "highres", 34.9, -77.9, 34, -77, ElevationData.MODEL_TERRAIN,
                10d, 3d, 9d, true, 34567));

        sources.add(createSingleChunkElevationSource("w077n35-highres",
                "highres", 34.9, -76.9, 34, -76, ElevationData.MODEL_TERRAIN,
                12d, 8d, 7d, true, 45678));
        sources.add(createSingleChunkElevationSource("w077n34-highres",
                "highres", 33.9, -76.9, 33, -76, ElevationData.MODEL_SURFACE,
                12d, 4d, 13d, false, 56789));

        for (ElevationSource src : sources)
            ElevationSourceManager.attach(src);
    }

    @AfterClass
    public static void deconfigureElevationManager() {
        for (ElevationSource src : sources)
            ElevationSourceManager.detach(src);
        sources.clear();
    }

    @Test
    public void get_elevation_retrieves_best_elevation() {
        final double hae = ElevationManager.getElevation(34.5, -77.5, null);
        assertEquals(34567, hae, 0.0);
    }

    @Test
    public void get_elevation_returns_nan_on_miss() {
        final double hae = ElevationManager.getElevation(35.5, -77.5, null);
        assertTrue(Double.isNaN(hae));
    }

    @Test
    public void get_elevation_returns_result_type() {
        GeoPointMetaData md = new GeoPointMetaData();
        ElevationManager.getElevation(34.5, -77.5, null, md);
        assertEquals(34.5, md.get().getLatitude(), 0.0);
        assertEquals(md.get().getLongitude(), -77.5, 0.0);
        assertEquals(34567, md.get().getAltitude(), 0.0);
        assertEquals(GeoPoint.AltitudeReference.HAE,
                md.get().getAltitudeReference());
        assertEquals("highres", md.getAltitudeSource());
    }

    @Test
    public void get_elevation_bulk_retrieves_best_elevation() {
        final int numPostsLat = 5;
        final int numPostsLng = 5;
        Collection<GeoPoint> pts = createGeopointMesh(34.75, -77.75, 34.25,
                -77.25, numPostsLat, numPostsLng);
        double[] els = new double[numPostsLat * numPostsLng];
        final boolean done = ElevationManager.getElevation(pts.iterator(), els,
                null);
        assertTrue(done);

        for (final double hae : els) {
            assertEquals(34567, hae, 0.0);
        }
    }

    @Test
    public void get_elevation_bulk_returns_nan_on_miss() {
        final int numPostsLat = 5;
        final int numPostsLng = 5;
        Collection<GeoPoint> pts = createGeopointMesh(30, 15, 29, 16,
                numPostsLat, numPostsLng);
        double[] els = new double[numPostsLat * numPostsLng];
        final boolean done = ElevationManager.getElevation(pts.iterator(), els,
                null);
        assertFalse(done);

        for (final double hae : els) {
            assertTrue(Double.isNaN(hae));
        }
    }

    @Test
    public void get_elevation_bulk_fills_across_boundaries() {
        Collection<GeoPoint> pts = new ArrayList<>(2);
        pts.add(new GeoPoint(33.5, -76.5)); // center of w077n34-highre, res 56789
        pts.add(new GeoPoint(34.5, -77.5)); // center of w078n35-highres, res 34567

        double[] els = new double[2];
        final boolean done = ElevationManager.getElevation(pts.iterator(), els,
                null);
        assertTrue(done);

        assertEquals(56789, els[0], 0.0);
        assertEquals(34567, els[1], 0.0);
    }

    @Test
    public void get_elevation_bulk_with_miss_returns_not_done() {
        final int numPostsLat = 5;
        final int numPostsLng = 5;
        Collection<GeoPoint> pts = createGeopointMesh(35, -78.5, 34, -77.5,
                numPostsLat, numPostsLng);
        double[] els = new double[numPostsLat * numPostsLng];
        final boolean done = ElevationManager.getElevation(pts.iterator(), els,
                null);
        assertFalse(done);

        int fetched = 0;

        for (final double hae : els) {
            if (!Double.isNaN(hae))
                fetched++;
        }

        assertTrue(fetched > 0);
    }

    @Test
    public void chunk_query_order_resolution_asc() {
        ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
        params.order = Collections.<ElevationSource.QueryParameters.Order> singletonList(
                ElevationSource.QueryParameters.Order.ResolutionAsc);
        ElevationSource.Cursor result = null;
        try {
            result = ElevationManager.queryElevationSources(params);
            assertTrue(result.moveToNext());
            double res = result.getResolution();
            while (result.moveToNext()) {
                double d = result.getResolution();
                // Ascending resolution starts with numerically greatest, and
                // increases numerically. NaN is prohibited
                assertParamsDoubleArgGreaterThanEqual(res, d,
                        NaNCompareMode.Prohibited);
                res = d;
            }
        } finally {
            if (result != null)
                result.close();
        }
    }

    @Test
    public void chunk_query_order_resolution_desc() {
        ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
        params.order = Collections.<ElevationSource.QueryParameters.Order> singletonList(
                ElevationSource.QueryParameters.Order.ResolutionDesc);
        ElevationSource.Cursor result = null;
        try {
            result = ElevationManager.queryElevationSources(params);
            assertTrue(result.moveToNext());
            double res = result.getResolution();
            while (result.moveToNext()) {
                double d = result.getResolution();
                // Descending resolution starts with numerically smallest, and
                // increases numerically. NaN is prohibited
                assertParamsDoubleArgLessThanEqual(res, d,
                        NaNCompareMode.Prohibited);
                res = d;
            }
        } finally {
            if (result != null)
                result.close();
        }
    }

    @Test
    public void chunk_query_order_ce_desc() {
        ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
        params.order = Collections.<ElevationSource.QueryParameters.Order> singletonList(
                ElevationSource.QueryParameters.Order.CEDesc);
        ElevationSource.Cursor result = null;
        try {
            result = ElevationManager.queryElevationSources(params);
            assertTrue(result.moveToNext());
            double ce = result.getCE();
            while (result.moveToNext()) {
                double d = result.getCE();
                // Descending CE starts with numerically smallest, and
                // increases numerically. NaN is considered "worst" CE
                assertParamsDoubleArgLessThanEqual(ce, d,
                        NaNCompareMode.Greater);
                ce = d;
            }
        } finally {
            if (result != null)
                result.close();
        }
    }

    @Test
    public void chunk_query_order_ce_asc() {
        ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
        params.order = Collections.<ElevationSource.QueryParameters.Order> singletonList(
                ElevationSource.QueryParameters.Order.CEAsc);
        ElevationSource.Cursor result = null;
        try {
            result = ElevationManager.queryElevationSources(params);
            assertTrue(result.moveToNext());
            double ce = result.getCE();
            while (result.moveToNext()) {
                double d = result.getCE();
                // Asccending CE starts with numerically greatest, and
                // increases numerically. NaN is considered "worst" CE
                assertParamsDoubleArgGreaterThanEqual(ce, d,
                        NaNCompareMode.Greater);
                ce = d;
            }
        } finally {
            if (result != null)
                result.close();
        }
    }

    @Test
    public void chunk_query_order_le_asc() {
        ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
        params.order = Collections.<ElevationSource.QueryParameters.Order> singletonList(
                ElevationSource.QueryParameters.Order.LEAsc);
        ElevationSource.Cursor result = null;
        try {
            result = ElevationManager.queryElevationSources(params);
            assertTrue(result.moveToNext());
            double le = result.getLE();
            while (result.moveToNext()) {
                double d = result.getLE();
                // Asccending CE starts with numerically greatest, and
                // increases numerically. NaN is considered "worst" CE
                assertParamsDoubleArgGreaterThanEqual(le, d,
                        NaNCompareMode.Greater);
                le = d;
            }
        } finally {
            if (result != null)
                result.close();
        }
    }

    @Test
    public void chunk_query_order_le_desc() {
        ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
        params.order = Collections.<ElevationSource.QueryParameters.Order> singletonList(
                ElevationSource.QueryParameters.Order.LEDesc);
        ElevationSource.Cursor result = null;
        try {
            result = ElevationManager.queryElevationSources(params);
            assertTrue(result.moveToNext());
            double le = result.getLE();
            while (result.moveToNext()) {
                double d = result.getLE();
                // Descending CE starts with numerically smallest, and
                // increases numerically. NaN is considered "worst" CE
                assertParamsDoubleArgLessThanEqual(le, d,
                        NaNCompareMode.Greater);
                le = d;
            }
        } finally {
            if (result != null)
                result.close();
        }
    }

    @Test
    public void mosaicdb_adapter_receives_params_and_hints() {
        Envelope captureBounds = new Envelope(45, -20, 0d, 46, -19, 0d);
        MosaicDatabase2_ParamsCapture capture = new MosaicDatabase2_ParamsCapture(
                "mosaicdb_adapter_receives_params_and_hints", captureBounds);
        try {
            final int numPostsLat = 5;
            final int numPostsLng = 5;
            Collection<GeoPoint> pts = createGeopointMesh(captureBounds.maxY,
                    captureBounds.minX, captureBounds.minY, captureBounds.maxX,
                    numPostsLat, numPostsLng);
            double[] els = new double[numPostsLat * numPostsLng];

            ElevationData.Hints hints = new ElevationData.Hints();
            hints.resolution = 1234;

            ElevationManager.registerElevationSource(capture);
            ElevationManager.registerDataSpi(capture.spi());
            ElevationManager.getElevation(pts.iterator(),
                    els, null, hints);

            MosaicDatabase2.QueryParameters params = capture.params;
            ElevationData.Hints rcvhints = capture.hints();

            assertNotNull(params);
            assertNotNull(rcvhints);
            assertEquals(rcvhints.resolution, hints.resolution, 0.0);
        } finally {
            ElevationManager.unregisterDataSpi(capture.spi());
            ElevationManager.unregisterElevationSource(capture);
        }
    }

    static ElevationSource createSingleChunkElevationSource(String name,
            String type, final double ullat, final double ullng,
            final double lrlat, final double lrlng, int flags,
            double resolution, double ce, double le, boolean authoritative,
            final double sampleValue) {
        ElevationSourceBuilder builder = new ElevationSourceBuilder();
        builder.add(ElevationChunk.Factory.create(type,
                "ElevationManagerTest://" + name, flags, resolution,
                (Polygon) GeometryFactory.fromEnvelope(
                        new Envelope(ullng, lrlat, 0d, lrlng, ullat, 0d)),
                ce, le, authoritative, new ElevationChunk.Factory.Sampler() {
                    @Override
                    public void dispose() {
                    }

                    @Override
                    public double sample(double latitude, double longitude) {
                        return Rectangle.contains(ullng, lrlat, lrlng, ullat,
                                longitude, latitude) ? sampleValue : Double.NaN;
                    }
                }));
        return builder.build(name);
    }

    static Collection<GeoPoint> createGeopointMesh(double ullat, double ullng,
            double lrlat, double lrlng, int numPostsLat, int numPostsLng) {
        ArrayList<GeoPoint> retval = new ArrayList<>(numPostsLat * numPostsLng);
        for (int latIdx = 0; latIdx < numPostsLat; latIdx++) {
            for (int lngIdx = 0; lngIdx < numPostsLng; lngIdx++) {
                retval.add(new GeoPoint(ullat
                        - (latIdx * ((ullat - lrlat) / (numPostsLat - 1))),
                        ullng + (lngIdx
                                * ((lrlng - ullng) / (numPostsLng - 1)))));
            }
        }
        return retval;
    }

    enum NaNCompareMode {
        /** NaN value is considered greater than non-NaN value */
        Greater,
        /** NaN value is considered less than non-NaN value */
        Less,
        /** NaN value is prohibited */
        Prohibited,
    }

    static void assertParamsDoubleArgGreaterThanEqual(double a, double b,
            final NaNCompareMode nans) {
        final boolean anan = Double.isNaN(a);
        final boolean bnan = Double.isNaN(b);

        if (anan == bnan) {
            // don't allow NaN if prohibited
            assertFalse((nans == NaNCompareMode.Prohibited) && anan);
            // if NaN permitted, pass on both NaN or `a > b`
            assertTrue(anan || (a >= b));
        } else if (nans == NaNCompareMode.Greater) {
            assertTrue(anan);
        } else if (nans == NaNCompareMode.Less) {
            // only one is NaN, pass if 'b' is a NaN
            assertTrue(bnan);
        } else if (nans == NaNCompareMode.Prohibited) {
            // if NaNs are prohibited, fail if either is NaN
            assertFalse(anan);
            assertFalse(bnan);
        }
    }

    static void assertParamsDoubleArgLessThanEqual(double a, double b,
            final NaNCompareMode nans) {
        final boolean anan = Double.isNaN(a);
        final boolean bnan = Double.isNaN(b);

        if (anan == bnan) {
            // don't allow NaN if prohibited
            assertFalse((nans == NaNCompareMode.Prohibited) && anan);
            // if NaN permitted, pass on both NaN or `a > b`
            assertTrue(anan || (a <= b));
        } else if (nans == NaNCompareMode.Less) {
            assertTrue(anan);
        } else if (nans == NaNCompareMode.Greater) {
            // only one is NaN, pass if 'b' is a NaN
            assertTrue(bnan);
        } else if (nans == NaNCompareMode.Prohibited) {
            // if NaNs are prohibited, fail if either is NaN
            assertFalse(anan);
            assertFalse(bnan);
        }
    }

    static class MosaicDatabase2_ParamsCapture implements MosaicDatabase2 {

        String type;
        Envelope bounds;
        QueryParameters params;
        ElevationData__ParamsCapture dataCapturer;

        MosaicDatabase2_ParamsCapture(String type, Envelope bounds) {
            this.type = type;
            this.bounds = bounds;
            this.dataCapturer = new ElevationData__ParamsCapture(this.type,
                    ElevationData.MODEL_SURFACE);
        }

        @Override
        public String getType() {
            return this.type;
        }

        @Override
        public void open(File f) {
        }

        @Override
        public void close() {
        }

        @Override
        public Coverage getCoverage() {
            return new Coverage(GeometryFactory.fromEnvelope(bounds),
                    Double.MAX_VALUE, 0d);
        }

        @Override
        public void getCoverages(Map<String, Coverage> coverages) {
            Map<String, Coverage> retval = new HashMap<>();
            retval.put(null, getCoverage());
            retval.put(getType(), getCoverage());
        }

        @Override
        public Coverage getCoverage(String type) {
            return new Coverage(GeometryFactory.fromEnvelope(bounds),
                    Double.MAX_VALUE, 0d);
        }

        @Override
        public Cursor query(QueryParameters params) {
            this.params = params;
            if (this.dataCapturer == null)
                return Cursor.EMPTY;
            return new Cursor() {
                int row = -1;

                @Override
                public GeoPoint getUpperLeft() {
                    return new GeoPoint(getMaxLat(), getMinLon());
                }

                @Override
                public GeoPoint getUpperRight() {
                    return new GeoPoint(getMaxLat(), getMaxLon());
                }

                @Override
                public GeoPoint getLowerRight() {
                    return new GeoPoint(getMinLat(), getMaxLon());
                }

                @Override
                public GeoPoint getLowerLeft() {
                    return new GeoPoint(getMinLat(), getMinLon());
                }

                @Override
                public double getMinLat() {
                    return bounds.minY;
                }

                @Override
                public double getMinLon() {
                    return bounds.minX;
                }

                @Override
                public double getMaxLat() {
                    return bounds.maxY;
                }

                @Override
                public double getMaxLon() {
                    return bounds.maxX;
                }

                @Override
                public String getPath() {
                    return Integer.toString(
                            MosaicDatabase2_ParamsCapture.this.hashCode(), 16);
                }

                @Override
                public String getType() {
                    return type;
                }

                @Override
                public double getMinGSD() {
                    return 0d;
                }

                @Override
                public double getMaxGSD() {
                    return 0d;
                }

                @Override
                public int getWidth() {
                    return 2;
                }

                @Override
                public int getHeight() {
                    return 2;
                }

                @Override
                public int getId() {
                    return 1;
                }

                @Override
                public int getSrid() {
                    return 4326;
                }

                @Override
                public boolean isPrecisionImagery() {
                    return false;
                }

                @Override
                public Frame asFrame() {
                    return new MosaicDatabase2.Frame(this);
                }

                @Override
                public boolean moveToNext() {
                    if (row >= 0)
                        return false;
                    row++;
                    return true;
                }

                @Override
                public void close() {
                }

                @Override
                public boolean isClosed() {
                    return false;
                }
            };
        }

        ElevationDataSpi spi() {
            return new ElevationDataSpi() {
                @Override
                public int getPriority() {
                    return 0;
                }

                @Override
                public ElevationData create(ImageInfo object) {
                    if (object.path.equals(Integer.toString(
                            MosaicDatabase2_ParamsCapture.this.hashCode(), 16)))
                        return dataCapturer;
                    else
                        return null;
                }
            };
        }

        ElevationData.Hints hints() {
            if (this.dataCapturer == null)
                return null;
            else
                return this.dataCapturer.capturedHints;
        }
    }

    static class ElevationData__ParamsCapture implements ElevationData {

        String type;
        Hints capturedHints;
        int elevationModel;

        ElevationData__ParamsCapture(String type, int model) {
            this.type = type;
            elevationModel = model;
        }

        @Override
        public int getElevationModel() {
            return this.elevationModel;
        }

        @Override
        public String getType() {
            return null;
        }

        @Override
        public double getResolution() {
            return 0;
        }

        @Override
        public double getElevation(double latitude, double longitude) {
            return Double.NaN;
        }

        @Override
        public void getElevation(Iterator<GeoPoint> points, double[] elevations,
                Hints hints) {
            this.capturedHints = hints;
            java.util.Arrays.fill(elevations, Double.NaN);
        }
    }
}
