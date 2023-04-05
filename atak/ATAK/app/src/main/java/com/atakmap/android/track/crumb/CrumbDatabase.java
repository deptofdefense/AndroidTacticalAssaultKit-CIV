
package com.atakmap.android.track.crumb;

import android.content.SharedPreferences;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.util.SparseArray;

import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.track.BreadcrumbReceiver;
import com.atakmap.android.track.TrackDetails;
import com.atakmap.android.track.maps.TrackPolyline;
import com.atakmap.android.track.task.TrackProgress;
import com.atakmap.android.track.ui.TrackUser;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;

import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.StatementIface;
import com.atakmap.map.layer.feature.datastore.FeatureSpatialDatabase;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.SchemaData;
import com.ekito.simpleKML.model.SimpleArrayData;
import com.ekito.simpleKML.model.SimpleData;
import com.ekito.simpleKML.model.Track;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CrumbDatabase {
    public static final String TAG = "CrumbDatabase";
    public static final int DATABASE_VERSION = 5;

    private static final int DEFAULT_NUMBER_TRACKS = 15;
    private static final double TEMP_TRACK_THRESHOLD_MILLIS = 1000 * 60 * 10; //10 minutes
    private static final int TEMP_TRACK_THRESHOLD_METERS = 100;

    public static final double VALUE_UNKNOWN = GeoPoint.UNKNOWN;
    private static final int MAX_TITLE_LENGTH = 30;

    private final Set<Crumb> crumbsToProcess = new HashSet<>();
    private final ExecutorService pool = Executors
            .newSingleThreadExecutor(new NamedThreadFactory(
                    "CrumbPool"));
    private Handler worker;

    private final static File CRUMB_DB_FILE2 = FileSystemUtils
            .getItem("Databases/crumbs2.sqlite");

    /* Listener interface */
    private final ConcurrentLinkedQueue<OnCrumbListener> _listeners = new ConcurrentLinkedQueue<>();

    private final Object lock = new Object();

    public interface OnCrumbListener {
        void onCrumbAdded(int trackId, Crumb c);
    }

    /**
     * This legacy table contained crumbs for self and any node which the
     * user explicitly turned on crumbs (but only for the period the crumb trail was enabled)
     */
    private static final String LEGACY_BREADCRUMB_TABLE_NAME = "breadcrumb";

    /**
     * SQLite does not allow column DROP, and I also had issues dropping and recreating this table
     * So we just create a new table. This table contains crumbs for self and any node which the
     * user explicitly turned on crumbs (but only for the period the crumb trail was enabled)
     */
    private static final String BREADCRUMB_TABLE_NAME2 = "breadcrumb2";

    /**
     * This table contains track segments e.g. when ATAK was restarted or the user
     * manually created a segment via track history tool
     */
    private static final String SEGMENT_TABLE_NAME = "segment";

    //breadcrumb table columns
    public final static String COLUMN_ID = "_id"; // unique id field
    private final static String COLUMN_SEGMENT_ID = "_sid"; // unique id of the corresponding segment
    private final static String LEGACY_COLUMN_UID = "uid"; // MOVED TO SEGMENT table - unique identifier for the map item being logged
    private final static String COLUMN_TIMESTAMP = "timestamp"; // the UTC time of this fix, in milliseconds since January 1, 1970
    private final static String LEGACY_COLUMN_TITLE = "title"; // MOVED TO SEGMENT table - callsign or human readable title of the map item being logged
    public final static String COLUMN_LAT = "lat"; // latitude in degrees
    public final static String COLUMN_LON = "lon"; // longitude in degrees
    private final static String COLUMN_ALT = "alt"; // altitude in meters HAE
    private final static String COLUMN_CE = "ce"; // ce90 in meters
    private final static String COLUMN_LE = "le"; // le90 in meters
    private final static String COLUMN_BEARING = "bearing"; // bearing in degrees
    private final static String COLUMN_SPEED = "speed"; // speed in meters / second
    private final static String COLUMN_POINT_SOURCE = "ptsource"; // source of the point information
    private final static String COLUMN_ALTITUDE_SOURCE = "altsource"; // source of the altitude/elevation information
    private final static String COLUMN_POINT_GEOM = "point_geom"; // searchable point entry

    private static final int COLUMN_ID_INDEX = 0;
    private static final int COLUMN_SEGMENT_ID_INDEX = 1;

    private static final int COLUMN_TIMESTAMP_INDEX = 2;
    private static final int COLUMN_LAT_INDEX = 3;
    private static final int COLUMN_LON_INDEX = 4;
    private static final int COLUMN_ALT_INDEX = 5;
    private static final int COLUMN_CE_INDEX = 6;
    private static final int COLUMN_LE_INDEX = 7;
    private static final int COLUMN_BEARING_INDEX = 8;
    private static final int COLUMN_SPEED_INDEX = 9;
    private static final int COLUMN_POINT_SOURCE_INDEX = 10;
    private static final int COLUMN_INDEX = 11;
    private static final int COLUMN_POINT_GEOM_INDEX = 12;

    //segment table columns
    private final static String SEG_COLUMN_ID = "_id"; // unique id field
    public final static String SEG_COLUMN_TIMESTAMP = "timestamp"; // the UTC time of this fix, in milliseconds since January 1, 1970
    private final static String SEG_COLUMN_TITLE = "title"; //label for segment
    private final static String SEG_COLUMN_COLOR = "color"; //int AARRGGBB
    private final static String SEG_COLUMN_STYLE = "style"; //line, dash, arrow
    private final static String SEG_COLUMN_USER_UID = "uuid"; // unique identifier for the map item being logged
    private final static String SEG_COLUMN_USER_TITLE = "utitle"; // callsign or human readable title of the map item being logged

    private static final int SEG_COLUMN_ID_INDEX = 0;
    private static final int SEG_COLUMN_TIMESTAMP_INDEX = 1;
    private static final int SEG_COLUMN_TITLE_INDEX = 2;
    private static final int SEG_COLUMN_STYLE_INDEX = 3;
    private static final int SEG_COLUMN_COLOR_INDEX = 4;
    private static final int SEG_COLUMN_USER_UID_INDEX = 5;
    private static final int SEG_COLUMN_USER_TITLE_INDEX = 6;

    private static final String META_CRUMB_DBID = "crumb_dbid";
    public static final String META_TRACK_DBID = "track_dbid";
    public static final String META_TRACK_CURRENT = "track_current";
    public static final String META_TRACK_DBIDS = "track_dbids";
    public static final String META_TRACK_NODE_UID = "node_uid";
    public static final String META_TRACK_NODE_TITLE = "node_title";

    // maybe we should provide a reasonable waiting period for a GPS fix to get true coordinated time?
    public final static long STALE = CoordinatedTime.currentDate().getTime()
            - (31 * 24L * 60L * 60L * 1000L);

    private DatabaseIface crumbdb;

    private static CrumbDatabase instance;

    public static synchronized CrumbDatabase instance() {
        if (instance == null) {
            instance = new CrumbDatabase();
        }

        return instance;
    }

    private void initDatabase() {

        final DatabaseIface oldCrumbDb = crumbdb;

        DatabaseInformation dbi = new DatabaseInformation(
                Uri.fromFile(CRUMB_DB_FILE2),
                DatabaseInformation.OPTION_RESERVED1
                        | DatabaseInformation.OPTION_ENSURE_PARENT_DIRS);

        DatabaseIface newCrumbDb = IOProviderFactory.createDatabase(dbi);

        if (newCrumbDb == null) {
            try {
                final File f = CRUMB_DB_FILE2;
                if (!IOProviderFactory.renameTo(f,
                        new File(CRUMB_DB_FILE2 + ".corrupt."
                                + new CoordinatedTime().getMilliseconds()))) {
                    Log.d(TAG, "could not move corrupt db out of the way");
                } else {
                    Log.d(TAG,
                            "default crumbs database corrupted, move out of the way: "
                                    + f);
                }
            } catch (Exception ignored) {
            }
            if (IOProviderFactory.exists(CRUMB_DB_FILE2)) {
                IOProviderFactory.delete(CRUMB_DB_FILE2,
                        IOProvider.SECURE_DELETE);
                newCrumbDb = IOProviderFactory.createDatabase(dbi);
            }
        }
        if (newCrumbDb.getVersion() != DATABASE_VERSION) {
            Log.d(TAG, "Upgrading from v" + newCrumbDb.getVersion()
                    + " to v" + DATABASE_VERSION);
            onUpgrade(newCrumbDb, newCrumbDb.getVersion(), DATABASE_VERSION);
        }

        // swap only after the newCrumbDb is good to go.
        crumbdb = newCrumbDb;

        try {
            if (oldCrumbDb != null)
                oldCrumbDb.close();
        } catch (Exception ignored) {
        }

    }

    private CrumbDatabase() {
        if (!IOProviderFactory.exists(CRUMB_DB_FILE2.getParentFile()))

            if (!IOProviderFactory.mkdirs(CRUMB_DB_FILE2.getParentFile())) {
                Log.e(TAG, "Failed to make Directory at " +
                        CRUMB_DB_FILE2.getParentFile().getAbsolutePath());
            }

        initDatabase();

    }

    void onUpgrade(DatabaseIface db, int oldVersion, int newVersion) {

        if (oldVersion == 4) {
            //upgrade previous version
            Log.d(TAG, "Upgrading db from VERSION=4");

            CursorIface result = null, innerresult = null;
            try {
                //add UID/callsign columns to segments table

                //drop rows in breadcrumbs table where UID != localDeviceUid since a segment will now be for a single UID
                Log.d(TAG, "Purging breadcrumbs for other UIDs");
                String sql = "DELETE FROM " + LEGACY_BREADCRUMB_TABLE_NAME
                        + " WHERE "
                        + LEGACY_COLUMN_UID + " != '" + MapView.getDeviceUid()
                        + "'";
                db.execute(sql, null);

                Log.d(TAG, "Adding segment columns");
                db.execute("ALTER TABLE " + SEGMENT_TABLE_NAME
                        + " ADD COLUMN " + SEG_COLUMN_USER_UID + " TEXT", null);
                db.execute("ALTER TABLE " + SEGMENT_TABLE_NAME
                        + " ADD COLUMN " + SEG_COLUMN_USER_TITLE + " TITLE",
                        null);

                //populate those columns from breadcrumbs table
                //get list of segments
                Log.d(TAG, "Searching for existing segments");
                sql = "SELECT " + SEG_COLUMN_ID + " FROM " + SEGMENT_TABLE_NAME;
                result = db.query(sql, null);

                int trackDbId;
                //loop segments
                while (result.moveToNext()) {
                    trackDbId = result.getInt(SEG_COLUMN_ID_INDEX);
                    if (trackDbId >= 0) {
                        Log.d(TAG,
                                "Migrating metadata for track: " + trackDbId);
                        try {

                            //search title/UID from breadcrumbs table
                            sql = "SELECT " + LEGACY_COLUMN_UID + ","
                                    + LEGACY_COLUMN_TITLE + " FROM "
                                    + LEGACY_BREADCRUMB_TABLE_NAME +
                                    " WHERE " + COLUMN_SEGMENT_ID + " = "
                                    + trackDbId + " LIMIT 1";
                            innerresult = db.query(sql, null);

                            if (innerresult.moveToNext()) {
                                //old version had UID/title on each crumb, so just take from first
                                String migrationTitle = innerresult
                                        .getString(1);
                                String migrationUID = innerresult.getString(0);
                                if (!FileSystemUtils.isEmpty(migrationTitle)) {
                                    //now set segment title
                                    sql = "UPDATE " + SEGMENT_TABLE_NAME +
                                            " SET " + SEG_COLUMN_USER_TITLE
                                            + "= ? WHERE " + SEG_COLUMN_ID + "="
                                            + trackDbId;
                                    //Log.d(TAG, "Setting segment user title: " + sql);
                                    db.execute(sql, new String[] {
                                            migrationTitle
                                    });
                                } else {
                                    Log.w(TAG,
                                            "No segments user title found for upgrade...");
                                }
                                if (!FileSystemUtils.isEmpty(migrationUID)) {
                                    //now set segment user UID
                                    sql = "UPDATE " + SEGMENT_TABLE_NAME +
                                            " SET " + SEG_COLUMN_USER_UID
                                            + "= ? WHERE " + SEG_COLUMN_ID + "="
                                            + trackDbId;
                                    //Log.d(TAG, "Setting segment user UID: " + sql);
                                    db.execute(sql, new String[] {
                                            migrationUID
                                    });
                                } else {
                                    Log.w(TAG,
                                            "No segments user UID found for upgrade...");
                                }
                            } else {
                                Log.w(TAG, "No segments found for upgrade...");
                            }

                        } finally {
                            if (innerresult != null)
                                innerresult.close();
                            innerresult = null;
                        }
                    } else {
                        Log.w(TAG, "No segments found for upgrade...");
                    }
                } //end segment loop

                //SQLite does not support column drop so we just create a new table
                Log.d(TAG, "Creating new crumb table schema: "
                        + BREADCRUMB_TABLE_NAME2);
                db.execute("CREATE TABLE " + BREADCRUMB_TABLE_NAME2 +
                        " (" + COLUMN_ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COLUMN_SEGMENT_ID + " INTEGER, "
                        + COLUMN_TIMESTAMP + " INTEGER, "
                        + COLUMN_LAT + " REAL, "
                        + COLUMN_LON + " REAL, "
                        + COLUMN_ALT + " REAL, "
                        + COLUMN_CE + " REAL, "
                        + COLUMN_LE + " REAL, "
                        + COLUMN_BEARING + " REAL, "
                        + COLUMN_SPEED + " REAL, "
                        + COLUMN_POINT_SOURCE + " TEXT, "
                        + COLUMN_ALTITUDE_SOURCE + " TEXT) ", null);

                Log.d(TAG,
                        "Inserting spatial geometry column into new crumb table: "
                                + BREADCRUMB_TABLE_NAME2);
                final int major = FeatureSpatialDatabase
                        .getSpatialiteMajorVersion(db);
                final int minor = FeatureSpatialDatabase
                        .getSpatialiteMinorVersion(db);

                final String initSpatialMetadataSql;
                if (major > 4 || (major == 4 && minor >= 1))
                    initSpatialMetadataSql = "SELECT InitSpatialMetadata(1)";
                else
                    initSpatialMetadataSql = "SELECT InitSpatialMetadata()";

                db.execute(initSpatialMetadataSql, null);

                db.execute("SELECT AddGeometryColumn('"
                        + BREADCRUMB_TABLE_NAME2
                        + "', '" + COLUMN_POINT_GEOM
                        + "', 4326, 'POINT', 'XY')",
                        null);

                //copy from legacy table to new table
                Log.d(TAG, "Inserting into new crumb table: "
                        + BREADCRUMB_TABLE_NAME2);
                sql = "INSERT INTO " + BREADCRUMB_TABLE_NAME2 + " SELECT " +
                        COLUMN_ID + "," +
                        COLUMN_SEGMENT_ID + "," +
                        COLUMN_TIMESTAMP + "," +
                        COLUMN_LAT + "," +
                        COLUMN_LON + "," +
                        COLUMN_ALT + "," +
                        COLUMN_CE + "," +
                        COLUMN_LE + "," +
                        COLUMN_BEARING + "," +
                        COLUMN_SPEED + "," +
                        COLUMN_POINT_SOURCE + "," +
                        COLUMN_ALTITUDE_SOURCE + "," +
                        COLUMN_POINT_GEOM + " FROM "
                        + LEGACY_BREADCRUMB_TABLE_NAME;
                db.execute(sql, null);

                db.setVersion(DATABASE_VERSION);
                Log.d(TAG, "Upgrade complete to version: " + DATABASE_VERSION);
            } catch (Exception e) {
                Log.e(TAG, "Failed to upgrade", e);
            } finally {
                if (result != null)
                    result.close();
            }
        }

        //delete legacy breadcrumbs table in its own transaction since SQLite database locked
        //exception is likely on the first attempt (during the upgrade). The table drop seems to
        //happen without issue on the next ATAK run
        if (Databases.getTableNames(db).contains(
                LEGACY_BREADCRUMB_TABLE_NAME)) {
            try {
                Log.d(TAG, "Dropping legacy breadcrumbs table: "
                        + LEGACY_BREADCRUMB_TABLE_NAME);
                db.execute("DROP TABLE IF EXISTS "
                        + LEGACY_BREADCRUMB_TABLE_NAME, null);
            } catch (Exception e) {
                Log.e(TAG, "Failed to drop legacy breadcrumbs table: "
                        + LEGACY_BREADCRUMB_TABLE_NAME, e);
            }
        }

        //if v4 upgrade failed or older version, then drop tables
        if (db.getVersion() != DATABASE_VERSION) {
            deleteAll(db);
        }

        if (!Databases.getTableNames(db)
                .contains(BREADCRUMB_TABLE_NAME2)) {
            Log.d(TAG, "creating a new table: " + BREADCRUMB_TABLE_NAME2);
            db.execute("CREATE TABLE " + BREADCRUMB_TABLE_NAME2 +
                    " (" + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_SEGMENT_ID + " INTEGER, "
                    + COLUMN_TIMESTAMP + " INTEGER, "
                    + COLUMN_LAT + " REAL, "
                    + COLUMN_LON + " REAL, "
                    + COLUMN_ALT + " REAL, "
                    + COLUMN_CE + " REAL, "
                    + COLUMN_LE + " REAL, "
                    + COLUMN_BEARING + " REAL, "
                    + COLUMN_SPEED + " REAL, "
                    + COLUMN_POINT_SOURCE + " TEXT, "
                    + COLUMN_ALTITUDE_SOURCE + " TEXT) ", null);

            // probably should index (uid and timestamp but won't for the first cut
            //
            //crumbdb.execute("CREATE INDEX " + TABLE_NAME_INDEX + 
            //                              " on " + TABLE_NAME + "(" + COLUMN_UID + ")");

            final int major = FeatureSpatialDatabase
                    .getSpatialiteMajorVersion(db);
            final int minor = FeatureSpatialDatabase
                    .getSpatialiteMinorVersion(db);

            final String initSpatialMetadataSql;
            if (major > 4 || (major == 4 && minor >= 1))
                initSpatialMetadataSql = "SELECT InitSpatialMetadata(1)";
            else
                initSpatialMetadataSql = "SELECT InitSpatialMetadata()";

            db.execute(initSpatialMetadataSql, null);

            db.execute("SELECT AddGeometryColumn('"
                    + BREADCRUMB_TABLE_NAME2
                    + "', '" + COLUMN_POINT_GEOM + "', 4326, 'POINT', 'XY')",
                    null);

            db.setVersion(DATABASE_VERSION);
        } else {
            Log.d(TAG,
                    "purging breadcrumb entries with a timestamp older than: "
                            + STALE);

            final String sql = "DELETE FROM " + BREADCRUMB_TABLE_NAME2 +
                    " WHERE " + COLUMN_TIMESTAMP + " <= " + STALE;
            db.execute(sql, null);
        }

        //now setup segment table
        if (!Databases.getTableNames(db).contains(SEGMENT_TABLE_NAME)) {
            Log.d(TAG, "creating a new table: " + SEGMENT_TABLE_NAME);
            db.execute("CREATE TABLE " + SEGMENT_TABLE_NAME +
                    " (" + SEG_COLUMN_ID
                    + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + SEG_COLUMN_TIMESTAMP + " INTEGER, "
                    + SEG_COLUMN_TITLE + " TITLE, "
                    + SEG_COLUMN_STYLE + " TEXT, "
                    + SEG_COLUMN_COLOR + " INTEGER, "
                    + SEG_COLUMN_USER_UID + " TEXT, "
                    + SEG_COLUMN_USER_TITLE + " TITLE) ", null);

            // probably should index (uid and timestamp but won't for the first cut
            //
            //crumbdb.execute("CREATE INDEX " + TABLE_NAME_INDEX + 
            //                              " on " + TABLE_NAME + "(" + COLUMN_UID + ")");  
        } else {
            //remove segments no longer referenced by any crumbs. We could have a track with
            //stale timestamp which still has un-stale crumbs
            Log.d(TAG, "purging segment entries with no crumbs");

            final String sql = "DELETE FROM " + SEGMENT_TABLE_NAME + " WHERE "
                    + SEGMENT_TABLE_NAME + "." + SEG_COLUMN_ID +
                    " NOT IN (" + " SELECT " + BREADCRUMB_TABLE_NAME2 + "."
                    + COLUMN_SEGMENT_ID + " FROM " + BREADCRUMB_TABLE_NAME2
                    + ")";
            db.execute(sql, null);
        }
    }

    /**
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.1")
    public void deleteAll() {
        deleteAll(crumbdb);
        crumbdb.close();
        crumbdb = null;
    }

    private void deleteAll(DatabaseIface db) {
        if (db != null) {
            synchronized (lock) {
                Log.d(TAG, "dropping the database tables");

                try {
                    db.execute(
                            "DROP TABLE IF EXISTS " + BREADCRUMB_TABLE_NAME2,
                            null);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to delete crumbs table", e);
                }

                try {
                    db.execute(
                            "DROP TABLE IF EXISTS "
                                    + LEGACY_BREADCRUMB_TABLE_NAME,
                            null);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to delete legacy crumbs table", e);
                }

                try {
                    db.execute("DROP TABLE IF EXISTS " + SEGMENT_TABLE_NAME,
                            null);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to delete segments table", e);
                }

            }
        }
    }

    /**
     * Get ID of segment with most recent timestamp
     *
     * @return the current segment id based on the uid passed in.
     */
    public int getCurrentSegmentId(String uid, String orderBy) {

        synchronized (lock) {
            CursorIface result = null;
            int mostRecentSegment_id = -1;
            try {
                String sql = "SELECT " + SEG_COLUMN_ID + " FROM "
                        + SEGMENT_TABLE_NAME + " WHERE " + SEG_COLUMN_USER_UID
                        + "='" + uid +
                        "' ORDER BY " + orderBy + " DESC LIMIT 1";

                result = crumbdb.query(sql, null);
                if (result.moveToNext()) {
                    mostRecentSegment_id = result.getInt(SEG_COLUMN_ID_INDEX);
                } //else {
                  //Log.d(TAG, "No segments found: " + sql);
                  //}
            } catch (Exception e) {
                Log.w(TAG, "Failed to find any track segments, " + e);
                mostRecentSegment_id = -1;
            } finally {
                if (result != null)
                    result.close();
            }
            return mostRecentSegment_id;
        }
    }

    /**
     * Writes the crumb for a PointMapItem to the database.
     *
     * @param m the point map to create a crumb for
     * @param timestamp the timestamp for the point map item
     * @param prefs the shared preference used to record the next
     *              available track color.
     */
    public void persist(final PointMapItem m,
            final long timestamp, final SharedPreferences prefs) {

        double speed = m.getMetaDouble("Speed", Double.NaN);
        double bearing = VALUE_UNKNOWN;

        final String title = ATAKUtilities.getDisplayName(m);

        if (m instanceof Marker) {
            bearing = ((Marker) m).getTrackHeading();
            if (Double.isNaN(speed))
                speed = ((Marker) m).getTrackSpeed();
        }

        if (Double.isNaN(speed))
            speed = VALUE_UNKNOWN;

        if (Double.isNaN(bearing))
            bearing = VALUE_UNKNOWN;

        persist(m.getPoint(), m.getUID(), title, timestamp, speed, bearing, -1,
                m.getMetaString(GeoPointMetaData.GEOPOINT_SOURCE,
                        GeoPointMetaData.UNKNOWN),
                m.getMetaString(GeoPointMetaData.ALTITUDE_SOURCE,
                        GeoPointMetaData.UNKNOWN),
                prefs, false);
    }

    /**
     * Persist crumb to database
     * If no segment exists for the userUid and prefs are provided, then create a segment
     *
     * @param gp        Crumb point
     * @param userUid   User UID
     * @param userTitle User name
     * @param timestamp UNIX Timestamp in milliseconds
     * @param speed     Speed in crumb in meters/second
     * @param bearing   Bearing in true degrees
     * @param trackId   Track id #
     * @param prefs     Optionally used to create segment if one does not exist for userUid
     * @param immediate perist the crumb immediately
     */
    private void persist(final GeoPoint gp, String userUid,
            String userTitle,
            final long timestamp, final double speed, final double bearing,
            int trackId, String geopointSource, String altitudeSource,
            final SharedPreferences prefs, boolean immediate) {
        try {
            //see if we were provided a track ID
            if (trackId < 0) {
                //just use most recent
                trackId = getCurrentSegmentId(userUid, SEG_COLUMN_TIMESTAMP);
            }

            // auto create a track segment as necessary
            if (trackId < 0 && prefs != null) {
                trackId = createSegment(timestamp, userTitle, userUid, prefs);
            }

            //Log.d(TAG, "creating crumb: " + userUid + ", " + timestamp +
            //    ", with segment id: " + trackId);

            Crumb c = new Crumb(gp, UUID.randomUUID().toString());
            c.setDirection(bearing);
            c.timestamp = timestamp;
            c.speed = (float) speed;
            c.bearing = (float) bearing;
            c.trackDBID = trackId;
            c.setMetaString("tmpgpSource", geopointSource);
            c.setMetaString("tmpaltSource", altitudeSource);
            synchronized (CrumbDatabase.this) {
                if (worker == null) {
                    worker = new Handler();
                    try {
                        pool.execute(this.worker);
                    } catch (Exception e) {
                        Log.d(TAG, "rejected execution");
                    }
                }
                if (immediate) {
                    worker.insert(c);
                } else {
                    crumbsToProcess.add(c);
                    this.notify();
                }
            }

            // Notify listeners
            fireOnCrumbAdded(trackId, c);

        } catch (Exception e) {
            Log.w(TAG, "error occurred saving breadcrumb: " + timestamp, e);
        }
    }

    /**
     * Create new track segment
     *
     * @param timestamp the timestamp for the segment
     * @param userTitle the title
     * @param userUid the user identifier for the track segment
     * @param prefs the preference used to look up the next title or color
     * @return
     */
    private int createSegment(long timestamp, String userTitle, String userUid,
            final SharedPreferences prefs) {
        createSegment(timestamp, BreadcrumbReceiver.getNextColor(prefs),
                BreadcrumbReceiver.getTrackTitle(prefs),
                BreadcrumbReceiver.DEFAULT_LINE_STYLE, userTitle, userUid,
                false);
        return getCurrentSegmentId(userUid, SEG_COLUMN_TIMESTAMP);
    }

    /**
     * Create new track segment with specified timestamp and style
     * If bStitch is true, then set last point for that UID in
     * the previous segment as the first point in this segment
     *
     * @param timestamp the timestamp for the segment
     * @param color     the color of the segment
     * @param title     the title of the segment
     * @param style     how the segment should be styled
     * @param userTitle the title of the segment
     * @param userUid   the user id that represents the segment
     * @param bStitch   true will result in the new segement being joined with the previous segment
     */
    public void createSegment(final long timestamp,
            final int color,
            String title, final String style, String userTitle, String userUid,
            boolean bStitch) {

        if (FileSystemUtils.isEmpty(title) || timestamp < 0) {
            Log.w(TAG, "Unable to create segment w/out title and timestamp");
            return;
        }

        if (FileSystemUtils.isEmpty(userUid)
                || FileSystemUtils.isEmpty(userTitle)) {
            Log.w(TAG, "Unable to create segment w/out user UID and title");
            return;
        }

        //clamp title length
        if (title.length() > MAX_TITLE_LENGTH) {
            Log.d(TAG, "Clamping segment title: " + title);
            title = title.substring(0, MAX_TITLE_LENGTH - 1);
        }

        synchronized (lock) {
            try {
                Log.d(TAG, "creating segment: " + title + ", for " + userUid);
                int previousSegmentId = getCurrentSegmentId(userUid,
                        SEG_COLUMN_TIMESTAMP);

                StatementIface insertStmt = null;
                try {
                    insertStmt = crumbdb
                            .compileStatement(
                                    "INSERT INTO " + SEGMENT_TABLE_NAME +
                                            "(" + SEG_COLUMN_TIMESTAMP + ", " +
                                            SEG_COLUMN_TITLE + ", " +
                                            SEG_COLUMN_STYLE + ", " +
                                            SEG_COLUMN_COLOR + ", " +
                                            SEG_COLUMN_USER_UID + ", " +
                                            SEG_COLUMN_USER_TITLE + ") " +
                                            "VALUES (?, ?, ?, ?, ?, ?)");

                    insertStmt.bind(1, timestamp);
                    insertStmt.bind(2, title);
                    insertStmt.bind(3, style);
                    insertStmt.bind(4, color);
                    insertStmt.bind(5, userUid);
                    insertStmt.bind(6, userTitle);
                    insertStmt.execute();
                } finally {
                    if (insertStmt != null)
                        insertStmt.close();
                }

                if (bStitch && previousSegmentId >= 0) {
                    try {
                        Log.d(TAG,
                                "Stitching new segment with previous segment for UID: "
                                        + userUid);
                        //get last point from previous segment
                        CrumbPoint c = getLastCrumb(previousSegmentId);
                        if (c != null && c.gp.isValid()) {
                            //add last point to new segment
                            persist(c.gp, userUid, userTitle, c.timestamp,
                                    c.speed, c.bearing, -1,
                                    c.gpm.getGeopointSource(),
                                    c.gpm.getAltitudeSource(), null, true);
                        }
                    } catch (SQLiteException e) {
                        Log.w(TAG,
                                "error occurred stitching segment: "
                                        + timestamp,
                                e.getCause());
                    } catch (Exception e) {
                        Log.w(TAG,
                                "error occurred stitching segment: "
                                        + timestamp,
                                e);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "error occurred saving segment: " + timestamp, e);
            }
        }
    }

    /**
     * Given a segment identifier delete the segment
     * @param track_dbid the segment id
     */
    public void deleteSegment(int track_dbid) {
        //remove breadcrumbs
        if (track_dbid < 0) {
            Log.w(TAG, "Unable to delete track w/out trackID");
            return;
        }

        synchronized (lock) {
            try {
                String sql = "DELETE FROM " + BREADCRUMB_TABLE_NAME2 +
                        " WHERE " + COLUMN_SEGMENT_ID + " = " + track_dbid;
                crumbdb.execute(sql, null);

                //remove segment
                sql = "DELETE FROM " + SEGMENT_TABLE_NAME +
                        " WHERE " + SEG_COLUMN_ID + " = " + track_dbid;
                crumbdb.execute(sql, null);
                Log.d(TAG, "Deleted segment: " + track_dbid);
            } catch (Exception e) {
                Log.w(TAG, "Failed to delete track id: " + track_dbid, e);
            }
        }
    }

    /**
     * Get last crumb for specified UID in specified track
     *
     * @param trackDbId Track database ID
     * @return Last crumb point
     */
    public CrumbPoint getLastCrumb(int trackDbId) {
        if (trackDbId < 0) {
            Log.w(TAG, "Unable to get crumbs w/out trackID");
            return null;
        }

        String sql = "SELECT _id, _sid, timestamp, lat, lon, alt, ce, le,"
                + " bearing, speed, ptsource, altsource " +
                " FROM " + BREADCRUMB_TABLE_NAME2 +
                " WHERE " + COLUMN_SEGMENT_ID + " = " + trackDbId +
                " ORDER BY " + COLUMN_TIMESTAMP + " DESC LIMIT 1";

        CursorIface result = null;

        synchronized (lock) {
            try {
                result = crumbdb.query(sql, null);
                if (result.moveToNext())
                    return crumbPointFromCursor(result);
            } finally {
                if (result != null)
                    result.close();
            }
        }
        return null;
    }

    /**
     * NOTE: Each Crumb is a map item and not as memory efficient as CrumbPoint
     *
     * @param trackDbId Track database ID
     * @return List of crumbs
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = false)
    public List<Crumb> getCrumbs(int trackDbId) {
        List<Crumb> crumbs = new ArrayList<>();
        if (trackDbId < 0) {
            Log.w(TAG, "Unable to get crumbs w/out trackID");
            return crumbs;
        }

        String sql = "SELECT _id, _sid, timestamp, lat, lon, alt, ce, le,"
                + " bearing, speed, ptsource, altsource " +
                " FROM " + BREADCRUMB_TABLE_NAME2 +
                " WHERE " + COLUMN_SEGMENT_ID + " = " + trackDbId +
                " ORDER BY " + COLUMN_TIMESTAMP + " ASC";

        CursorIface result = null;
        synchronized (lock) {
            try {
                result = crumbdb.query(sql, null);

                Crumb c;
                while (result.moveToNext()) {
                    c = crumbFromCursor(result);
                    if (c != null)
                        crumbs.add(c);
                }
            } finally {
                if (result != null)
                    result.close();
            }
        }

        return crumbs;
    }

    public List<CrumbPoint> getCrumbPoints(int trackDbId) {
        List<CrumbPoint> points = new ArrayList<>();
        if (trackDbId < 0) {
            Log.w(TAG, "Unable to get crumbs w/out trackID");
            return points;
        }

        String sql = "SELECT _id, _sid, timestamp, lat, lon, alt, ce, le,"
                + " bearing, speed, ptsource, altsource" +
                " FROM " + BREADCRUMB_TABLE_NAME2 +
                " WHERE " + COLUMN_SEGMENT_ID + " = " + trackDbId +
                " ORDER BY " + COLUMN_TIMESTAMP + " ASC";

        CursorIface result = null;
        synchronized (lock) {
            try {
                result = crumbdb.query(sql, null);

                CrumbPoint c;
                while (result.moveToNext()) {
                    c = crumbPointFromCursor(result);
                    if (c.gp.isValid())
                        points.add(c);
                }
            } finally {
                if (result != null)
                    result.close();
            }
        }

        return points;
    }

    public void getCrumbPoints(int trackDbId, TrackPolyline track) {
        if (trackDbId < 0) {
            Log.w(TAG, "Unable to get crumbs w/out trackID");
            return;
        }

        String sql = "SELECT _id, _sid, timestamp, lat, lon, alt, ce, le,"
                + " bearing, speed, ptsource, altsource" +
                " FROM " + BREADCRUMB_TABLE_NAME2 +
                " WHERE " + COLUMN_SEGMENT_ID + " = " + trackDbId +
                " ORDER BY " + COLUMN_TIMESTAMP + " ASC";

        CrumbPoint last = null;
        CursorIface result = null;
        synchronized (lock) {
            try {
                result = crumbdb.query(sql, null);
                CrumbPoint c;
                while (result.moveToNext()) {
                    c = crumbPointFromCursor(result);
                    if (c.gp.isValid()) {
                        track.addPoint(c.gpm, false);
                        last = c;
                    }
                }
            } finally {
                if (result != null)
                    result.close();
            }
        }
        if (last != null)
            track.setMetaLong("lastcrumbtime", last.timestamp);
        track.refreshPoints();
    }

    /**
     * Get all tracks in the specified time range
     *
     * @param uid the identifier for the map item that created the track (ie the user uid)
     * @param startTime the start time for the track
     * @param endTime,  if -1 then search all crumbs starting at startTime
     * @return a list of tracks that meet the criteria
     */
    public List<TrackPolyline> getTracks(String uid,
            long startTime, long endTime, TrackProgress progress) {
        List<TrackPolyline> tracks = new ArrayList<>();
        if (FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "Unable to get crumbs w/out user UID");
            return tracks;
        }

        //TODO this get only crumbs in the time range
        //should we get the entire track if even a single crumb matches?

        String sql = "SELECT " +
                "bc2tab." + COLUMN_ID + "," +
                "bc2tab." + COLUMN_SEGMENT_ID + "," +
                "bc2tab." + COLUMN_TIMESTAMP + "," +
                "bc2tab." + COLUMN_LAT + "," +
                "bc2tab." + COLUMN_LON + "," +
                "bc2tab." + COLUMN_ALT + "," +
                "bc2tab." + COLUMN_CE + "," +
                "bc2tab." + COLUMN_LE + "," +
                "bc2tab." + COLUMN_BEARING + "," +
                "bc2tab." + COLUMN_SPEED + "," +
                "bc2tab." + COLUMN_POINT_SOURCE + "," +
                "bc2tab." + COLUMN_ALTITUDE_SOURCE +
                " FROM " + BREADCRUMB_TABLE_NAME2 + " bc2tab" +
                " INNER JOIN " + SEGMENT_TABLE_NAME
                + " segtab ON bc2tab._sid=segtab._id" +
                " WHERE " + "segtab." + SEG_COLUMN_USER_UID + " = '" + uid +
                "' AND " + "bc2tab." + COLUMN_TIMESTAMP + " >= " + startTime;

        //open ended query if no end time is provided
        if (endTime > 0) {
            sql += " AND " + "bc2tab." + COLUMN_TIMESTAMP + " <= " + endTime;
        }

        //sort by timestamp
        sql += " ORDER BY " + "bc2tab." + COLUMN_TIMESTAMP + " ASC";

        Log.d(TAG, "getCrumbs: " + sql);

        //map crumbs to track ID
        SparseArray<List<CrumbPoint>> crumbMap = new SparseArray<>();
        CursorIface result = null;
        synchronized (lock) {
            try {
                //get all crumbs in timerange for this UID
                result = crumbdb.query(sql, null);

                //loop all crumbs
                CrumbPoint c;
                while (result.moveToNext()) {
                    if (progress != null && progress.cancelled()) {
                        Log.w(TAG, "Cancelled search query");
                        return tracks;
                    }
                    c = crumbPointFromCursor(result);
                    //store crumbs by track/segment ID
                    int trackIdForCrumb = result
                            .getInt(COLUMN_SEGMENT_ID_INDEX);
                    if (trackIdForCrumb < 0) {
                        Log.w(TAG, "No track id for crumb");
                        continue;
                    }

                    List<CrumbPoint> crumbs = crumbMap.get(trackIdForCrumb);
                    if (crumbs == null) {
                        crumbs = new ArrayList<>();
                        crumbMap.put(trackIdForCrumb, crumbs);
                    }
                    crumbs.add(c);
                }
            } finally {
                if (result != null)
                    result.close();
            }
        }

        for (int i = 0; i < crumbMap.size(); i++) {
            int trackDBID = crumbMap.keyAt(i);
            List<CrumbPoint> crumbs = crumbMap.valueAt(i);
            if (progress != null && progress.cancelled())
                break;
            TrackPolyline track = getTrack(trackDBID, crumbs);
            if (track == null) {
                Log.w(TAG, "No track found for id: " + trackDBID);
                continue;
            }

            Log.d(TAG, "Adding track: " + trackDBID);
            tracks.add(track);
        }

        return tracks;
    }

    public List<TrackPolyline> getTracks(String uid,
            long startTime, long endTime) {
        return getTracks(uid, startTime, endTime, null);
    }

    /**
     * Get all crumbs in the specified time range
     *
     * @param uid the user identifier for the crumbs.
     * @param startTime the start time
     * @param endTime,  if -1 then search all crumbs starting at startTime
     * @return the list of crumns that are in that time range
     */
    public List<Crumb> getCrumbs(String uid, long startTime, long endTime) {
        List<Crumb> crumbs = new ArrayList<>();
        if (FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "Unable to get crumbs w/out user UID");
            return crumbs;
        }

        String sql = "SELECT " +
                "bc2tab." + COLUMN_ID + "," +
                "bc2tab." + COLUMN_SEGMENT_ID + "," +
                "bc2tab." + COLUMN_TIMESTAMP + "," +
                "bc2tab." + COLUMN_LAT + "," +
                "bc2tab." + COLUMN_LON + "," +
                "bc2tab." + COLUMN_ALT + "," +
                "bc2tab." + COLUMN_CE + "," +
                "bc2tab." + COLUMN_LE + "," +
                "bc2tab." + COLUMN_BEARING + "," +
                "bc2tab." + COLUMN_SPEED + "," +
                "bc2tab." + COLUMN_POINT_SOURCE + "," +
                "bc2tab." + COLUMN_ALTITUDE_SOURCE +
                " FROM " + BREADCRUMB_TABLE_NAME2 + " bc2tab" +
                " INNER JOIN " + SEGMENT_TABLE_NAME
                + " segtab ON bc2tab._sid=segtab._id" +
                " WHERE " + "segtab." + SEG_COLUMN_USER_UID + " = '" + uid +
                "' AND " + "bc2tab." + COLUMN_TIMESTAMP + " >= " + startTime;

        //open ended query if no end time is provided
        if (endTime > 0) {
            sql += " AND " + "bc2tab." + COLUMN_TIMESTAMP + " <= " + endTime;
        }

        //sort by timestamp
        sql += " ORDER BY " + "bc2tab." + COLUMN_TIMESTAMP + " ASC";

        Log.d(TAG, "getCrumbs: " + sql);

        CursorIface result = null;
        synchronized (lock) {
            try {
                result = crumbdb.query(sql, null);

                Crumb c;
                while (result.moveToNext()) {
                    c = crumbFromCursor(result);
                    if (c != null)
                        crumbs.add(c);
                }
            } finally {
                if (result != null)
                    result.close();
            }
        }

        return crumbs;
    }

    /**
     * Create Crumb from database entry
     *
     * @param cursor Crumb cursor
     * @return New breadcrumb map item
     */
    private Crumb crumbFromCursor(CursorIface cursor) {
        if (cursor == null) {
            Log.w(TAG, "Cannot create crumb w/out cursor");
            return null;
        }

        GeoPoint gp = pointFromCursor(cursor);
        if (!gp.isValid()) {
            Log.w(TAG, "Cannot create crumb w/invalid GeoPoint");
            return null;
        }

        Crumb c = new Crumb(gp, UUID.randomUUID().toString());
        c.setDirection(cursor.getDouble(COLUMN_BEARING_INDEX));
        c.timestamp = cursor.getLong(COLUMN_TIMESTAMP_INDEX);
        c.trackDBID = cursor.getInt(COLUMN_SEGMENT_ID_INDEX);
        c.crumbDBID = cursor.getInt(COLUMN_ID_INDEX);
        c.speed = (float) cursor.getDouble(COLUMN_SPEED_INDEX);
        c.bearing = (float) cursor.getDouble(COLUMN_BEARING_INDEX);

        //Log.d(TAG, "crumbFromCursor: " + c.toString());
        return c;
    }

    /**
     * Create point from cursor
     *
     * @param cursor Crumb point cursor
     * @return Geo point
     */
    private GeoPoint pointFromCursor(CursorIface cursor) {
        if (cursor == null) {
            Log.w(TAG, "Cannot create crumb w/out cursor");
            return null;
        }
        return new GeoPoint(cursor.getDouble(COLUMN_LAT_INDEX),
                cursor.getDouble(COLUMN_LON_INDEX),
                cursor.getDouble(COLUMN_ALT_INDEX),
                AltitudeReference.HAE,
                cursor.getDouble(COLUMN_CE_INDEX),
                cursor.getDouble(COLUMN_LE_INDEX));
    }

    /**
     * Create crumb point from database entry
     *
     * @param cursor Crumb point cursor
     * @return Crumb point
     */
    private CrumbPoint crumbPointFromCursor(CursorIface cursor) {
        if (cursor == null) {
            Log.w(TAG, "Cannot create crumb w/out cursor");
            return null;
        }

        return new CrumbPoint(cursor.getDouble(COLUMN_LAT_INDEX),
                cursor.getDouble(COLUMN_LON_INDEX),
                cursor.getDouble(COLUMN_ALT_INDEX),
                cursor.getDouble(COLUMN_CE_INDEX),
                cursor.getDouble(COLUMN_LE_INDEX),
                (float) cursor.getDouble(COLUMN_SPEED_INDEX),
                (float) cursor.getDouble(COLUMN_BEARING_INDEX),
                cursor.getLong(COLUMN_TIMESTAMP_INDEX),
                cursor.getString(COLUMN_INDEX),
                cursor.getString(COLUMN_POINT_SOURCE_INDEX));
    }

    /**
     * Create a polyline from the specified parameters
     * Does not access database
     *
     * @param callsign  Track callsign
     * @param uid       Track UID
     * @param timestamp Track start time
     * @param crumbs    List of crumbs
     * @return New track polyline
     */
    public TrackPolyline getTrack(String callsign, String uid, long timestamp,
            List<Crumb> crumbs) {
        if (FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "Cannot create track w/out uid");
            return null;
        }

        TrackPolyline p = new TrackPolyline(timestamp);
        p.setStrokeColor(BreadcrumbReceiver.DEFAULT_LINE_COLOR);
        TrackDetails.setBasicStyle(p, BreadcrumbReceiver.DEFAULT_LINE_STYLE);
        p.setTitle(callsign + " Track");
        p.setMetaInteger(META_TRACK_DBID, -1);
        p.setMetaString(CrumbDatabase.META_TRACK_NODE_UID, uid);
        p.setMetaString(CrumbDatabase.META_TRACK_NODE_TITLE, callsign);

        if (!FileSystemUtils.isEmpty(crumbs)) {
            //populate track points based on crumbs
            Crumb lastCrumb = null;
            List<GeoPoint> points = new ArrayList<>();
            for (Crumb c : crumbs) {
                GeoPoint gp = c.getPoint();
                if (gp == null || !gp.isValid()) {
                    Log.w(TAG, "Ignoring invalid crumb point: " + c);
                    continue;
                }

                points.add(gp);
                lastCrumb = c;
            } //end crumb loop

            if (lastCrumb != null) {
                //attempt to set an end time based on queried crumbs
                p.setMetaLong("lastcrumbtime", lastCrumb.timestamp);
            }

            p.setPoints(points.toArray(new GeoPoint[0]));
        }

        Log.d(TAG,
                "Created track Polyline for track: "
                        + p.getMetaString("title", null) + ", " + timestamp);
        return p;
    }

    /**
     * Pull track metadata from DB and use 'crumbs' for the track points
     *
     * @param trackDbId Track database ID
     * @param points    Crumb points
     * @return the track polyline based on a list of points and a track id
     */
    public TrackPolyline getTrack(int trackDbId, List<CrumbPoint> points) {
        if (trackDbId < 0) {
            Log.w(TAG, "Cannot get track for id: " + trackDbId);
            return null;
        }

        //select all segments, sort by timestamp
        String sql = "SELECT * FROM " + SEGMENT_TABLE_NAME +
                " WHERE " + SEG_COLUMN_ID + "=" + trackDbId;

        Log.d(TAG, "getTrack: " + sql);

        TrackPolyline track = null;
        CursorIface result = null;
        synchronized (lock) {
            try {
                result = crumbdb.query(sql, null);

                if (result.moveToNext()) {
                    track = trackFromCursor(result, -1);
                }
            } finally {
                if (result != null)
                    result.close();
            }
        }

        if (track == null) {
            Log.w(TAG,
                    "Cannot create tracks w/out tracks for id: " + trackDbId);
            return null;
        }

        if (FileSystemUtils.isEmpty(points)) {
            Log.w(TAG,
                    "Cannot create tracks w/out crumbs for id: " + trackDbId);
            return null;
        }

        //populate track points based on crumbs
        CrumbPoint lastCrumb = points.get(points.size() - 1);
        if (lastCrumb != null) {
            //attempt to set an end time based on queried crumbs
            track.setMetaLong("lastcrumbtime", lastCrumb.timestamp);
        }

        GeoPointMetaData[] pts = new GeoPointMetaData[points.size()];
        for (int i = 0; i < points.size(); ++i)
            pts[i] = points.get(i).gpm;
        track.setPoints(pts);
        Log.d(TAG, "Found " + points.size() + " crumbs for track " + trackDbId);
        return track;
    }

    public TrackPolyline getTrack(int trackDbId, boolean bPoints) {
        if (trackDbId < 0) {
            Log.w(TAG, "Cannot get track for id: " + trackDbId);
            return null;
        }

        //select all segments, sort by timestamp
        String sql = "SELECT * FROM " + SEGMENT_TABLE_NAME +
                " WHERE " + SEG_COLUMN_ID + "=" + trackDbId;

        Log.d(TAG, "getTrack: " + sql);

        TrackPolyline track = null;
        CursorIface result = null;
        synchronized (lock) {
            try {
                result = crumbdb.query(sql, null);

                if (result.moveToNext()) {
                    track = trackFromCursor(result, -1);
                }
            } finally {
                if (result != null)
                    result.close();
            }
        }

        if (track == null) {
            Log.w(TAG,
                    "Cannot create tracks w/out tracks for id: " + trackDbId);
            return null;
        }

        if (!bPoints) {
            Log.d(TAG, "Skipping points for track: " + trackDbId);
            return track;
        }

        //get crumbs for this track
        getCrumbPoints(trackDbId, track);
        Log.d(TAG, "Found " + track.getNumPoints() + " crumbs for track "
                + trackDbId);
        return track;
    }

    /**
     * Get first track matching the callsign & title
     *
     * @param callsign the title (callsign) for the track
     * @param uid the uid for the for the track
     * @return the Track Polyline that matches the title and uid
     */
    public TrackPolyline getTrackByCallsign(String callsign, String uid) {
        String sql = "SELECT * FROM " + SEGMENT_TABLE_NAME +
                " WHERE " + SEG_COLUMN_USER_TITLE + "= ?  AND "
                + SEG_COLUMN_USER_UID + "= ? LIMIT 1";

        Log.d(TAG, "getTrack: " + sql);

        TrackPolyline track = null;
        CursorIface result = null;
        synchronized (lock) {
            try {
                int currentTrackId = getCurrentSegmentId(uid,
                        SEG_COLUMN_TIMESTAMP);

                result = crumbdb.query(sql, new String[] {
                        callsign, uid
                });

                if (result.moveToNext()) {
                    track = trackFromCursor(result, currentTrackId);
                }
            } finally {
                if (result != null)
                    result.close();
            }
        }

        if (track == null) {
            Log.w(TAG, "Cannot create tracks w/out tracks for uid: " + uid);
            return null;
        }

        return track;
    }

    /**
     * Get first track matching the uid & title
     *
     * @param title the segment title
     * @param uid the uid for the user segment
     * @return the track representing the title and uid pair
     */
    public TrackPolyline getTrackByTrackTitle(final String title,
            final String uid) {
        String sql = "SELECT * FROM " + SEGMENT_TABLE_NAME +
                " WHERE " + SEG_COLUMN_TITLE + "= ? AND " + SEG_COLUMN_USER_UID
                + "= ? LIMIT 1";

        Log.d(TAG, "getTrack: " + sql);

        TrackPolyline track = null;
        CursorIface result = null;
        synchronized (lock) {
            try {
                int currentTrackId = getCurrentSegmentId(uid,
                        SEG_COLUMN_TIMESTAMP);

                result = crumbdb.query(sql, new String[] {
                        title, uid
                });

                if (result.moveToNext()) {
                    track = trackFromCursor(result, currentTrackId);
                }
            } finally {
                if (result != null)
                    result.close();
            }
        }

        if (track == null) {
            Log.w(TAG, "Cannot create tracks w/out tracks for uid: " + uid);
            return null;
        }

        return track;
    }

    /**
     * Returns the most recent track for a given user identifer
     * @param uid the uid
     * @return the track
     */
    public TrackPolyline getMostRecentTrack(String uid) {
        return getTrack(getCurrentSegmentId(uid, SEG_COLUMN_TIMESTAMP), true);
    }

    /**
     * Get tracks for the specified user UID
     * If bDisplayAll, then get all tracks, else
     * Get first 15 tracks, optionally skipping "temporary" tracks
     *
     * @param uid the user uid
     * @param bHideTemp   If true, omit tracks shorter than 10 minutes and did not move more than 100 meters
     * @param bDisplayAll If true, include all tracks, otherwise include first 15 tracks
     * @param progress the progress callback for long running operatations.
     * @return the list of track polylines that match
     */
    public List<TrackPolyline> getTracks(final String uid,
            boolean bHideTemp, boolean bDisplayAll, TrackProgress progress) {
        //query all segments, mapped by ID. Note Polyline point lists are currently immutable so we
        //also track list of points here
        List<TrackPolyline> tracks = new ArrayList<>();

        // query all tracks from the segments table, for this UID, build out metadata
        //select all segments, sort by timestamp
        CursorIface result = null;
        synchronized (lock) {
            try {
                int currentTrackId = getCurrentSegmentId(uid,
                        SEG_COLUMN_TIMESTAMP);

                //now get list of segments
                String sql = "SELECT * FROM " + SEGMENT_TABLE_NAME +
                        " WHERE " + SEG_COLUMN_USER_UID + "= ? ORDER BY "
                        + SEG_COLUMN_TIMESTAMP + " DESC";
                Log.d(TAG, "getTracks: " + sql);

                result = crumbdb.query(sql, new String[] {
                        uid
                });
                if (progress != null)
                    progress.onProgress(5);

                TrackPolyline track;
                while (result.moveToNext()) {
                    track = trackFromCursor(result, currentTrackId);
                    if (track != null) {
                        tracks.add(track);
                    }
                }
                if (progress != null)
                    progress.onProgress(10);
            } catch (Exception e) {
                Log.e(TAG, "Failed to query tracks for user: " + uid, e);
                return null;
            } finally {
                if (result != null)
                    result.close();
            }
        }

        if (tracks.size() < 1) {
            Log.w(TAG, "Cannot create tracks w/out tracks for uid: " + uid);
            return null;
        }

        List<TrackPolyline> toReturn = new ArrayList<>();
        if (bDisplayAll)
            Log.d(TAG, "Getting all tracks for uid: " + uid);

        //now get crumbs for each track
        double percentPerTrack = bDisplayAll ? 90D / ((double) tracks.size())
                : 90D / ((double) DEFAULT_NUMBER_TRACKS);
        Log.d(TAG, "Querying crumbs for " + tracks.size() + " tracks");
        int totalCrumbs = 0;
        for (int i = -0; i < tracks.size(); i++) {
            if (progress != null && progress.cancelled())
                break;
            TrackPolyline track = tracks.get(i);
            int trackId = track.getMetaInteger(CrumbDatabase.META_TRACK_DBID,
                    -1);
            getCrumbPoints(trackId, track);
            totalCrumbs += track.getNumPoints();

            if (bDisplayAll) {
                toReturn.add(track);
                if (progress != null)
                    progress.onProgress((int) (10 + Math.round(toReturn.size()
                            * percentPerTrack)));
            } else {
                long elaspedTime = TrackDetails.getTimeElapsedLong(track);
                double distance = track.getTotalDistance();
                if (bHideTemp && elaspedTime < TEMP_TRACK_THRESHOLD_MILLIS
                        && distance < TEMP_TRACK_THRESHOLD_METERS) {
                    Log.d(TAG, "Skipping temp track: " + trackId);
                    continue;
                } else {
                    Log.d(TAG, "Adding track: " + trackId);
                    toReturn.add(track);
                    if (progress != null)
                        progress.onProgress((int) (10 + Math.round(toReturn
                                .size() * percentPerTrack)));
                }

                if (toReturn.size() >= DEFAULT_NUMBER_TRACKS) {
                    Log.d(TAG, "Hit max number of tracks: "
                            + DEFAULT_NUMBER_TRACKS);
                    break;
                }
            }
        } //end track loop

        Log.d(TAG, "Returning " + toReturn.size() + " tracks with "
                + totalCrumbs + " crumbs");
        return toReturn;
    }

    /**
     * Get tracks for the specified track IDs
     *
     * @param trackIds the list of track id's
     * @param progress the progress callback for getting the tracks.
     * @return a list of tracks for a given set of track ids.
     */
    public List<TrackPolyline> getTracks(final int[] trackIds,
            TrackProgress progress) {
        if (trackIds == null || trackIds.length < 1) {
            Log.w(TAG, "Cannot create tracks w/out track IDs");
            return null;
        }

        List<TrackPolyline> toReturn = new ArrayList<>();

        //now get crumbs for each track
        double percentPerTrack = 100D / (double) trackIds.length;
        Log.d(TAG, "Querying " + trackIds.length + " tracks");
        for (int i = -0; i < trackIds.length; i++) {
            if (progress != null) {
                if (progress.cancelled())
                    break;
                progress.onProgress((int) (Math.round(i * percentPerTrack)));
            }

            TrackPolyline track = getTrack(trackIds[i], true);
            if (track == null) {
                Log.w(TAG, "Cannot create track for id: " + trackIds[i]);
                continue;
            }

            Log.d(TAG, "Adding track: " + trackIds[i]);
            toReturn.add(track);
        } //end track loop

        Log.d(TAG, "Returning " + toReturn.size() + " tracks");
        return toReturn;
    }

    /**
     * Get list of users which have tracks in the local database
     *
     * @return the list of users that have recorded tracks locally stored
     */
    public List<TrackUser> getUserList() {
        //query all segments
        List<TrackUser> trackUsers = new ArrayList<>();

        // query all tracks from the segments table, for this UID, build out metadata
        //select all segments, sort by timestamp
        String sql = "SELECT * FROM " + SEGMENT_TABLE_NAME;
        Log.d(TAG, "getUserlist: " + sql);

        //group tracks by user UID
        CursorIface result = null;
        synchronized (lock) {
            try {
                result = crumbdb.query(sql, null);

                TrackUser trackUser;
                while (result.moveToNext()) {
                    trackUser = trackUserFromCursor(result);
                    if (trackUser != null) {
                        int index = trackUsers.indexOf(trackUser);
                        if (index < 0) {
                            //Log.d(TAG, "Adding track user: " + trackUser.toString());
                            trackUsers.add(trackUser);
                        } else {
                            int numTracks = trackUsers.get(index).increment();
                            //Log.d(TAG, "Incrementing track user: " + trackUser.toString());
                        }
                    }
                }
            } finally {
                if (result != null)
                    result.close();
            }
        }
        if (trackUsers.size() < 1) {
            Log.w(TAG, "Cannot create track users w/out segments");
            return null;
        }

        Log.d(TAG, "Found " + trackUsers.size() + " track users");
        return trackUsers;
    }

    /**
     * Create Polyline from database entry (metadata, not actual track points)
     *
     * @param cursor
     * @param mostRecentTrackForUID of the most recent track on this device, for the owner of the track
     *                              being created
     * @return a track polyline from the database cursor
     */
    private TrackPolyline trackFromCursor(CursorIface cursor,
            int mostRecentTrackForUID) {
        if (cursor == null) {
            Log.w(TAG, "Cannot create track w/out cursor");
            return null;
        }

        //use timestamp as UID
        long timestamp = cursor.getLong(SEG_COLUMN_TIMESTAMP_INDEX);

        TrackPolyline p = new TrackPolyline(timestamp);
        p.setTitle(cursor.getString(SEG_COLUMN_TITLE_INDEX));
        p.setStrokeColor(cursor.getInt(SEG_COLUMN_COLOR_INDEX));
        TrackDetails.setBasicStyle(p, cursor.getString(SEG_COLUMN_STYLE_INDEX));

        //set track owner info
        p.setMetaString(CrumbDatabase.META_TRACK_NODE_UID,
                cursor.getString(SEG_COLUMN_USER_UID_INDEX));
        p.setMetaString(CrumbDatabase.META_TRACK_NODE_TITLE,
                cursor.getString(SEG_COLUMN_USER_TITLE_INDEX));
        if (mostRecentTrackForUID <= 0) {
            //we were not given the current track ID for this User, let's try to look
            //it up from the user UID stored for this track
            String tempUid = p.getMetaString(CrumbDatabase.META_TRACK_NODE_UID,
                    null);
            if (!FileSystemUtils.isEmpty(tempUid)) {
                mostRecentTrackForUID = getCurrentSegmentId(tempUid,
                        SEG_COLUMN_TIMESTAMP);
            }
        }
        p.setMetaInteger(META_TRACK_DBID, cursor.getInt(SEG_COLUMN_ID_INDEX));
        //set flag if this the current track for the user/UID
        p.setMetaBoolean(
                META_TRACK_CURRENT,
                mostRecentTrackForUID >= 0
                        && (p.getMetaInteger(META_TRACK_DBID,
                                -1) == mostRecentTrackForUID));

        //Log.d(TAG, "Created track Polyline for track: " + p.getMetaString("title", null) + ", " + p.getMetaInteger(META_TRACK_DBID, -1));
        return p;
    }

    /**
     * Create TrackUser from database entry/segment table row (metadata about
     * user who created the track)
     *
     * @param cursor the cursor representing a track user
     * @return the track user object
     */
    private TrackUser trackUserFromCursor(CursorIface cursor) {
        if (cursor == null) {
            Log.w(TAG, "Cannot create track user w/out cursor");
            return null;
        }

        return new TrackUser(
                cursor.getString(SEG_COLUMN_USER_TITLE_INDEX),
                cursor.getString(SEG_COLUMN_USER_UID_INDEX),
                1);
    }

    public void setTrackName(int track_dbid, final String name) {
        if (FileSystemUtils.isEmpty(name) || track_dbid < 0) {
            Log.w(TAG, "Unable to set track name w/out name and trackID");
            return;
        }

        synchronized (lock) {
            try {
                String sql = "UPDATE " + SEGMENT_TABLE_NAME + " SET "
                        + SEG_COLUMN_TITLE + "= ? WHERE " + SEG_COLUMN_ID + "="
                        + track_dbid;

                crumbdb.execute(sql, new String[] {
                        name
                });
            } catch (Exception e) {
                Log.w(TAG, "Failed to set name: " + name + ", for track id: "
                        + track_dbid, e);
            }
        }
    }

    public void setTrackColor(int track_dbid, String color) {
        if (track_dbid < 0) {
            Log.w(TAG, "Unable to set track color w/out trackID");
            return;
        }

        synchronized (lock) {
            try {
                String sql = "UPDATE " + SEGMENT_TABLE_NAME + " SET "
                        + SEG_COLUMN_COLOR + "= ? WHERE " + SEG_COLUMN_ID + "="
                        + track_dbid;

                crumbdb.execute(sql, new String[] {
                        color
                });
            } catch (Exception e) {
                Log.w(TAG, "Failed to set color: " + color + ", for track id: "
                        + track_dbid, e);
            }
        }
    }

    public void setTrackStyle(int track_dbid, String style) {
        if (FileSystemUtils.isEmpty(style) || track_dbid < 0) {
            Log.w(TAG, "Unable to set track style w/out style and trackID");
            return;
        }

        synchronized (lock) {
            try {
                String sql = "UPDATE " + SEGMENT_TABLE_NAME + " SET "
                        + SEG_COLUMN_STYLE + "= ? WHERE " + SEG_COLUMN_ID + "="
                        + track_dbid;

                crumbdb.execute(sql, new String[] {
                        style
                });
            } catch (Exception e) {
                Log.w(TAG, "Failed to set style: " + style + ", for track id: "
                        + track_dbid, e);
            }
        }
    }

    /**
     * We store server queried tracks once for a given uid/timestamp, in local DB. If a future
     * server query is returned with the same start time, it will be replaced in the local DB
     * with the updated track (which may be shorter or longer depending on user's search terms).
     * Like all tracks in the local DB, data is erased on demand by user, or automatically 30 days
     * from the date of the track (not 30 days from the date of DB insertion)
     *
     * @param callsign
     * @param uid
     * @param startTime
     * @param track
     * @return
     */
    public int setServerTrack(String callsign, String uid, long startTime,
            Track track,
            SharedPreferences prefs) {
        if (FileSystemUtils.isEmpty(callsign) || FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "Cannot set server track without callsign/uid");
            return -1;
        }

        if (startTime < 0 || track == null) {
            Log.w(TAG, "Cannot set server track without track");
            return -1;
        }

        //this is title convention for tracks queried from server
        String segTitle = "Server "
                + callsign
                + " "
                + KMLUtil.KMLDateTimeFormatter.get().format(startTime)
                        .replace(':', '-');
        //clamp title length
        if (segTitle.length() > MAX_TITLE_LENGTH) {
            Log.d(TAG, "Clamping segment title: " + segTitle);
            segTitle = segTitle.substring(0, MAX_TITLE_LENGTH - 1);
        }

        //search DB for an existing segment with that title & uid
        TrackPolyline dbTrack = getTrackByTrackTitle(segTitle, uid);
        int trackId;
        synchronized (lock) {
            try {
                crumbdb.beginTransaction();
                if (dbTrack != null) {
                    //if exists delete it and all associated crumbs
                    trackId = dbTrack.getMetaInteger(META_TRACK_DBID, -1);
                    if (trackId >= 0) {
                        Log.d(TAG,
                                "Deleting previous server track with segment id: "
                                        + trackId);

                        String sql = "DELETE FROM " + BREADCRUMB_TABLE_NAME2 +
                                " WHERE " + COLUMN_SEGMENT_ID + "=" + trackId;
                        crumbdb.execute(sql, null);

                        sql = "DELETE FROM " + SEGMENT_TABLE_NAME +
                                " WHERE " + SEG_COLUMN_ID + "=" + trackId;
                        crumbdb.execute(sql, null);
                    }
                } else {
                    Log.d(TAG,
                            "No previous server tracks to delete for: " + uid);
                }

                //wrap new segment with title & uid
                createSegment(startTime, BreadcrumbReceiver.getNextColor(prefs),
                        segTitle,
                        BreadcrumbReceiver.DEFAULT_LINE_STYLE, callsign, uid,
                        false);
                trackId = getCurrentSegmentId(uid, SEG_COLUMN_ID);
                if (trackId < 0) {
                    Log.w(TAG, "Failed to create server track");
                    return -1;
                }

                //insert all breadcrumbs based on KML
                List<String> when = track.getWhen();
                List<String> coord = track.getCoord();
                List<String> angles = track.getAngles();

                //extract Extended if present
                List<String> speed = null;
                List<String> ce = null;
                List<String> le = null;
                if (track.getExtendedData() != null
                        && !FileSystemUtils.isEmpty(track.getExtendedData()
                                .getSchemaDataList())) {
                    for (SchemaData sd : track.getExtendedData()
                            .getSchemaDataList()) {
                        if (sd != null
                                && !FileSystemUtils.isEmpty(sd
                                        .getSchemaDataExtension())) {
                            Log.d(TAG,
                                    "Processing SchemaData: "
                                            + sd.getSchemaUrl());
                            for (Object sde : sd.getSchemaDataExtension()) {
                                if (sde instanceof SimpleArrayData) {
                                    SimpleArrayData sad = (SimpleArrayData) sde;
                                    if (FileSystemUtils.isEmpty(speed)
                                            && FileSystemUtils.isEquals("speed",
                                                    sad.getName())) {
                                        //Log.d(TAG, "Processing track speed");
                                        speed = sad.getValue();
                                    } else if (FileSystemUtils.isEmpty(ce)
                                            && FileSystemUtils.isEquals("ce",
                                                    sad.getName())) {
                                        //Log.d(TAG, "Processing track ce error");
                                        ce = sad.getValue();
                                    } else if (FileSystemUtils.isEmpty(le)
                                            && FileSystemUtils.isEquals("le",
                                                    sad.getName())) {
                                        //Log.d(TAG, "Processing track le error");
                                        le = sad.getValue();
                                    }
                                } else {
                                    if (sde != null)
                                        Log.d(TAG,
                                                "Skipping non SimpleArrayData: "
                                                        + sde.getClass()
                                                                .getName());
                                }
                            }
                        }

                        if (sd != null
                                && !FileSystemUtils
                                        .isEmpty(sd.getSimpleDataList())) {
                            for (SimpleData sde : sd.getSimpleDataList()) {
                                if (sde != null) {
                                    //currently not using SimpleData
                                    //Log.d(TAG, "Ignoring SimpleData: " + sde.getName() + "=" + sde.getValue());
                                }
                            }
                        }
                    }
                } //end extended data

                if (FileSystemUtils.isEmpty(when)
                        || FileSystemUtils.isEmpty(coord)) {
                    Log.d(TAG, "No Track details found");
                    return -1;
                }

                if (when.size() != coord.size()) {
                    Log.d(TAG, "Track size mismatch");
                    return -1;
                }

                Log.d(TAG, "Processing track point count: " + when.size());
                Date date;
                for (int i = 0; i < when.size(); i++) {
                    //TODO try/catch per iteration?
                    date = KMLUtil.parseKMLDate(when.get(i));
                    if (date == null) {
                        Log.w(TAG, "Skipping invalid track time: " + when.get(i)
                                + " at index " + i);
                        continue;
                    }
                    long timestamp = date.getTime();

                    double curCe = VALUE_UNKNOWN;
                    if (!FileSystemUtils.isEmpty(ce) && ce.size() > i) {
                        try {
                            if (FileSystemUtils.isEmpty(ce.get(i))) {
                                //Log.d(TAG, "Skipping empty curCe");
                            } else {
                                curCe = Double.parseDouble(ce.get(i));
                                //Log.d(TAG, "Parsed curCe: " + curCe);
                            }
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Failed to parse ce: " + ce.get(i), e);
                            curCe = VALUE_UNKNOWN;
                        }
                    }

                    double curLe = VALUE_UNKNOWN;
                    if (!FileSystemUtils.isEmpty(le) && le.size() > i) {
                        try {
                            if (FileSystemUtils.isEmpty(le.get(i))) {
                                //Log.d(TAG, "Skipping empty curLe");
                            } else {
                                curLe = Double.parseDouble(le.get(i));
                                //Log.d(TAG, "Parsed curLe: " + curLe);
                            }
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Failed to parse le: " + le.get(i), e);
                            curLe = VALUE_UNKNOWN;
                        }
                    }

                    GeoPointMetaData geoPoint = KMLUtil.parseKMLCoord(
                            coord.get(i),
                            curCe,
                            curLe);
                    if (geoPoint == null) {
                        Log.w(TAG,
                                "Skipping invalid track point: " + coord.get(i)
                                        + " at index " + i);
                        continue;
                    }

                    double bearing = VALUE_UNKNOWN;
                    if (!FileSystemUtils.isEmpty(angles) && angles.size() > i) {
                        try {
                            if (FileSystemUtils.isEmpty(angles.get(i))) {
                                //Log.d(TAG, "Skipping empty bearing");
                            } else {
                                bearing = Double.parseDouble(angles.get(i));
                                //Log.d(TAG, "Parsed bearing: " + bearing);
                            }
                        } catch (NumberFormatException e) {
                            Log.w(TAG,
                                    "Failed to parse angle: " + angles.get(i),
                                    e);
                            bearing = VALUE_UNKNOWN;
                        }
                    }

                    double curSpeed = VALUE_UNKNOWN;
                    if (!FileSystemUtils.isEmpty(speed) && speed.size() > i) {
                        try {
                            if (FileSystemUtils.isEmpty(speed.get(i))) {
                                //Log.d(TAG, "Skipping empty curSpeed");
                            } else {
                                curSpeed = Double.parseDouble(speed.get(i));
                                //Log.d(TAG, "Parsed curSpeed: " + curSpeed);
                            }
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Failed to parse speed: " + speed.get(i),
                                    e);
                            curSpeed = VALUE_UNKNOWN;
                        }
                    }

                    //TODO any other error checking? NaN?
                    persist(geoPoint.get(), uid, callsign, timestamp, curSpeed,
                            bearing,
                            trackId, geoPoint.getGeopointSource(),
                            geoPoint.getAltitudeSource(), null, true);
                } //end gx:track point list

                //end transaction & return trackDB id
                crumbdb.setTransactionSuccessful();
                return trackId;
            } catch (Exception e) {
                Log.e(TAG, "Failed to setServerTrack", e);
            } finally {
                crumbdb.endTransaction();
            }
        }

        return -1;
    }

    public void addCrumbListener(OnCrumbListener l) {
        if (!_listeners.contains(l))
            _listeners.add(l);
    }

    public void removeCrumbListener(OnCrumbListener l) {

        _listeners.remove(l);

    }

    private void fireOnCrumbAdded(final int trackId, final Crumb c) {
        for (OnCrumbListener l : _listeners)
            l.onCrumbAdded(trackId, c);

    }

    class Handler implements Runnable {

        private final Set<Crumb> localCrumbsToProcess = new HashSet<>();
        private boolean endTransactionError = false;
        private StatementIface insertStmt = null;

        Handler() {
            insertStmt = crumbdb.compileStatement(
                    "INSERT INTO " + BREADCRUMB_TABLE_NAME2 +
                            "(" + COLUMN_SEGMENT_ID + ", " +
                            COLUMN_TIMESTAMP + ", " +
                            COLUMN_LAT + ", " +
                            COLUMN_LON + ", " +
                            COLUMN_ALT + ", " +
                            COLUMN_CE + ", " +
                            COLUMN_LE + ", " +
                            COLUMN_BEARING + ", " +
                            COLUMN_SPEED + ", " +
                            COLUMN_POINT_SOURCE + ", " +
                            COLUMN_ALTITUDE_SOURCE + ", " +
                            COLUMN_POINT_GEOM + ") " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, MakePoint(?,?,4326))");

        }

        @Override
        public void run() {

            while (!endTransactionError) {
                try {
                    // batch process the crumbs for efficiency
                    Thread.sleep(500);
                } catch (Exception ignored) {
                }

                synchronized (CrumbDatabase.this) {
                    if (crumbsToProcess.size() < 1) {
                        try {
                            CrumbDatabase.this.wait();
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }
                    localCrumbsToProcess
                            .addAll(CrumbDatabase.this.crumbsToProcess);
                    CrumbDatabase.this.crumbsToProcess.clear();
                }

                //long start = SystemClock.elapsedRealtime();
                synchronized (lock) {
                    try {
                        crumbdb.beginTransaction();
                        //Log.d(TAG, "processing crumbs: " + localCrumbsToProcess.size());
                        for (Crumb c : localCrumbsToProcess) {
                            int trackId = c.trackDBID;
                            long timestamp = c.timestamp;
                            GeoPoint gp = c.getPoint();
                            double bearing = c.bearing;
                            double speed = c.speed;
                            String geopointSource = c.getMetaString(
                                    "tmpgpSource", GeoPointMetaData.UNKNOWN);
                            String altitudeSource = c.getMetaString(
                                    "tmpaltSource", GeoPointMetaData.UNKNOWN);

                            try {
                                insertStmt.bind(1, trackId);
                                insertStmt.bind(2, timestamp);
                                insertStmt.bind(3, gp.getLatitude());
                                insertStmt.bind(4, gp.getLongitude());
                                insertStmt.bind(5, gp.getAltitude());
                                insertStmt.bind(6, gp.getCE());
                                insertStmt.bind(7, gp.getLE());
                                insertStmt.bind(8, bearing);
                                insertStmt.bind(9, speed);
                                insertStmt.bind(10, geopointSource);
                                insertStmt.bind(11, altitudeSource);
                                insertStmt.bind(12, gp.getLongitude());
                                insertStmt.bind(13, gp.getLatitude());
                                insertStmt.execute();
                            } finally {
                                insertStmt.clearBindings();
                            }
                        }
                        localCrumbsToProcess.clear();
                        crumbdb.setTransactionSuccessful();
                    } catch (Exception e) {
                        Log.d(TAG, "transaction error", e);
                    } finally {
                        try {
                            crumbdb.endTransaction();
                            //Log.d(TAG, "success processing: " +
                            //        (SystemClock.elapsedRealtime() - start));
                        } catch (Exception e) {
                            Log.d(TAG, "end transaction error", e);
                            endTransactionError = true;
                        }
                    }
                }
            }
            try {
                insertStmt.close();
            } catch (Exception ignored) {
            }

            CrumbDatabase.this.worker = null;
        }

        void insert(Crumb c) {
            synchronized (lock) {
                try {
                    crumbdb.beginTransaction();
                    int trackId = c.trackDBID;
                    long timestamp = c.timestamp;
                    GeoPoint gp = c.getPoint();
                    double bearing = c.bearing;
                    double speed = c.speed;
                    String geopointSource = c.getMetaString("tmpgpSource",
                            GeoPointMetaData.UNKNOWN);
                    String altitudeSource = c.getMetaString("tmpaltSource",
                            GeoPointMetaData.UNKNOWN);

                    try {
                        insertStmt.bind(1, trackId);
                        insertStmt.bind(2, timestamp);
                        insertStmt.bind(3, gp.getLatitude());
                        insertStmt.bind(4, gp.getLongitude());
                        insertStmt.bind(5, gp.getAltitude());
                        insertStmt.bind(6, gp.getCE());
                        insertStmt.bind(7, gp.getLE());
                        insertStmt.bind(8, bearing);
                        insertStmt.bind(9, speed);
                        insertStmt.bind(10, geopointSource);
                        insertStmt.bind(11, altitudeSource);
                        insertStmt.bind(12, gp.getLongitude());
                        insertStmt.bind(13, gp.getLatitude());
                        insertStmt.execute();
                    } finally {
                        insertStmt.clearBindings();
                    }
                    crumbdb.setTransactionSuccessful();
                } catch (Exception e) {
                    Log.d(TAG, "transaction error", e);
                } finally {
                    try {
                        crumbdb.endTransaction();
                        //Log.d(TAG, "success processing: " +
                        //        (SystemClock.elapsedRealtime() - start));
                    } catch (Exception e) {
                        Log.d(TAG, "end transaction error", e);
                        endTransactionError = true;
                    }
                }
            }
        }
    }
}
