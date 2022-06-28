package com.iai.pri;

import java.io.InputStream;

public final class PRI {
    private long pointer;
    private ElevationSegmentData[] elevationSegments;
    private PRICorners info;

    private PRIImagePoint[] image;
    private PRIGroundPoint[] ground;

    private PRI(long pointer) {
        // XXX - note that the current implementation of PRIJNI utilizes a
        //       pointer value of '-1L' to denote the NULL pointer rather
        //       than zero. Until all downstream references to that class
        //       have been removed, check and reset to '0L' to represent
        //       NULL-ness
        if(pointer == -1L)
            pointer = 0L;
        this.pointer = pointer;
        this.elevationSegments = null;
        this.info = null;
    }

    private void checkValid() {
        if (this.pointer == 0L)
            throw new IllegalStateException();
    }

    /**
     * Use a PRI image's metadata to calculate geographic points using image
     * pixel locations.
     * @param pointer Long representing a pointer to a PRI file that has been
     * loaded into memory. This pointer can be acquired by calling the
     * {@link com.iai.pri.PRIJNI#loadImage(String) loadImage()} method. DO NOT
     * call this method using a long that has already been passed to the
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * method, as that may cause a crash when the native code attempts to
     * dereference a pointer that has already been deleted.
     * @param points An array of PRIImagePoints that can should be used to
     * calculate equivalent ground points.
     * @return Array of PRIGroundPoints representing the image points passed
     * into this method. These points will be in the same order as their
     * corresponding image points. If an error occurred or the image was not a
     * PRI, this method will return null.
     **/
    public synchronized PRIGroundPoint[] imageToGround(PRIImagePoint[] points) {
        this.checkValid();
        return PRIJNI.imageToGround(this.pointer, points);
    }

    public PRIGroundPoint imageToGround(double x, double y) {
        if(this.image == null)
            this.image = new PRIImagePoint[] {new PRIImagePoint(0, 0)};
        this.image[0].line = y;
        this.image[0].sample = x;
        PRIGroundPoint[] result = this.imageToGround(this.image);
        if(result == null || result.length == 0)
            return null;
        return result[0];
    }

    /**
     * Use a PRI image's metadata to calculate pixel locations using geographic
     * points on the earth. <br/><br/>
     * NOTE: This method is synchronized while most others are because it does
     * not interact with the cache that stores loaded PRI images in the native
     * code.
     * {@link com.iai.pri.PRIJNI#loadImage(String) loadImage()} method. DO NOT
     * call this method using a long that has already been passed to the
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * method, as that may cause a crash when the native code attempts to
     * dereference a pointer that has already been deleted.
     * @param points An array of PRIGroundPoints that can should be used to
     * calculate equivalent pixel locations.
     * @return Array of PRIImagePoints representing the ground points passed
     * into this method. These points will be in the same order as their
     * corresponding ground points. If an error occurred, or the image was not a
     * PRI, this method will return null.
     **/
    public synchronized PRIImagePoint[] groundToImage(PRIGroundPoint[] points) {
        checkValid();
        return PRIJNI.groundToImage(this.pointer, points);
    }

    public PRIImagePoint groundToImage(double lat, double lng, double hae) {
        if(this.ground == null)
            this.ground = new PRIGroundPoint[] {new PRIGroundPoint(0, 0, 0, 0, 0)};
        this.ground[0].lat = lat;
        this.ground[0].lon = lng;
        this.ground[0].eleMeters = hae;
        this.ground[0].ce90Meters = 99;
        this.ground[0].le90Meters = 99;
        PRIImagePoint[] result = this.groundToImage(this.ground);
        if(result == null || result.length == 0)
            return null;
        return result[0];
    }

