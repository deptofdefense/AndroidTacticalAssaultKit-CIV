
package com.atakmap.android.elev.srtm1;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;

public class Srtm1ElevationModel {

    public static final String TAG = "Srtm1ElevatonModel";

    private final static long _LOWER_LEFT_OFFSET = (3601 - 1) * 3601 * 2;

    private final String[] _rootPath;
    private static final DecimalFormat _latFormat = LocaleUtil
            .getDecimalFormat("00");
    private static final DecimalFormat _lngFormat = LocaleUtil
            .getDecimalFormat("000");

    private static Srtm1ElevationModel _instance;

    private Srtm1ElevationModel() {
        _rootPath = findSrtmPaths();
    }

    static public synchronized Srtm1ElevationModel getInstance() {
        if (_instance == null) {
            _instance = new Srtm1ElevationModel();
        }
        return _instance;
    }

    private String[] findSrtmPaths() {
        String[] mountPoints = FileSystemUtils.findMountPoints();
        String[] srtmPaths = new String[mountPoints.length];

        for (int i = 0; i < mountPoints.length; ++i) {
            srtmPaths[i] = mountPoints[i] + File.separator + "SRTM"
                    + File.separator;
        }
        return srtmPaths;
    }

    private double queryPointLegacy(final double latitude,
            final double longitude) {
        String fileName = _makeFileName(latitude, longitude);

        for (String a_rootPath : _rootPath) {

            File file = new File(a_rootPath + fileName);
            if (IOProviderFactory.exists(file)) {
                return _fromHgtFile(file, latitude, longitude);
            }

            file = new File(a_rootPath + fileName + ".zip");
            if (IOProviderFactory.exists(file)) {
                return _fromZippedHgtFile(file, fileName, latitude, longitude);
            }
        }
        return Double.NaN;
    }

    /**
     * Queries for an elevation value given a location in decimal degrees.
     *
     * @param latitude A double containing the latitude to be queried in decimal degrees.
     * @param longitude A double containing the longitude to be queried in decimal degrees.
     * @return An altitude object in MSL from the highest available SRTM level.
     */
    public GeoPointMetaData queryPoint(final double latitude,
            final double longitude) {

        double d = queryPointLegacy(latitude, longitude);
        if (!Double.isNaN(d)) {
            d = EGM96.getHAE(latitude, longitude, d);
        }
        return GeoPointMetaData.wrap(new GeoPoint(latitude, longitude, d),
                GeoPointMetaData.UNKNOWN,
                GeoPointMetaData.SRTM1);

    }

    private double _fromHgtFile(File file, double latitude, double longitude) {

        double result = Double.NaN;

        try (FileInputStream fis = IOProviderFactory.getInputStream(file)) {
            result = _getHeightFromInputStream(fis, latitude, longitude);
        } catch (IOException e) {
            Log.e(TAG, "error: ", e);
        }

        return result;
    }

    private double _fromZippedHgtFile(File file, String fileName,
            double latitude, double longitude) {

        ZipFile zip = null;
        double result = Double.NaN;

        try {
            zip = new ZipFile(file);
            ZipEntry entry = zip.getEntry(fileName);
            try {
                if (entry != null) {
                    InputStream in = null;
                    try {
                        in = zip.getInputStream(entry);
                        result = _getHeightFromInputStream(in, latitude,
                                longitude);
                    } finally {
                        if (in != null)
                            in.close();
                    }
                }
            } catch (NullPointerException e) {
                Log.e(TAG, "error: ", e);
            }
        } catch (IOException e) {
            Log.e(TAG, "error: ", e);
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException e) {
                    Log.e(TAG, "error: ", e);
                }
            }
        }

        return result;
    }

    private static String _makeFileName(final double latitude,
            final double longitude) {
        StringBuilder p = new StringBuilder();

        int latIndex = (int) latitude;
        if (latitude >= 0) {
            p.append("N");
        } else {
            p.append("S");
            latIndex = -latIndex + 1;
        }

        p.append(_latFormat.format(latIndex));

        int lngIndex = (int) longitude;
        if (longitude >= 0) {
            p.append("E");
        } else {
            p.append("W");
            lngIndex = -lngIndex + 1;
        }

        p.append(_lngFormat.format(lngIndex));
        p.append(".hgt");

        return p.toString();
    }

    private static double _getHeightFromInputStream(InputStream in,
            double latitude, double longitude) {
        double result = Double.NaN;

        double lineRatio = latitude - Math.floor(latitude);
        if (latitude < 0) {
            lineRatio = latitude - Math.ceil(latitude);
        }

        double sampleRatio = longitude - Math.floor(longitude);
        if (longitude < 0) {
            sampleRatio = longitude - Math.ceil(longitude);
        }

        try {
            long byteOffset = _LOWER_LEFT_OFFSET
                    + (long) (7202 * (sampleRatio - (lineRatio * 3601)));

            if (byteOffset != in.skip(byteOffset)) {
                Log.e(TAG, "error seeking " + byteOffset);
            }

            ByteBuffer bb = ByteBuffer.allocate(2);
            bb.order(ByteOrder.BIG_ENDIAN);
            bb.put((byte) in.read());
            bb.put((byte) in.read());

            short height = bb.getShort(0);

            result = height;
        } catch (IOException e) {
            Log.e(TAG, "error: ", e);
        }

        return result;
    }

}
