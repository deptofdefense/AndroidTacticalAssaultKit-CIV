
package com.atakmap.android.image;

import android.content.Context;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.conversion.GeomagneticField;
import androidx.exifinterface.media.ExifInterface;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.util.zip.IoUtils;
import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.ImageWriteException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.IImageMetadata;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.jpeg.exifRewrite.ExifRewriter;
import org.apache.sanselan.formats.tiff.TiffField;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TagInfo;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;
import org.apache.sanselan.formats.tiff.fieldtypes.FieldType;
import org.apache.sanselan.formats.tiff.write.TiffOutputDirectory;
import org.apache.sanselan.formats.tiff.write.TiffOutputField;
import org.apache.sanselan.formats.tiff.write.TiffOutputSet;
import org.json.JSONObject;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Reference: 
 *    https://android.googlesource.com/platform/frameworks/base/+/cd92588/media/java/android/media/ExifInterface.java
 */

public class ExifHelper {

    public static final String TAG = "ExifHelper";

    /**
     * Convert latlng value to DMS number array
     * @param tude the latitude or longitude
     */
    public static Number[] getDMSArray(double tude) {
        tude = Math.abs(tude);
        int deg = (int) tude;
        tude *= 60;
        tude -= (deg * 60.0d);
        int min = (int) tude;
        tude *= 60;
        tude -= (min * 60.0d);
        return new Number[] {
                deg, min, Math.round(tude * 10000.0f) / 10000.0f
        };
    }

    /**
     * Obtains the current image offset.
     */
    static private double getImageOrientation(final int orientationFlag) {
        double orientationOffset = 0d;
        switch (orientationFlag) {
            case ExifInterface.ORIENTATION_NORMAL:
                orientationOffset = 0d;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                orientationOffset = 180d;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                orientationOffset = 270d;
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                orientationOffset = 90d;
                break;
        }
        return orientationOffset;
    }

    /**
     * Returns the image orientation as an integer
     * @param img the image file
     * @return a value representing the orientation as 0, 90, 180, 270.
     */
    public static int getImageOrientation(File img) {
        TiffImageMetadata exif = getExifMetadata(img);
        if (exif != null) {
            return (int) getImageOrientation(getInt(exif,
                    TiffConstants.EXIF_TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL));
        }
        return 0;
    }