    /**
     * @param pointer Long representing a pointer to a PRI file that has been
     * loaded into memory. This pointer can be acquired by calling the
     * {@link com.iai.pri.PRIJNI#loadImage(String) loadImage()} method. DO NOT
     * call this method using a long that has already been passed to the
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * method, as that may cause a crash when the native code attempts to
     * dereference a pointer that has already been deleted.
     * @param structTop The image coordinate of the top of the structure whose
     * height is to be measured.
     * @param shadowTip The image coordinate of the tip of the shadow that
     * corresponds to the top of the structure, i.e. structTop.
     * @return The estimated height of the structure in meters.
     **/
    public synchronized PRIGroundPoint getHeightOffsetTopToShadowTip(PRIImagePoint structureTopCoord, PRIImagePoint shadowTipCoord) {
        this.checkValid();
        return PRIJNI.getHeightOffsetTopToShadowTip(this.pointer, structureTopCoord, shadowTipCoord);
    }

    public static PRIGroundPoint getHeightOffsetTopToShadowTip(PRI structureTopPri, PRIImagePoint structureTopCoord, PRI shadowTipPri, PRIImagePoint shadowTipCoord) {
        if(structureTopPri == shadowTipPri)
            return structureTopPri.getHeightOffsetTopToShadowTip(structureTopCoord, shadowTipCoord);

        structureTopPri.checkValid();
        shadowTipPri.checkValid();
        return PRIJNI.getHeightOffsetTopToShadowTip(structureTopPri.pointer, structureTopCoord, shadowTipPri.pointer, shadowTipCoord);
    }

    /**
     * @param pointer Long representing a pointer to a PRI file that has been
     * loaded into memory. This pointer can be acquired by calling the
     * {@link com.iai.pri.PRIJNI#loadImage(String) loadImage()} method. DO NOT
     * call this method using a long that has already been passed to the
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * method, as that may cause a crash when the native code attempts to
     * dereference a pointer that has already been deleted.
     * @param baseCoord The image coordinate of the base of the structure whose
     * height is to be measured.
     * @param shadowTipCoord The image coordinate of the tip of the shadow that
     * corresponds to the top of the structure.
     * @return The estimated height of the structure in meters.
     **/
    public synchronized double getHeightOffsetBaseToShadowTip(PRIImagePoint structureBaseCoord, PRIImagePoint shadowTipCoord) {
        this.checkValid();
        return PRIJNI.getHeightOffsetBaseToShadowTip(this.pointer, structureBaseCoord, shadowTipCoord);
    }
    public static double getHeightOffsetBaseToShadowTip(PRI structureBasePri, PRIImagePoint structureBaseCoord, PRI shadowTipPri, PRIImagePoint shadowTipCoord) {
        if(structureBasePri == shadowTipPri)
            return structureBasePri.getHeightOffsetBaseToShadowTip(structureBaseCoord, shadowTipCoord);

        structureBasePri.checkValid();
        shadowTipPri.checkValid();
        return PRIJNI.getHeightOffsetBaseToShadowTip(structureBasePri.pointer, structureBaseCoord, shadowTipPri.pointer, shadowTipCoord);
    }

    /**
     * @param pointer Long representing a pointer to a PRI file that has been
     * loaded into memory. This pointer can be acquired by calling the
     * {@link com.iai.pri.PRIJNI#loadImage(String) loadImage()} method. DO NOT
     * call this method using a long that has already been passed to the
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * method, as that may cause a crash when the native code attempts to
     * dereference a pointer that has already been deleted.
     * @param baseCoord The image coordinate of the base of the structure whose
     * height is to be measured.
     * @param topCoord The image coordinate of the top of the structure whose
     * height is to be measured.
     * @return The estimated height of the structure in meters.
     **/
    public synchronized double getHeightOffsetBaseToTop(PRIImagePoint structureBaseCoord, PRIImagePoint structureTopCoord) {
        this.checkValid();
        return PRIJNI.getHeightOffsetBaseToTop(this.pointer, structureBaseCoord, structureTopCoord);
    }
    public static double getHeightOffsetBaseToTop(PRI structureBasePri, PRIImagePoint structureBaseCoord, PRI structureTopPri, PRIImagePoint structureTopCoord) {
        if(structureBasePri == structureTopPri)
            return structureBasePri.getHeightOffsetBaseToTop(structureBaseCoord, structureTopCoord);

        structureBasePri.checkValid();
        structureTopPri.checkValid();

        return PRIJNI.getHeightOffsetBaseToTop(structureBasePri.pointer, structureBaseCoord, structureTopPri.pointer, structureTopCoord);
    }

