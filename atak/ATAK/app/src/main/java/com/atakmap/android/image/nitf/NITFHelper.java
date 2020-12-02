
package com.atakmap.android.image.nitf;

import com.atakmap.android.image.ExifHelper;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.Ellipsoid;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MGRSPoint;

import org.apache.sanselan.formats.tiff.constants.TiffConstants;
import org.apache.sanselan.formats.tiff.write.TiffOutputSet;
import org.gdal.gdal.Dataset;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

/**
 * NITF metadata helper
 */
public class NITFHelper {

    private static final String TAG = "NITFHelper";

    public static final int TRE_KEY_LEN = 6;
    public static final String DATE_TIME = "NITF_IDATIM";
    public static final String FILE_TITLE = "NITF_FTITLE";
    public static final String COORDINATE_SYSTEM = "NITF_ICORDS";
    public static final String COORDINATE_STRING = "NITF_IGEOLO";
    public static final String GPS_DATETIME = "GPSDT";
    public static final String GPS_LOCATION = "GPSLOC";
    public static final String GPS_DIRECTION = "GPSDIR";
    public static final String GPS_ALTITUDE = "GPSALT";
    public static final String HORIZONTAL_FOV = "HFOV";
    public static final String CAMERA_MAKE = "MAKE";
    public static final String CAMERA_MODEL = "MODEL";
    public static final String CAMERA_FOCAL = "FOCAL";
    public static final String CAMERA_FLASH = "FLASH";

    /**
     * Retrieve string data from user-defined metadata
     * @param nitf NITF dataset
     * @param key The field key
     * @return The value at the given key or null if not found
     */
    public static String getExtra(Dataset nitf, String key, String defValue) {
        if (nitf != null) {
            // Retrieve user-defined metadata
            Hashtable tre = nitf.GetMetadata_Dict("TRE");
            if (tre != null) {
                if (!tre.containsKey(key)) {
                    // Try adding whitespace
                    final StringBuilder keyBuilder = new StringBuilder(key);
                    while (keyBuilder.length() < TRE_KEY_LEN)
                        keyBuilder.append(" ");
                    key = keyBuilder.toString();
                    if (key.length() > TRE_KEY_LEN)
                        key = key.substring(0, TRE_KEY_LEN);
                    key = key.toUpperCase(LocaleUtil.getCurrent());
                }
                if (tre.containsKey(key))
                    return String.valueOf(tre.get(key));
            }
        }
        return defValue;
    }

    public static int getExtra(Dataset nitf, String key, int defValue) {
        try {
            String v = getExtra(nitf, key, null);
            return v == null ? defValue : Integer.parseInt(v);
        } catch (Exception e) {
            return defValue;
        }
    }

    public static float getExtra(Dataset nitf, String key, float defValue) {
        try {
            String v = getExtra(nitf, key, null);
            return v == null ? defValue : Float.parseFloat(v);
        } catch (Exception e) {
            return defValue;
        }
    }

    public static double getExtra(Dataset nitf, String key, double defValue) {
        try {
            String v = getExtra(nitf, key, null);
            return v == null ? defValue : Double.parseDouble(v);
        } catch (Exception e) {
            return defValue;
        }
    }

    public static boolean getExtra(Dataset nitf, String key, boolean defValue) {
        try {
            String v = getExtra(nitf, key, null);
            return v == null ? defValue : Boolean.parseBoolean(v);
        } catch (Exception e) {
            return defValue;
        }
    }

    /**
     * Get NITF date time as a colon/space separated string
     * @param nitf NITF dataset
     * @return Date time string = "YYYY:MM:SS hh:mm:ss"
     */
    public static String getDateTime(Dataset nitf) {
        if (nitf != null) {
            String datim = nitf.GetMetadataItem(DATE_TIME);
            if (datim != null) {
                // Format = YYYYMMDDhhmmss
                String year = datim.substring(0, 4);
                String month = datim.substring(4, 6);
                String day = datim.substring(6, 8);
                String hour = datim.substring(8, 10);
                String mins = datim.substring(10, 12);
                String secs = datim.substring(12, 14);
                return String.format(LocaleUtil.getCurrent(),
                        "%s:%s:%s %s:%s:%s",
                        year, month, day, hour, mins, secs);
            }
        }
        return null;
    }

