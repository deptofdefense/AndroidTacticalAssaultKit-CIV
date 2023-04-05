
package com.atakmap.android.elev.dt2;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.elevation.AbstractElevationData;
import com.atakmap.map.elevation.ElevationData;
import com.atakmap.map.elevation.ElevationDataSpi;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.math.Rectangle;
import com.atakmap.util.zip.IoUtils;

import gov.tak.api.annotation.DeprecatedApi;

/** @deprecated */
@Deprecated
@DeprecatedApi(since = "4.6", forRemoval = true, removeAt = "4.9")
public class Dt2ElevationData extends AbstractElevationData {

    private static final String TAG = "Dt2ElevationData";

    /** offset into header where 4 char line count starts (4 char sample point follows) */
    private static final int _NUM_LNG_LINES_OFFSET = 47;
    private static final int _HEADER_OFFSET = 3428;
    private static final int _DATA_RECORD_PREFIX_SIZE = 8;
    private static final int _DATA_RECORD_SUFFIX_SIZE = 4;

    public final static ElevationDataSpi SPI = new ElevationDataSpi() {
        @Override
        public ElevationData create(ImageInfo object) {
            final DtedFormat fmt = DTED_TYPES.get(object.type);
            if (fmt == null)
                return null;
            final File file = new File(object.path);
            if (!IOProviderFactory.exists(file))
                return null;
            return new Dt2ElevationData(file, fmt, object);
        }

        @Override
        public int getPriority() {
            return 0;
        }
    };

    public enum DtedFormat {
        DTED0("DTED0", ".dt0", 1000d),
        DTED1("DTED1", ".dt1", 100d),
        DTED2("DTED2", ".dt2", 30d),
        DTED3("DTED3", ".dt3", 10d);

        public final String type;
        public final String extension;
        public final double resolution;

        DtedFormat(String type, String ext, double resolution) {
            this.type = type;
            this.extension = ext;
            this.resolution = resolution;
        }
    }

    private final static Map<String, DtedFormat> DTED_TYPES = new HashMap<>();
    static {
        DTED_TYPES.put(DtedFormat.DTED0.type, DtedFormat.DTED0);
        DTED_TYPES.put(DtedFormat.DTED1.type, DtedFormat.DTED1);
        DTED_TYPES.put(DtedFormat.DTED2.type, DtedFormat.DTED2);
        DTED_TYPES.put(DtedFormat.DTED3.type, DtedFormat.DTED3);
    }

    private final File file;
    private final ImageInfo info;

    // XXX - properties on info redundant with format???

    private Dt2ElevationData(final File file, final DtedFormat fmt,
            final ImageInfo info) {
        super(MODEL_TERRAIN, fmt.type, fmt.resolution);

        this.file = file;
        this.info = info;
    }

    @Override
    public double getElevation(double lat, double lng) {
        if (!Rectangle.contains(this.info.lowerLeft.getLongitude(),
                this.info.lowerLeft.getLatitude(),
                this.info.upperRight.getLongitude(),
                this.info.upperRight.getLatitude(),
                lng, lat)) {

            return Double.NaN;
        }

        double altMSL = _fromDtXFile(file, lat, lng);

        // Return expects elevation in meters HAE
        return EGM96.getHAE(lat, lng, altMSL);
    }

    @Override
    public void getElevation(Iterator<GeoPoint> points, double[] elevations,
            Hints hints) {

        _bulkFromDtXFile(file, this.info, points, elevations,
                this.info.lowerLeft.getLatitude(),
                this.info.lowerLeft.getLongitude());

    }

    /**
     * Interprets a raw 16-bit DTED sample into a float-point
     * elevation value.
     * <P>
     *  The negative in DTED is NOT two's complement,
     *  it's signed-magnitude. Mask off the MSB and multiple by
     * -1 to make it 2's complement.
     * @param s is the value.
     * @return  a double that represents the value correctly
     * interpretting the sign magnitude or {@link Double#NaN}
     * in the event that the value is null or invalid per
     * MIL-PRF-89020B
     */
    public static double interpretSample(final short s) {
        if (((s & 0xFFFF) == 0xFFFF))
            return Float.NaN;

        final float val = (1 - (2 * ((s & 0x8000) >> 15))) * (s & 0x7FFF);

        // XXX - per MIL-PRF89020B 3.11.2, elevation values should never exceed
        //       these values in practice
        if ((val < -12000) || (val > 9000))
            return Float.NaN;
        return val;
    }

    private static double _readAndInterp(final RandomAccessFile in,
            final int dataRecSize,
            final double xratio,
            final double yratio) throws IOException {

        double r = Double.NaN;

        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.BIG_ENDIAN);

