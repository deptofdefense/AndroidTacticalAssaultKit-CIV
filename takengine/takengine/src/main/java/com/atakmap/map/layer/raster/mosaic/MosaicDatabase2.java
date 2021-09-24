package com.atakmap.map.layer.raster.mosaic;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.RowIterator;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.raster.ImageInfo;

public interface MosaicDatabase2 {

    public String getType();
    public void open(File f);
    public void close();
    public Coverage getCoverage();
    public void getCoverages(Map<String, Coverage> coverages);
    public Coverage getCoverage(String type);
    public Cursor query(QueryParameters params);

    /**************************************************************************/

    public static class QueryParameters {
        public static enum GsdCompare {
            MinimumGsd,
            MaximumGsd,
        }
        public static enum Order {
            MinGsdAsc,
            MinGsdDesc,
            MaxGsdAsc,
            MaxGsdDesc,
        }

        public String path;
        public Geometry spatialFilter;
        public double minGsd;
        public double maxGsd;
        public Set<String> types;
        public int srid;
        public Boolean precisionImagery;
        public GsdCompare minGsdCompare = GsdCompare.MaximumGsd;
        public GsdCompare maxGsdCompare = GsdCompare.MaximumGsd;
        public Order order = Order.MaxGsdDesc;

        public QueryParameters() {
            this.path = null;
            this.spatialFilter = null;
            this.minGsd = Double.NaN;
            this.maxGsd = Double.NaN;
            this.types = null;
            this.srid = -1;
            this.precisionImagery = null;
        }
        
        public QueryParameters(QueryParameters other) {
            this.path = other.path;
            // XXX - needs to be cloned
            this.spatialFilter = other.spatialFilter;
            this.minGsd = other.minGsd;
            this.maxGsd = other.maxGsd;
            if(other.types != null)
                this.types = new HashSet<String>(other.types);
            else
                this.types = null;
            this.srid = other.srid;
            this.precisionImagery = null;
            this.minGsdCompare = other.minGsdCompare;
            this.maxGsdCompare = other.maxGsdCompare;
        }
    }

    public static interface Cursor extends RowIterator {
        public final static Cursor EMPTY = new Cursor() {

            @Override
            public boolean moveToNext() { return false; }
            @Override
            public void close() {}
            @Override
            public boolean isClosed() { return false; }
            @Override
            public GeoPoint getUpperLeft() { return null; }
            @Override
            public GeoPoint getUpperRight() { return null; }
            @Override
            public GeoPoint getLowerRight() { return null; }
            @Override
            public GeoPoint getLowerLeft() { return null; }
            @Override
            public double getMinLat() { return 0; }
            @Override
            public double getMinLon() { return 0; }
            @Override
            public double getMaxLat() { return 0; }
            @Override
            public double getMaxLon() { return 0; }
            @Override
            public String getPath() { return null; }
            @Override
            public String getType() { return null; }
            @Override
            public double getMinGSD() { return 0; }
            @Override
            public double getMaxGSD() { return 0; }
            @Override
            public int getWidth() { return 0; }
            @Override
            public int getHeight() { return 0; }
            @Override
            public int getId() { return 0; }
            @Override
            public int getSrid() { return -1; }
            @Override
            public boolean isPrecisionImagery() { return false; }
            @Override
            public Frame asFrame() { return null; }
        };

        public abstract GeoPoint getUpperLeft();
        public abstract GeoPoint getUpperRight();
        public abstract GeoPoint getLowerRight();
        public abstract GeoPoint getLowerLeft();
        public abstract double getMinLat();
        public abstract double getMinLon();
        public abstract double getMaxLat();
        public abstract double getMaxLon();
        public abstract String getPath();
        public abstract String getType();
        public abstract double getMinGSD();
        public abstract double getMaxGSD();
        public abstract int getWidth();
        public abstract int getHeight();
        public abstract int getId();
        public int getSrid();
        public boolean isPrecisionImagery();
        public MosaicDatabase2.Frame asFrame();
    }
    
    public final static class Frame extends ImageInfo {
        public final int id;
        public final double minLat;
        public final double minLon;
        public final double maxLat;
        public final double maxLon;
        public final double minGsd;

        public Frame(int id, String path, String type, boolean precisionImagery, double minLat, double minLon, double maxLat,
                double maxLon, GeoPoint upperLeft, GeoPoint upperRight, GeoPoint lowerRight,
                GeoPoint lowerLeft, double minGsd, double maxGsd, int width, int height, int srid) {
            super(path, type, precisionImagery, upperLeft, upperRight, lowerRight, lowerLeft, maxGsd, width, height, srid);
            
            this.id = id;
            this.minLat = minLat;
            this.minLon = minLon;
            this.maxLat = maxLat;
            this.maxLon = maxLon;
            this.minGsd = minGsd;
        }
        
        public Frame(Cursor row) {
            this(row.getId(),
                 row.getPath(),
                 row.getType(),
                 row.isPrecisionImagery(),
                 row.getMinLat(),
                 row.getMinLon(),
                 row.getMaxLat(),
                 row.getMaxLon(),
                 row.getUpperLeft(),
                 row.getUpperRight(),
                 row.getLowerRight(),
                 row.getLowerLeft(),
                 row.getMinGSD(),
                 row.getMaxGSD(),
                 row.getWidth(),
                 row.getHeight(),
                 row.getSrid());
        }
    }

    public final static class Coverage {
        public final Geometry geometry;
        public final double minGSD;
        public final double maxGSD;

        public Coverage(Geometry geometry, double minGSD, double maxGSD) {
            this.geometry = geometry;
            this.minGSD = minGSD;
            this.maxGSD = maxGSD;
        }

        public String toString() {
            final Envelope mbb = this.geometry.getEnvelope();
            return "Coverage {minLat=" + mbb.minY + ",minLon=" + mbb.minX + ",maxLat=" + mbb.maxY
                    + ",maxLon=" + mbb.maxX + ",minGSD=" + minGSD + ",maxGSD=" + maxGSD + "}";
        }
    }
}
