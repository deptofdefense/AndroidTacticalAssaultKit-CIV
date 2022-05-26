package com.atakmap.map.elevation;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.util.Disposable;
import com.atakmap.coremap.maps.coords.GeoBounds;

import java.util.ArrayList;
import java.util.Collections;

public final class Adapter {
    public static ElevationChunk adapt(ImageInfo info, boolean authoritative) {
        return adapt(info, authoritative, null);
    }

    private static ElevationChunk adapt(ImageInfo info, boolean authoritative, ElevationData.Hints hints) {
        final ElevationData data = ElevationManager.createData(info);
        if(data == null)
            return null;

        return ElevationChunk.Factory.create(
                data.getType(),
                info.path,
                data.getElevationModel(),
                info.maxGsd,
                (Polygon)
                DatasetDescriptor.createSimpleCoverage(info.upperLeft,
                                                       info.upperRight,
                                                       info.lowerRight,
                                                       info.lowerLeft),
                Double.NaN,
                Double.NaN,
                authoritative,
                new ElevationDataSampler(data, hints));
    }

    public static ElevationSource adapt(final String name, final MosaicDatabase2 source, final boolean authoritative) {
        return new MosaicDatabaseSource(name, authoritative, source);
    }

    static ElevationSource.QueryParameters adapt(final ElevationManager.QueryParameters filter, ElevationData.Hints hints) {
        ElevationSource.QueryParameters params = null;
        if(filter != null) {
            params = new ElevationSource.QueryParameters();
            params.order = Collections.<ElevationSource.QueryParameters.Order>singletonList(ElevationSource.QueryParameters.Order.ResolutionDesc);
            params.spatialFilter = filter.spatialFilter;
            params.maxResolution = filter.maxResolution;
            params.minResolution = filter.minResolution;
            params.types = filter.types;
            if(filter.elevationModel != 0)
                params.flags = Integer.valueOf(filter.elevationModel);
        }
        if(hints != null) {
            if(params == null)
                params = new ElevationSource.QueryParameters();
            params.targetResolution = hints.resolution;
            if(params.spatialFilter == null && hints.bounds != null) {
                // adopt spatial filter from hints
                params.spatialFilter = GeometryFactory.fromEnvelope(new Envelope(hints.bounds.getWest(), hints.bounds.getSouth(), 0d, hints.bounds.getEast(), hints.bounds.getNorth(), 0d));
            } else if(params.spatialFilter != null && hints.bounds != null) {
                // intersect the spatial filters
                Envelope spatialFilter = params.spatialFilter.getEnvelope();
                spatialFilter.minX = Math.max(spatialFilter.minX, hints.bounds.getWest());
                spatialFilter.minY = Math.max(spatialFilter.minY, hints.bounds.getSouth());
                spatialFilter.maxX = Math.min(spatialFilter.maxX, hints.bounds.getEast());
                spatialFilter.maxY = Math.min(spatialFilter.maxY, hints.bounds.getNorth());

                // if the two regions did not intersect, create an empty rectangle
                if(spatialFilter.minX > spatialFilter.maxX)
                    spatialFilter.maxX = spatialFilter.minX;
                if(spatialFilter.minY > spatialFilter.maxY)
                    spatialFilter.maxY = spatialFilter.minY;

                params.spatialFilter = GeometryFactory.fromEnvelope(spatialFilter);
            }
        }

        return params;
    }

    final static class ElevationDataSampler extends ElevationChunk.Factory.Sampler {

        private ElevationData impl;
        private ElevationData.Hints hints;

        ElevationDataSampler(ElevationData impl, ElevationData.Hints hints) {
            this.impl = impl;
            this.hints = hints;
        }

        @Override
        public double sample(double latitude, double longitude) {
            return this.impl.getElevation(latitude, longitude);
        }

        @Override
        public boolean sample(double[] lla, int off, int len) {
            return this.sample(lla, off, len, this.hints);
        }

