
package com.atakmap.android.geofence.data;

public class GeoFenceConstants {

    public static final String COT_TRIGGER = "trigger";
    public static final String MARKER_TRIGGER = "geofence_trigger";

    public static final String COT_TRACKING = "tracking";
    public static final String MARKER_TRACKING = "geofence_tracking";

    public static final String COT_MONITOR = "monitor";
    public static final String MARKER_MONITOR = "geofence_monitor";

    public static final String COT_BOUNDING_SPHERE = "boundingSphere"; // value in meters
    public static final String MARKER_BOUNDING_SPHERE = "geofence_boundingSphere";

    public static final String COT_ELEVATION_MONITORED = "elevationMonitored";
    public static final String MARKER_ELEVATION_MONITORED = "geofence_elevationMonitored";

    public static final String COT_ELEVATION_MIN = "minElevation"; // value in meters hae
    public static final String MARKER_ELEVATION_MIN = "geofence_minElevation";

    public static final String COT_ELEVATION_MAX = "maxElevation"; // value in meters hae
    public static final String MARKER_ELEVATION_MAX = "geofence_maxElevation";

    public static final String MARKER_MONITOR_UIDS = "geofence_monitor_uids";

    // Metadata flag to determine if a geo-fence is being imported from CoT
    // and therefore should NOT trigger a persist when added
    public static final String GEO_FENCE_IMPORTED = "__geoFenceImported";

    public static final int DEFAULT_ENTRY_RADIUS_METERS = 160000;
}
