
package com.atakmap.android.routes;

import android.location.Location;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Pair;

import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.nav.NavigationCue;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.StatementIface;
import com.atakmap.map.layer.feature.datastore.FeatureSpatialDatabase;
import com.atakmap.math.MathUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This class performs calculations over all of the points in a route, both control points and
 * way points, in support of the RouteNavigator. It must have shutdown() called on it at some point.
 */
final class RouteNavigatorEngine {
    public static final String TAG = "RouteNavigatorEngine";
    private static final String WAYPOINT_TYPE = "b-m-p-w";

    private static final String POINTS_TABLE = "route_points";
    private static final String LINE_TABLE = "route_line";
    private static final String TRIGGERS_TABLE = "route_triggers";

    private static final String QUERY_DROP_POINTS_TABLE = "DROP TABLE IF EXISTS "
            + POINTS_TABLE;

    private static final String QUERY_CREATE_POINTS_TABLE = "CREATE TABLE "
            + POINTS_TABLE + " ("
            + "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,"
            + "uid TEXT NOT NULL,"
            + "position INTEGER NOT NULL,"
            + "isWaypoint INTEGER NOT NULL,"
            + "line_relative REAL NOT NULL,"
            + "trigger_count INTEGER NOT NULL)";

    private static final String QUERY_DROP_TRIGGERS_TABLE = "DROP TABLE IF EXISTS "
            + TRIGGERS_TABLE;

    private static final String QUERY_CREATE_TRIGGERS_TABLE = "CREATE TABLE "
            + TRIGGERS_TABLE + "("
            + "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, "
            + "position INTEGER NOT NULL,"
            + "trigger_position INTEGER NOT NULL,"
            + "line_relative_start REAL NOT NULL,"
            + "line_relative_end REAL NOT NULL)";

    private static final String QUERY_ADD_POINT_GEOM_TO_POINTS_TABLE = "SELECT AddGeometryColumn('"
            + POINTS_TABLE
            + "', 'pt', 4326, 'POINT', 'XY')";

    /*    private static final String QUERY_ADD_NEAR_TRIGGER_GEOM_TO_POINTS_TABLE = "SELECT AddGeometryColumn('"
                + POINTS_TABLE
                + "', 'nearTrigger', 4326, 'LINESTRING', 'XY')";
    
        private static final String QUERY_ADD_FAR_TRIGGER_GEOM_TO_POINTS_TABLE = "SELECT AddGeometryColumn('"
                + POINTS_TABLE
                + "', 'farTrigger', 4326, 'LINESTRING', 'XY')";*/

    private static final String QUERY_DROP_LINE_TABLE = "DROP TABLE IF EXISTS "
            + LINE_TABLE;

    private static final String QUERY_CREATE_LINE_TABLE = "CREATE TABLE "
            + LINE_TABLE + " ("
            + "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT)";

    private static final String QUERY_ADD_GEOM_TO_LINE_TABLE = "SELECT AddGeometryColumn('"
            + LINE_TABLE
            + "', 'routeline', 4326, 'LINESTRING', 'XY')";

    //NOTE:: This is here for debugging purposes only.  It is easy to test queries on the desktop.
    private static final boolean EXPORT_DB_ON_CLOSE = false;

    private DatabaseIface _db;
    private File _dbDir;
    private File _dbFile;

    private volatile boolean isShutdown = false;
    private long lineId = -1;
    private volatile boolean isMarkerTransactionOpen = false;

    public RouteNavigatorEngine() throws IOException {

        // Setup the file path for our DB
        _dbDir = FileSystemUtils.createTempDir("route_nav_engine", null, null);
        if (EXPORT_DB_ON_CLOSE) {
            _dbFile = new File(
                    FileSystemUtils.getItem(FileSystemUtils.EXPORT_DIRECTORY),
                    "route_engine.sqlite");
        } else {
            _dbFile = new File(_dbDir, "route_engine.sqlite");
        }

        // Make sure the necessary directory structure exists
        if (!IOProviderFactory.exists(_dbFile.getParentFile())) {
            if (!IOProviderFactory.mkdirs(_dbFile.getParentFile())) {
                Log.d(TAG, "could not wrap: " + _dbFile.getParentFile());
            }
        }

        // Get the DB opened up and initialized
        initDb();

        // Get our points table setup
        try {

            Log.d(TAG, QUERY_CREATE_POINTS_TABLE);
            Log.d(TAG, QUERY_ADD_POINT_GEOM_TO_POINTS_TABLE);

            _db.execute(QUERY_DROP_POINTS_TABLE, null); // drop
            _db.execute(QUERY_CREATE_POINTS_TABLE, null); // wrap
            _db.execute(QUERY_ADD_POINT_GEOM_TO_POINTS_TABLE, null); // add point geom
        } catch (Exception e) {
            Log.e(TAG, "Unable to create the " + POINTS_TABLE + " table", e);
        }

        //Get our triggers table setup
        try {
            _db.execute(QUERY_DROP_TRIGGERS_TABLE, null); //drop
            _db.execute(QUERY_CREATE_TRIGGERS_TABLE, null); //wrap
        } catch (Exception e) {
            Log.e(TAG, "Unable to create the " + TRIGGERS_TABLE + " table", e);
        }

        // Get our line table setup
        try {
            _db.execute(QUERY_DROP_LINE_TABLE, null); // drop
            _db.execute(QUERY_CREATE_LINE_TABLE, null); // wrap
            _db.execute(QUERY_ADD_GEOM_TO_LINE_TABLE, null); // add geom
        } catch (Exception e) {
            Log.e(TAG, "Unable to create the " + LINE_TABLE + " table", e);
        }
    }

