
package com.atakmap.map.elevation;

import com.atakmap.interop.Pointer;
import com.atakmap.map.AInteropTest;
import com.atakmap.map.Interop;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.math.Rectangle;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class ElevationSourceInteropTest extends AInteropTest<ElevationSource> {
    public ElevationSourceInteropTest() {
        super(ElevationSource.class, false, true, true);
    }

    @Override
    protected ElevationSource createMockInstance() {
        String id = Long.toString(System.currentTimeMillis(), 16);
        LineString bounds = new LineString(2);
        bounds.addPoint(-78, 35);
        bounds.addPoint(-77, 35);
        bounds.addPoint(-77, 34);
        bounds.addPoint(-78, 34);
        bounds.addPoint(-78, 35);

        ElevationSourceBuilder builder = new ElevationSourceBuilder();
        builder.add(ElevationChunk.Factory.create("type", "chunk://" + id,
                ElevationData.MODEL_TERRAIN, 78910, new Polygon(bounds), 12, 34,
                true, new ElevationChunk.Factory.Sampler() {
                    @Override
                    public void dispose() {
                    }

                    @Override
                    public double sample(double latitude, double longitude) {
                        return latitude * 360 + longitude;
                    }
                }));
        return builder.build(id);
    }

    @Test
    public void name_roundtrip() {
        final String name = Long.toString(System.currentTimeMillis(), 16);
        final Envelope bounds = new Envelope(1, 2, 3, 4, 5, 6);
        // create mock source with our desired traits
        ElevationSource mock = new ElevationSource() {
            public String getName() {
                return name;
            }

            public Cursor query(QueryParameters params) {
                return new MultiplexingElevationChunkCursor(
                        Collections.<Cursor> emptySet());
            }

            public Envelope getBounds() {
                return bounds;
            }

            public void addOnContentChangedListener(
                    OnContentChangedListener l) {
            }

            public void removeOnContentChangedListener(
                    OnContentChangedListener l) {
            }
        };

        Interop<ElevationSource> interop = Interop
                .findInterop(ElevationSource.class);

        // wrap the Object with native
        final Pointer cmock = interop.wrap(mock);
        // wrap native with an Object
        final ElevationSource doubleWrap = interop.create(cmock);

        assertEquals(name, doubleWrap.getName());
    }

    @Test
    public void getBounds_roundtrip() {
        final String name = Long.toString(System.currentTimeMillis(), 16);
        final Envelope bounds = new Envelope(1, 2, 3, 4, 5, 6);
        // create mock source with our desired traits
        ElevationSource mock = new ElevationSource() {
            public String getName() {
                return name;
            }

            public Cursor query(QueryParameters params) {
                return new MultiplexingElevationChunkCursor(
                        Collections.<Cursor> emptySet());
            }

            public Envelope getBounds() {
                return bounds;
            }

            public void addOnContentChangedListener(
                    OnContentChangedListener l) {
            }

            public void removeOnContentChangedListener(
                    OnContentChangedListener l) {
            }
        };

        Interop<ElevationSource> interop = Interop
                .findInterop(ElevationSource.class);

        // wrap the Object with native
        final Pointer cmock = interop.wrap(mock);
        // wrap native with an Object
        final ElevationSource doubleWrap = interop.create(cmock);

        final Envelope doubleWrapBounds = doubleWrap.getBounds();
        assertNotNull(doubleWrapBounds);

        assertEquals(doubleWrapBounds.minX, bounds.minX, 0.0);
        assertEquals(doubleWrapBounds.minY, bounds.minY, 0.0);
        assertEquals(doubleWrapBounds.minZ, bounds.minZ, 0.0);
        assertEquals(doubleWrapBounds.maxX, bounds.maxX, 0.0);
        assertEquals(doubleWrapBounds.maxY, bounds.maxY, 0.0);
        assertEquals(doubleWrapBounds.maxZ, bounds.maxZ, 0.0);
    }

    @Test
    public void addOnContentChangedListener_adds_listener() {
        // create object
        final String name = Long.toString(System.currentTimeMillis(), 16);
        final Envelope bounds = new Envelope(1, 2, 3, 4, 5, 6);
        final Set<ElevationSource.OnContentChangedListener> listeners = new HashSet<>();
        // create mock source with our desired traits
        ElevationSource mock = new ElevationSource() {
            public String getName() {
                return name;
            }

            public Cursor query(QueryParameters params) {
                return new MultiplexingElevationChunkCursor(
                        Collections.<Cursor> emptySet());
            }

            public Envelope getBounds() {
                return bounds;
            }

            public void addOnContentChangedListener(
                    OnContentChangedListener l) {
                listeners.add(l);
            }

            public void removeOnContentChangedListener(
                    OnContentChangedListener l) {
                listeners.remove(l);
            }
        };

        final ElevationSource.OnContentChangedListener l = new ElevationSource.OnContentChangedListener() {
            @Override
            public void onContentChanged(ElevationSource source) {
            }
        };

        Interop<ElevationSource> interop = Interop
                .findInterop(ElevationSource.class);

        // wrap object as native
        final Pointer cmock = interop.wrap(mock);
        // wrap native as object
        final ElevationSource doubleWrap = interop.create(cmock);
        // register listener
        doubleWrap.addOnContentChangedListener(l);

        // test added
        assertEquals(1, listeners.size());
    }

    @Test
    public void removeOnContentChangedListener_removes_listener() {
        // create object
        final String name = Long.toString(System.currentTimeMillis(), 16);
        final Envelope bounds = new Envelope(1, 2, 3, 4, 5, 6);
        final Set<ElevationSource.OnContentChangedListener> listeners = new HashSet<ElevationSource.OnContentChangedListener>();
        final boolean[] removeInvoked = new boolean[] {
                false
        };
        // create mock source with our desired traits
        ElevationSource mock = new ElevationSource() {
            public String getName() {
                return name;
            }

            public Cursor query(QueryParameters params) {
                return new MultiplexingElevationChunkCursor(
                        Collections.<Cursor> emptySet());
            }

            public Envelope getBounds() {
                return bounds;
            }

            public void addOnContentChangedListener(
                    OnContentChangedListener l) {
                listeners.add(l);
            }

            public void removeOnContentChangedListener(
                    OnContentChangedListener l) {
                listeners.remove(l);
                removeInvoked[0] = true;
            }
        };

        final ElevationSource.OnContentChangedListener l = new ElevationSource.OnContentChangedListener() {
            @Override
            public void onContentChanged(ElevationSource source) {
            }
        };

        Interop<ElevationSource> interop = Interop
                .findInterop(ElevationSource.class);

        // wrap object as native
        final Pointer cmock = interop.wrap(mock);
        // wrap native as object
        final ElevationSource doubleWrap = interop.create(cmock);
        // register listener
        doubleWrap.addOnContentChangedListener(l);
        // unregister listener
        doubleWrap.removeOnContentChangedListener(l);

        // test removed
        // check invoked
        assertTrue(removeInvoked[0]);
        // check actual removal
        assertEquals(0, listeners.size());
    }

    void queryParameters_interop_roundtrip_impl(
            final Geometry spatialFilter,
            final double targetResolution,
            final double minResolution,
            final double maxResolution,
            final Boolean authoritative,
            final Set<String> types,
            final List<ElevationSource.QueryParameters.Order> order,
            final Integer flags,
            final double minCE,
            final double minLE) {

        final ElevationSource.QueryParameters srcParams = new ElevationSource.QueryParameters();
        srcParams.spatialFilter = spatialFilter;
        srcParams.targetResolution = targetResolution;
        srcParams.minResolution = minResolution;
        srcParams.maxResolution = maxResolution;
        srcParams.authoritative = authoritative;
        srcParams.types = types;
        srcParams.order = order;
        srcParams.flags = flags;
        srcParams.minCE = minCE;
        srcParams.minLE = minLE;

        final ElevationSource.QueryParameters[] receivedParams = new ElevationSource.QueryParameters[1];

        final String name = Long.toString(System.currentTimeMillis(), 16);
        final Envelope bounds = new Envelope(1, 2, 3, 4, 5, 6);
        // create mock source with our desired traits
        ElevationSource mock = new ElevationSource() {
            public String getName() {
                return name;
            }

            public Cursor query(QueryParameters params) {
                receivedParams[0] = params;
                return new MultiplexingElevationChunkCursor(
                        Collections.<Cursor> emptySet());
            }

            public Envelope getBounds() {
                return bounds;
            }

            public void addOnContentChangedListener(
                    OnContentChangedListener l) {
            }

            public void removeOnContentChangedListener(
                    OnContentChangedListener l) {
            }
        };

        Interop<ElevationSource> interop = Interop
                .findInterop(ElevationSource.class);

        // wrap the Object with native
        final Pointer cmock = interop.wrap(mock);
        // wrap native with an Object
        final ElevationSource doubleWrap = interop.create(cmock);

        // invoke the query
        ElevationSource.Cursor result = null;
        try {
            result = doubleWrap.query(srcParams);
            assertNotNull(result);
        } finally {
            if (result != null)
                result.close();
        }

        assertNotNull(receivedParams[0]);
        assertNotSame(srcParams, receivedParams[0]);
        assertEquals(srcParams, receivedParams[0]);
    }

    @Test
    public void queryParameters_interop_null_object_args_roundtrip() {
        final Geometry spatialFilter = null;
        final double minResolution = 10.5d;
        final double maxResolution = 13.875d;
        final double targetResolution = 12d;
        final Boolean authoritative = null;
        final Set<String> types = null;
        final List<ElevationSource.QueryParameters.Order> order = null;
        final Integer flags = null;
        final double minCE = 5d;
        final double minLE = 0.625d;

        queryParameters_interop_roundtrip_impl(spatialFilter,
                targetResolution,
                minResolution,
                maxResolution,
                authoritative,
                types,
                order,
                flags,
                minCE,
                minLE);
    }

    @Test
    public void queryParameters_interop_nonnull_object_args_roundtrip() {
        final Geometry spatialFilter = createMBR(-78, 36, -77, 37);
        final double minResolution = 10.5d;
        final double maxResolution = 13.875d;
        final double targetResolution = 12d;
        final Boolean authoritative = Boolean.FALSE;
        final Set<String> types = new HashSet<>(
                java.util.Arrays.<String> asList("a", "b", "c"));
        final List<ElevationSource.QueryParameters.Order> order = java.util.Arrays.<ElevationSource.QueryParameters.Order> asList(
                ElevationSource.QueryParameters.Order.CEAsc,
                ElevationSource.QueryParameters.Order.CEDesc,
                ElevationSource.QueryParameters.Order.LEAsc,
                ElevationSource.QueryParameters.Order.LEDesc,
                ElevationSource.QueryParameters.Order.ResolutionAsc,
                ElevationSource.QueryParameters.Order.ResolutionDesc);
        final Integer flags = 5;
        final double minCE = 5d;
        final double minLE = 0.625d;

        queryParameters_interop_roundtrip_impl(spatialFilter,
                targetResolution,
                minResolution,
                maxResolution,
                authoritative,
                types,
                order,
                flags,
                minCE,
                minLE);
    }

    @Test
    public void queryParameters_interop_nan_double_args_roundtrip() {
        final Geometry spatialFilter = null;
        final double minResolution = Double.NaN;
        final double maxResolution = Double.NaN;
        final double targetResolution = Double.NaN;
        final Boolean authoritative = null;
        final Set<String> types = null;
        final List<ElevationSource.QueryParameters.Order> order = null;
        final Integer flags = null;
        final double minCE = Double.NaN;
        final double minLE = Double.NaN;

        queryParameters_interop_roundtrip_impl(spatialFilter,
                targetResolution,
                minResolution,
                maxResolution,
                authoritative,
                types,
                order,
                flags,
                minCE,
                minLE);
    }

    @Test
    public void null_params_fetches_all() {
        ElevationSourceBuilder builder = new ElevationSourceBuilder();
        String id = Long.toString(System.currentTimeMillis(), 16);

        final int numChunks = 5;
        final double[] resolution = new double[numChunks];
        final double[] ce = new double[numChunks];
        final double[] le = new double[numChunks];
        final boolean[] authoritative = new boolean[numChunks];
        final double[] fixedEl = new double[numChunks];
        for (int i = 0; i < numChunks; i++) {
            final double originLat = 35;
            final double originLng = -78;

            resolution[i] = 10d + (double) i / 50d;
            ce[i] = 3d + (double) i / 50d;
            le[i] = 5d + (double) i / 50d;
            authoritative[i] = (i % 2) == 0;
            fixedEl[i] = 1000d * (i + 1);

            LineString bounds = new LineString(2);
            bounds.addPoint(originLng + i, originLat);
            bounds.addPoint(originLng + i + 1, originLat);
            bounds.addPoint(originLng + i + 1, originLat - 1);
            bounds.addPoint(originLng + i, originLat - 1);
            bounds.addPoint(originLng + i, originLat);

            final int idx = i;
            builder.add(ElevationChunk.Factory.create("type",
                    "chunk://" + id + "-" + i, ElevationData.MODEL_TERRAIN,
                    resolution[i], new Polygon(bounds), ce[i], le[i],
                    authoritative[i], new ElevationChunk.Factory.Sampler() {
                        @Override
                        public void dispose() {
                        }

                        @Override
                        public double sample(double latitude,
                                double longitude) {
                            return fixedEl[idx];
                        }
                    }));
        }

        Interop<ElevationSource> interop = Interop
                .findInterop(ElevationSource.class);

        // wrap the Object with native
        final Pointer cmock = interop.wrap(builder.build(id));
        // wrap native with an Object
        final ElevationSource doubleWrap = interop.create(cmock);

        // invoke the query
        ElevationSource.Cursor result = null;
        int resultCount = 0;
        try {
            result = doubleWrap.query(null);
            while (result.moveToNext()) {
                resultCount++;
            }
        } finally {
            if (result != null)
                result.close();
        }

        assertEquals(numChunks, resultCount);
    }

    // XXX - params spatial filter
    @Test
    public void queryparams_spatial_filter_selects_all() {
        Interop<ElevationSource> interop = Interop
                .findInterop(ElevationSource.class);

        // wrap the Object with native
        final Pointer cmock = interop.wrap(create1x1DegChunks(
                "spatialfiltertest", 35, -78, 2, new ChunkSpec[4]));
        // wrap native with an Object
        final ElevationSource doubleWrap = interop.create(cmock);

        // invoke the query
        ElevationSource.Cursor result = null;
        int resultCount = 0;
        try {
            ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
            params.spatialFilter = createMBR(-76.5, 33.5, -77.5, 34.5);
            result = doubleWrap.query(params);
            while (result.moveToNext()) {
                final Envelope paramsMbb = params.spatialFilter.getEnvelope();
                final Envelope chunkMbb = result.getBounds().getEnvelope();
                assertTrue(Rectangle.intersects(paramsMbb.minX, paramsMbb.minY,
                        paramsMbb.maxX, paramsMbb.maxY, chunkMbb.minX,
                        chunkMbb.minY, chunkMbb.maxX, chunkMbb.maxY));
                resultCount++;
            }
        } finally {
            if (result != null)
                result.close();
        }

        assertEquals(4, resultCount);
    }

    @Test
    public void queryparams_spatial_filter_selects_none() {
        Interop<ElevationSource> interop = Interop
                .findInterop(ElevationSource.class);

        // wrap the Object with native
        final Pointer cmock = interop.wrap(create1x1DegChunks(
                "spatialfiltertest", 35, -78, 2, new ChunkSpec[4]));
        // wrap native with an Object
        final ElevationSource doubleWrap = interop.create(cmock);

        // invoke the query
        ElevationSource.Cursor result = null;
        int resultCount = 0;
        try {
            ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
            params.spatialFilter = createMBR(20, -40, 21, -39);
            result = doubleWrap.query(params);
            while (result.moveToNext()) {
                final Envelope paramsMbb = params.spatialFilter.getEnvelope();
                final Envelope chunkMbb = result.getBounds().getEnvelope();
                assertTrue(Rectangle.intersects(paramsMbb.minX, paramsMbb.minY,
                        paramsMbb.maxX, paramsMbb.maxY, chunkMbb.minX,
                        chunkMbb.minY, chunkMbb.maxX, chunkMbb.maxY));
                resultCount++;
            }
        } finally {
            if (result != null)
                result.close();
        }

        assertEquals(0, resultCount);
    }

    @Test
    public void queryparams_spatial_filter_selects_some() {
        Interop<ElevationSource> interop = Interop
                .findInterop(ElevationSource.class);

        // wrap the Object with native
        final Pointer cmock = interop.wrap(create1x1DegChunks(
                "spatialfiltertest", 35, -78, 2, new ChunkSpec[4]));
        // wrap native with an Object
        final ElevationSource doubleWrap = interop.create(cmock);

        // invoke the query
        ElevationSource.Cursor result = null;
        int resultCount = 0;
        try {
            ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
            params.spatialFilter = createMBR(-77.5, 33.5, -78.5, 34.5);
            result = doubleWrap.query(params);
            while (result.moveToNext()) {
                final Envelope paramsMbb = params.spatialFilter.getEnvelope();
                final Envelope chunkMbb = result.getBounds().getEnvelope();
                assertTrue(Rectangle.intersects(paramsMbb.minX, paramsMbb.minY,
                        paramsMbb.maxX, paramsMbb.maxY, chunkMbb.minX,
                        chunkMbb.minY, chunkMbb.maxX, chunkMbb.maxY));
                resultCount++;
            }
        } finally {
            if (result != null)
                result.close();
        }

        assertEquals(2, resultCount);
    }

    // XXX - params type filter
    @Test
    public void queryparams_types_filter_selects_all() {
        Interop<ElevationSource> interop = Interop
                .findInterop(ElevationSource.class);

        // wrap the Object with native
        final Pointer cmock = interop.wrap(create1x1DegChunks(
                "spatialfiltertest", 35, -78, 2, new ChunkSpec[4]));
        // wrap native with an Object
        final ElevationSource doubleWrap = interop.create(cmock);

        // invoke the query
        ElevationSource.Cursor result = null;
        int resultCount = 0;
        try {
            ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
            params.types = new HashSet<>(
                    java.util.Arrays.<String> asList("0", "1", "2", "3"));
            result = doubleWrap.query(params);
            while (result.moveToNext()) {
                assertTrue(params.types.contains(result.getType()));
                resultCount++;
            }
        } finally {
            if (result != null)
                result.close();
        }

        assertEquals(4, resultCount);
    }

    @Test
    public void queryparams_types_filter_selects_none() {
        Interop<ElevationSource> interop = Interop
                .findInterop(ElevationSource.class);

        // wrap the Object with native
        final Pointer cmock = interop.wrap(create1x1DegChunks(
                "spatialfiltertest", 35, -78, 2, new ChunkSpec[4]));
        // wrap native with an Object
        final ElevationSource doubleWrap = interop.create(cmock);

        // invoke the query
        ElevationSource.Cursor result = null;
        int resultCount = 0;
        try {
            ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
            params.types = new HashSet<>(
                    java.util.Arrays.<String> asList("a", "b", "c", "d"));
            result = doubleWrap.query(params);
            while (result.moveToNext()) {
                assertTrue(params.types.contains(result.getType()));
                resultCount++;
            }
        } finally {
            if (result != null)
                result.close();
        }

        assertEquals(0, resultCount);
    }

    @Test
    public void queryparams_types_filter_selects_some() {
        Interop<ElevationSource> interop = Interop
                .findInterop(ElevationSource.class);

        // wrap the Object with native
        final Pointer cmock = interop.wrap(create1x1DegChunks(
                "spatialfiltertest", 35, -78, 2, new ChunkSpec[4]));
        // wrap native with an Object
        final ElevationSource doubleWrap = interop.create(cmock);

        // invoke the query
        ElevationSource.Cursor result = null;
        int resultCount = 0;
        try {
            ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
            params.types = new HashSet<>(
                    java.util.Arrays.<String> asList("1", "3"));
            result = doubleWrap.query(params);
            while (result.moveToNext()) {
                assertTrue(params.types.contains(result.getType()));
                resultCount++;
            }
        } finally {
            if (result != null)
                result.close();
        }

        assertEquals(2, resultCount);
    }

    // XXX - params resolution filter
    // XXX - params order

    // Utilities

    static Polygon createMBR(double minX, double minY, double maxX,
            double maxY) {
        LineString ls = new LineString(2);
        ls.addPoint(maxY, minX);
        ls.addPoint(maxY, maxX);
        ls.addPoint(minY, maxX);
        ls.addPoint(minY, minX);
        ls.addPoint(maxY, minX);
        return new Polygon(ls);
    }

    static ElevationSource create1x1DegChunks(String name, double originLat,
            double originLng, int gridWidth, final ChunkSpec[] specs) {
        ElevationSourceBuilder builder = new ElevationSourceBuilder();
        for (int i = 0; i < specs.length; i++) {
            final double minX = originLng + (i % gridWidth);
            final double maxY = originLat - (i / gridWidth);
            final double maxX = minX + 1;
            final double minY = maxY - 1;

            if (specs[i] == null) {
                specs[i] = new ChunkSpec();
                specs[i].uri = "chunk://" + name + "&id=" + i;
                specs[i].type = String.valueOf(i);
                specs[i].ce = 2 + ((double) i / 25d);
                specs[i].le = 4 + ((double) i / 25d);
                specs[i].resolution = 12 + ((double) i / 50d);
                specs[i].sampleValue = i;
                specs[i].flags = (i % 2 == 0) ? ElevationData.MODEL_TERRAIN
                        : ElevationData.MODEL_SURFACE;
                specs[i].authoritative = (i % 2 == 0);
            }

            final int idx = i;
            builder.add(ElevationChunk.Factory.create(specs[i].type,
                    specs[i].uri, specs[i].flags, specs[i].resolution,
                    createMBR(minX, minY, maxX, maxY), specs[i].ce, specs[i].le,
                    specs[i].authoritative,
                    new ElevationChunk.Factory.Sampler() {
                        @Override
                        public void dispose() {
                        }

                        @Override
                        public double sample(double latitude,
                                double longitude) {
                            return specs[idx].sampleValue;
                        }
                    }));
        }
        return builder.build(name);
    }

    static class ChunkSpec {
        String uri;
        double ce;
        double le;
        double resolution;
        double sampleValue;
        String type;
        int flags;
        boolean authoritative;
    }
}
