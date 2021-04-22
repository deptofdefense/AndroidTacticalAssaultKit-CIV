
package com.atakmap.android.video;

import com.atakmap.coremap.log.Log;
import com.partech.pgscmedia.frameaccess.DecodedMetadataItem;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.elevation.ElevationData;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.Map;

public class VideoMetadata {

    private final static String TAG = "VideoMetaData";

    //
    // The fifth decimal place is worth up to 1.1 m: it distinguish trees from
    // each other. Accuracy to this level with commercial GPS units can only be
    // achieved with differential correction.

    final private static double PRECISION_5 = 100000;

    private final static ElevationManager.QueryParameters DTM_FILTER = new ElevationManager.QueryParameters();
    static {
        DTM_FILTER.elevationModel = ElevationData.MODEL_TERRAIN;
    }

    ConnectionEntry connectionEntry;

    boolean hasMetadata;

    // cache the previous lat/lon to cut down on successive lookups of elevation
    private double prevFrameLongitude;
    private double prevFrameLatitude;

    double frameLongitude;
    double frameLatitude;
    double frameElevation;
    double frameHAE;
    final GeoPointMetaData frameDTED = new GeoPointMetaData();

    double sensorAltitude;
    double sensorLatitude;
    double sensorLongitude;
    double sensorHFOV;
    double sensorEllipsoidHeight;
    double sensorRoll;

    double corner1lat, corner1lon;
    double corner2lat, corner2lon;
    double corner3lat, corner3lon;
    double corner4lat, corner4lon;

    String platformDesignator = null;
    String platformTail = null;

    long metadataTimestamp = -1;

    boolean useKlvElevation = false;