    /**
     * Instructs the engine to begin building a single DB transaction out of subsequent calls.
     */
    void beginMarkerTransaction() {
        if (isMarkerTransactionOpen) {
            throw new IllegalStateException("A transaction is already open.");
        }

        isMarkerTransactionOpen = true;
        _db.beginTransaction();
    }

    /**
     * Instructs the engine to mark the transaction it's building as successful.
     */
    void setMarkerTransactionSuccessful() {
        if (!isMarkerTransactionOpen) {
            throw new IllegalStateException("No transaction open.");
        }

        _db.setTransactionSuccessful();
    }

    /**
     * Instructs the engine to close the transaction it's building.
     */
    void endMarkerTransaction() {
        if (!isMarkerTransactionOpen) {
            throw new IllegalStateException("No transaction open.");
        }

        _db.endTransaction();

        isMarkerTransactionOpen = false;
    }

    /**
     * Adds the route's points with their associated indices and nav cues to the engine.
     *
     * @param route The route that's points need adding.
     */
    void addRoutePoints(Route route) {
        if (isShutdown) {
            throw new IllegalStateException("The engine has been shutdown.");
        }

        long start = SystemClock.elapsedRealtime();

        // Compress route points: 4 degrees over 1 km
        List<PointMapItem> routePoints = compressRoutePoints(route, 4, 1000);
        final int numRoutePoints = routePoints.size();

        StatementIface stmt;
        StatementIface insertTriggerStmt;

        // add line

        // we'll iterate the points in the line, building the SpatiaLite blob
        // geometry for the route, and insert it into 'route_lines' with one
        // statement execution
        ByteBuffer routeLineBlob = ByteBuffer
                .allocate(48 + numRoutePoints * 8 * 2);
        routeLineBlob.put((byte) 0x00);
        if (routeLineBlob.order() == ByteOrder.BIG_ENDIAN)
            routeLineBlob.put((byte) 0x00);
        else if (routeLineBlob.order() == ByteOrder.LITTLE_ENDIAN)
            routeLineBlob.put((byte) 0x01);
        else
            throw new IllegalStateException();
        routeLineBlob.putInt(4326);
        routeLineBlob.putDouble(-180);
        routeLineBlob.putDouble(-90);
        routeLineBlob.putDouble(180);
        routeLineBlob.putDouble(90);
        routeLineBlob.put((byte) 0x7C);

        // linestring class type
        routeLineBlob.putInt(2);

        // num points
        final int pointCountPos = routeLineBlob.position();
        routeLineBlob.putInt(numRoutePoints);
        int actualPointCount = 0;

        // will hold the relative distances
        double[] distances = new double[numRoutePoints];
        double[] geodeticDistances = new double[numRoutePoints];
        double totalDistance = 0d;
        double totalGeodeticDistance = 0d;
        GeoPoint currentGeo;
        GeoPoint lastGeo = null;

        for (int i = 0; i < numRoutePoints; i++) {
            if (Thread.currentThread().isInterrupted())
                return;

            PointMapItem currentPoint = routePoints.get(i);
            if (currentPoint == null)
                continue;

            currentGeo = currentPoint.getPoint();
            routeLineBlob.putDouble(currentGeo.getLongitude());
            routeLineBlob.putDouble(currentGeo.getLatitude());
            actualPointCount++;

            if (actualPointCount > 1) {
                // NOTE: while this is NOT an accurate geodesic distance
                //       calculation it is consistent with the method used by
                //       ST_Line_Locate_Point and much, much faster than running
                //       that function on the points table.
                //       A more robust alternative may be to compute the segment
                //       length using ST_Length, against a precompiled query
                //       with a SpatiaLite blob.
                if (lastGeo != null) {
                    totalDistance += MathUtils.distance(lastGeo.getLongitude(),
                            lastGeo.getLatitude(), currentGeo.getLongitude(),
                            currentGeo.getLatitude());
                    totalGeodeticDistance += estimateDistance(lastGeo,
                            currentGeo);
                    distances[i] = totalDistance;
                    geodeticDistances[i] = totalGeodeticDistance;
                }
            }
            lastGeo = currentGeo;
        }
        routeLineBlob.put((byte) 0xFE);

        // if any points were skipped, record the actual point count
        if (actualPointCount != numRoutePoints) {
            routeLineBlob.putInt(pointCountPos, actualPointCount);
        }

        // insert the route geometry
        stmt = null;

        try {
            StringBuilder sql = new StringBuilder();
            sql.append("INSERT INTO ");
            sql.append(LINE_TABLE);
            sql.append(" (routeline) VALUES (SanitizeGeometry(?))");

            stmt = _db.compileStatement(sql.toString());
            stmt.bind(1, routeLineBlob.array());

            stmt.execute();
            lineId = Databases.lastInsertRowId(_db);
        } finally {
            if (stmt != null)
                stmt.close();
        }

        // populate points and triggers tables
        stmt = null;
        insertTriggerStmt = null;

        try {
            // pre-compile the route point insert statement
            StringBuilder sql = new StringBuilder("INSERT INTO ");
            sql.append(POINTS_TABLE);
            sql.append(
                    " (id, uid, position, isWaypoint, line_relative, trigger_count, pt) VALUES (NULL, ?, ?, ?, ?, ?,  MakePoint(?, ?, 4326))");

            stmt = _db.compileStatement(sql.toString());

            //pre-compile the trigger insertion statement
            String insertTriggerSql = "INSERT INTO "
                    + TRIGGERS_TABLE
                    + " (id, position, trigger_position, line_relative_start, line_relative_end)"
                    + " VALUES (NULL, ?, ?, ?, ?)";

            insertTriggerStmt = _db.compileStatement(insertTriggerSql);
            PointComparator comparator = new PointComparator();
            int lastWaypointIndex = 0;

            for (int i = 0; i < routePoints.size(); i++) {
                if (Thread.currentThread().isInterrupted())
                    return;

                PointMapItem currentPoint = routePoints.get(i);
                if (currentPoint == null)
                    continue;

                int pointPos = route.getIndexOfMarker(currentPoint);

                int isWaypoint = 0;
                int triggerCount = 0;
                NavigationCue cue = null;
                String uid = currentPoint.getUID();

                if (currentPoint.getType().equals(WAYPOINT_TYPE)) {
                    // Currently only way points have cues
                    cue = route.getCueForPoint(uid);
                    isWaypoint = 1;
                }

                /*if(isWaypoint == 0)
                    continue;*/

                //Store our triggers, if any
                if (cue != null && i > 0) {

                    //We can't have a trigger that starts before this point
                    final double previousPointDistance = distances[lastWaypointIndex];
                    final double previousPointGDistance = geodeticDistances[lastWaypointIndex];

                    //We can't have a trigger that goes beyond this point
                    final double currentEndDistance = distances[i];
                    final double currentEndGDistance = geodeticDistances[i];
                    double endGDistance = currentEndGDistance;

                    List<NavigationCue.ConditionalNavigationCue> rawCueList = cue
                            .getCues();

                    if (rawCueList != null && rawCueList.size() > 0) {
                        List<NavigationCue.ConditionalNavigationCue> cnCueList = new ArrayList<>(
                                rawCueList);

                        //Need to ensure the list is sorted
                        Collections.sort(cnCueList, comparator);
                        triggerCount = 0;

                        //Store our triggers
                        for (int cPos = 0; cPos < cnCueList.size(); cPos++) {
                            triggerCount++;
                            NavigationCue.ConditionalNavigationCue cnCue = cnCueList
                                    .get(cPos);
                            double startGDistance = Math.max(
                                    previousPointGDistance, endGDistance
                                            - cnCue.getTriggerValue());

                            double startDistance = getDistance(startGDistance,
                                    previousPointDistance,
                                    currentEndDistance, previousPointGDistance,
                                    currentEndGDistance);
                            double endDistance = getDistance(endGDistance,
                                    previousPointDistance,
                                    currentEndDistance, previousPointGDistance,
                                    currentEndGDistance);

                            try {
                                insertTriggerStmt.bind(1, pointPos); //Position
                                insertTriggerStmt.bind(2,
                                        rawCueList.indexOf(cnCue)); //Trigger Position
                                insertTriggerStmt.bind(3, startDistance
                                        / totalDistance); //Line Relative Start
                                insertTriggerStmt.bind(4, endDistance
                                        / totalDistance); // Line Relative End

                                insertTriggerStmt.execute();
                            } finally {
                                insertTriggerStmt.clearBindings();
                            }

                            endGDistance = startGDistance;

                            if (startDistance == previousPointDistance) {
                                break; //No room left for additional cues
                            }
                        }
                    }
                }

                GeoPoint pt = currentPoint.getPoint();
                try {
                    int idx = 1;
                    stmt.bind(idx++, uid);
                    stmt.bind(idx++, pointPos);
                    stmt.bind(idx++, isWaypoint);
                    stmt.bind(idx++, distances[i] / totalDistance);
                    stmt.bind(idx++, triggerCount);
                    stmt.bind(idx++, pt.getLongitude());
                    stmt.bind(idx++, pt.getLatitude());

                    stmt.execute();
                } finally {
                    stmt.clearBindings();
                }

                if (isWaypoint == 1)
                    lastWaypointIndex = i;
            }
        } finally {
            if (stmt != null)
                stmt.close();

            if (insertTriggerStmt != null)
                insertTriggerStmt.close();
        }

        Log.d(TAG, "Route points added successfully in "
                + (SystemClock.elapsedRealtime() - start) + "ms");
    }