    /**
     * Retrieve the file title for this dataset
     * @param nitf NITF dataset
     * @return File title or null if not defined
     */
    public static String getTitle(Dataset nitf) {
        return nitf != null ? nitf.GetMetadataItem(FILE_TITLE) : null;
    }

    /**
     * Set the file title for this dataset
     * @param nitf NITF dataset
     * @param title File title
     */
    public static void setTitle(Dataset nitf, String title) {
        if (nitf != null)
            nitf.SetMetadataItem(FILE_TITLE, title);
    }

    /**
     * Get center point of NITF image coordinates
     * @param repStr Coordinate representation ("U" = MGRS, "G"/"C" = DMS, "D" = DD)
     * @param coordStr 4-point Coordinate string (see NITF 2.0 spec for formatting)
     * @return Geo-point conversion of coordinate string, or null if failed
     */
    public static GeoPoint getCenterLocation(String repStr, String coordStr) {
        if (!FileSystemUtils.isEmpty(coordStr)
                && !FileSystemUtils.isEmpty(repStr)
                && coordStr.length() >= 60) {
            double[] avgLL = null;
            int numValid = 0;
            for (int i = 0; i < 60; i += 15) {
                String coord = coordStr.substring(i, i + 15);
                GeoPoint gp = readCoordinate(repStr, coord);
                if (gp != null) {
                    if (avgLL == null)
                        avgLL = new double[] {
                                gp.getLatitude(),
                                gp.getLongitude()
                        };
                    else {
                        avgLL[0] += gp.getLatitude();
                        avgLL[1] += gp.getLongitude();
                    }
                    numValid++;
                }
            }
            if (avgLL != null)
                return new GeoPoint(avgLL[0] / numValid, avgLL[1] / numValid);
        }
        return null;
    }

    public static GeoPoint getCenterLocation(Dataset nitf) {
        if (nitf != null) {
            // First see if we can get the user-defined form
            String loc = getExtra(nitf, GPS_LOCATION, null);
            if (loc != null) {
                // Format = "DD.DDDDDD DD.DDDDDD"
                try {
                    String[] latlon = loc.split(" ");
                    return new GeoPoint(Double.parseDouble(latlon[0]),
                            Double.parseDouble(latlon[1]));
                } catch (Exception e) {
                    // Failed to parse user-defined form
                }
            }
            // Average the image coordinates
            String coordRep = nitf.GetMetadataItem(COORDINATE_SYSTEM);
            String coordStr = nitf.GetMetadataItem(COORDINATE_STRING);
            return getCenterLocation(coordRep, coordStr);
        }
        return null;
    }

