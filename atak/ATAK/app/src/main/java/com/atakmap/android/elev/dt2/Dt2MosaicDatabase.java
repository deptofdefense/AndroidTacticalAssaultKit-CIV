
package com.atakmap.android.elev.dt2;

import java.io.File;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;

import gov.tak.api.annotation.DeprecatedApi;

/**
 * @deprecated use {@link com.atakmap.map.formats.dted.DtedElevationSource}
 */
@Deprecated
@DeprecatedApi(since = "4.6", forRemoval = true, removeAt = "4.9")
public class Dt2MosaicDatabase implements MosaicDatabase2 {

    private final static Geometry world = DatasetDescriptor
            .createSimpleCoverage(
                    new GeoPoint(90, -180),
                    new GeoPoint(90, 180),
                    new GeoPoint(-90, 180),
                    new GeoPoint(-90, -180));

    private final static Coverage DTED0_COVERAGE = new Coverage(world,
            Dt2ElevationData.DtedFormat.DTED0.resolution,
            Dt2ElevationData.DtedFormat.DTED0.resolution);
    private final static Coverage DTED1_COVERAGE = new Coverage(world,
            Dt2ElevationData.DtedFormat.DTED1.resolution,
            Dt2ElevationData.DtedFormat.DTED1.resolution);
    private final static Coverage DTED2_COVERAGE = new Coverage(world,
            Dt2ElevationData.DtedFormat.DTED2.resolution,
            Dt2ElevationData.DtedFormat.DTED2.resolution);
    private final static Coverage DTED3_COVERAGE = new Coverage(world,
            Dt2ElevationData.DtedFormat.DTED3.resolution,
            Dt2ElevationData.DtedFormat.DTED3.resolution);

    private final static Coverage dtedCov = new Coverage(world,
            DTED0_COVERAGE.minGSD, DTED3_COVERAGE.maxGSD);

    private File basePath;

    @Override
    public String getType() {
        return "dted";
    }

    @Override
    public void open(final File f) {
        this.basePath = f;
    }

    @Override
    public void close() {
        this.basePath = null;
    }

    @Override
    public Coverage getCoverage() {
        return dtedCov;
    }

    @Override
    public void getCoverages(final Map<String, Coverage> coverages) {
        coverages.put(Dt2ElevationData.DtedFormat.DTED0.type, DTED0_COVERAGE);
        coverages.put(Dt2ElevationData.DtedFormat.DTED1.type, DTED1_COVERAGE);
        coverages.put(Dt2ElevationData.DtedFormat.DTED2.type, DTED2_COVERAGE);
        coverages.put(Dt2ElevationData.DtedFormat.DTED3.type, DTED3_COVERAGE);
    }

    @Override
    public Coverage getCoverage(final String type) {
        if (type == null)
            return dtedCov;
        if (!type.matches("DTED[0-3]"))
            return null;
        // XXX - efficiency
        Map<String, Coverage> covs = new HashMap<>();
        this.getCoverages(covs);
        return covs.get(type);
    }

    @Override
    public Cursor query(QueryParameters params) {
        Envelope mbb;
        List<Dt2ElevationData.DtedFormat> fmts = new LinkedList<>();
        fmts.add(Dt2ElevationData.DtedFormat.DTED3);
        fmts.add(Dt2ElevationData.DtedFormat.DTED2);
        fmts.add(Dt2ElevationData.DtedFormat.DTED1);
        fmts.add(Dt2ElevationData.DtedFormat.DTED0);

        if (params != null) {
            if (params.spatialFilter != null)
                mbb = params.spatialFilter.getEnvelope();
            else
                mbb = world.getEnvelope();

            if (params.types != null) {
                Iterator<Dt2ElevationData.DtedFormat> iter = fmts.iterator();
                while (iter.hasNext()) {
                    if (!params.types.contains(iter.next().type))
                        iter.remove();
                }
            }
            if (!Double.isNaN(params.minGsd)) {
                Iterator<Dt2ElevationData.DtedFormat> iter = fmts.iterator();
                while (iter.hasNext()) {
                    if (iter.next().resolution > params.minGsd)
                        iter.remove();
                }
            }
            if (!Double.isNaN(params.maxGsd)) {
                Iterator<Dt2ElevationData.DtedFormat> iter = fmts.iterator();
                while (iter.hasNext()) {
                    if (iter.next().resolution < params.maxGsd)
                        iter.remove();
                }
            }
        } else {
            mbb = world.getEnvelope();
        }

        return new CursorImpl(this.basePath,
                fmts.toArray(new Dt2ElevationData.DtedFormat[0]),
                mbb);
    }

