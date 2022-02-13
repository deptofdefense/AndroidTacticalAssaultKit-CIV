package com.atakmap.map.layer.raster.nativeimagery;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.gdal.GdalLayerInfo;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.math.Rectangle;

final class ImageDatasetDescriptorMosaicDatabase implements MosaicDatabase2 {

    final ImageDatasetDescriptor desc;
    final String type;
    final Coverage coverage;
    final Envelope mbb;
    final String uri;
    private final Map<String, Coverage> coverages;

    public ImageDatasetDescriptorMosaicDatabase(ImageDatasetDescriptor desc) {
        this.desc = desc;
        
        this.type = this.desc.getImageryType();
        this.coverage = new Coverage(this.desc.getCoverage(null), this.desc.getMinResolution(null), this.desc.getMaxResolution(null));
        this.mbb = this.coverage.geometry.getEnvelope();
        this.coverages = Collections.singletonMap(this.type, this.coverage);
        this.uri = GdalLayerInfo.getGdalFriendlyUri(desc);
    }

    @Override
    public String getType() {
        return "image-descriptor";
    }

    @Override
    public void open(File f) {}

    @Override
    public void close() {}

    @Override
    public Coverage getCoverage() {
        return this.coverage;
    }

    @Override
    public void getCoverages(Map<String, Coverage> coverages) {
        coverages.clear();
        coverages.putAll(this.coverages);
    }

    @Override
    public Coverage getCoverage(String type) {
        return this.coverages.get(type);
    }

    @Override
    public Cursor query(QueryParameters params) {
        boolean result = true;
        if(params != null) {
            result &= ((params.types == null) || params.types.contains(this.type));
            if(params.spatialFilter != null) {
                final Envelope roi = params.spatialFilter.getEnvelope();
                result &= Rectangle.intersects(this.mbb.minX,
                                               this.mbb.minY,
                                               this.mbb.maxX,
                                               this.mbb.maxY,
                                               roi.minX,
                                               roi.minY,
                                               roi.maxX,
                                               roi.maxY);
            }
            result &= (Double.isNaN(params.minGsd) || this.coverage.maxGSD <= params.minGsd);
            result &= (Double.isNaN(params.maxGsd) || this.coverage.minGSD >= params.maxGsd);
            result &= (params.path == null || params.path.equals(this.desc.getUri()));
            result &= (params.srid < 0 || params.srid == this.desc.getSpatialReferenceID());
            result &= (params.precisionImagery == null || params.precisionImagery.booleanValue() == this.desc.isPrecisionImagery());
        }

        return new CursorImpl(result);
    }
    

    private class CursorImpl implements MosaicDatabase2.Cursor {

        boolean hasRow;

        public CursorImpl(boolean hasRow) {
            this.hasRow = hasRow;
        }

        @Override
        public boolean moveToNext() {
            final boolean r = this.hasRow;
            this.hasRow = false;
            return r;
        }

        @Override
        public GeoPoint getUpperLeft() {
            return ImageDatasetDescriptorMosaicDatabase.this.desc.getUpperLeft();
        }

        @Override
        public GeoPoint getUpperRight() {
            return ImageDatasetDescriptorMosaicDatabase.this.desc.getUpperRight();
        }

        @Override
        public GeoPoint getLowerRight() {
            return ImageDatasetDescriptorMosaicDatabase.this.desc.getLowerRight();
        }

        @Override
        public GeoPoint getLowerLeft() {
            return ImageDatasetDescriptorMosaicDatabase.this.desc.getLowerLeft();
        }

        @Override
        public double getMinLat() {
            return ImageDatasetDescriptorMosaicDatabase.this.mbb.minY;
        }

        @Override
        public double getMinLon() {
            return ImageDatasetDescriptorMosaicDatabase.this.mbb.minX;
        }

        @Override
        public double getMaxLat() {
            return ImageDatasetDescriptorMosaicDatabase.this.mbb.maxY;
        }

        @Override
        public double getMaxLon() {
            return ImageDatasetDescriptorMosaicDatabase.this.mbb.maxX;
        }

        @Override
        public String getPath() {
            return ImageDatasetDescriptorMosaicDatabase.this.uri;
        }

        @Override
        public String getType() {
            return ImageDatasetDescriptorMosaicDatabase.this.type;
        }

        @Override
        public double getMinGSD() {
            return ImageDatasetDescriptorMosaicDatabase.this.coverage.minGSD;
        }

        @Override
        public double getMaxGSD() {
            return ImageDatasetDescriptorMosaicDatabase.this.coverage.maxGSD;
        }

        @Override
        public int getWidth() {
            return ImageDatasetDescriptorMosaicDatabase.this.desc.getWidth();
        }

        @Override
        public int getHeight() {
            return ImageDatasetDescriptorMosaicDatabase.this.desc.getHeight();
        }

        @Override
        public int getId() {
            return 1;
        }
        
        @Override
        public int getSrid() {
            return ImageDatasetDescriptorMosaicDatabase.this.desc.getSpatialReferenceID();
        }
        
        @Override
        public boolean isPrecisionImagery() {
            return ImageDatasetDescriptorMosaicDatabase.this.desc.isPrecisionImagery();
        }

        @Override
        public MosaicDatabase2.Frame asFrame() {
            return new MosaicDatabase2.Frame(this);
        }
        
        @Override
        public boolean isClosed() {
            return false;
        }
        
        @Override
        public void close() {}
    }
}