    /**
     * Test whether or not a file is a PRI image.
     * @param path Full or relative path on disk to where the image to test is
     * located.
     * @return True if the image is a PRI that can be exploited, false otherwise.
     **/
    public static boolean isPRI(String path) {
        return PRIJNI.isPRI(path);
    }

    /**
     * Test whether or not a file is a PRI image.
     * @param inputStream InputStream object that contains PRI file data. This
     * stream is assumed to currently set to the beginning of the file, so the
     * next call to read() will read the first byte of the PRI NITF file header.
     * @return Positive long value representing a pointer to a PRI object stored
     * in native memory if the file is a PRI, -1 otherwise. If this method
     * returns a positive long value, you MUST then call
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * at some later time or the native code will leak the memory for the
     * tested PRI.
     **/
    public static synchronized boolean isPRI(InputStream inputStream) {
        long pointer = -1L;
        try {
            pointer = PRIJNI.isPRI(inputStream);
            return (pointer != -1L);
        } finally {
            if(pointer != -1L)
                PRIJNI.clearImageCache(pointer);
        }
    }

    public synchronized int getWidth() {
        this.checkValid();
        if(this.info == null)
            this.info = PRIJNI.getCorners(this.pointer);
        return this.info.width;
    }
    public synchronized int getHeight() {
        this.checkValid();
        if(this.info == null)
            this.info = PRIJNI.getCorners(this.pointer);
        return this.info.height;
    }
    public synchronized PRIGroundPoint getUpperLeft() {
        this.checkValid();
        if(this.info == null)
            this.info = PRIJNI.getCorners(this.pointer);
        return this.info.ul;
    }
    public synchronized PRIGroundPoint getUpperRight() {
        this.checkValid();
        if(this.info == null)
            this.info = PRIJNI.getCorners(this.pointer);
        return this.info.ur;
    }
    public synchronized PRIGroundPoint getLowerRight() {
        this.checkValid();
        if(this.info == null)
            this.info = PRIJNI.getCorners(this.pointer);
        return this.info.lr;
    }
    public synchronized PRIGroundPoint getLowerLeft() {
        this.checkValid();
        if(this.info == null)
            this.info = PRIJNI.getCorners(this.pointer);
        return this.info.ll;
    }
    /**
     * Returns the elevation, in meters HAE, at the given image pixel.
     */
    public synchronized double getElevation(double x, double y) {
        this.checkValid();
        return PRIJNI.getElevation(this.pointer, x, y);
    }

    public synchronized int getNumElevationSegments() {
        this.checkValid();
        return PRIJNI.getNumElevationSegments(this.pointer);
    }
    public synchronized ElevationSegmentData getElevationSegmentData(int idx) {
        this.checkValid();

        if(this.elevationSegments == null)
            this.elevationSegments = new ElevationSegmentData[PRIJNI.getNumElevationSegments(this.pointer)];

        if(idx < 0 || idx >= this.elevationSegments.length)
            throw new IndexOutOfBoundsException();

        if(this.elevationSegments[idx] == null) {
            this.elevationSegments[idx] = PRIJNI.getElevationSegmentData(this.pointer, idx);

            // record 'this' as the owner to prevent finalization so long as
            // there is a live reference to the segment data
            this.elevationSegments[idx].ownerRef = this;
        }

        return this.elevationSegments[idx];
    }

    public synchronized void finalize() {
        if(this.pointer == 0L)
            return;
        PRIJNI.clearImageCache(this.pointer);
        this.pointer = 0L;
    }

    public static PRI open(String path) {
        final long pointer = PRIJNI.loadImage(path);
        if(pointer == -1L || pointer == 0L)
            return null;
        return new PRI(pointer);
    }

    public static PRI open(InputStream inputStream) {
        final long pointer = PRIJNI.loadImage(inputStream);
        if(pointer == -1L || pointer == 0L)
            return null;
        return new PRI(pointer);
    }
}