        public boolean sample(double[] lla, int off, int len, ElevationData.Hints legacyHints) {
            ArrayList<GeoPoint> pts = new ArrayList<GeoPoint>(len);


            double north = -90;
            double south = 90;
            double east = -180;
            double west = 180;

            for(int i = 0; i < len; i++) {
                final int idx = (i+off)*3;
                if(!Double.isNaN(lla[idx+2]))
                    continue;
                pts.add(new GeoPoint(lla[idx+1], lla[idx]));
                final double lng = lla[idx];
                final double lat = lla[idx+1];
                if(lat > north)
                    north = lat;
                if(lat < south)
                    south = lat;
                if(lng > east)
                    east = lng;
                if(lng < west)
                    west = lng;
            }
            if(pts.isEmpty())
                return true;

            ElevationData.Hints h = legacyHints;
            if(legacyHints == null) {
                h = new ElevationData.Hints();
                h.bounds = new GeoBounds(north, west, south, east);
            } else if(legacyHints.bounds == null) {
                h = new ElevationData.Hints(legacyHints);
                h.bounds = new GeoBounds(north, west, south, east);
            }

            double[] els = new double[pts.size()];
            this.impl.getElevation(pts.iterator(), els, h);
            boolean retval = true;
            int elIdx = 0;
            for(int i = 0; i < len; i++) {
                final int idx = (i+off)*3;
                if(!Double.isNaN(lla[idx+2]))
                    continue;
                final double el = els[elIdx++];
                if(Double.isNaN(el))
                    retval = false;
                else
                    lla[idx+2] = el;
                if(elIdx == els.length)
                    break;
            }
            return retval;
        }

        @Override
        public void dispose() {
            if(this.impl instanceof Disposable)
                ((Disposable) this.impl).dispose();
        }
    }

    final static class MosaicDatabaseSource extends ElevationSource {
        private String name;
        private boolean authoritative;
        private MosaicDatabase2 source;

        MosaicDatabaseSource(String name, boolean authoritative, MosaicDatabase2 source) {
            this.name = name;
            this.authoritative = authoritative;
            this.source = source;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Cursor query(QueryParameters params) {
            MosaicDatabase2.QueryParameters mparams = null;
            if(params != null) {
                mparams = new MosaicDatabase2.QueryParameters();
                if(!Double.isNaN(params.minResolution))
                    mparams.minGsd = params.minResolution;
                if(!Double.isNaN(params.maxResolution))
                    mparams.maxGsd = params.maxResolution;
                if(params.authoritative != null && params.authoritative.booleanValue() != authoritative)
                    return ElevationSource.Cursor.EMPTY;
                if(params.spatialFilter != null)
                    mparams.spatialFilter = params.spatialFilter;
                if(params.types != null)
                    mparams.types = params.types;
            }

            return new CursorAdapter(source.query(mparams), authoritative, params);
        }

        @Override
        public Envelope getBounds() {
            final MosaicDatabase2.Coverage cov = source.getCoverage();
            return cov != null ? cov.geometry.getEnvelope() : new Envelope(-180d, -90d, 0d, 180d, 90d, 180d);
        }

        @Override
        public void addOnContentChangedListener(OnContentChangedListener l) {}
        @Override
        public void removeOnContentChangedListener(OnContentChangedListener l) {}
    }

    final static class CursorAdapter implements ElevationSource.Cursor {

        private MosaicDatabase2.Cursor impl;
        private boolean authoritative;
        private ElevationSource.QueryParameters filter;
        private ElevationData.Hints hints;

        public CursorAdapter(MosaicDatabase2.Cursor impl, boolean authoritative, ElevationSource.QueryParameters filter) {
            this.impl = impl;
            this.authoritative = authoritative;
            this.filter = filter;

            if(filter != null) {
                hints = new ElevationData.Hints();
                if(filter.spatialFilter != null) {
                    Envelope mbb = filter.spatialFilter.getEnvelope();
                    hints.bounds = new GeoBounds(mbb.maxY, mbb.minX, mbb.minY, mbb.maxX);
                }
                hints.resolution = filter.targetResolution;
                if(Double.isNaN(hints.resolution))
                    hints.resolution = filter.targetResolution;
            }
        }

        @Override
        public ElevationChunk get() {
            return adapt(this.impl.asFrame(), authoritative, this.hints);
        }

        @Override
        public int getFlags() {
            ElevationChunk ec = get();
            if (ec != null) 
                return ec.getFlags();
            else 
                return 0;
        }

        @Override
        public double getResolution() {
            return this.impl.getMaxGSD();
        }

        @Override
        public boolean isAuthoritative() {
            return this.authoritative;
        }

        @Override
        public double getCE() {
            return Double.NaN;
        }

        @Override
        public double getLE() {
            return Double.NaN;
        }

        @Override
        public String getUri() {
            return this.impl.getPath();
        }

        @Override
        public String getType() {
            return this.impl.getType();
        }

        @Override
        public Geometry getBounds() {
            return DatasetDescriptor.createSimpleCoverage(this.impl.getUpperLeft(), this.impl.getUpperRight(), this.impl.getLowerRight(), this.impl.getLowerLeft());
        }

        @Override
        public boolean moveToNext() {
            do {
                if(!this.impl.moveToNext())
                    return false;
                if(!ElevationSource.accept(this, filter))
                    continue;
                return true;
            } while(true);
        }

        @Override
        public void close() {
            this.impl.close();
        }

        @Override
        public boolean isClosed() {
            return this.impl.isClosed();
        }
    }
}