    public void update(
            final Map<DecodedMetadataItem.MetadataItemIDs, DecodedMetadataItem> items) {

        if (items.containsKey(
                DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_FRAME_CENTER_LATITUDE)) {

            // if the frame contains the center latitude and longitude but does not contain 
            // new corner coordinates, just shift the current corner coordinates by the amount 
            // of the center.

            DecodedMetadataItem decodedMetadataItem = items.get(
                    DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_FRAME_CENTER_LATITUDE);
            double newLatitude = (Double) decodedMetadataItem.getValue();

            DecodedMetadataItem.MetadataItemIDs id = DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_CORNER_LATITUDE_POINT_1;
            if (!items.containsKey(id)) {
                if (!Double.isNaN(corner1lat)) {
                    double newCorner1lat = corner1lat - frameLatitude
                            + newLatitude;
                    DecodedMetadataItem item = new DecodedMetadataItem(id,
                            newCorner1lat);
                    items.put(id, item);
                }
            }

            id = DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_CORNER_LATITUDE_POINT_2;
            if (!items.containsKey(id)) {
                if (!Double.isNaN(corner2lat)) {
                    double newCorner2lat = corner2lat - frameLatitude
                            + newLatitude;
                    DecodedMetadataItem item = new DecodedMetadataItem(id,
                            newCorner2lat);
                    items.put(id, item);
                }
            }

            id = DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_CORNER_LATITUDE_POINT_3;
            if (!items.containsKey(id)) {
                if (!Double.isNaN(corner3lat)) {
                    double newCorner3lat = corner3lat - frameLatitude
                            + newLatitude;
                    DecodedMetadataItem item = new DecodedMetadataItem(id,
                            newCorner3lat);
                    items.put(id, item);
                }
            }

            id = DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_CORNER_LATITUDE_POINT_4;
            if (!items.containsKey(id)) {
                if (!Double.isNaN(corner4lat)) {
                    double newCorner4lat = corner4lat - frameLatitude
                            + newLatitude;
                    DecodedMetadataItem item = new DecodedMetadataItem(id,
                            newCorner4lat);
                    items.put(id, item);
                }
            }
        }

        if (items.containsKey(
                DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_FRAME_CENTER_LONGITUDE)) {
            DecodedMetadataItem decodedMetadataItem = items.get(
                    DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_FRAME_CENTER_LONGITUDE);
            double newLongitude = (Double) decodedMetadataItem.getValue();

            DecodedMetadataItem.MetadataItemIDs id = DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_CORNER_LONGITUDE_POINT_1;
            if (!items.containsKey(id)) {
                if (!Double.isNaN(corner1lon)) {
                    double newCorner1lon = corner1lon - frameLongitude
                            + newLongitude;
                    DecodedMetadataItem item = new DecodedMetadataItem(id,
                            newCorner1lon);
                    items.put(id, item);
                }
            }

            id = DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_CORNER_LONGITUDE_POINT_2;
            if (!items.containsKey(id)) {
                if (Double.isNaN(corner2lon)) {
                    double newCorner2lon = corner2lon - frameLongitude
                            + newLongitude;
                    DecodedMetadataItem item = new DecodedMetadataItem(id,
                            newCorner2lon);
                    items.put(id, item);
                }
            }

            id = DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_CORNER_LONGITUDE_POINT_3;
            if (!items.containsKey(id)) {
                if (!Double.isNaN(corner3lon)) {
                    double newCorner3lon = corner3lon - frameLongitude
                            + newLongitude;
                    DecodedMetadataItem item = new DecodedMetadataItem(id,
                            newCorner3lon);
                    items.put(id, item);
                }
            }

            id = DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_CORNER_LONGITUDE_POINT_4;
            if (!items.containsKey(id)) {
                if (!Double.isNaN(corner4lon)) {
                    double newCorner4lon = corner4lon - frameLongitude
                            + newLongitude;
                    DecodedMetadataItem item = new DecodedMetadataItem(id,
                            newCorner4lon);
                    items.put(id, item);
                }
            }
        }

        for (final Map.Entry<DecodedMetadataItem.MetadataItemIDs, DecodedMetadataItem> e : items
                .entrySet()) {
            if (e != null) {
                try {
                    switch (e.getKey()) {
                        case METADATA_ITEMID_SENSOR_LATITUDE: {
                            sensorLatitude = trim(
                                    (Double) e.getValue().getValue());
                            break;
                        }
                        case METADATA_ITEMID_SENSOR_LONGITUDE: {
                            sensorLongitude = trim(
                                    (Double) e.getValue().getValue());
                            break;
                        }
                        case METADATA_ITEMID_SENSOR_TRUE_ALTITUDE: {
                            sensorAltitude = trim(
                                    (Double) e.getValue().getValue());
                            break;
                        }
                        case METADATA_ITEMID_FRAME_CENTER_LATITUDE: {
                            frameLatitude = trim(
                                    (Double) e.getValue().getValue());
                            break;
                        }
                        case METADATA_ITEMID_FRAME_CENTER_LONGITUDE: {
                            frameLongitude = trim(
                                    (Double) e.getValue().getValue());
                            break;
                        }
                        case METADATA_ITEMID_FRAME_CENTER_HEIGHT_ABOVE_ELLIPSOID: {
                            frameHAE = (Double) e.getValue().getValue();

                            break;
                        }
                        case METADATA_ITEMID_FRAME_CENTER_ELEVATION: {
                            frameElevation = (Double) e.getValue().getValue();
                            break;
                        }
                        case METADATA_ITEMID_SENSOR_ELLIPSOID_HEIGHT: {
                            sensorEllipsoidHeight = (Double) e.getValue()
                                    .getValue();
                            break;
                        }
                        case METADATA_ITEMID_SENSOR_HORIZONTAL_FIELD_OF_VIEW: {
                            sensorHFOV = (Double) e.getValue().getValue();
                            break;
                        }
                        case METADATA_ITEMID_SENSOR_RELATIVE_ROLL_ANGLE: {
                            sensorRoll = (Double) e.getValue().getValue();
                            break;
                        }
                        case METADATA_ITEMID_PLATFORM_DESIGNATION: {
                            platformDesignator = (String) e.getValue()
                                    .getValue();
                            break;
                        }
                        case METADATA_ITEMID_PLATFORM_TAIL_NUMBER: {
                            platformTail = (String) e.getValue().getValue();
                            break;
                        }
                        case METADATA_ITEMID_TARGET_WIDTH: {
                            break;
                        }
                        case METADATA_ITEMID_CORNER_LATITUDE_POINT_1: {
                            corner1lat = trim((Double) e.getValue().getValue());
                            break;
                        }
                        case METADATA_ITEMID_CORNER_LONGITUDE_POINT_1: {
                            corner1lon = trim((Double) e.getValue().getValue());
                            break;
                        }
                        case METADATA_ITEMID_CORNER_LATITUDE_POINT_2: {
                            corner2lat = trim((Double) e.getValue().getValue());
                            break;
                        }
                        case METADATA_ITEMID_CORNER_LONGITUDE_POINT_2: {
                            corner2lon = trim((Double) e.getValue().getValue());
                            break;
                        }
                        case METADATA_ITEMID_CORNER_LATITUDE_POINT_3: {
                            corner3lat = trim((Double) e.getValue().getValue());
                            break;
                        }
                        case METADATA_ITEMID_CORNER_LONGITUDE_POINT_3: {
                            corner3lon = trim((Double) e.getValue().getValue());
                            break;
                        }
                        case METADATA_ITEMID_CORNER_LATITUDE_POINT_4: {
                            corner4lat = trim((Double) e.getValue().getValue());
                            break;
                        }
                        case METADATA_ITEMID_CORNER_LONGITUDE_POINT_4: {
                            corner4lon = trim((Double) e.getValue().getValue());
                            break;
                        }
                        case METADATA_ITEMID_UNIX_TIME_STAMP: {
                            long timestamp = (long) e.getValue().getValue();
                            metadataTimestamp = timestamp / 1000;
                        }
                        default:
                            break;

                    }
                } catch (Exception ex) {
                    Log.e(TAG, "error decoding: " + e.getKey() + " "
                            + e.getValue() + " " + ex);
                }

            }
        }
        // only perform a lookup if the lat/lon has changed
        if (Double.compare(prevFrameLatitude, frameLatitude) != 0 ||
                Double.compare(prevFrameLongitude,
                        frameLongitude) != 0) {

            if (useKlvElevation && !Double.isNaN(frameElevation)) {
                double haeAlt = EGM96.getHAE(frameLatitude, frameLongitude,
                        frameElevation);
                frameDTED
                        .set(new GeoPoint(frameLatitude, frameLongitude,
                                haeAlt))
                        .setAltitudeSource(GeoPointMetaData.CALCULATED);
            } else if (useKlvElevation && !Double.isNaN(frameHAE)) {
                frameDTED
                        .set(new GeoPoint(frameLatitude, frameLongitude,
                                frameHAE))
                        .setAltitudeSource(GeoPointMetaData.CALCULATED);
            } else {
                // alt is not used, the ElevationManager is responsible for setting frameDTED
                double alt = ElevationManager.getElevation(frameLatitude,
                        frameLongitude, DTM_FILTER, frameDTED);
            }

            prevFrameLatitude = frameLatitude;
            prevFrameLongitude = frameLongitude;
        }

    }