    private double getDistance(double geodeticDistance, double startDistance,
            double endDistance,
            double startGeodeticDistance, double endGeodeticDistance) {

        double denominator = (endGeodeticDistance - startGeodeticDistance);

        if (denominator == 0)
            return 0;

        double numerator = ((geodeticDistance - startGeodeticDistance)
                * (endDistance - startDistance));

        return (numerator / denominator) + startDistance;

        /*return ((geodeticDistance - startGeodeticDistance) * (endDistance - startDistance))
                /
                (endGeodeticDistance - startGeodeticDistance) + startDistance;*/
    }

    /**
     * Gets a Line Substring from the route active in LINE_TABLE
     * @param start The start point (% of entire route between 0 and 1) of the segment.
     * @param end The end point (% of entire route between 0 and 1) of the segment.
     * @return A string in the common format for LineString
     */
    private String getLineSubstring(double start, double end) {
        String gQuery = "SELECT routeline FROM " + LINE_TABLE + " WHERE id="
                + lineId;

        String query = "SELECT AsText(Line_Substring((" + gQuery + "), "
                + start + ", " + end + "))";

        CursorIface result = null;
        try {
            result = _db.query(query, null);
            //Only 1 item should be returned
            result.moveToNext();
            return result.getString(0);
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Getting Line_Substring failed query string executed: [" +
                            query + "]",
                    ex);
        } finally {
            if (result != null)
                result.close();
        }
    }

