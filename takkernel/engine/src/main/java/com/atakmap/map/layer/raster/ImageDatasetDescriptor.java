package com.atakmap.map.layer.raster;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import android.util.Pair;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.Polygon;

public final class ImageDatasetDescriptor extends DatasetDescriptor {
    private final int width;
    private final int height;
    private final Polygon bounds;
    private final String imageryType;
    private final boolean isPrecisionImagery;

    public ImageDatasetDescriptor(String name,
                                  String uri,
                                  String provider,
                                  String datasetType,
                                  String imageryType,
                                  int width,
                                  int height,
                                  int numResolutionLevels,
                                  GeoPoint upperLeft,
                                  GeoPoint upperRight,
                                  GeoPoint lowerRight,
                                  GeoPoint lowerLeft,
                                  int srid,
                                  boolean isRemote,
                                  File workingDir,
                                  Map<String, String> extraData) {

        this(name,
             uri,
             provider,
             datasetType,
             imageryType,
             width,
             height,
             computeGSD(width, height, upperLeft, upperRight, lowerRight, lowerLeft),
             numResolutionLevels,
             upperLeft,
             upperRight,
             lowerRight,
             lowerLeft,
             srid,
             isRemote,
             false,
             workingDir,
             extraData);
    }
    
    public ImageDatasetDescriptor(String name,
                                  String uri,
                                  String provider,
                                  String datasetType,
                                  String imageryType,
                                  int width,
                                  int height,
                                  int numResolutionLevels,
                                  GeoPoint upperLeft,
                                  GeoPoint upperRight,
                                  GeoPoint lowerRight,
                                  GeoPoint lowerLeft,
                                  int srid,
                                  boolean isRemote,
                                  boolean precisionImagery,
                                  File workingDir,
                                  Map<String, String> extraData) {

        this(name,
             uri,
             provider,
             datasetType,
             imageryType,
             width,
             height,
             computeGSD(width, height, upperLeft, upperRight, lowerRight, lowerLeft),
             numResolutionLevels,
             upperLeft,
             upperRight,
             lowerRight,
             lowerLeft,
             srid,
             isRemote,
             precisionImagery,
             workingDir,
             extraData);
    }

    public ImageDatasetDescriptor(String name,
                                  String uri,
                                  String provider,
                                  String datasetType,
                                  String imageryType,
                                  int width,
                                  int height,
                                  double resolution,
                                  int numResolutionLevels,
                                  GeoPoint upperLeft,
                                  GeoPoint upperRight,
                                  GeoPoint lowerRight,
                                  GeoPoint lowerLeft,
                                  int srid,
                                  boolean isRemote,
                                  File workingDir,
                                  Map<String, String> extraData) {
        
        this(0L,
             name,
             uri,
             provider,
             datasetType,
             imageryType,
             resolution*(1<<(numResolutionLevels-1)),
             resolution,
             createSimpleCoverage(upperLeft, upperRight, lowerRight, lowerLeft),
             srid,
             isRemote,
             workingDir,
             extraData,
             width,
             height,
             false);
    }
    
    public ImageDatasetDescriptor(String name,
                                  String uri,
                                  String provider,
                                  String datasetType,
                                  String imageryType,
                                  int width,
                                  int height,
                                  double resolution,
                                  int numResolutionLevels,
                                  GeoPoint upperLeft,
                                  GeoPoint upperRight,
                                  GeoPoint lowerRight,
                                  GeoPoint lowerLeft,
                                  int srid,
                                  boolean isRemote,
                                  boolean precisionImagery,
                                  File workingDir,
                                  Map<String, String> extraData) {

        this(0L,
             name,
             uri,
             provider,
             datasetType,
             imageryType,
             resolution*(1<<(numResolutionLevels-1)),
             resolution,
             createSimpleCoverage(upperLeft, upperRight, lowerRight, lowerLeft),
             srid,
             isRemote,
             workingDir,
             extraData,
             width,
             height,
             precisionImagery);
    }

    ImageDatasetDescriptor(long layerId,
                           String name,
                           String uri,
                           String provider,
                           String datasetType,
                           String imageryType,
                           double minResolution,
                           double maxResolution,
                           Geometry coverage,
                           int srid,
                           boolean isRemote,
                           File workingDir,
                           Map<String, String> extraData,
                           int width,
                           int height,
                           boolean precisionImagery) {
        
        super(layerId,
              name,
              uri,
              provider,
              datasetType,
              Collections.singleton(imageryType),
              Collections.singletonMap(imageryType, Pair.create(Double.valueOf(minResolution), Double.valueOf(maxResolution))),
              Collections.singletonMap(imageryType, coverage),
              srid,
              isRemote,
              workingDir,
              extraData);

        this.imageryType = imageryType;
        this.width = width;
        this.height = height;
        this.bounds = (Polygon)super.getCoverage(imageryType);
        this.isPrecisionImagery = precisionImagery;
    }
    
    public String getImageryType() {
        return this.imageryType;
    }

    public boolean isPrecisionImagery() {
        return this.isPrecisionImagery;
    }

    public int getWidth() {
        return this.width;
    }
    
    public int getHeight() {
        return this.height;
    }
    
    public GeoPoint getUpperLeft() {
        return getPoint(0);
    }
    
    public GeoPoint getUpperRight() {
        return getPoint(1);
    }
    
    public GeoPoint getLowerRight() {
        return getPoint(2);
    }
    
    public GeoPoint getLowerLeft() {
        return getPoint(3);
    }
    
    private GeoPoint getPoint(int i) {
        return new GeoPoint(this.bounds.getExteriorRing().getY(i),
                            this.bounds.getExteriorRing().getX(i));
    }
}