        bb.put((byte) in.read());
        bb.put((byte) in.read());

        double sw = interpretSample(bb.getShort(0));

        bb.rewind();
        bb.put((byte) in.read());
        bb.put((byte) in.read());

        double nw = interpretSample(bb.getShort(0));

        in.skipBytes(dataRecSize - 4);
        bb.rewind();
        bb.put((byte) in.read());
        bb.put((byte) in.read());

        double se = interpretSample(bb.getShort(0));

        bb.rewind();
        bb.put((byte) in.read());
        bb.put((byte) in.read());

        double ne = interpretSample(bb.getShort(0));

        if (Double.isNaN(sw) &&
                Double.isNaN(nw) &&
                Double.isNaN(se) &&
                Double.isNaN(ne)) {

            return Double.NaN;
        }

        double mids;
        double midn;

        if (!Double.isNaN(nw) &&
                !Double.isNaN(ne) &&
                !Double.isNaN(se) &&
                !Double.isNaN(sw)) {
            mids = sw + (se - sw) * xratio;
            midn = nw + (ne - nw) * xratio;
        } else if (Double.isNaN(nw) &&
                !Double.isNaN(ne) &&
                !Double.isNaN(se) &&
                !Double.isNaN(sw)) {
            mids = sw + (se - sw) * xratio;
            midn = ne;
        } else if (!Double.isNaN(nw) &&
                Double.isNaN(ne) &&
                !Double.isNaN(se) &&
                !Double.isNaN(sw)) {
            mids = sw + (se - sw) * xratio;
            midn = nw;
        } else if (!Double.isNaN(nw) &&
                !Double.isNaN(ne) &&
                Double.isNaN(se) &&
                !Double.isNaN(sw)) {
            mids = sw;
            midn = nw + (ne - nw) * xratio;
        } else if (!Double.isNaN(nw) &&
                !Double.isNaN(ne) &&
                !Double.isNaN(se) &&
                Double.isNaN(sw)) {
            mids = se;
            midn = nw + (ne - nw) * xratio;
        } else {

            // XXX - consider interpolation as long as at least 2 values are
            //       non-null
            return r;
        }

        r = mids + (midn - mids) * yratio;