    /**
     * Obtains the current window offset.
     */
    static private double getWindowOffset(final Context context) {
        double orientationOffset = 0d;
        try {
            WindowManager _winManager = (WindowManager) context
                    .getSystemService(Context.WINDOW_SERVICE);
            Display display = _winManager.getDefaultDisplay();
            if (display == null)
                return orientationOffset;

            int rotation = display.getRotation();
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientationOffset = 0d;
                    break;
                case Surface.ROTATION_90:
                    orientationOffset = 90d;
                    break;
                case Surface.ROTATION_180:
                    orientationOffset = 180d;
                    break;
                case Surface.ROTATION_270:
                    orientationOffset = 270d;
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG,
                    "Error has occurred getting the window and rotation, setting 0",
                    e);
            orientationOffset = 0d;
        }
        return orientationOffset;
    }

    /**
     * Computes the required correction to the image based on the orientation of the 
     * of ATAK vs the orientation of the image.
     */
    static private double computeCorrection(double iO, double aO) {
        Log.d(TAG, "captured image orientation: " + iO
                + " map view orientation: " + aO);
        if (iO == 90d && aO == 90d)
            return -90d;
        else if (iO == 180d && aO == 90d)
            return +180d;
        else if (iO == 270d && aO == 90d)
            return +90d;

        else if (iO == 0d && aO == 0d)
            return +90d;
        else if (iO == 180d && aO == 0d)
            return -90d;
        else if (iO == 270d && aO == 0d)
            return +180d;

        else if (iO == 0d && aO == 270d)
            return +180d;
        else if (iO == 90d && aO == 270d)
            return +90d;
        else if (iO == 270d && aO == 270d)
            return -90d;

        else
            return 0d;
    }

    public static TiffImageMetadata getExifMetadata(File jpegFile) {
        try (InputStream is = IOProviderFactory.getInputStream(jpegFile)) {
            IImageMetadata metadata = Sanselan.getMetadata(is, null);
            JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
            if (jpegMetadata != null)
                return jpegMetadata.getExif();
        } catch (Exception e) {
            Log.w(TAG, "Error getting exif metadata from "
                    + jpegFile.getAbsolutePath());
        }
        return null;
    }

    public static TiffImageMetadata getExifMetadata(byte[] blob) {
        try {
            IImageMetadata metadata = Sanselan.getMetadata(blob);
            JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
            if (jpegMetadata != null)
                return jpegMetadata.getExif();
        } catch (Exception e) {
            Log.w(TAG, "Error getting exif metadata from blob");
        }
        return null;
    }

    public static TiffOutputSet getExifOutput(TiffImageMetadata exif) {
        TiffOutputSet tos = null;
        // Get existing output set
        if (exif != null) {
            try {
                tos = exif.getOutputSet();
            } catch (Exception e) {
                Log.w(TAG, "Error getting exif output set.");
            }
        }
        // Create new output set if we couldn't get the existing one
        if (tos == null)
            tos = new TiffOutputSet();
        return tos;
    }

    public static double getDouble(TiffImageMetadata exif, TagInfo tag,
            double defaultValue) {
        if (exif != null && tag != null) {
            String type = null;
            try {
                TiffField tf = exif.findField(tag);
                if (tf != null) {
                    type = tf.getFieldTypeName();
                    return tf.getDoubleValue();
                }
            } catch (ImageReadException | ClassCastException e) {
                Log.w(TAG, "Failed to read EXIF tag " + tag.name
                        + " as double, it is a " + type);
            }
        }
        return defaultValue;
    }

    public static int getInt(TiffImageMetadata exif, TagInfo tag,
            int defaultValue) {
        if (exif != null && tag != null) {
            String type = null;
            try {
                TiffField tf = exif.findField(tag);
                if (tf != null) {
                    type = tf.getFieldTypeName();
                    return tf.getIntValue();
                }
            } catch (ImageReadException | ClassCastException e) {
                Log.w(TAG, "Failed to read EXIF tag " + tag.name
                        + " as integer, it is a " + type);
            }
        }
        return defaultValue;
    }

    public static String getString(TiffImageMetadata exif, TagInfo tag,
            String defaultValue) {
        if (exif != null && tag != null) {
            String type = null;
            try {
                TiffField tf = exif.findField(tag);
                if (tf != null) {
                    type = tf.getFieldTypeName();
                    String ret = tf.getStringValue();
                    return (ret == null ? defaultValue : ret);
                }
            } catch (ImageReadException e) {
                Log.w(TAG, "Failed to read EXIF tag " + tag.name
                        + " as string, it is a " + type);
            }
        }
        return defaultValue;
    }

    /**
     * Get the EXIF GPS time stamp
     * @param exif Exif metadata
     * @param defaultValue Default timestamp return
     * @return Time stamp in milliseconds since UNIX epoch
     */
    public static long getTimeStamp(TiffImageMetadata exif, long defaultValue) {
        if (exif != null) {
            try {
                // First get time
                TiffField tf = exif
                        .findField(TiffConstants.GPS_TAG_GPS_TIME_STAMP);
                if (tf != null) {
                    double[] hms = tf.getDoubleArrayValue();
                    // Then get date
                    tf = exif.findField(TiffConstants.GPS_TAG_GPS_DATE_STAMP);
                    if (tf != null) {
                        String ymd = tf.getStringValue();
                        // Then convert to date object
                        DateFormat dateFormat = new SimpleDateFormat(
                                "yyyy:MM:dd HH:mm:ss", LocaleUtil.getCurrent());
                        String input = String.format(LocaleUtil.getCurrent(),
                                "%s %2.0f:%2.0f:%2.0f", ymd, hms[0], hms[1],
                                hms[2]);
                        Date d = dateFormat.parse(input);
                        if (d != null)
                            return d.getTime();
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to read EXIF timestamp.", e);
            }
        }
        return defaultValue;
    }

    public static double[] getDoubleArray(TiffImageMetadata exif, TagInfo tag,
            double[] defaultValue) {
        if (exif != null && tag != null) {
            try {
                TiffField tf = exif.findField(tag);
                if (tf != null)
                    return tf.getDoubleArrayValue();
            } catch (ImageReadException e) {
                Log.w(TAG, "Failed to read EXIF tag " + tag.name
                        + " as double array.");
            }
        }
        return defaultValue;
    }

    public static double[] getLatLon(TiffImageMetadata exif) {
        double latitude = Double.NaN, longitude = Double.NaN;
        if (exif != null) {
            try {
                TiffImageMetadata.GPSInfo latlng = exif.getGPS();
                if (latlng != null) {
                    latitude = latlng.getLatitudeAsDegreesNorth();
                    longitude = latlng.getLongitudeAsDegreesEast();
                }

                // On certain devices the image purports to have GPS 
                // even when it does not and falsely advertises the 
                // image at 0,0.    We have seen this on the Verison S8
                // running Android 9.    This seems to occur in both the 
                // case where GPS is enabled for photos and when the 
                // GPS is disabled for photos.   In this case where the 
                // latitude and logitude are both 0, just indicate that 
                // no latitude and longitude was found.    -shb

                if (Double.compare(latitude, 0.0) == 0 &&
                        Double.compare(longitude, 0.0) == 0) {
                    latitude = Double.NaN;
                    longitude = Double.NaN;
                }
            } catch (ImageReadException ignored) {
                Log.w(TAG, "Failed to read EXIF GPS location.");
            }
        }
        return new double[] {
                latitude, longitude
        };
    }

    /**
     * Get the GPS location this image was taken it (if available)
     * @param exif Exif metadata
     * @return Location as geo point
     */
    public static GeoPoint getLocation(TiffImageMetadata exif) {
        double[] latlon = getLatLon(exif);
        double d = getAltitude(exif,
                GeoPoint.UNKNOWN);
        return new GeoPoint(latlon[0], latlon[1],
                EGM96.getHAE(latlon[0], latlon[1], d));
    }

    /**
     * Get the altitude this image was taken at in meters MSL
     * @param exif Exif metadata
     * @param defaultValue Default elevation (meters MSL)
     * @return Elevation (meters MSL)
     */
    public static double getAltitude(TiffImageMetadata exif,
            double defaultValue) {
        double alt = getDouble(exif,
                TiffConstants.GPS_TAG_GPS_ALTITUDE, Double.NaN);
        if (!Double.isNaN(alt)) {
            if (getInt(exif,
                    TiffConstants.GPS_TAG_GPS_ALTITUDE_REF, 0) == 1)
                alt *= -1;
            return alt;
        }
        return defaultValue;
    }

    /**
     * Set the point and altitude (if valid)
     * @param tos EXIF output set
     * @param point Point to set
     */
    public static boolean setPoint(TiffOutputSet tos, GeoPoint point) {
        boolean ret = updateField(tos, TiffConstants.GPS_TAG_GPS_LATITUDE,
                getDMSArray(point.getLatitude()));
        ret &= updateField(tos, TiffConstants.GPS_TAG_GPS_LATITUDE_REF,
                point.getLatitude() < 0.00d ? "S" : "N");
        ret &= updateField(tos, TiffConstants.GPS_TAG_GPS_LONGITUDE,
                getDMSArray(point.getLongitude()));
        ret &= updateField(tos, TiffConstants.GPS_TAG_GPS_LONGITUDE_REF,
                point.getLongitude() < 0.00d ? "W" : "E");
        double msl = EGM96.getMSL(point);
        if (GeoPoint.isAltitudeValid(msl)) {
            ret &= updateField(tos, GPS_ALTITUDE, Math.abs(msl));
            ret &= updateField(tos, GPS_ALTITUDE_REF, (byte) (msl > 0 ? 0 : 1));
        }
        return ret;
    }

    /**
     * Convert JSON string in UserComment to a Map (GeoTakCam images only)
     * Fields include:
     * - ImgPitch (float)
     * - ImgRoll (float)
     * - Inclination (float)
     * - HorizontalFOV (float)
     * - VerticalFOV (float)
     * - Address (string)
     */
    public static void getExtras(TiffImageMetadata exif,
            Map<String, Object> bundle) {
        if (exif == null)
            return;
        String json = getString(exif, USER_COMMENT, null);
        if (!FileSystemUtils.isEmpty(json)) {
            try {
                // Fail-safe in case user comment is corrupted
                if (!json.startsWith("{") && json.contains("{")) {
                    json = json.substring(json.indexOf("{"));
                    if (!json.endsWith("}")) {
                        if (!json.endsWith("\""))
                            json += "\"";
                        json += "}";
                    }
                }
                JSONObject jo = new JSONObject(json);
                Iterator<String> iter = jo.keys();
                while (iter.hasNext()) {
                    String key = iter.next();
                    bundle.put(key, jo.get(key));
                }
            } catch (Exception e) {
                Log.w(TAG,
                        "Failed to parse EXIF UserComment as JSON: "
                                + e.getMessage());
            }
        }
    }

    /**
     * Put the extras bundle back into the TIFF output set
     * @param bundle Extras bundle
     * @param tos TIFF output set
     */
    public static void putExtras(Map<String, Object> bundle,
            TiffOutputSet tos) {
        if (bundle == null)
            return;
        try {
            // Need to remove UserComment from IFD 0 first
            tos.removeField(USER_COMMENT);
            // Then write it back to Sub IFD
            JSONObject jo = new JSONObject(bundle);
            updateField(tos, USER_COMMENT, jo.toString());
        } catch (Exception e) {
            Log.w(TAG, "Failed to putExtras", e);
        }
    }

    /**
     * Get a single extra parameter
     * @param exif Exif metadata
     * @param key Extra key
     * @param defValue Extra default value
     * @return The extra value (defValue if null or cannot be converted)
     */
    public static Object getExtra(TiffImageMetadata exif, String key,
            Object defValue) {
        Map<String, Object> map = new HashMap<>();
        getExtras(exif, map);
        Object v = map.get(key);
        if (v == null)
            return defValue;
        try {
            String vStr = String.valueOf(v);
            if (defValue instanceof Integer)
                return Integer.parseInt(vStr);
            else if (defValue instanceof Long)
                return Long.parseLong(vStr);
            else if (defValue instanceof Float)
                return Float.parseFloat(vStr);
            else if (defValue instanceof Double)
                return Double.parseDouble(vStr);
            else if (defValue instanceof Boolean)
                return Boolean.parseBoolean(vStr);
            else if (defValue instanceof String)
                return vStr;
        } catch (Exception e) {
            Log.w(TAG, "getExtra: " + key + " return (" + v
                    + ") does not match default value (" + defValue + ")", e);
        }
        return defValue;
    }

    public static int getExtra(TiffImageMetadata exif,
            String key, int defaultValue) {
        return (Integer) getExtra(exif, key, (Integer) defaultValue);
    }

    public static float getExtra(TiffImageMetadata exif,
            String key, float defaultValue) {
        return (Float) getExtra(exif, key, (Float) defaultValue);
    }

    public static boolean getExtra(TiffImageMetadata exif,
            String key, boolean defaultValue) {
        return (Boolean) getExtra(exif, key, (Boolean) defaultValue);
    }

    public static float getExtraFloat(TiffImageMetadata exif,
            String key, float defaultValue) {
        return getExtra(exif, key, defaultValue);
    }

    public static String getExtraString(TiffImageMetadata exif,
            String key, String defaultValue) {
        return String.valueOf(getExtra(exif, key, defaultValue));
    }

    public static boolean updateField(TiffOutputSet set, TagInfo tag,
            Object data) {
        if (set != null && tag != null) {
            try {
                TiffOutputDirectory tod = getFieldDirectory(set, tag);
                if (tod == null)
                    throw new ImageWriteException("Invalid directory");
                tod.removeField(tag);
                TiffOutputField outputField;
                if (data instanceof String)
                    outputField = TiffOutputField.create(tag, set.byteOrder,
                            (String) data);
                else if (data instanceof Number)
                    outputField = TiffOutputField.create(tag, set.byteOrder,
                            (Number) data);
                else if (data instanceof Number[])
                    outputField = TiffOutputField.create(tag, set.byteOrder,
                            (Number[]) data);
                else
                    throw new ImageWriteException("Invalid data type.");
                tod.add(outputField);
                return true;
            } catch (Exception e) {
                StringBuilder exc = new StringBuilder(
                        "Failed to write EXIF tag ");
                exc.append(tag.name);
                exc.append(" as ");
                exc.append(data.getClass());
                // List valid data types
                exc.append("\nExpected data types [");
                exc.append(tag.length);
                exc.append("]:");
                for (FieldType type : tag.dataTypes) {
                    exc.append("\n\t");
                    exc.append(type.name);
                }
                Log.e(TAG, exc.toString(), e);
            }
        }
        return false;
    }

    /**
     * Get the output directory for a given metadata tag
     * @param tos TIFF output set
     * @param tag Metadata tag
     * @return Output directory or null if error
     */
    public static TiffOutputDirectory getFieldDirectory(
            TiffOutputSet tos, TagInfo tag) {
        try {
            switch (tag.directoryType.directoryType) {
                case TiffConstants.DIRECTORY_TYPE_GPS:
                    return tos.getOrCreateGPSDirectory();
                case TiffConstants.DIRECTORY_TYPE_EXIF:
                    return tos.getOrCreateExifDirectory();
            }
            return tos.getOrCreateRootDirectory();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean saveExifOutput(TiffOutputSet tos, File imageFile) {
        BufferedOutputStream bos = null;
        // Obtain unmodified image byte array first
        try {
            byte[] imageData = FileSystemUtils.read(imageFile);
            bos = new BufferedOutputStream(
                    IOProviderFactory.getOutputStream(imageFile));
            // Then update and save
            new ExifRewriter().updateExifMetadataLossless(imageData, bos, tos);
            bos.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save EXIF output to "
                    + imageFile.getAbsolutePath(), e);
        } finally {
            IoUtils.close(bos);
        }
        return false;
    }

    /**
     * Use EXIF location, otherwise use self location, default to last known location...
     * 
     * @return
     */
    public static GeoPoint fixImage(final MapView _mapView, final String path) {
        try {
            // Use sanselan to get jpeg exif metadata instead of built-in library
            // which is unable to read most of the GPS tags
            File imageFile = new File(
                    FileSystemUtils.validityScan(path,
                            new String[] {
                                    "jpeg", "jpg"
                            }));
            TiffImageMetadata exif = getExifMetadata(imageFile);
            TiffOutputSet tos = getExifOutput(exif);

            // Need to update the user comment using the correct TagInfo
            // or else it gets corrupted
            String userComment = getString(exif, USER_COMMENT, "");
            updateField(tos, USER_COMMENT, userComment);

            // Check for existing exif metadata
            double latitude = Double.NaN, longitude = Double.NaN;
            if (exif != null) {
                TiffImageMetadata.GPSInfo latlng = exif.getGPS();
                if (latlng != null) {
                    latitude = latlng.getLatitudeAsDegreesNorth();
                    longitude = latlng.getLongitudeAsDegreesEast();

                    // On certain devices the image purports to have GPS 
                    // even when it does not and falsely advertises the 
                    // image at 0,0.    We have seen this on the Verison S8
                    // running Android 9.    This seems to occur in both the 
                    // case where GPS is enabled for photos and when the 
                    // GPS is disabled for photos.   In this case where the 
                    // latitude and logitude are both 0, just indicate that 
                    // no latitude and longitude was found.    -shb

                    if (Double.compare(latitude, 0.0) == 0 &&
                            Double.compare(longitude, 0.0) == 0) {
                        latitude = Double.NaN;
                        longitude = Double.NaN;
                    }
                }
            }
            // Azimuth
            boolean trueNorth = getString(exif,
                    TiffConstants.GPS_TAG_GPS_IMG_DIRECTION_REF, "M").equals(
                            "T");
            double direction = getDouble(exif,
                    TiffConstants.GPS_TAG_GPS_IMG_DIRECTION, Double.NaN);

            // Altitude (MSL)
            double alt = getAltitude(exif, Double.NaN);

            // Circular error (CE)
            double ce = getDouble(exif,
                    TiffConstants.GPS_TAG_GPS_DOP, Double.NaN);

            // Self-marker point (use as default for missing data)
            // If self-marker isn't set then use the middle of the screen
            PointMapItem item = ATAKUtilities.findSelf(_mapView);
            GeoPointMetaData sp = (item != null ? item.getGeoPointMetaData()
                    : _mapView.getPoint());

            // Fill in missing exif data

            // Missing latitude
            if (Double.isNaN(latitude)) {
                latitude = sp.get().getLatitude();
                updateField(tos, TiffConstants.GPS_TAG_GPS_LATITUDE,
                        getDMSArray(latitude));
                Log.d(TAG, "Image latitude is invalid, using self-marker: "
                        + latitude);
            }
            // Missing longitude
            if (Double.isNaN(longitude)) {
                longitude = sp.get().getLongitude();
                updateField(tos, TiffConstants.GPS_TAG_GPS_LONGITUDE,
                        getDMSArray(longitude));
                Log.d(TAG, "Image longitude is invalid, using self-marker: "
                        + longitude);
            }
            double altitude = GeoPoint.UNKNOWN;

            // Missing altitude
            if (Double.isNaN(alt)) {
                // Self-marker altitude
                double spAltitude = EGM96.getMSL(sp.get());
                // If we're using the self-marker location, use the altitude
                // since it may be more accurate than DTED
                if (latitude == sp.get().getLatitude()
                        && longitude == sp.get().getLongitude())
                    altitude = spAltitude;
                // Then try DTED
                if (!GeoPoint.isAltitudeValid(altitude)) {
                    GeoPointMetaData gpm = ElevationManager
                            .getElevationMetadata(
                                    latitude, longitude, null);
                    double dtedAltitude = EGM96.getMSL(gpm.get());
                    // If that doesn't work, revert back to self-marker altitude
                    if (GeoPoint.isAltitudeValid(dtedAltitude))
                        altitude = dtedAltitude;
                }

                // Set new altitude
                if (GeoPoint.isAltitudeValid(altitude)) {
                    Log.d(TAG,
                            "Image altitude is invalid, using DTED/self-marker: "
                                    + alt);
                    // field is in MSL
                    updateField(tos, GPS_ALTITUDE,
                            Math.abs(altitude));
                }
            } else
                // EXIF altitude
                altitude = alt;

            // Missing circular error
            if (Double.isNaN(ce)) {
                ce = sp.get().getCE();
                updateField(tos, GPS_DOP, ce);
                Log.d(TAG, "Image CE is invalid, using self-marker: " + ce);
            }

            // Missing azimuth
            if (Double.isNaN(direction)) {
                // use the ATAK computed device azimuth
                direction = _mapView.getMapData().getDouble(
                        "deviceAzimuth",
                        Double.NaN);
                if (!Double.isNaN(direction)) {
                    // Calculate azimuth based on device orientation
                    final int oFlag = getInt(exif,
                            TiffConstants.EXIF_TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL);
                    final double iO = getImageOrientation(oFlag);
                    final double aO = getWindowOffset(_mapView.getContext());
                    direction = direction + computeCorrection(iO, aO) + 360d;
                    while (direction > 360)
                        direction = direction - 360d;

                    Log.d(TAG,
                            "computed GPSImgDirection based on orientation: "
                                    + direction);
                }
            } else if (trueNorth) {
                // Need to compute magnetic north from true north
                GeomagneticField gf = new GeomagneticField((float) latitude,
                        (float) longitude,
                        (float) EGM96
                                .getHAE(latitude, longitude, altitude),
                        new CoordinatedTime().getMilliseconds());
                double declination = gf.getDeclination();
                direction -= declination;
                Log.d(TAG, "Corrected GPSImgDirection azimuth to " + direction
                        + " (declination = " + declination + ")");
            }

            // Update EXIF data
            updateField(tos, GPS_IMG_DIRECTION, direction);
            updateField(tos, GPS_IMG_DIRECTION_REF, "M");
            updateField(tos, TiffConstants.GPS_TAG_GPS_LATITUDE_REF,
                    latitude < 0.0d ? "S" : "N");
            updateField(tos, TiffConstants.GPS_TAG_GPS_LONGITUDE_REF,
                    longitude < 0.0d ? "W" : "E");
            if (GeoPoint.isAltitudeValid(altitude))
                updateField(tos, GPS_ALTITUDE_REF,
                        (byte) (altitude > 0 ? 0 : 1));

            // do not use the device heading
            //double heading = ((Marker) item).getTrackHeading();

            saveExifOutput(tos, imageFile);

            Log.d(TAG, "captured image and corrected/added metadata");
            // Return geopoint of image
            return new GeoPoint(latitude, longitude, altitude, ce,
                    GeoPoint.UNKNOWN);
        } catch (Exception e) {
            Log.e(TAG, "error correcting the exif data", e);
        }
        // else return the center point of the current map view.
        Log.d(TAG, "captured image and placed without geospatial information");
        return _mapView.getPoint().get();
    }

    /** Sanselan doesn't properly define certain tags... **/

    // GPS altitude (MSL)
    public static final TagInfo GPS_ALTITUDE = new TagInfo(
            "GPSAltitude", 0x06,
            TiffConstants.FIELD_TYPE_DESCRIPTION_RATIONAL,
            1, TiffConstants.EXIF_DIRECTORY_GPS);

    // Altitude reference (above or below sea level)
    public static final TagInfo GPS_ALTITUDE_REF = new TagInfo(
            "GPSAltitudeRef", 0x05,
            TiffConstants.FIELD_TYPE_DESCRIPTION_BYTE,
            1, TiffConstants.EXIF_DIRECTORY_GPS);

    // GPS degree of precision (accuracy)
    public static final TagInfo GPS_DOP = new TagInfo(
            "GPSDOP", 0x0B,
            TiffConstants.FIELD_TYPE_DESCRIPTION_RATIONAL,
            1, TiffConstants.EXIF_DIRECTORY_GPS);

    // GPS processing method
    public static final TagInfo GPS_SOURCE = new TagInfo.Text(
            "GPSProcessingMethod", 0x1B, TiffConstants.FIELD_TYPE_UNKNOWN,
            -1, TiffConstants.EXIF_DIRECTORY_GPS);

    // GPS speed
    public static final TagInfo GPS_SPEED = new TagInfo("GPSSpeed",
            0x0D, TiffConstants.FIELD_TYPE_DESCRIPTION_RATIONAL,
            1, TiffConstants.EXIF_DIRECTORY_GPS);

    // GPS movement tracking direction
    public static final TagInfo GPS_TRACK = new TagInfo("GPSTrack",
            0x0F, TiffConstants.FIELD_TYPE_DESCRIPTION_RATIONAL,
            1, TiffConstants.EXIF_DIRECTORY_GPS);

    // Azimuth image was taken at
    public static final TagInfo GPS_IMG_DIRECTION = new TagInfo(
            "GPSImgDirection", 0x11,
            TiffConstants.FIELD_TYPE_DESCRIPTION_RATIONAL,
            1, TiffConstants.EXIF_DIRECTORY_GPS);

    // Azimuth reference (T or M)
    public static final TagInfo GPS_IMG_DIRECTION_REF = new TagInfo(
            "GPSImgDirectionRef", 0x10,
            TiffConstants.FIELD_TYPE_DESCRIPTION_ASCII,
            1, TiffConstants.EXIF_DIRECTORY_GPS);

    // User comment (this is where extras are stored in JSON format)
    public static final TagInfo USER_COMMENT = new TagInfo.Text(
            "UserComment", 0x9286, TiffConstants.FIELD_TYPE_DESCRIPTION_ASCII,
            1, TiffConstants.EXIF_DIRECTORY_EXIF_IFD);

    /**
     * Set PNG description directly by adding/modifying the tEXt chunk
     * @param f The PNG file to modify
     * @param desc The image description
     */
    public static void setPNGDescription(File f, String desc) {
        File fOut = new File(f.getAbsolutePath() + ".tmp");
        byte[] buf = new byte[FileSystemUtils.BUF_SIZE];
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = IOProviderFactory.getInputStream(f);

            // Make sure the file isn't empty
            int fileLen = fis.available();
            if (fileLen <= 0)
                return;

            // Check for valid PNG signature
            if (!(fis.read(buf, 0, 8) == 8 && buf[0] == -119 && buf[1] == 80
                    && buf[2] == 78 && buf[3] == 71))
                return;

            Map<String, String> textKV = new HashMap<>();
            int pos, startPos = -1, endPos = -1, chunkLen = 0;
            while ((pos = fis.available()) > 0) {

                // Start position of this chuck (before length)
                startPos = fileLen - pos;

                // Chunk length
                chunkLen = 0;
                if (fis.read(buf, 0, 4) == 4)
                    chunkLen = (buf[0] << 24) | (buf[1] << 16)
                            | (buf[2] << 8) | buf[3];

                // Chunk type
                String chunkType = null;
                if (fis.read(buf, 0, 4) == 4)
                    chunkType = new String(buf, 0, 4,
                            FileSystemUtils.UTF8_CHARSET);
                if (chunkType == null)
                    break;

                // Stop reading once we hit the first IDAT
                if (chunkType.equals("IDAT"))
                    break;

                // This is where the description goes
                if (chunkType.equals("tEXt")) {
                    if (fis.read(buf, 0, chunkLen) != chunkLen) {
                        Log.e(TAG, "Failed to read tExt chunk");
                        return;
                    }
                    // Split up kv strings (delimited by 0)
                    String key = null;
                    int start = 0;
                    for (int i = 0; i <= chunkLen; i++) {
                        if (i == chunkLen || buf[i] == 0) {
                            String s = new String(buf, start, i - start,
                                    StandardCharsets.ISO_8859_1);
                            if (key == null)
                                key = s;
                            else {
                                textKV.put(key, s);
                                key = null;
                            }
                            start = i + 1;
                        }
                    }
                    // Mark the end position of this chunk (length, type, and CRC included)
                    endPos = startPos + chunkLen + 12;
                    break;
                } else {
                    long n = fis.skip(chunkLen); // Skip chunk data
                    if (n < chunkLen)
                        Log.d(TAG, "skip failed to advance: " + chunkLen);
                }
                long n = fis.skip(4); // Skip CRC
                if (n < 4)
                    Log.d(TAG, "skip failed to advance by 4");
            }
            if (startPos <= 0)
                return;
            fis.close();

            // Now copy the PNG to a new file with the new description
            textKV.put("Description", desc);

            // Convert the map back to byte data and calc length/CRC
            int t = 0, textLen = 0;
            byte[][] textB = new byte[textKV.size() * 2][];
            byte[] tEXt = "tEXt".getBytes();
            CRC32 crc = new CRC32();
            crc.update(tEXt);
            for (Map.Entry<String, String> e : textKV.entrySet()) {
                if (FileSystemUtils.isEmpty(e.getKey())
                        || FileSystemUtils.isEmpty(e.getValue()))
                    continue;
                if (t > 0) {
                    crc.update(0);
                    textLen++;
                }
                crc.update(textB[t] = e.getKey().getBytes());
                crc.update(0);
                crc.update(textB[t + 1] = e.getValue().getBytes());
                textLen += textB[t].length + textB[t + 1].length + 1;
                t += 2;
            }

            // Copy and update
            fis = IOProviderFactory.getInputStream(f);
            fos = IOProviderFactory.getOutputStream(fOut);

            int read;
            pos = 0;
            while ((read = fis.read(buf)) != -1) {
                pos += read;
                if (startPos != -1 && pos > startPos) {
                    // Write preceding data
                    fos.write(buf, 0, startPos);

                    // Write length of new tEXt chunk
                    fos.write(textLen >> 24 & 0xFF);
                    fos.write(textLen >> 16 & 0xFF);
                    fos.write(textLen >> 8 & 0xFF);
                    fos.write(textLen & 0xFF);

                    // Write tEXt tag
                    fos.write(tEXt);

                    // Write tEXt data
                    for (int i = 0; i < t; i += 2) {
                        fos.write(textB[i]);
                        fos.write(0);
                        fos.write(textB[i + 1]);
                        if (i < t - 2)
                            fos.write(0);
                    }

                    // Write tEXt CRC
                    long crcVal = crc.getValue();
                    fos.write((byte) (crcVal >> 24 & 0xFF));
                    fos.write((byte) (crcVal >> 16 & 0xFF));
                    fos.write((byte) (crcVal >> 8 & 0xFF));
                    fos.write((byte) (crcVal & 0xFF));

                    // Write the rest of the data in the buffer
                    if (endPos > -1 && read > endPos) {
                        fos.write(buf, endPos, read - endPos);
                        endPos = -1;
                    } else if (read > startPos)
                        fos.write(buf, startPos, read - startPos);
                    startPos = -1;
                } else {
                    if (endPos > -1) {
                        if (read > endPos) {
                            fos.write(buf, endPos, read - endPos);
                            endPos = -1;
                        }
                    } else
                        fos.write(buf, 0, read);
                }
            }

            fis.close();
            fos.close();
            FileSystemUtils.delete(f);
            if (!IOProviderFactory.renameTo(fOut, f))
                Log.e(TAG, "Failed to rename " + fOut + " to " + f);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write PNG description", e);
        } finally {
            IoUtils.close(fis);
            IoUtils.close(fos);
            if (IOProviderFactory.exists(fOut))
                FileSystemUtils.delete(fOut);
        }
    }
}