    private static class CursorImpl implements Cursor {

        final File baseDir;
        final int minCellLat;
        final int maxCellLat;
        final int minCellLng;
        final int maxCellLng;
        int idx;
        final int limit;
        final int numFormats;
        File cell;
        Dt2ElevationData.DtedFormat cellFormat;
        Dt2ElevationData.DtedFormat[] formats;
        private final BitSet[] coverages;
        private final int lngSpan;

        CursorImpl(final File baseDir,
                final Dt2ElevationData.DtedFormat[] formats,
                final Envelope mbb) {
            this.baseDir = baseDir;
            this.formats = formats;
            this.numFormats = formats.length;

            this.maxCellLat = (int) Math.floor(mbb.maxY);
            this.minCellLng = (int) Math.floor(mbb.minX);
            this.minCellLat = (int) Math.floor(mbb.minY);
            this.maxCellLng = (int) Math.floor(mbb.maxX);
            this.lngSpan = (this.maxCellLng - this.minCellLng) + 1;

            this.idx = -1;
            this.limit = (this.maxCellLat - this.minCellLat + 1)
                    * (this.maxCellLng - this.minCellLng + 1)
                    * this.numFormats;

            this.cell = null;
            this.cellFormat = null;
            final Dt2FileWatcher w = Dt2FileWatcher.getInstance();
            if (w != null)
                this.coverages = w.getCoverages();
            else
                this.coverages = null;
        }

        @Override
        public boolean moveToNext() {
            do {
                this.idx++;
                this.cell = null;
                if (this.idx >= this.limit)
                    break;

                int lat = (int) Math.floor(getMinLat());
                int lng = (int) Math.floor(getMinLon());
                int cvIdx = Dt2FileWatcher.getCoverageIndex(lat, lng);

                for (int i = (this.idx
                        % this.numFormats); i < this.numFormats; i++) {
                    Dt2ElevationData.DtedFormat fmt = this.formats[i];
                    int level = fmt.ordinal();
                    if (coverages != null) {
                        BitSet coverage = this.coverages[level];
                        if (cvIdx < 0 || cvIdx >= coverage.length()
                                || !coverage.get(cvIdx)) {
                            this.idx++;
                            continue;
                        }
                    }
                    final File f = new File(this.baseDir,
                            Dt2FileWatcher.getRelativePath(level, lat, lng));
                    if (coverages == null && !f.exists()) {
                        idx++;
                        continue;
                    }
                    this.cell = f;
                    this.cellFormat = fmt;
                    break;
                }
                if (this.cell != null)
                    break;
            } while (true);
            return (this.cell != null);
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public GeoPoint getUpperLeft() {
            return new GeoPoint(this.getMaxLat(), this.getMinLon());
        }

        @Override
        public GeoPoint getUpperRight() {
            return new GeoPoint(this.getMaxLat(), this.getMaxLon());
        }

        @Override
        public GeoPoint getLowerRight() {
            return new GeoPoint(this.getMinLat(), this.getMaxLon());
        }

        @Override
        public GeoPoint getLowerLeft() {
            return new GeoPoint(this.getMinLat(), this.getMinLon());
        }

        @Override
        public double getMinLat() {
            return this.maxCellLat - ((this.idx / this.numFormats) / lngSpan);
        }

        @Override
        public double getMinLon() {
            return this.minCellLng + ((this.idx / this.numFormats) % lngSpan);
        }

        @Override
        public double getMaxLat() {
            return this.getMinLat() + 1;
        }

        @Override
        public double getMaxLon() {
            return this.getMinLon() + 1;
        }

        @Override
        public String getPath() {
            return this.cell.getAbsolutePath();
        }

        @Override
        public String getType() {
            return this.cellFormat.type;
        }

        @Override
        public double getMinGSD() {
            return this.cellFormat.resolution;
        }

        @Override
        public double getMaxGSD() {
            return this.cellFormat.resolution;
        }

        @Override
        public int getWidth() {
            // XXX - 
            return 0;
        }

        @Override
        public int getHeight() {
            // XXX - 
            return 0;
        }

        @Override
        public int getId() {
            return (int) ((this.getMinLat() * 360) + this.getMinLon())
                    * this.cellFormat.ordinal();
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
            return new Frame(this);
        }
    }
}