        return r;
    }

    /**
     * Get elevation from a DTED file
     *
     * @param file DTED file
     * @param lat Latitude
     * @param lng Longitude
     * @return Elevation in meters MSL
     */
    private static double _fromDtXFile(File file, double lat, double lng) {
        RandomAccessFile raf = null;
        try {
            raf = IOProviderFactory.getRandomAccessFile(file, "r");
            return _getHeight(raf, lat, lng);
        } catch (Exception e) {
            Log.e(TAG,
                    "Error getting height from input stream: "
                            + file.getAbsolutePath(),
                    e);
        } finally {
            IoUtils.close(raf, TAG,
                    "Error closing file: " + file.getAbsolutePath());
        }
        return GeoPoint.UNKNOWN;
    }

    /**
     * This will open up the file and try to read all the points storing the results in the
     * elevations array where the elevations will be stored in the array based on index of the
     * point that was extracted from the iterator
     *
     * @param file
     *             The file to open
     * @param points
     *             An iterator of points to get elevations for
     * @param elevations
     *             The array where the elevations will be stored
     */
    private static void _bulkFromDtXFile(File file, ImageInfo info,
            Iterator<GeoPoint> points, double[] elevations,
            double cellLat,
            double cellLng) {
        RandomAccessFile raf = null;

        try {
            raf = IOProviderFactory.getRandomAccessFile(file, "r");

            raf.skipBytes(_NUM_LNG_LINES_OFFSET);
            byte[] bytes = {
                    0, 0, 0, 0, 0, 0, 0, 0
            };

            if (raf.read(bytes, 0, 8) < 8) {
                throw new IOException("Invalid File");
            }

            String lngLinesStr = new String(bytes, 0, 4,
                    StandardCharsets.US_ASCII);
            String latPointsStr = new String(bytes, 4, 4,
                    StandardCharsets.US_ASCII);

            int lngLines;
            int latPoints;

            try {
                lngLines = Integer.parseInt(lngLinesStr);
            } catch (Exception e) {
                throw new IOException(e);
            }
            try {
                latPoints = Integer.parseInt(latPointsStr);
            } catch (Exception e) {
                throw new IOException(e);
            }

            long start = raf.getFilePointer();
            int index = 0;
            while (points.hasNext()) {
                double elevation = Double.NaN;

                try {
                    GeoPoint point = points.next();
                    double latitude = point.getLatitude();
                    double longitude = point.getLongitude();

                    if (!Rectangle.contains(
                            info.lowerLeft.getLongitude(),
                            info.lowerLeft.getLatitude(),
                            info.upperRight.getLongitude(),
                            info.upperRight.getLatitude(),
                            longitude, latitude)) {
                        elevations[index] = Double.NaN;
                        index++;
                        continue;
                    }

                    double latRatio = latitude - cellLat;
                    double lngRatio = longitude - cellLng;

                    double yd = latRatio * (latPoints - 1);
                    double xd = lngRatio * (lngLines - 1);

                    int x = (int) xd;
                    int y = (int) yd;

                    int dataRecSize = _DATA_RECORD_PREFIX_SIZE
                            + (latPoints * 2)
                            + _DATA_RECORD_SUFFIX_SIZE;

                    int byteOffset = (_HEADER_OFFSET - _NUM_LNG_LINES_OFFSET
                            - 8)
                            + x * dataRecSize
                            + _DATA_RECORD_PREFIX_SIZE
                            + y * 2;

                    int skipped;
                    do {
                        skipped = raf.skipBytes(byteOffset);
                        byteOffset = byteOffset - skipped;

                        // three exit conditions, I want to know which one we hit
                        if (skipped == 0) { // ran out of file
                            break;
                        } else if (byteOffset == 0) { // perfect
                            break;
                        } else if (byteOffset < 0) { // overshot
                            break;
                        }
                    } while (true);

                    double a = _readAndInterp(raf, dataRecSize, xd - x, yd - y);

                    if (!Double.isNaN(a)) {
                        elevation = a + EGM96.getOffset(latitude, longitude);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error reading height from input stream: "
                            + file
                                    .getAbsolutePath(),
                            e);
                }

                elevations[index] = elevation;
                index++;

                raf.seek(start);
            }
        } catch (Exception e) {
            Log.e(TAG,
                    "Error getting height from input stream: "
                            + file.getAbsolutePath(),
                    e);
            int index = 0;
            while (points.hasNext()) {
                elevations[index++] = Double.NaN;
                points.next();
            }
        } finally {
            IoUtils.close(raf, TAG,
                    "Error closing file: " + file.getAbsolutePath());
        }
    }

    /**
     * Reads the raw height given a latitude and longitude from the supplied file. The elevation
     * posting data is provided in MSL as per MIL-PRF-89020B page 3.
     *
     * @return Altitude in MSL based on the underlying DTED file.
     */
    private static double _getHeight(RandomAccessFile in, double latitude,
            double longitude) throws IOException {

        in.skipBytes(_NUM_LNG_LINES_OFFSET);
        byte[] bytes = {
                0, 0, 0, 0, 0, 0, 0, 0
        };

        if (in.read(bytes, 0, 8) < 8) {
            // read did not get all of the information required
            throw new IOException("invalid file");
        }

        String lngLinesStr = new String(bytes, 0, 4, StandardCharsets.US_ASCII);
        String latPointsStr = new String(bytes, 4, 4,
                StandardCharsets.US_ASCII);

        int lngLines;
        int latPoints;

        try {
            lngLines = Integer.parseInt(lngLinesStr);
        } catch (Exception e) {
            throw new IOException(e);
        }
        try {
            latPoints = Integer.parseInt(latPointsStr);
        } catch (Exception e) {
            throw new IOException(e);
        }

        double latRatio = latitude - Math.floor(latitude);
        double lngRatio = longitude - Math.floor(longitude);

        double yd = latRatio * (latPoints - 1);
        double xd = lngRatio * (lngLines - 1);

        int x = (int) xd;
        int y = (int) yd;

        int dataRecSize = _DATA_RECORD_PREFIX_SIZE + (latPoints * 2)
                + _DATA_RECORD_SUFFIX_SIZE;

        int byteOffset = (_HEADER_OFFSET - _NUM_LNG_LINES_OFFSET - 8)
                + x * dataRecSize
                + _DATA_RECORD_PREFIX_SIZE
                + y * 2;

        int skipped;
        do {
            skipped = in.skipBytes(byteOffset);
            byteOffset -= skipped;

            // three exit conditions, I want to know which one we hit
            if (skipped == 0) { // ran out of file
                break;
            } else if (byteOffset == 0) { // perfect
                break;
            } else if (byteOffset < 0) { // overshot
                break;
            }
        } while (true);

        return _readAndInterp(in, dataRecSize, xd - x, yd - y);
    }
}