    /**
     * Gets a Line Substring from the route provided
     * @param linestring The linestring of which to take a substring.
     * @param start The start point (% of entire route between 0 and 1) of the segment.
     * @param end The end point (% of entire route between 0 and 1) of the segment.
     * @return A string in the common format for LineString
     */
    private byte[] getLineSubstring(String linestring, double start,
            double end) {
        String query = "SELECT Line_Substring(" + linestring + ", "
                + start + ", " + end + ")";

        CursorIface result = null;
        try {
            result = _db.query(query, null);
            //Only 1 item should be returned
            result.moveToNext();
            return result.getBlob(0);
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Getting Line_Substring failed query string executed: [" +
                            query + "]",
                    ex);
        } finally {
            if (result != null)
                result.close();
        }
    }

    /**
     * Creates a LineString from indices of a route.
     * @param points The entire list of route points
     * @param startSegmentIndex The index to start building from (inclusive).
     * @param endSegmentIndex The index to stop building at (inclusive).
     * @return A LineString in its common string form.
     */
    private String getLineString(List<PointMapItem> points,
            int startSegmentIndex, int endSegmentIndex) {
        StringBuilder lineString = new StringBuilder();
        lineString.append("GeomFromText('LINESTRING(");
        for (int i = startSegmentIndex; i <= endSegmentIndex; i++) {
            if (i > startSegmentIndex)
                lineString.append(",");

            lineString.append(points.get(i).getPoint().getLongitude());
            lineString.append(" ");
            lineString.append(points.get(i).getPoint().getLatitude());
        }

        lineString.append(")', 4326)");

        return lineString.toString();
    }

    /**
     * Gets the length of the route from the active route in Line_Table.
     * @return A double representing the length of the route in its entirety in meters.
     */
    private double getRouteLengthInMeters() {
        // XXX - consider moving to ST_Length(geom, use_ellipsoid) rather than
        //       web mercator
        String gQuery = "SELECT routeline FROM " + LINE_TABLE + " WHERE id="
                + lineId;
        String query = "SELECT ST_Length(ST_Transform("
                + "(" + gQuery + "), 3857)) as length";

        CursorIface result = null;
        try {
            result = _db.query(query, null);
            // XXX - should check is cursor is moving to next and return an
            //       value appropriate value or throw an appropriate exception
            //       that can be handled by the caller
            //Only 1 item should be returned
            result.moveToNext();
            return result.getDouble(0);
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Getting route length failed. Query string executed: [" +
                            query + "]",
                    ex);
        } finally {
            if (result != null)
                result.close();
        }
    }

    /**
     * Gets the location of a point along the route as a percentage of the route.
     * @param pt The point to position along the route that is active in the Line_Table.
     * @return A percentage from 0 to 1 representing the position of the point along the route.
     */
    synchronized public double getLocationOfPointAlongRoute(GeoPoint pt) {
        String gQuery = "SELECT routeline FROM " + LINE_TABLE + " WHERE id="
                + lineId;

        String query = "SELECT ST_Line_Locate_Point(" +
                "(" + gQuery + ")," +
                "MakePoint(" + pt.getLongitude() + "," + pt.getLatitude()
                + ",4326" + "))";

        CursorIface result = null;
        try {
            result = _db.query(query, null);
            //Only 1 item should be returned
            result.moveToNext();
            return result.getDouble(0);
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Getting location of point along route failed. Query string executed: ["
                            +
                            query + "]",
                    ex);
        } finally {
            if (result != null)
                result.close();
        }
    }

    /**
     * Gets the length of the provided segment.
     * @param lineString The segment to measure.
     * @return The total length of the provided LineString in meters.
     */
    private double getSegmentLengthInMeters(String lineString) {
        //Build the query to get the length in meters
        String lengthQuery = "SELECT ST_Length(ST_Transform(" +
                lineString + ", 3857)) as length";

        CursorIface result = null;
        try {
            result = _db.query(lengthQuery, null);
            //Only 1 item should be returned
            result.moveToNext();
            return result.getDouble(0);
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Getting length failed. Query string executed: [" +
                            lengthQuery + "]",
                    ex);
        } finally {
            if (result != null)
                result.close();
        }
    }