    /**
     * Simple 4 corner calculation at the moment.
     * @return if the 4 corners are valid numbers.
     */
    boolean hasFourCorners() {
        return (!Double.isNaN(corner1lat) && !Double.isNaN(corner1lon) &&
                !Double.isNaN(corner2lat) && !Double.isNaN(corner2lon) &&
                !Double.isNaN(corner3lat) && !Double.isNaN(corner3lon) &&
                !Double.isNaN(corner4lat) && !Double.isNaN(corner4lon) &&

                Double.compare(corner1lat, corner3lat) != 0 && // rectangle cross point comparison
                Double.compare(corner2lat, corner4lat) != 0 &&
                Double.compare(corner1lon, corner3lon) != 0 &&
                Double.compare(corner2lon, corner4lon) != 0);
    }

    /**
     * Reduces the precision of the value so it is as accurate as needed.  See the notes in 
     * LocationMapComponent.
     * @param d the double to trim.
     */
    private double trim(final Double d) {
        if (d == null)
            return Double.NaN;

        return Math.round(d * PRECISION_5) / PRECISION_5;
    }

    /**
     * Clears out the metadata assigned so that it is fresh for a new video
     */
    public void dispose() {
        frameLatitude = Double.NaN;
        frameLongitude = Double.NaN;
        platformTail = null;
        platformDesignator = null;
        corner1lat = Double.NaN;
        corner1lon = Double.NaN;
        corner2lat = Double.NaN;
        corner2lon = Double.NaN;
        corner3lat = Double.NaN;
        corner3lon = Double.NaN;
        corner4lat = Double.NaN;
        corner4lon = Double.NaN;
        frameDTED.set(GeoPoint.UNKNOWN_POINT);
        prevFrameLatitude = Double.NaN;
        prevFrameLongitude = Double.NaN;
    }

}
