package com.iai.pri;

public final class ElevationSegmentData {
    public final static int DATASOURCE_DTED0 = 0;
    public final static int DATASOURCE_DTED1 = 1;
    public final static int DATASOURCE_DTED2 = 2;
    public final static int DATASOURCE_SRTM = 3;
    public final static int DATASOURCE_DPPDB = 4;
    public final static int DATASOURCE_LIDAR = 5;
    public final static int DATASOURCE_DPSS = 6;
    public final static int DATASOURCE_GXP = 7;
    public final static int DATASOURCE_PLATES = 8;
    public final static int DATASOURCE_UNKNOWN = 9;

    public final static int ELEVATIONDATUM_HAE = 0;
    public final static int ELEVATIONDATUM_MSL84 = 1;
    public final static int ELEVATIONDATUM_MSL96 = 2;
    public final static int ELEVATIONDATUM_MSL08 = 3;
    public final static int ELEVATIONDATUM_AGL = 4;
    public final static int ELEVATIONDATUM_UNKNOWN = 5;

    public final static int ELEVUNITS_METERS = 0;
    public final static int ELEVUNITS_FEET = 1;
    public final static int ELEVUNITS_DECIMETERS = 2;
    public final static int ELEVUNITS_UNKNOWN = 3;

    public final static int DATACOMPRESSION_GZIP = 0;
    public final static int DATACOMPRESSION_NOCOMPRESSION = 1;
    public final static int DATACOMPRESSION_UNKNOWN = 2;

    private long pointer;
    private int numPoints;
    private int source;
    private int datum;
    private int units;
    private int compression;

    Object ownerRef;

    private ElevationSegmentData(long pointer) {
        this.pointer = pointer;
        this.source = getSource(this.pointer);
        this.datum = getDatum(this.pointer);
        this.units = getUnits(this.pointer);
        this.compression = getCompression(this.pointer);
        this.numPoints = getNumPoints(pointer);
    }

    public int getElevationSource() {
        return this.source;
    }

    public int getElevationDatum() {
        return this.datum;
    }

    public int getElevationUnits() {
        return this.units;
    }

    public int getDataCompression() {
        return this.compression;
    }

    public int getNumPoints() {
        return this.numPoints;
    }

    public ElevationPoint getPoint(int idx) {
        if(idx < 0 || idx >= this.numPoints)
            throw new IndexOutOfBoundsException();
        return getPoint(this.pointer, idx);
    }

    /**
     * Returns all points as ordered triplets: column,line,height
     * @param points
     */
    public void getPoints(double[] points) {
        if(points.length < this.numPoints*3)
            throw new IllegalArgumentException();
        getPoints(this.pointer, points);
    }

    /**
     * Returns all points as ordered triplets: column,line,height
     * @param points
     */
    public void getPoints(float[] points) {
        if(points.length < this.numPoints*3)
            throw new IllegalArgumentException();
        getPoints(this.pointer, points);
    }

    private static native int getSource(long pointer);
    private static native int getDatum(long pointer);
    private static native int getUnits(long pointer);
    private static native int getCompression(long pointer);
    private static native int getNumPoints(long pointer);
    private static native ElevationPoint getPoint(long pointer, int idx);
    private static native void getPoints(long pointer, double[] arr);
    private static native void getPoints(long pointer, float[] arr);
}