    /**
     * Adds a point to the line that is stored in Line_Table.
     * @param pt The point to add.
     */

    /**
     * Gets the trigger associated with the passed in point, if one exists.
     *
     * @param pt Point to evaluate with (Note, it will be projected to the nearest point on the route).
     * @return A pair representing the position of the trigger point, and a boolean that is true if it is a near trigger or false if it is a far trigger.  Note, this will return NULL if the point is not within a trigger area.
     */
    synchronized public Pair<Integer, Integer> findTriggerHit(GeoPoint pt) {

        double rPos = getLocationOfPointAlongRoute(pt);
        String sql = "SELECT position, trigger_position FROM " + TRIGGERS_TABLE
                + " WHERE " + rPos + " > line_relative_start"
                + " AND " + rPos + " < line_relative_end";

        CursorIface result = null;
        try {
            result = _db.query(sql, null);

            if (result.moveToNext()) {
                int position = result.getInt(0);
                int triggerPosition = result.getInt(1);
                //Log.d("TESTME", "Found hit at Index " + position + " and trigger index " + triggerPosition);
                return new Pair<>(position, triggerPosition);
            } else {
                return null;
            }
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Getting trigger hit failed. Query string executed: [" +
                            sql + "]",
                    ex);
        } finally {
            if (result != null)
                result.close();
        }

    }

    /**
     * Gets the distance between two points as the route goes.  Note the provided locations will be projected to the closest point on the route geometry.
     * @param pt1 The first point.
     * @param pt2 The second point.
     * @return The length in meters between the two points along the route.
     */
    synchronized public double getDistanceBetweenTwoPointsAlongRoute(
            GeoPoint pt1,
            GeoPoint pt2) {

        double loc1 = getLocationOfPointAlongRoute(pt1);
        double loc2 = getLocationOfPointAlongRoute(pt2);

        String gQuery = "SELECT routeline FROM " + LINE_TABLE + " WHERE id="
                + lineId;

        //        //Build the query to get the length in meters
        //        String lengthQuery = "SELECT ST_Length(ST_Transform(" +
        //                "ST_Line_Substring( (" +
        //                gQuery + "), " + Math.min(loc1, loc2) + ", "
        //                + Math.max(loc1, loc2) + "), 3857)) as length";

        //Build the query to get the length in meters
        String lengthQuery = "SELECT GeodesicLength(" +
                "ST_Line_Substring( (" +
                gQuery + "), " + Math.min(loc1, loc2) + ", "
                + Math.max(loc1, loc2) + ")) as length";

        CursorIface result = null;
        try {
            result = _db.query(lengthQuery, null);
            //Only 1 item should be returned
            result.moveToNext();
            return result.getDouble(0);
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Getting Distance Between Two Points Query Failed. Query string executed: ["
                            +
                            lengthQuery + "]",
                    ex);
        } finally {
            if (result != null)
                result.close();
        }

    }

    /**
     * Gets the nearest hit to either the closest far trigger area or near trigger area depending on the value of searchNearTriggers
     * @param pointGeometry The point of use for comparison.
     * @param searchNearTriggers True to search near triggers otherwise false to search far triggers.
     * @return A Pair of Integer and Double for point index and distance in meters respectively.  May return null if there are no points in the database.
     * @throws Exception Invalid engine state
     */
    private Pair<Integer, Double> getNearestHit(String pointGeometry,
            boolean searchNearTriggers) throws Exception {
        ensureEngineStateIsValid();

        String targetColumn = searchNearTriggers ? "nearTrigger" : "farTrigger";

        String query = "SELECT position, MIN(Distance(ST_Transform("
                + targetColumn + ", 3857),"
                + "ST_Transform(GeomFromText('" + pointGeometry
                + "', 4326), 3857))) as distance "
                + "FROM " + POINTS_TABLE + " WHERE " + targetColumn
                + " NOT NULL";

        CursorIface result = null;
        try {
            result = _db.query(query, null);
            //At most, 1 item should be returned
            if (result.moveToNext()) {
                int index = result.getInt(0);
                double distance = result.getDouble(1);

                if (index == 0 && distance == 0) {
                    return null; //Effectively no results were returned, there is just no real way to represent/get that information.
                } else {
                    return new Pair<>(index, distance);
                }
            } else {
                return null;
            }
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Getting nearest hit Failed. Query string executed: [" +
                            query + "]",
                    ex);
        } finally {
            if (result != null)
                result.close();
        }

    }