    /**
     * Convert NITF metadata to EXIF output set
     * @param nitf NITF dataset
     * @param outWidth Output width (optional)
     * @param outHeight Output height (optional)
     * @return EXIF output set
     */
    public static TiffOutputSet getExifOutput(Dataset nitf,
            int outWidth, int outHeight) {
        if (nitf == null)
            return null;

        // General metadata
        TiffOutputSet tos = new TiffOutputSet();
        ExifHelper.updateField(tos, TiffConstants.EXIF_TAG_EXIF_IMAGE_WIDTH,
                outWidth);
        ExifHelper.updateField(tos, TiffConstants.EXIF_TAG_EXIF_IMAGE_LENGTH,
                outHeight);
        ExifHelper.updateField(tos, TiffConstants.EXIF_TAG_ORIENTATION, 0);

        // Get file title
        String title = getTitle(nitf);
        if (!FileSystemUtils.isEmpty(title))
            ExifHelper.updateField(tos,
                    TiffConstants.EXIF_TAG_IMAGE_DESCRIPTION, title);

        String creationDate = getDateTime(nitf);
        if (!FileSystemUtils.isEmpty(creationDate)) {
            ExifHelper.updateField(tos, TiffConstants.EXIF_TAG_CREATE_DATE,
                    creationDate);
            ExifHelper.updateField(tos, TiffConstants.TIFF_TAG_DATE_TIME,
                    creationDate);
        }

        String gpsDate = getExtra(nitf, GPS_DATETIME, null);
        if (!FileSystemUtils.isEmpty(gpsDate))
            ExifHelper.updateField(tos, TiffConstants.GPS_TAG_GPS_DATE_STAMP,
                    gpsDate);

        // GPS location and altitude
        GeoPoint loc = getCenterLocation(nitf);
        if (loc != null) {
            double alt = getExtra(nitf, GPS_ALTITUDE, Double.NaN);
            if (!Double.isNaN(alt))
                loc = new GeoPoint(loc.getLatitude(), loc.getLongitude(),
                        EGM96.getHAE(loc.getLatitude(),
                                loc.getLongitude(), alt));
            ExifHelper.setPoint(tos, loc);
        }

        // Get GPS image direction
        double dir = getExtra(nitf, GPS_DIRECTION, Double.NaN);
        if (!Double.isNaN(dir))
            ExifHelper.updateField(tos, ExifHelper.GPS_IMG_DIRECTION, dir);

        // Get Horizontal FOV
        Map<String, Object> bundle = new HashMap<>();
        double hFOV = getExtra(nitf, HORIZONTAL_FOV, Double.NaN);
        if (!Double.isNaN(hFOV))
            bundle.put("HorizontalFOV", hFOV);
        JSONObject jo = new JSONObject(bundle);
        ExifHelper.updateField(tos, ExifHelper.USER_COMMENT, jo.toString());

        String make = getExtra(nitf, CAMERA_MAKE, null);
        if (!FileSystemUtils.isEmpty(make))
            ExifHelper.updateField(tos, TiffConstants.EXIF_TAG_MAKE, make);

        String model = getExtra(nitf, CAMERA_MODEL, null);
        if (!FileSystemUtils.isEmpty(model))
            ExifHelper.updateField(tos, TiffConstants.EXIF_TAG_MODEL, model);

        double focalLength = getExtra(nitf, CAMERA_FOCAL, Double.NaN);
        if (!Double.isNaN(focalLength))
            ExifHelper.updateField(tos, TiffConstants.EXIF_TAG_FOCAL_LENGTH,
                    focalLength);

        int flash = getExtra(nitf, CAMERA_FLASH, -1);
        if (flash > -1)
            ExifHelper.updateField(tos, TiffConstants.EXIF_TAG_FLASH, flash);

        return tos;
    }

    public static TiffOutputSet getExifOutput(Dataset nitf) {
        return getExifOutput(nitf, nitf.getRasterXSize(),
                nitf.getRasterYSize());
    }

    public static GeoPoint readCoordinate(String repStr, String coord) {
        try {
            switch (repStr) {
                case "G":
                case "C":
                    // Geodetic DMS - "DDMMSSHDDDMMSSH"
                    String latD = coord.substring(0, 2);
                    String latM = coord.substring(2, 4);
                    String latS = coord.substring(4, 6);
                    String latH = coord.substring(6, 7);
                    String lonD = coord.substring(7, 10);
                    String lonM = coord.substring(10, 12);
                    String lonS = coord.substring(12, 14);
                    String lonH = coord.substring(14, 15);
                    String[] dms = new String[] {
                            (latH.equals("S") ? "-" : "") + latD, latM, latS,
                            (lonH.equals("W") ? "-" : "") + lonD, lonM, lonS
                    };
                    return CoordinateFormatUtilities.convert(dms,
                            CoordinateFormat.DMS);
                case "D":
                    // DD - "+DD.DDD+DDD.DDD"
                    String lat = coord.substring(0, 7);
                    String lon = coord.substring(7);
                    return new GeoPoint(Double.parseDouble(lat),
                            Double.parseDouble(lon));
                case "U":
                    // MGRS - "zzBJKeeeeennnnn"
                    double[] latlon = MGRSPoint.decodeString(coord,
                            Ellipsoid.WGS_84, null).toLatLng(null);
                    return new GeoPoint(latlon[0], latlon[1]);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to convert " + repStr + " coordinate " + coord,
                    e);
        }
        return null;
    }

}