    /**
     * Finds how far away a point is from the route.
     *
     * @param pt Point
     * @return -1 if error, otherwise, distance in meters
     * @throws Exception Unknown exception occurred
     */
    synchronized public double findDistanceFromRoute(GeoPoint pt)
            throws Exception {

        ensureEngineStateIsValid();

        String query = "SELECT Y(cpt) as Y, X(cpt) as x " +
                "FROM (SELECT ClosestPoint( " +
                "(SELECT routeline FROM " + LINE_TABLE + " WHERE id=" + lineId
                + "), " +
                "MakePoint(" + pt.getLongitude() + ", " + pt.getLatitude()
                + ", 4326)) as cpt)";

        CursorIface cursor = null;
        try {
            cursor = _db.query(query, null);

            if (cursor.moveToNext()) {
                GeoPoint cloestPt = new GeoPoint(cursor.getDouble(0),
                        cursor.getDouble(1));

                return pt.distanceTo(cloestPt);
            } else {
                return -1;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Gets a projected point onto the current active route in Line_Table.
     * @param pt The point to project.
     * @return The common string representation of the projected point.
     * @throws Exception
     */
    private String getClosestPoint(GeoPoint pt) throws Exception {

        ensureEngineStateIsValid();

        String query = "SELECT AsText("
                + "ST_ClosestPoint("
                + "(SELECT routeline FROM " + LINE_TABLE + " WHERE id="
                + lineId + "),"
                + "MakePoint(" + pt.getLongitude() + ", " + pt.getLatitude()
                + ", 4326)"
                + "))";

        CursorIface result = null;
        try {
            result = _db.query(query, null);
            //At most, 1 item should be returned
            if (result.moveToNext()) {
                return result.getString(0);
            } else {
                //NOTE: This query will return nothing/null if the point is exactly on the line (i.e. the distance is 0)
                //For that reason, if we get nothing back, we'll return the original point making the assumption that it is on the line.
                return "POINT(" + pt.getLongitude() + " " + pt.getLatitude()
                        + ")";
            }
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Getting Min Distance In Meters Failed. Query string executed: ["
                            +
                            query + "]",
                    ex);
        } finally {
            if (result != null)
                result.close();
        }

    }

    /**
     * Finds the index of the closest point (located in the route after the provided point) with a trigger.
     * @param pt The point of which the result should be after.
     * @return The index of the next closes point
     * @throws Exception
     */
    synchronized public int findNextClosestIndexWithTrigger(GeoPoint pt)
            throws Exception {

        ensureEngineStateIsValid();

        double ptLocation = getLocationOfPointAlongRoute(pt);

        //String gQuery = "SELECT routeline FROM " + LINE_TABLE + " WHERE id="
        //        + lineId;

        //NOTE: Every point that will have a trigger must always have at least a near trigger.
        String query = "SELECT position, MIN(line_relative) as l "
                + "FROM " + POINTS_TABLE
                + " WHERE line_relative > " + ptLocation
                + " AND trigger_count > 0";

        int position = -1;

        CursorIface cursor = null;
        try {
            cursor = _db.query(query, null);

            if (cursor.moveToNext()) {
                position = cursor.getInt(0);
            }
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Getting Next Closest Index With Trigger Failed. Query string executed: ["
                            +
                            query + "]",
                    ex);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return position;
    }

    /**
     * Finds the index of the closest point (located in the route after or at the provided point) with a trigger.
     * @param pt The point of which the result should be at or after.
     * @return The index of the next closes point
     * @throws Exception
     */
    synchronized public int findNextClosestWaypoint(GeoPoint pt)
            throws Exception {

        ensureEngineStateIsValid();

        double ptLocation = getLocationOfPointAlongRoute(pt);

        //String gQuery = "SELECT routeline FROM " + LINE_TABLE + " WHERE id="
        //        + lineId;

        //NOTE: Every point that will have a trigger must always have at least a near trigger.
        String query = "SELECT position, MIN(line_relative) as l "
                + "FROM " + POINTS_TABLE
                + " WHERE line_relative >= " + ptLocation
                + " AND isWaypoint = 1";

        int position = -1;

        CursorIface cursor = null;
        try {
            cursor = _db.query(query, null);

            if (cursor.moveToNext()) {
                position = cursor.getInt(0);
            }
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Getting Next Closest Waypoint Index Failed. Query string executed: ["
                            +
                            query + "]",
                    ex);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return position;
    }

    /**
     * Finds the index in the route of the point closest to the given point, pt.
     *
     * @param pt Point of interest who's neighboring point we are searching for
     * @return index of the closest point. -1 if no closest point was found
     * @throws Exception An unknown exception occurred
     */
    synchronized public int findIndexOfClosestPoint(GeoPoint pt)
            throws Exception {

        ensureEngineStateIsValid();

        String query = "SELECT position FROM " + POINTS_TABLE + " AS a"
                + " ORDER BY ST_Distance(GeomFromText('POINT("
                + pt.getLongitude() + " " + pt.getLatitude()
                + ")', 4326), a.pt)";

        int position = -1;

        CursorIface cursor = null;
        try {
            cursor = _db.query(query, null);

            if (cursor.moveToNext()) {
                position = cursor.getInt(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return position;
    }

    synchronized public List<GeoPoint> getGeometryOfRouteSection(GeoPoint pt,
            double tolerance,
            double backwardDistance, double forwardDistance) throws Exception {
        ensureEngineStateIsValid();

        double routeLength = getRouteLengthInMeters();

        double ptLocation = getLocationOfPointAlongRoute(pt);

        double lengthToPt = routeLength * ptLocation;
        double startLengthInMeters = Math.max(lengthToPt - backwardDistance, 0);
        double endLengthInMeters = Math.min(lengthToPt + forwardDistance,
                routeLength);

        double startPointPercentage = startLengthInMeters / routeLength;
        double endPointPercentage = endLengthInMeters / routeLength;

        String interestingSegment = getLineSubstring(startPointPercentage,
                endPointPercentage);

        if (tolerance > 0) {
            CursorIface cursor = null;
            try {
                cursor = _db.query("SELECT ASTEXT(Simplify( " +
                        "GeomFromText(?, 4326), ?))", new String[] {
                                interestingSegment, Double.toString(tolerance)
                });

                if (cursor.moveToNext()) {
                    interestingSegment = cursor.getString(0);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (interestingSegment == null) {
            Log.w(TAG, "No interesting segment found (" + pt + ", "
                    + tolerance + ", " + backwardDistance + ", "
                    + forwardDistance + ")");
            return null;
        }

        //parse Spatialite Points to GeoPoint
        int startPos = interestingSegment.indexOf('(') + 1;
        int endPos = interestingSegment.lastIndexOf(')');

        if (startPos > -1 && endPos > -1) {
            interestingSegment = interestingSegment.substring(startPos, endPos)
                    .trim();
            String[] pointPairs = interestingSegment.split(",");

            List<GeoPoint> points = new ArrayList<>(pointPairs.length);

            for (String pointPair : pointPairs) {
                String[] lngLat = pointPair.trim().split(" ");
                GeoPoint point = new GeoPoint(Double.parseDouble(lngLat[1]),
                        Double.parseDouble(lngLat[0]));
                points.add(point);
            }

            return points;
        }

        return null;

        /*String gQuery = "SELECT routeline FROM " + LINE_TABLE + " WHERE id="
                + lineId;
        
        String query = "SELECT position, ST_Line_Locate_Point( (" + gQuery
                + "), pt) as l "
                + "FROM " + POINTS_TABLE
                + " AS a WHERE CAST(Distance(ST_Transform("
                + "GeomFromText('" + interestingSegment
                + "', 4326), 3857), ST_Transform(a.pt, 3857)) AS int) = 0 "
                + "AND (farTrigger IS NOT NULL OR nearTrigger IS NOT NULL)";
        
        CursorIface cursor = null;
        List<Pair<Integer, Boolean>> results = new ArrayList<Pair<Integer, Boolean>>();
        
        try {
            cursor = _db.query(query, null);
        
            while (cursor.moveToNext()) {
                Pair<Integer, Boolean> entry = new Pair<Integer, Boolean>(
                        cursor.getInt(0), ptLocation <= cursor.getDouble(1));
                results.add(entry);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return results;*/

    }

    /**
     * Gets a list of points that are within a given distance.
     * @param pt The point used for comparison.
     * @param backwardDistance The distance behind the provided point to use when determining for comparison.
     * @param forwardDistance The distance in front of the provided point to use when determining for comparison.
     * @return A List of Pairs indicating the index and whether or not it is before or after the point provided.  (True = pt is at or before index; False = pt is after index)
     * @throws Exception
     */
    synchronized public List<Pair<Integer, Boolean>> findIndexOfPointsWithinDistance(
            GeoPoint pt, double backwardDistance, double forwardDistance)
            throws Exception {

        ensureEngineStateIsValid();

        double routeLength = getRouteLengthInMeters();

        double ptLocation = getLocationOfPointAlongRoute(pt);

        double lengthToPt = routeLength * ptLocation;
        double startLengthInMeters = Math.max(lengthToPt - backwardDistance, 0);
        double endLengthInMeters = Math.min(lengthToPt + forwardDistance,
                routeLength);

        double startPointPercentage = startLengthInMeters / routeLength;
        double endPointPercentage = endLengthInMeters / routeLength;

        String interestingSegment = getLineSubstring(startPointPercentage,
                endPointPercentage);

        //String gQuery = "SELECT routeline FROM " + LINE_TABLE + " WHERE id="
        //        + lineId;

        String query = "SELECT position, line_relative as l "
                + "FROM " + POINTS_TABLE
                + " AS a WHERE CAST(Distance(ST_Transform("
                + "GeomFromText(?, 4326), 3857), ST_Transform(a.pt, 3857)) AS int) = 0 "
                + "AND isWaypoint=1";

        CursorIface cursor = null;
        List<Pair<Integer, Boolean>> results = new ArrayList<>();

        try {
            cursor = _db.query(query, new String[] {
                    interestingSegment
            });

            while (cursor.moveToNext()) {
                Pair<Integer, Boolean> entry = new Pair<>(
                        cursor.getInt(0), ptLocation <= cursor.getDouble(1));
                results.add(entry);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return results;
    }

    /**
     * Ensures the engine is not shutdown and throws an IllegalSateException if it is.
     */
    private void ensureEngineStateIsValid() {
        if (isShutdown) {
            throw new IllegalStateException("The engine has been shutdown.");
        }
    }

    /**
     * Shuts down the engine, cleaning up any necessary internal references / DB handles.
     */
    synchronized void shutdown() {
        if (isShutdown) {
            return;
        }
        Thread t = new Thread("routenavengine-shutdown") {
            @Override
            public void run() {
                // Close out the DB
                synchronized (RouteNavigatorEngine.this) {
                    Log.d(TAG, "begining cleanup process for: " + _dbFile);
                    if (_db != null) {
                        _db.close();
                        _db = null;
                    }

                    // Cleanup our temp file and directory
                    if (!EXPORT_DB_ON_CLOSE) {
                        if (_dbFile != null) {
                            FileSystemUtils.delete(_dbFile);
                            Log.d(TAG,
                                    "finished cleanup process for: " + _dbFile);
                        }
                    } else {
                        Log.d(TAG, "cleanup process preserved " + _dbFile
                                + ", EXPORT is on");
                    }

                    if (_dbDir != null) {
                        FileSystemUtils.delete(_dbDir);

                        Log.d(TAG, "finished cleanup process for: " + _dbDir);
                    }
                }
            }
        };
        t.start();
        isShutdown = true;
    }

    private static final double EARTH_RADIUS = 6371e3;

    private double estimateDistance(GeoPoint pt1, GeoPoint pt2) {
        double lat1 = Math.toRadians(pt1.getLatitude());
        double lat2 = Math.toRadians(pt2.getLatitude());

        double lon1 = Math.toRadians(pt1.getLongitude());
        double lon2 = Math.toRadians(pt2.getLongitude());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.pow(Math.sin(dLat / 2.0), 2.0) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.pow(dLon / 2.0, 2.0);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double comptuedDistance = c * EARTH_RADIUS;

        return comptuedDistance;
    }

    /**
     * Returns a copy of the compressed route points
     * @param route Route to read points from
     * @param angDiff If the angular diff. between 2 consecutive segments
     *                is less than this value, merge them into 1 segment (deg)
     * @param maxDist If the 2 segments' combined distance exceeds this value,
     *                do not merge them into 1 (km)
     * @return List of points
     */
    synchronized public static List<PointMapItem> compressRoutePoints(
            Route route,
            double angDiff, double maxDist) {
        long start = SystemClock.elapsedRealtime();
        angDiff = Math.abs(angDiff);
        maxDist = Math.abs(maxDist);
        int srcPoints = route.getNumPoints();
        List<PointMapItem> points = new ArrayList<>(
                route.getPointMapItems());
        PointMapItem p, c, n;
        float[] res = new float[3];

        for (int i = 1; i < points.size() - 1; i++) {
            p = points.get(i - 1);
            c = points.get(i);
            n = points.get(i + 1);
            if (p != null && n != null && !c.getType().equals(WAYPOINT_TYPE)) {

                // Calculate angle between previous and current point
                double dist = 0;
                Location.distanceBetween(
                        p.getPoint().getLatitude(),
                        p.getPoint().getLongitude(),
                        c.getPoint().getLatitude(),
                        c.getPoint().getLongitude(), res);
                double pAng = res[1];
                dist += res[0];

                // Calculate angle between current and next point
                Location.distanceBetween(
                        c.getPoint().getLatitude(),
                        c.getPoint().getLongitude(),
                        n.getPoint().getLatitude(),
                        n.getPoint().getLongitude(), res);
                double nAng = res[1];
                dist += res[0];

                // If the R&B diff between 2 consecutive lines is
                // <2 deg and <1 km then remove the middle point
                if (dist < maxDist && Math.abs(pAng - nAng) < angDiff)
                    points.remove(i--);
            }
        }
        if (srcPoints > 0) {
            Log.d(TAG, "Compressed " + route.getTitle() + " "
                    + srcPoints + " -> " + points.size() + " points ("
                    + Math.round(((float) points.size() / srcPoints) * 100)
                    + "%) in " + (SystemClock.elapsedRealtime() - start)
                    + "ms | angDiff = " + angDiff + " deg, maxDist = "
                    + maxDist + " m");
        } else {
            Log.d(TAG, "Compressed " + route.getTitle()
                    + " no compression required");
        }

        return points;
    }

    private void initDb() {
        try {
            final DatabaseIface oldDb = _db;

            _db = IOProviderFactory.createDatabase(
                    new DatabaseInformation(Uri.fromFile(_dbFile)));
            lineId = -1;
            isMarkerTransactionOpen = false;

            try {
                if (oldDb != null)
                    oldDb.close();
            } catch (Exception ignored) {
                Log.e(TAG, "Old route navigator engine db close unsuccessful");
            }

            String query = "SELECT InitSpatialMetadata()";

            final int major = FeatureSpatialDatabase
                    .getSpatialiteMajorVersion(_db);
            final int minor = FeatureSpatialDatabase
                    .getSpatialiteMinorVersion(_db);

            Log.d(TAG, "RouteNavigatorEngine using Spatialite version: "
                    + major + "." + minor);

            if (major > 4 || (major == 4 && minor >= 1))
                query = "SELECT InitSpatialMetadata(1)";
            else
                query = "SELECT InitSpatialMetadata()";

            _db.execute(query, null);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open RouteNavigatorEngine database", e);
        }
    }

    private static class PointComparator implements
            Comparator<NavigationCue.ConditionalNavigationCue> {
        @Override
        public int compare(NavigationCue.ConditionalNavigationCue lhs,
                NavigationCue.ConditionalNavigationCue rhs) {
            int lhi = lhs.getTriggerValue();
            int rhi = rhs.getTriggerValue();

            return Integer.compare(lhi, rhi);
        }
    }
}
