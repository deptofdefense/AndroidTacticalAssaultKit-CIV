package com.atakmap.spatial;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.os.Environment;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.StatementIface;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.util.ArrayIterator;
import com.atakmap.map.layer.feature.FeatureDataSource;
import com.atakmap.map.layer.feature.datastore.FeatureSpatialDatabase;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;

/**
 * Spatial calculator backed by SpatiaLite. The calculator will require both
 * memory and disk resources. It is recommended that the {@link #dispose()}
 * method be invoked when the calculator will no longer be used to immediately
 * release all allocated resources. In the event that {@link #dispose()} is not
 * explicitly invoked, the resources will be released on finalization.
 * 
 * <H2>Workflow</H2>
 * 
 * <P>The general workflow for the calculator will be to create geometries in
 * the calculator's memory and then perform various spatial operations on those
 * geometries. When a geometry is created, a handle to that geometry in the
 * calculator's memory is returned. The geometry may be accessed (e.g. used as
 * an argument to a function) via the memory handle. The calculator's memory
 * may be cleared at any time by invoking the method, {@link #clear()}. When the
 * memory is cleared all existing geometries handles are invalidated. Attempting
 * to use an invalid handle will produce undefined results.
 * 
 * <P>The calculator makes use of two mechanisms to improve performance. The
 * first mechanism is caching. Data structures and instructions are cached to
 * reduce general overhead. The user may clear the cache at any time by invoking
 * the method, {@link #clearCache()}. If a reference to the calculator is going
 * to be maintained for a long time but its use is infrequent it may be
 * appropriate to clear the cache between uses. The second mechanism is
 * batching. Batching instructs the calculator to perform all instructions
 * without performing an actual commit to the calculator memory until the batch
 * has been completed. This can significantly improve performance when
 * instructions are being executed in high volume or frequency. All instructions
 * given to the calculator within the batch will produce valid results, however,
 * all instructions issued during the batch are not actually committed to the
 * calculator's memory until {@link #endBatch(boolean)} is invoked. If
 * {@link #endBatch(boolean)} is invoked with an argument of <code>false</code>
 * all instructions issued during the batch are undone and the calculator is in
 * the same state as it was prior to the batch.
 * 
 * <H2>Thread Safety</H2>
 * 
 * <P>This class is <B>NOT</B> thread-safe. Care needs to be taken to ensure
 * that methods are invoked in a thread-safe manner. It is strongly recommended
 * that each instance only be used on a single thread.
 * 
 * <P>Special care must be taken when the calculator is in batch mode. When in
 * batch mode, instructions may only be issued to the calculator on the thread
 * that the batch was started in.
 * 
 * @author Developer
 */
public final class SpatialCalculator {
    private final static byte NATIVE_BLOB_ENDIAN = (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? (byte)0x00 : (byte)0x01;

    // spatialite blob encoding seems to perform the best, as would make sense
    private static final int preferredCoding = FeatureDataSource.FeatureDefinition.GEOM_SPATIALITE_BLOB;
    
    private final static File DEFAULT_TMP_DIR = new File(Environment.getExternalStorageDirectory(), ".tmp");
    /**
     * A temporary files directory designated for use by SpatialCalculator.
     */
    private static File _tmpDir = DEFAULT_TMP_DIR;
    /**
     * A subdirectory of {@link #_tmpDir} to be used exclusively during this
     * runtime for storage of <code>SpatialCalculator</code> databases. A
     * runtime-specific directory is used to assist in cleanup, specifically
     * avoiding possible race conditions.
     */
    private static File _runtimeTempDir = null;

    public static final String TAG = "SpatialCalculator";
    public final static int GEOM_TYPE_POINT = 0x01;
    public final static int GEOM_TYPE_LINESTRING = 0x02;
    public final static int GEOM_TYPE_POLYGON = 0x03;
    public final static int GEOM_TYPE_MULTIPOINT = 0x04;
    public final static int GEOM_TYPE_MULTILINESTRING = 0x05;
    public final static int GEOM_TYPE_MULTIPOLYGON = 0x06;
    public final static int GEOM_TYPE_GEOMETRYCOLLECTION = 0x07;

    static {
        // spawn a thread to clean up any old files
        cleanup(DEFAULT_TMP_DIR, getRuntimeTempDir(), false);
    }
    
    private File spatialdbFile;
    private DatabaseIface database;

    /** Flag to tell clients that this calculator instance has been disposed */
    private boolean disposed = false;
    /** Indicator of 2D vs 3D points */
    private final int dimension;
    
    private String[] arr1;
    private String[] arr2;
    private StringBuilder wkt;
    private QuadBlob quad;

    private StatementIface insertGeomWkt;
    private StatementIface insertGeomBlob;
    private StatementIface insertGeomWkb;
    private StatementIface insertPoint;
    private StatementIface bufferInsert;
    private StatementIface bufferUpdate;
    private StatementIface intersectionInsert;
    private StatementIface intersectionUpdate;
    private StatementIface unionInsert;
    private StatementIface unionUpdate;
    private StatementIface unaryUnionInsert;
    private StatementIface unaryUnionUpdate;
    private StatementIface differenceInsert;
    private StatementIface differenceUpdate;
    private StatementIface simplifyInsert;
    private StatementIface simplifyUpdate;
    private StatementIface simplifyPreserveTopologyInsert;
    private StatementIface simplifyPreserveTopologyUpdate;
    private StatementIface clear;
    private StatementIface deleteGeom;

    private StatementIface updatePoint;
    private StatementIface updateGeomBlob;
    private StatementIface updateGeomWkt;

    /**
     * Marking this constructor for removal.
     * @deprecated use the Builder instead
     */
    @DeprecatedApi(since = "4.2.0", forRemoval = true)
    public SpatialCalculator(){
        this(false);
    }

    /**
     * Marking this constructor for removal.
     *
     * Examples:
     * <pre>
     *     // A standard SpatialCalculator
     *     // formerly new SpatialCalculator();
     *     new SpatialCalculator.Builder().build();
     *
     *     // A SpatialCalculator in memory
     *     // formerly new SpatialCalculator(true);
     *     new SpatialCalculator.Builder().inMemory().build();
     *
     *     // Allow Point feature Z data to pass through the calculator
     *     // Wasn't available previously
     *     new SpatialCalculator.Builder().includePointZDimension().build();
     *
     *     // In memory, and with Z data
     *     // Wasn't available previously
     *     new SpatialCalculator.Builder().inMemory().includePointZDimension().build();
     * </pre>
     *
     * @deprecated use the Builder instead
     */
    @DeprecatedApi(since = "4.2.0", forRemoval = true)
    public SpatialCalculator(boolean memory){
        this.dimension = 2;
        File tempDir = getRuntimeTempDir();
        memory |= (tempDir == null);
        try{
            File dbFile = null;
            if(!memory) {
                dbFile = IOProviderFactory.createTempFile("spatialcalc", ".tmp", getRuntimeTempDir());
                dbFile.deleteOnExit();
            }
            init(dbFile);
        }catch(Exception e){
            SQLiteException toThrow = new SQLiteException();
            toThrow.initCause(e);
            throw toThrow;
        }
    }

    /**
     * A new private constructor that takes Builder instance.
     * @param builder A Builder instance to initialize from
     */
    private SpatialCalculator(Builder builder) {
        this.dimension = builder.dimension;
        File tempDir = getRuntimeTempDir();
        boolean memory = builder.inMemory | (tempDir == null);
        try{
            File dbFile = null;
            if(!memory) {
                dbFile = File.createTempFile("spatialcalc", ".tmp", getRuntimeTempDir());
                dbFile.deleteOnExit();
            }
            init(dbFile);
        }catch(Exception e){
            SQLiteException toThrow = new SQLiteException();
            toThrow.initCause(e);
            throw toThrow;
        }
    }

    /**
     * Creates a new instance.
     */
    private void init(File dbFile) {
        this.spatialdbFile = dbFile;
        this.database = IOProviderFactory.createDatabase(dbFile);
        
        CursorIface result;

        final int major = FeatureSpatialDatabase.getSpatialiteMajorVersion(this.database);
        final int minor = FeatureSpatialDatabase.getSpatialiteMinorVersion(this.database);

        final String initSpatialMetadataSql;
        if(major > 4 || (major == 4 && minor >= 2))
            initSpatialMetadataSql = "SELECT InitSpatialMetadata(1, \'WGS84\')";
        else if (major > 4 || (major == 4 && minor >= 1))
            initSpatialMetadataSql = "SELECT InitSpatialMetadata(1)";
        else
            initSpatialMetadataSql = "SELECT InitSpatialMetadata()";

        result = null;
        try {
            result = this.database.query(initSpatialMetadataSql, null);
            result.moveToNext();
        } finally {
            if (result != null)
                result.close();
        }

        this.database.execute(
                "CREATE TABLE Calculator (id INTEGER PRIMARY KEY AUTOINCREMENT)",
                null);

        // if includeZ is true, use the XYZ dimension model
        final String dimensionModel = this.dimension == 3 ? "XYZ" : "XY";
        result = null;
        try {
            String query = String.format(
                    "SELECT AddGeometryColumn(\'Calculator\', \'geom\', 4326, \'GEOMETRY\', \'%s\')",
                    dimensionModel);
            result = this.database.query(query, null);
            result.moveToNext();
        } finally {
            if (result != null)
                result.close();
        }
    }
    
    /**
     * Clears the calculator instruction cache.
     */
    public void clearCache() {
        if(this.insertGeomWkt != null) {
            this.insertGeomWkt.close();
            this.insertGeomWkt = null;
        }
        if(this.insertGeomBlob != null) {
            this.insertGeomBlob.close();
            this.insertGeomBlob = null;
        }
        if(this.insertGeomWkb != null) {
            this.insertGeomWkb.close();
            this.insertGeomWkb = null;
        }
        if(this.insertPoint != null) {
            this.insertPoint.close();
            this.insertPoint = null;
        }
        if(this.bufferInsert != null) {
            this.bufferInsert.close();
            this.bufferInsert = null;
        }
        if(this.bufferUpdate != null) {
            this.bufferUpdate.close();
            this.bufferUpdate = null;
        }
        if(this.intersectionInsert != null) {
            this.intersectionInsert.close();
            this.intersectionInsert = null;
        }
        if(this.intersectionUpdate != null) {
            this.intersectionUpdate.close();
            this.intersectionUpdate = null;
        }
        if(this.unionInsert != null) {
            this.unionInsert.close();
            this.unionInsert = null;
        }
        if(this.unionUpdate != null) {
            this.unionUpdate.close();
            this.unionUpdate = null;
        }
        if(this.differenceInsert != null) {
            this.differenceInsert.close();
            this.differenceInsert = null;
        }
        if(this.differenceUpdate != null) {
            this.differenceUpdate.close();
            this.differenceUpdate = null;
        }
        if(this.simplifyInsert != null) {
            this.simplifyInsert.close();
            this.simplifyInsert = null;
        }
        if(this.simplifyUpdate != null) {
            this.simplifyUpdate.close();
            this.simplifyUpdate = null;
        }
        if(this.simplifyPreserveTopologyInsert != null) {
            this.simplifyPreserveTopologyInsert.close();
            this.simplifyPreserveTopologyInsert = null;
        }
        if(this.simplifyPreserveTopologyUpdate != null) {
            this.simplifyPreserveTopologyUpdate.close();
            this.simplifyPreserveTopologyUpdate = null;
        }
        if(this.clear != null) {
            this.clear.close();
            this.clear = null;
        }
        if(this.deleteGeom != null) {
            this.deleteGeom.close();
            this.deleteGeom = null;
        }
        
        this.arr1 = null;
        this.arr2 = null;
        this.wkt = null;
        this.quad = null;
    }

    /**
     * Disposes the calculator. All allocated resources are released. Any use of
     * the calculator following an invocation of this method will produce
     * undefined results.
     */
    public synchronized void dispose() {
        if(this.database != null) {
            stmtClose(insertGeomWkt);
            stmtClose(insertGeomBlob);
            stmtClose(insertGeomWkb);
            stmtClose(insertPoint);
            stmtClose(bufferInsert);
            stmtClose(bufferUpdate);
            stmtClose(intersectionInsert);
            stmtClose(intersectionUpdate);
            stmtClose(unionInsert);
            stmtClose(unionUpdate);
            stmtClose(unaryUnionInsert);
            stmtClose(unaryUnionUpdate);
            stmtClose(differenceInsert);
            stmtClose(differenceUpdate);
            stmtClose(simplifyInsert);
            stmtClose(simplifyUpdate);
            stmtClose(simplifyPreserveTopologyInsert);
            stmtClose(simplifyPreserveTopologyUpdate);
            stmtClose(clear);
            stmtClose(deleteGeom);
            stmtClose(updatePoint);
            stmtClose(updateGeomBlob);
            stmtClose(updateGeomWkt);

            try {
                try {
                    this.clearCache();
                } catch(Throwable ignored) {}

                try { 
                    this.database.close();
                } catch (SQLiteException ignored) {}
            } finally {
                this.database = null;
                if(this.spatialdbFile != null)
                    FileSystemUtils.delete(this.spatialdbFile);
            }

            this.disposed = true;
        }
    }

    /**
     * Calling any method on this calculator after <code>dispose()</code>
     * has been called will lead to undefined behavior.
     *
     * It is recommended that you check for <code>!calc.isDisposed()</code>
     * before making any calls.
     *
     * @return true if this calculator has had its <code>dispose()</code>
     *          method called, or if it is in the middle of initializing
     *          i.e. <code>init()</code> has not returned. Returns false
     *          otherwise.
     */
    public boolean isDisposed() {
        return this.disposed;
    }
    
    private void stmtClose(final StatementIface s) { 
       if (s != null)
           s.close();
    }

    /**
     * Clears the calculator's memory. Any previously obtained geometry handles
     * must be considered invalid.
     */
    public void clear() {
        if(this.clear == null)
            this.clear = this.database.compileStatement("DELETE FROM Calculator");
        this.clear.execute();
    }
    
    /**
     * Begins an instruction batch. The calculator will not actually commit any
     * instructions issued to memory until the batch is completed via
     * {@link #endBatch(boolean)}. During the batch, any created or modified
     * handles will reside in a special volatile memory available to all
     * functions. If the batch is ended successfully, the contents of the
     * volatile memory will be merged with the calculator's memory, otherwise
     * the volatile memory will be released effectively undoing all instructions
     * issued during the batch.
     * 
     * <P>Once a batch is started, instructions may only be issued to the
     * calculator on the thread that the batch was started on.
     */
    public void beginBatch() {
        this.database.beginTransaction();
    }
    
    /**
     * Ends the current batch. If <code>commit</code> is <code>true</code> all
     * instructions issued during the batch are committed to the calculator's
     * memory.
     * 
     * @param commit    <code>true</code> to commit the batch instructions,
     *                  <code>false</code> to undo.
     */
    public void endBatch(boolean commit) {
        if(commit)
            this.database.setTransactionSuccessful();
        this.database.endTransaction();
    }

    /**
     * Creates a new point in the calculator's memory.
     *
     * If 3D Point features are enabled, check if the altitude
     * information by <code>point.isAltitudeValid()</code>.
     *
     * If it is valid, allow that data to pass through. Otherwise,
     * set it to 0.0d (clamped to the ground) by default.
     * 
     * @param point A GeoPoint instance to create a feature Point from
     * 
     * @return  A handle to the point created in the calculator's memory.
     */
    public long createPoint(GeoPoint point) {
        Point pt;
        if (this.dimension == 3) {
            double z = point.isAltitudeValid() ? point.getAltitude() : 0.0d;
            pt = new Point(point.getLongitude(), point.getLatitude(), z);
        } else {
            pt = new Point(point.getLongitude(), point.getLatitude());
        }
        return this.createPoint(pt);
    }

    /**
     * Creates a new point in the calculator's memory.
     *
     * If 3D Point features are enabled, a 3D point feature
     * will be created.
     *
     * This method <emph>does not</emph> make any attempt to
     * determine the validity of the Z field.
     *
     * @param point A Point feature instance to copy
     *
     * @return  A handle to the point created in the calculator's memory.
     */
    public long createPoint(Point point) {
        final double[] bindArgs = new double[this.dimension];

        bindArgs[0] = point.getX();
        bindArgs[1] = point.getY();
        if (this.dimension == 3) {
            bindArgs[2] = point.getZ();
        }

        try {
            if(this.insertPoint == null) {
                String spatialiteFunc = this.dimension == 3 ? "MakePointZ" : "MakePoint";
                // a more dimension agnostic approach would use StringBuilder and a loop
                // but we're limited to 2 or 3 dimensions so this is fine
                String placeholders = this.dimension == 3 ? "?, ?, ?" : "?, ?";
                String formatter = "INSERT INTO Calculator (geom) VALUES(%s(%s, 4326))";
                String statement = String.format(formatter, spatialiteFunc, placeholders);
                this.insertPoint = this.database.compileStatement(statement);
            }

            for (int i = 0; i < this.dimension; i++) {
                this.insertPoint.bind(i + 1, bindArgs[i]);
            }

            this.insertPoint.execute();

            return Databases.lastInsertRowId(this.database);
        } finally {
            if(this.insertPoint != null)
                this.insertPoint.clearBindings();
        }
    }

    /**
     * Creates a new linestring in the calculator's memory.
     * 
     * @param points    The linestring
     * 
     * @return  A handle to the linestring created in the calculator's memory.
     */
    public long createLineString(GeoPoint[] points) {
        return this.createLineString(points.length, new ArrayIterator<GeoPoint>(points));
    }
    
    /**
     * Creates a new linestring in the calculator's memory.
     * 
     * @param points    The linestring
     * 
     * @return  A handle to the linestring created in the calculator's memory.
     */
    public long createLineString(Collection<GeoPoint> points) {
        return this.createLineString(points.size(), points.iterator());
    }
        
    private long createLineString(int numPoints, Iterator<GeoPoint> points) {
        LineString line = new LineString(2);
        GeoPoint geo;
        while(points.hasNext()) {
            geo = points.next();
            line.addPoint(geo.getLongitude(), geo.getLatitude());
        }
        
        switch(preferredCoding) {
            case FeatureDataSource.FeatureDefinition.GEOM_SPATIALITE_BLOB :
                return this.createGeometry(line);
            case FeatureDataSource.FeatureDefinition.GEOM_WKT :
                return this.createGeometry(createLineStringWkt(line));
            default :
                throw new IllegalArgumentException();
        }
    }

    /**
     * Creates a new polygon in the calculator's memory.
     * 
     * @param points    The exterior ring of the polygon
     * 
     * @return  A handle to the polygon created in the calculator's memory.
     */
    public long createPolygon(GeoPoint[] points) {
        return this.createPolygon(Arrays.asList(points));
    }
    
    /**
     * Creates a new polygon in the calculator's memory.
     * 
     * @param points        The exterior ring of the polygon
     * @param innerRings    The inner rings (holes) of the polygon
     * 
     * @return  A handle to the polygon created in the calculator's memory.
     */
    public long createPolygon(GeoPoint[] points, GeoPoint[][] innerRings) {
        if(innerRings.length == 0)
            return this.createPolygon(points);
        
        Collection<Collection<GeoPoint>> cinnerRings = new ArrayList<Collection<GeoPoint>>(innerRings.length);
        for(GeoPoint[] innerRing : innerRings)
            cinnerRings.add(Arrays.asList(innerRing));
        return this.createPolygon(Arrays.asList(points), cinnerRings);
    }
    
    /**
     * Creates a new polygon in the calculator's memory.
     * 
     * @param points    The exterior ring of the polygon
     * 
     * @return  A handle to the polygon created in the calculator's memory.
     */
    public long createPolygon(Collection<GeoPoint> points) {
        return this.createPolygon(points, Collections.<Collection<GeoPoint>>emptySet());
    }
    
    /**
     * Creates a new polygon in the calculator's memory.
     * 
     * @param points        The exterior ring of the polygon
     * @param innerRings    The inner rings (holes) of the polygon
     * 
     * @return  A handle to the polygon created in the calculator's memory.
     */
    public long createPolygon(Collection<GeoPoint> points, Collection<Collection<GeoPoint>> innerRings) {
        Polygon polygon = new Polygon(2);
        LineString ring;
        
        ring = new LineString(2);
        for(GeoPoint geo : points)
            ring.addPoint(geo.getLongitude(), geo.getLatitude());
        polygon.addRing(ring);
        for(Collection<GeoPoint> inner : innerRings) {
            ring = new LineString(2);
            for(GeoPoint geo : inner)
                ring.addPoint(geo.getLongitude(), geo.getLatitude());
            polygon.addRing(ring);
        }
        switch(preferredCoding) {
            case FeatureDataSource.FeatureDefinition.GEOM_SPATIALITE_BLOB :
                return this.createGeometry(createPolygonBlob(polygon));
            case FeatureDataSource.FeatureDefinition.GEOM_WKT :
                return this.createGeometry(createPolygonWkt(points, innerRings));
            default :
                throw new IllegalArgumentException();
        }
    }

    public long createGeometry(byte[] blob) {
        try {
            if(this.insertGeomBlob == null)
                this.insertGeomBlob = this.database.compileStatement("INSERT INTO Calculator (geom) VALUES(?)");
            this.insertGeomBlob.bind(1, blob);
            
            this.insertGeomBlob.execute();
            
            return Databases.lastInsertRowId(this.database);
        } finally {
            if(this.insertGeomBlob!= null)
                this.insertGeomBlob.clearBindings();
        }
    }

    public long createGeometry(Geometry geometry) {
        return this.createGeometry(createGeometryBlob(geometry));
    }

    public long createWkbGeometry(byte[] blob) {
        try {
            if(this.insertGeomWkb == null)
                this.insertGeomWkb = this.database.compileStatement("INSERT INTO Calculator (geom) VALUES(GeomFromWkb(?, 4326))");
            this.insertGeomWkb.bind(1, blob);
            
            this.insertGeomWkb.execute();
            
            return Databases.lastInsertRowId(this.database);
        } finally {
            if(this.insertGeomWkb!= null)
                this.insertGeomWkb.clearBindings();
        }
    }

    public long createGeometry(String wkt) {
        try {
            if(this.insertGeomWkt == null)
                this.insertGeomWkt = this.database.compileStatement("INSERT INTO Calculator (geom) VALUES(GeomFromText(?, 4326))");
            this.insertGeomWkt.bind(1, wkt);
            
            this.insertGeomWkt.execute();
            
            return Databases.lastInsertRowId(this.database);
        } finally {
            if(this.insertGeomWkt != null)
                this.insertGeomWkt.clearBindings();
        }
    }
    
    /**
     * Creates a new quadrilateral polygon in the calculators memory. This
     * method may perform significantly faster than the other polygon creation
     * methods for creating a simple quadrilateral.
     * 
     * @param a A corner of the quadrilateral
     * @param b A corner of the quadrilateral
     * @param c A corner of the quadrilateral
     * @param d A corner of the quadrilateral
     * 
     * @return  A handle to the polygon created in the calculator's memory.
     */
    public long createPolygon(GeoPoint a, GeoPoint b, GeoPoint c, GeoPoint d) {
        return this.createGeometry(this.createQuadBlob(a, b, c, d));
    }
    
    /**
     * Deletes the specified geometry from the calculator's memory.
     * 
     * @param handle    The handle to the geometry in the calculator's memory.
     */
    public void deleteGeometry(long handle) {
        try {
            if(this.deleteGeom == null)
                this.deleteGeom = this.database.compileStatement("DELETE FROM Calculator WHERE id = ?");
            this.deleteGeom.bind(1, handle);
            this.deleteGeom.execute();
        } finally {
            if(this.deleteGeom != null)
                this.deleteGeom.clearBindings();
        }
    }
    
    /**
     * Returns the geometry type for the specified geometry in the calculator's
     * memory.
     * 
     * @param handle    The handle to the geometry in the calculator's memory
     * 
     * @return  The geometry type
     * 
     * @see #GEOM_TYPE_POINT
     * @see #GEOM_TYPE_LINESTRING
     * @see #GEOM_TYPE_POLYGON
     * @see #GEOM_TYPE_MULTIPOINT
     * @see #GEOM_TYPE_MULTILINESTRING
     * @see #GEOM_TYPE_MULTIPOLYGON
     * @see #GEOM_TYPE_GEOMETRYCOLLECTION
     */
    public int getGeometryType(long handle) {
        CursorIface result = null;
        try {
            if(this.arr1 == null)
                this.arr1 = new String[1];
            this.arr1[0] = String.valueOf(handle);
            result = this.database.query("SELECT GeometryType((SELECT geom FROM Calculator WHERE id = ?))", this.arr1);
            if(!result.moveToNext())
                return 0;
            return result.getInt(0);
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    /**
     * Returns the specified geometry from the calculator's memory as a
     * SpataiLite blob.
     * 
     * @param handle    The handle to the geometry in the calculator's memory
     * 
     * @return  The specified geometry from the calculator's memory as a
     *          SpataiLite blob.
     *          
     * @see <a href=http://www.gaia-gis.it/gaia-sins/BLOB-Geometry.html>BLOB-Geometry</a>
     */
    public byte[] getGeometryAsBlob(long handle) {
        CursorIface result = null;
        try {
            if(this.arr1 == null)
                this.arr1 = new String[1];
            this.arr1[0] = String.valueOf(handle);
            result = this.database.query("SELECT geom FROM Calculator WHERE id = ?", this.arr1);
            if(!result.moveToNext())
                return null;
            byte[] retVal = result.getBlob(0);
            return retVal;
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    /**
     * Returns the specified geometry from the calculator's memory as a
     * Well-Known Text string.
     * 
     * @param handle    The handle to the geometry in the calculator's memory
     * 
     * @return  The specified geometry from the calculator's memory as a
     *          Well-Known Text string        
     */
    public String getGeometryAsWkt(long handle) {
        CursorIface result = null;
        try {
            if(this.arr1 == null)
                this.arr1 = new String[1];
            this.arr1[0] = String.valueOf(handle);
            result = this.database.query("SELECT AsText(geom) FROM Calculator WHERE id = ?", this.arr1);
            if(!result.moveToNext())
                return null;
            return result.getString(0);
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    /**
     * Returns the specified geometry from the calculator's memory.
     * 
     * @param handle    The handle to the geometry in the calculator's memory
     * 
     * @return  The specified geometry from the calculator's memory
     */
    public Geometry getGeometry(long handle) {
        CursorIface result = null;
        try {
            if(this.arr1 == null)
                this.arr1 = new String[1];
            this.arr1[0] = String.valueOf(handle);
            result = this.database.query("SELECT geom FROM Calculator WHERE id = ?", this.arr1);
            if(!result.moveToNext())
                return null;
            return GeometryFactory.parseSpatiaLiteBlob(result.getBlob(0));
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    /**
     * Tests the two geometries for intersection.
     * 
     * @param geom1 A handle to the geometry in the calculator's memory
     * @param geom2 A handle to the geometry in the calculator's memory
     * 
     * @return  <code>true</code> if the two geometries intersect,
     *          <code>false</code> otherwise.
     */
    public boolean intersects(long geom1, long geom2) {
        CursorIface result = null;
        try {
            if(this.arr2 == null)
                this.arr2 = new String[2];
            this.arr2[0] = String.valueOf(geom1);
            this.arr2[1] = String.valueOf(geom2);
            result = this.database.query("SELECT Intersects((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?))", this.arr2);
            if(!result.moveToNext())
                return false;
            return (result.getInt(0)==1);
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    /**
     * Returns <code>true</code> if <code>geom1</code> contains
     * <code>geom2</code>.
     * 
     * @param geom1 A handle to the geometry in the calculator's memory
     * @param geom2 A handle to the geometry in the calculator's memory
     * 
     * @return  <code>true</code> if <code>geom1</code> contains
     *          <code>geom2</code>, <code>false</code> otherwise.
     */
    public boolean contains(long geom1, long geom2) {
        CursorIface result = null;
        try {
            if(this.arr2 == null)
                this.arr2 = new String[2];
            this.arr2[0] = String.valueOf(geom1);
            this.arr2[1] = String.valueOf(geom2);
            result = this.database.query("SELECT Contains((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?))", this.arr2);
            if(!result.moveToNext())
                return false;
            return (result.getInt(0)==1);
        } finally {
            if(result != null)
                result.close();
        }
    }

    /**
     * Returns the intersection of the specified geometries as a new geometry in
     * the calculator's memory.
     * 
     * @param geom1 A handle to the geometry in the calculator's memory
     * @param geom2 A handle to the geometry in the calculator's memory
     * 
     * @return  A handle to the new geometry that is the intersection of the
     *          specified geometries.
     */
    public long intersection(long geom1, long geom2) {
        try {
            if(this.intersectionInsert == null)
                this.intersectionInsert = this.database.compileStatement("INSERT INTO Calculator (geom) SELECT Intersection((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?))");
            this.intersectionInsert.bind(1, geom1);
            this.intersectionInsert.bind(2, geom2);
            
            this.intersectionInsert.execute();
            
            return Databases.lastInsertRowId(this.database);
        } finally {
            if(this.intersectionInsert != null)
                this.intersectionInsert.clearBindings();
        }
    }
    
    /**
     * Performs the intersection operation on the specified geometries and
     * stores the result in the specified memory location. This method may
     * perform significantly faster than {@link #intersection(long, long)}.
     * 
     * @param geom1     A handle to the geometry in the calculator's memory
     * @param geom2     A handle to the geometry in the calculator's memory
     * @param result    The memory location to store the intersection result.
     *                  this location must already exist. The same value as
     *                  <code>geom1</code> or <code>geom2</code> may be
     *                  specified in which case the old geometry will be
     *                  overwritten with the result.
     */ 
    public void intersection(long geom1, long geom2, long result) {
        try {
            if(this.intersectionUpdate == null)
                this.intersectionUpdate = this.database.compileStatement("UPDATE Calculator SET geom = Intersection((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?)) WHERE id = ?");
            this.intersectionUpdate.bind(1, geom1);
            this.intersectionUpdate.bind(2, geom2);
            this.intersectionUpdate.bind(2, result);
            
            this.intersectionUpdate.execute();
        } finally {
            if(this.intersectionUpdate != null)
                this.intersectionUpdate.clearBindings();
        }
    }
    
    /**
     * Returns the union of the specified geometries as a new geometry in the
     * calculator's memory.
     * 
     * @param geom1 A handle to the geometry in the calculator's memory
     * @param geom2 A handle to the geometry in the calculator's memory
     * 
     * @return  A handle to the new geometry that is the union of the specified
     *          geometries.
     */
    public long union(long geom1, long geom2) {
        try {
            if(this.unionInsert == null)
                this.unionInsert = this.database.compileStatement("INSERT INTO Calculator (geom) SELECT GUnion(geom) FROM Calculator WHERE id IN (?, ?)");
            this.unionInsert.bind(1, geom1);
            this.unionInsert.bind(2, geom2);
            
            this.unionInsert.execute();
            
            return Databases.lastInsertRowId(this.database);
        } finally {
            if(this.unionInsert != null)
                this.unionInsert.clearBindings();
        }
    }

    /**
     * Performs the union operation on the specified geometries and stores the
     * result in the specified memory location. This method may perform
     * significantly faster than {@link #union(long, long)}.
     * 
     * @param geom1     A handle to the geometry in the calculator's memory
     * @param geom2     A handle to the geometry in the calculator's memory
     * @param result    The memory location to store the union result. This
     *                  location must already exist. The same value as
     *                  <code>geom1</code> or <code>geom2</code> may be
     *                  specified in which case the old geometry will be
     *                  overwritten with the result.
     */ 
    public void union(long geom1, long geom2, long result) {
        try {
            if(this.unionUpdate == null)
                this.unionUpdate = this.database.compileStatement("UPDATE Calculator SET geom = GUnion((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?)) WHERE id = ?");
            this.unionUpdate.bind(1, geom1);
            this.unionUpdate.bind(2, geom2);
            this.unionUpdate.bind(3, result);
            this.unionUpdate.execute();
        } finally {
            if(this.unionUpdate != null)
                this.unionUpdate.clearBindings();
        }
    }
    
    /**
     * Performs the union operation on the specified geometries and stores the
     * result in the specified memory location. Before the union is performed, both
     * geometries will be buffered as if the {@link #buffer(long, double, long)}
     * method was called. 
     * 
     * @param geom1     A handle to the geometry in the calculator's memory
     * @param geom2     A handle to the geometry in the calculator's memory
     * @param dist      The buffer distance, in degrees
     * @param result    The memory location to store the union result. This
     *                  location must already exist. The same value as
     *                  <code>geom1</code> or <code>geom2</code> may be
     *                  specified in which case the old geometry will be
     *                  overwritten with the result.
     */ 
    public void unionWithBuffer(long geom1, long geom2, double dist, long result) {
        try {
            if(this.unionUpdate == null)
                this.unionUpdate = this.database.compileStatement("UPDATE Calculator SET geom = GUnion((SELECT geom FROM Calculator WHERE id = ?), Buffer((SELECT geom FROM Calculator WHERE id = ?),?)) WHERE id = ?");
            this.unionUpdate.bind(1, geom1);
            this.unionUpdate.bind(2, geom2);
            this.unionUpdate.bind(3, dist);
            this.unionUpdate.bind(4, result);
            
            this.unionUpdate.execute();
        } finally {
            if(this.unionUpdate != null)
                this.unionUpdate.clearBindings();
        }
    }
    
    /**
     * Computes the unary union of a GeometryCollection.
     * 
     * @param geom  The handle to the GeometryCollection
     * 
     * @return  The unary union result
     */
    public long unaryUnion(long geom) {
        try {
            if(this.unaryUnionInsert == null)
                this.unaryUnionInsert = this.database.compileStatement("INSERT INTO Calculator (geom) SELECT UnaryUnion(geom) FROM Calculator WHERE id  = ?");
            this.unaryUnionInsert.bind(1, geom);
            
            this.unaryUnionInsert.execute();
            
            return Databases.lastInsertRowId(this.database);
        } finally {
            if(this.unaryUnionInsert != null)
                this.unaryUnionInsert.clearBindings();
        }
    }

    /**
     * Computes the unary union of a GeometryCollection.
     * 
     * @param geom  The handle to the GeometryCollection
     * 
     * @return  The unary union result
     */
    public void unaryUnion(long geom, long result) {
        try {
            if(this.unaryUnionUpdate == null)
                this.unaryUnionUpdate = this.database.compileStatement("UPDATE Calculator SET geom = UnaryUnion((SELECT geom FROM Calculator WHERE id = ?)) WHERE id = ?");
            this.unaryUnionUpdate.bind(1, geom);
            this.unaryUnionUpdate.bind(2, result);
            this.unaryUnionUpdate.execute();
        } finally {
            if(this.unaryUnionUpdate != null)
                this.unaryUnionUpdate.clearBindings();
        }
    }
    
    /**
     * Returns the difference of the specified geometries as a new geometry in
     * the calculator's memory.
     * 
     * @param geom1 A handle to the geometry in the calculator's memory
     * @param geom2 A handle to the geometry in the calculator's memory
     * 
     * @return  A handle to the new geometry that is the difference of the
     *          specified geometries.
     */
    public long difference(long geom1, long geom2) {
        try {
            if(this.differenceInsert == null)
                this.differenceInsert = this.database.compileStatement("INSERT INTO Calculator (geom) SELECT Difference((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?))");
            this.differenceInsert.bind(1, geom1);
            this.differenceInsert.bind(2, geom2);
            
            this.differenceInsert.execute();
            
            return Databases.lastInsertRowId(this.database);
        } finally {
            if(this.differenceInsert != null)
                this.differenceInsert.clearBindings();
        }
    }

    /**
     * Performs the difference operation on the specified geometries and stores
     * the result in the specified memory location. This method may perform
     * significantly faster than {@link #difference(long, long)}.
     * 
     * @param geom1     A handle to the geometry in the calculator's memory
     * @param geom2     A handle to the geometry in the calculator's memory
     * @param result    The memory location to store the difference result. This
     *                  location must already exist. The same value as
     *                  <code>geom1</code> or <code>geom2</code> may be
     *                  specified in which case the old geometry will be
     *                  overwritten with the result.
     */ 
    public void difference(long geom1, long geom2, long result) {
        try {
            if(this.differenceUpdate == null)
                this.differenceUpdate = this.database.compileStatement("UPDATE Calculator SET geom = Difference((SELECT geom FROM Calculator WHERE id = ?), (SELECT geom FROM Calculator WHERE id = ?)) WHERE id = ?");
            this.differenceUpdate.bind(1, geom1);
            this.differenceUpdate.bind(2, geom2);
            this.differenceUpdate.bind(3, result);
            
            this.differenceUpdate.execute();
        } finally {
            if(this.differenceUpdate != null)
                this.differenceUpdate.clearBindings();
        }
    }

    /**
     * Returns the simplification of the specified geometry as a new geometry in
     * the calculator's memory.
     * 
     * @param handle            A handle to the geometry to be simplified
     * @param tolerance         The simplification tolerance, in degrees
     * @param preserveTopology  <code>true</code> to preserve topology,
     *                          <code>false</code> otherwise.
     *
     * @return  A handle to the new geometry that is the simplification of the
     *          specified geometry.
     */
    public long simplify(long handle, double tolerance, boolean preserveTopology) {
        StatementIface stmt = null;
        try {
            if(preserveTopology) {
                if(this.simplifyPreserveTopologyInsert == null)
                    this.simplifyPreserveTopologyInsert = this.database.compileStatement("INSERT INTO Calculator (geom) SELECT SimplifyPreserveTopology((SELECT geom FROM Calculator WHERE id = ?), ?)");
                stmt = this.simplifyPreserveTopologyInsert;
            } else {
                if(this.simplifyInsert == null)
                    this.simplifyInsert = this.database.compileStatement("INSERT INTO Calculator (geom) SELECT Simplify((SELECT geom FROM Calculator WHERE id = ?), ?)");
                stmt = this.simplifyInsert;
            }

            stmt.bind(1, handle);
            stmt.bind(2, tolerance);
            
            stmt.execute();
            
            return Databases.lastInsertRowId(this.database);
        } finally {
            if(stmt != null)
                stmt.clearBindings();
        }
    }
    
    /**
     * Performs the simplification operation on the specified geometry and
     * stores the result in the specified memory location. This method may
     * perform significantly faster than
     * {@link #simplify(long, double, boolean)}.
     * 
     * @param handle            A handle to the geometry to be simplified
     * @param tolerance         The buffer distance, in degrees
     * @param preserveTopology  <code>true</code> to preserve topology,
     *                          <code>false</code> otherwise.
     * @param result            The memory location to store the simplification
     *                          of <code>handle</code>. This location must
     *                          already exist. The same value as
     *                          <code>handle</code> may be specified in which
     *                          case the old geometry will be overwritten with
     *                          the result.
     */ 
    public void simplify(long handle, double tolerance, boolean preserveTopology, long result) {
        StatementIface stmt = null;
        try {
            if(preserveTopology) {
                if(this.simplifyPreserveTopologyUpdate == null)
                    this.simplifyPreserveTopologyUpdate = this.database.compileStatement("UPDATE Calculator SET geom = SimplifyPreserveTopology((SELECT geom FROM Calculator WHERE id = ?), ?) WHERE id = ?");
                stmt = this.simplifyPreserveTopologyUpdate;
            } else {
                if(this.simplifyUpdate == null)
                    this.simplifyUpdate = this.database.compileStatement("UPDATE Calculator SET geom = Simplify((SELECT geom FROM Calculator WHERE id = ?), ?) WHERE id = ?");
                stmt = this.simplifyUpdate;
            }

            stmt.bind(1, handle);
            stmt.bind(2, tolerance);
            stmt.bind(3, result);
            
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.clearBindings();
        }
    }
    
    /**
     * Returns the simplification of the specified linestring as a new
     * linestring.
     * 
     * @param points            A linestring
     * @param tolerance         The simplification tolerance, in degrees
     * @param preserveTopology  <code>true</code> to preserve topology,
     *                          <code>false</code> otherwise.
     *
     * @return  The new linestring that is the simplification of the specified
     *          linestring.
     */
    public Collection<GeoPoint> simplify(GeoPoint[] points, double tolerance, boolean preserveTopology) {
        return this.simplify(points.length, new ArrayIterator<GeoPoint>(points), tolerance, preserveTopology);
    }
    
    /**
     * Returns the simplification of the specified linestring as a new
     * linestring.
     * 
     * @param points            A linestring
     * @param tolerance         The simplification tolerance, in degrees
     * @param preserveTopology  <code>true</code> to preserve topology,
     *                          <code>false</code> otherwise.
     *
     * @return  The new linestring that is the simplification of the specified
     *          linestring.
     */
    public Collection<GeoPoint> simplify(Collection<GeoPoint> points, double tolerance, boolean preserveTopology) {
        return this.simplify(points.size(), points.iterator(), tolerance, preserveTopology);
    }
    
    private Collection<GeoPoint> simplify(int numPoints, Iterator<GeoPoint> points, double tolerance, boolean preserveTopology) {
        long handle = 0L;
        try {
            handle = this.createLineString(numPoints, points);
            this.simplify(handle, tolerance, preserveTopology, handle);

            byte[] blob = this.getGeometryAsBlob(handle);
            if(blob != null)
                return parseLineString(blob);
        } finally {
            if(handle != 0L)
                this.deleteGeometry(handle);
        }
        return null;
    }
    
    /**
     * Returns the buffer of the specified geometry as a new geometry in the
     * calculator's memory.
     * 
     * @param handle    A handle to the geometry to be buffered
     * @param dist      The buffer distance, in degrees
     *
     * @return  A handle to the new geometry that is the buffer of the specified
     *          geometry.
     */    
    public long buffer(long handle, double dist) {
        try {
            if(this.bufferInsert == null)
                this.bufferInsert = this.database.compileStatement("INSERT INTO Calculator (geom) SELECT Buffer((SELECT geom FROM Calculator WHERE id = ?), ?)");

            this.bufferInsert.bind(1, handle);
            this.bufferInsert.bind(2, dist);
            
            this.bufferInsert.execute();
            
            return Databases.lastInsertRowId(this.database);
        } finally {
            if(this.bufferInsert != null)
                this.bufferInsert.clearBindings();
        }
    }
    
    /**
     * Performs the buffer operation on the specified geometry and stores the
     * result in the specified memory location. This method may perform
     * significantly faster than {@link #buffer(long, double)}.
     * 
     * @param handle    A handle to the geometry to be buffered
     * @param dist      The buffer distance, in degrees
     * @param result    The memory location to store the buffer of
     *                  <code>handle</code>. This location must already exist.
     *                  The same value as <code>handle</code> may be specified
     *                  in which case the old geometry will be overwritten with
     *                  the result.
     */ 
    public void buffer(long handle, double dist, long result) {
        try {
            if(this.bufferUpdate == null)
                this.bufferUpdate = this.database.compileStatement("UPDATE Calculator SET geom = Buffer((SELECT geom FROM Calculator WHERE id = ?), ?) WHERE id = ?");

            this.bufferUpdate.bind(1, handle);
            this.bufferUpdate.bind(2, dist);
            this.bufferUpdate.bind(3, result);
            
            this.bufferUpdate.execute();
        } finally {
            if(this.bufferUpdate != null)
                this.bufferUpdate.clearBindings();
        }
    }

    /**************************************************************************/
    // Object
    
    @Override
    protected void finalize() throws Throwable {
        try {
            this.dispose();
        } finally {
            super.finalize();
        }
    }

    /**************************************************************************/
    
    private String createLineStringWkt(LineString lineString) {
        if(lineString.getNumPoints() < 1)
            throw new IllegalArgumentException();
        
        try {
            if(this.wkt == null)
                this.wkt = new StringBuilder();
            this.wkt = new StringBuilder("LINESTRING(");
                        
            this.wkt.append(lineString.getX(0));
            this.wkt.append(" ");
            this.wkt.append(lineString.getY(0));
            for(int i = 1; i < lineString.getNumPoints(); i++) {
                this.wkt.append(", ");
                this.wkt.append(lineString.getX(i));
                this.wkt.append(" ");
                this.wkt.append(lineString.getY(i));
            }
            this.wkt.append(")");
            return this.wkt.toString();
        } finally {
            if(this.wkt != null)
                this.wkt.setLength(0);
        }
    }

    private static byte[] createPolygonBlob(Polygon poly) {
        LineString extRing = poly.getExteriorRing();
        if(extRing == null || extRing.getNumPoints() < 1)
            throw new IllegalArgumentException();

        int geomLen = 4 + 4 + (poly.getExteriorRing().getNumPoints()*16);
        Collection<LineString> innerRings = poly.getInteriorRings();
        for(LineString innerRing : innerRings)
            geomLen += 4 + (innerRing.getNumPoints()*16);

        Envelope mbr = poly.getEnvelope();

        ByteBuffer buffer = ByteBuffer.wrap(new byte[43 + geomLen + 1]);
        buffer.order(ByteOrder.nativeOrder());
        
        buffer.put((byte)0x00);
        buffer.put(NATIVE_BLOB_ENDIAN);
        buffer.putInt(4326);
        buffer.putDouble(mbr.minX);
        buffer.putDouble(mbr.minY);
        buffer.putDouble(mbr.maxX);
        buffer.putDouble(mbr.maxY);
        buffer.put((byte)0x7C);
        buffer.putInt(0x03);
        
        buffer.putInt(1+innerRings.size());

        buffer.putInt(extRing.getNumPoints());        
        for(int i = 0; i < extRing.getNumPoints(); i++) {
            buffer.putDouble(extRing.getX(i));
            buffer.putDouble(extRing.getY(i));            
        }
        
        for(LineString innerRing : innerRings) {
            buffer.putInt(innerRing.getNumPoints());
            for(int i = 0; i < innerRing.getNumPoints(); i++) {
                buffer.putDouble(innerRing.getX(i));
                buffer.putDouble(innerRing.getY(i));
            }            
        }

        buffer.put((byte)0xFE);
        
        return buffer.array();
    }

    private byte[] createQuadBlob(GeoPoint a, GeoPoint b, GeoPoint c, GeoPoint d) {
        if(this.quad == null)
            this.quad = new QuadBlob();
        return this.quad.getBlob(a, b, c, d);
    }
    
    private String createPolygonWkt(Collection<GeoPoint> points, Collection<Collection<GeoPoint>> innerRings) {
        if(points.size() < 1)
            throw new IllegalArgumentException();

        try {
            if(this.wkt == null)
                this.wkt = new StringBuilder();

            wkt.append("POLYGON((");
    
            Iterator<GeoPoint> pointIter;
            GeoPoint point;
            
            pointIter = points.iterator();
            
            point = pointIter.next();
            wkt.append(point.getLongitude());
            wkt.append(" ");
            wkt.append(point.getLatitude());
            
            while(pointIter.hasNext()) {
                point = pointIter.next();
                wkt.append(", ");
                wkt.append(point.getLongitude());
                wkt.append(" ");
                wkt.append(point.getLatitude());
            }
            wkt.append(")");
            
            for(Collection<GeoPoint> innerRing : innerRings) {
                wkt.append("(");
                pointIter = innerRing.iterator();
                if(pointIter.hasNext()) {
                    point = pointIter.next();
                    wkt.append(point.getLongitude());
                    wkt.append(" ");
                    wkt.append(point.getLatitude());
                    while(pointIter.hasNext()) {
                        point = pointIter.next();
                        wkt.append(", ");
                        wkt.append(point.getLongitude());
                        wkt.append(" ");
                        wkt.append(point.getLatitude());
                    }
                }
                wkt.append(")");
            }
            wkt.append(")");
            return wkt.toString();
        } finally {
            if(this.wkt != null)
                wkt.setLength(0);
        }
    }

    /**
     * Updated polygon in the calculator's memory.
     *
     * @param handle    A handle to the polygon created in the calculator's memory.
     * @param points    The exterior ring of the polygon
     */
    public void updatePolygon(long handle, GeoPoint[] points) {
        this.updatePolygon(handle, Arrays.asList(points));
    }

    /**
     * Updated polygon in the calculator's memory.
     *
     * @param handle    A handle to the polygon created in the calculator's memory.
     * @param points    The exterior ring of the polygon
     */
    public void updatePolygon(long handle, Collection<GeoPoint> points) {
        this.updatePolygon(handle, points, Collections.<Collection<GeoPoint>>emptySet());
    }

    /**
     * Updated polygon in the calculator's memory.
     *
     * @param handle    A handle to the polygon created in the calculator's memory.
     * @param points    The exterior ring of the polygon
     */
    public void updatePolygon(long handle, Collection<GeoPoint> points, Collection<Collection<GeoPoint>> innerRings) {
        Polygon polygon = new Polygon(2);
        LineString ring;
        
        ring = new LineString(2);
        for(GeoPoint geo : points)
            ring.addPoint(geo.getLongitude(), geo.getLatitude());
        polygon.addRing(ring);
        for(Collection<GeoPoint> inner : innerRings) {
            ring = new LineString(2);
            for(GeoPoint geo : inner)
                ring.addPoint(geo.getLongitude(), geo.getLatitude());
            polygon.addRing(ring);
        }

        switch(preferredCoding) {
            case FeatureDataSource.FeatureDefinition.GEOM_SPATIALITE_BLOB :
                this.updateGeometry(handle, createPolygonBlob(polygon));
                return;
            case FeatureDataSource.FeatureDefinition.GEOM_WKT :
                this.updateGeometry(handle, createPolygonWkt(points, innerRings));
                return;
            default :
                throw new IllegalArgumentException("coding=" + preferredCoding);
        }
    }


    /**
     * Update a quadrilateral polygon in the calculators memory. This
     * method may perform significantly faster than the other polygon creation
     * methods for creating a simple quadrilateral.
     *
     * @param handle A handle to the polygon created in the calculator's memory.
     * @param a A corner of the quadrilateral
     * @param b A corner of the quadrilateral
     * @param c A corner of the quadrilateral
     * @param d A corner of the quadrilateral
     *
     * @return  A handle to the polygon created in the calculator's memory.
     */
    public void updatePolygon(long handle, GeoPoint a, GeoPoint b, GeoPoint c, GeoPoint d) {
        this.updateGeometry(handle, this.createQuadBlob(a, b, c, d));
    }



    private void updateGeometry(long handle, byte[] blob) {
        try {
            if(this.updateGeomBlob == null)
                this.updateGeomBlob = this.database.compileStatement("UPDATE Calculator SET geom = ? WHERE id = ?");

            this.updateGeomBlob.bind(1, blob);
            this.updateGeomBlob.bind(2, handle);

            this.updateGeomBlob.execute();
        } finally {
            if(this.updateGeomBlob!= null)
                this.updateGeomBlob.clearBindings();
        }
    }

    private void updateGeometry(long handle, String wkt) {
        try {
            if(this.updateGeomWkt == null)
                this.updateGeomWkt = this.database.compileStatement("UPDATE Calculator SET geom = GeomFromText(?, 4326) WHERE id = ?");

            this.updateGeomWkt.bind(1, wkt);
            this.updateGeomWkt.bind(2, handle);

            this.updateGeomWkt.execute();
        } finally {
            if(this.updateGeomWkt != null)
                this.updateGeomWkt.clearBindings();
        }
    }


    public void updatePoint(long handle, GeoPoint point) {
        try {
            if(this.updatePoint == null)
                this.updatePoint = this.database.compileStatement("UPDATE Calculator SET geom = MakePoint(?, ?, 4326) WHERE id = ?");

            this.updatePoint.bind(1, point.getLongitude());
            this.updatePoint.bind(2, point.getLatitude());
            this.updatePoint.bind(3, handle);

            this.updatePoint.execute();
        } finally {
            if(this.updatePoint!= null)
                this.updatePoint.clearBindings();
        }
    }
    
    private static GeoPoint parsePointClass(ByteBuffer blob) {
        final double lng = blob.getDouble();
        final double lat = blob.getDouble();
        return new GeoPoint(lat, lng);
    }

    private static Collection<GeoPoint> parseLineString(byte[] array) {
        ByteBuffer blob = ByteBuffer.wrap(array);
        
        switch(blob.get(1)&0xFF) {
            case 0x00 :
                blob.order(ByteOrder.BIG_ENDIAN);
                break;
            case 0x01 :
                blob.order(ByteOrder.LITTLE_ENDIAN);
                break;
            default :
                throw new IllegalStateException();
        }
        if(blob.getInt(39) != GEOM_TYPE_LINESTRING)
            throw new IllegalArgumentException();
        blob.position(43);
        return parseLineStringClass(blob);
    }
    
    private static Collection<GeoPoint> parseLineStringClass(ByteBuffer blob) {
        final int numPoints = blob.getInt();
        Collection<GeoPoint> retval = new ArrayList<GeoPoint>(numPoints);
        for(int i = 0; i < numPoints; i++)
            retval.add(parsePointClass(blob));
        return retval;
    }
    
    public Collection<Collection<GeoPoint>> parsePolygon(byte[] array) {
        ByteBuffer blob = ByteBuffer.wrap(array);
        
        switch(blob.get(1)&0xFF) {
            case 0x00 :
                blob.order(ByteOrder.BIG_ENDIAN);
                break;
            case 0x01 :
                blob.order(ByteOrder.LITTLE_ENDIAN);
                break;
            default :
                throw new IllegalStateException();
        }
        if(blob.getInt(39) != GEOM_TYPE_POLYGON)
            throw new IllegalArgumentException();
        blob.position(43);
        return parsePolygonClass(blob);
    }

    private static Collection<Collection<GeoPoint>> parsePolygonClass(ByteBuffer blob) {
        final int numRings = blob.getInt();
        Collection<Collection<GeoPoint>> retval = new ArrayList<Collection<GeoPoint>>(numRings);
        for(int i = 0; i < numRings; i++) {
            retval.add(parseLineStringClass(blob));
        }
        return retval;
    }
    
    public Collection<Collection<GeoPoint>> parseMultiLineString(byte[] array) {
        ByteBuffer blob = ByteBuffer.wrap(array);
        
        switch(blob.get(1)&0xFF) {
            case 0x00 :
                blob.order(ByteOrder.BIG_ENDIAN);
                break;
            case 0x01 :
                blob.order(ByteOrder.LITTLE_ENDIAN);
                break;
            default :
                throw new IllegalStateException();
        }
        if(blob.getInt(39) != GEOM_TYPE_MULTILINESTRING)
            throw new IllegalArgumentException();
        blob.position(43);
        final int numLineStrings = blob.getInt();
        Collection<Collection<GeoPoint>> retval = new ArrayList<Collection<GeoPoint>>(numLineStrings);
        for(int i = 0; i < numLineStrings; i++) {
            if((blob.get()&0xFF) != 0x69)
                throw new IllegalArgumentException();
            if(blob.getInt() != GEOM_TYPE_LINESTRING)
                throw new IllegalArgumentException();

            retval.add(parseLineStringClass(blob));
        }
        return retval;
    }
    
    public Collection<Collection<Collection<GeoPoint>>> parseMultiPolygon(byte[] array) {
        ByteBuffer blob = ByteBuffer.wrap(array);
        
        switch(blob.get(1)&0xFF) {
            case 0x00 :
                blob.order(ByteOrder.BIG_ENDIAN);
                break;
            case 0x01 :
                blob.order(ByteOrder.LITTLE_ENDIAN);
                break;
            default :
                throw new IllegalStateException();
        }
        if(blob.getInt(39) != GEOM_TYPE_MULTIPOLYGON)
            throw new IllegalArgumentException();
        blob.position(43);
        final int numPolygons = blob.getInt();
        Collection<Collection<Collection<GeoPoint>>> retval = new ArrayList<Collection<Collection<GeoPoint>>>(numPolygons);
        for(int i = 0; i < numPolygons; i++) {
            if((blob.get()&0xFF) != 0x69)
                throw new IllegalArgumentException();
            if(blob.getInt() != GEOM_TYPE_POLYGON)
                throw new IllegalArgumentException();

            retval.add(parsePolygonClass(blob));
        }
        return retval;
    }

    /**
     * Returns the runtime directory where <code>SpatialCalculator</code>
     * database files should be stored. If <code>null</code>, no directory is
     * available and in-memory databases must be used.
     *
     * @return
     */
    private static File getRuntimeTempDir() {
        if(!IOProviderFactory.exists(_tmpDir)) {
            do {
                if(IOProviderFactory.mkdirs(_tmpDir))
                    break;
                if(_tmpDir == DEFAULT_TMP_DIR)
                    break;
                _tmpDir = DEFAULT_TMP_DIR;
            } while(true);
            // if the temporary directory does not exist, no runtime temp dir
            if(!IOProviderFactory.exists(_tmpDir))
                return null;

            // try to create the runtime temp dir
            try {
                _runtimeTempDir = FileSystemUtils.createTempDir("spatialcalc", null, _tmpDir);
            } catch(IOException ignored) {}
        } else if(_runtimeTempDir == null) {
            // the temp dir exists, try to create the runtime temp subdirectory
            try {
                _runtimeTempDir = FileSystemUtils.createTempDir("spatialcalc", null, _tmpDir);
            } catch(IOException ignored) {}
        }
        return _runtimeTempDir;
    }
    
    public static void initTmpDir(Context context) {
        _tmpDir = new File(context.getFilesDir(), ".tmp");
        _runtimeTempDir = null;
        getRuntimeTempDir();
        cleanup(_tmpDir, _runtimeTempDir, false);
    }
    
    private static void cleanup(final File dir, final File exclude, final boolean removeDir) {
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    final String[] children = IOProviderFactory.list(dir, new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String filename) {
                            return filename.startsWith("spatialcalc");
                        }
                    });
                    if(children != null) {
                        for (int i = 0; i < children.length; i++) {
                            File f;
                            try {
                                f = new File(dir, children[i]);
                                if(exclude != null && f.equals(exclude))
                                    continue;
                                FileSystemUtils.delete(f);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    if(removeDir)
                        FileSystemUtils.delete(dir);
                } catch(Throwable ignored) {}
            }
        };
        t.setPriority(Thread.MIN_PRIORITY);
        t.setName("spatialcalc-cleanup");
        t.start();
    }
    
    private static GeometryCollection flatten(GeometryCollection c) {
        boolean isFlat = true;
        for(Geometry g : c.getGeometries())
            isFlat &= !(g instanceof GeometryCollection);
        if(isFlat)
            return c;
        GeometryCollection retval = new GeometryCollection(c.getDimension());
        for(Geometry g : c.getGeometries()) {
            if(g instanceof GeometryCollection) {
                for(Geometry f : flatten((GeometryCollection)g).getGeometries())
                    retval.addGeometry(f);
            }else {
                retval.addGeometry(g);
            }
        }
        return retval;
    }
    
    private static byte[] createGeometryBlob(Geometry geometry) {
        final int size = encodeGeometryBlob(geometry, null);
        byte[] retval = new byte[size];
        
        ByteBuffer blob = ByteBuffer.wrap(retval);
        blob.order(ByteOrder.nativeOrder());
        
        encodeGeometryBlob(geometry, blob);
        return retval;
    }

    private static int encodeGeometryBlob(Geometry geometry, ByteBuffer blob) {
        int retval = 0;
        
        if(blob != null) {
            Envelope mbr = geometry.getEnvelope();

            // header
            blob.put((byte)0x00);
            if(blob.order() == ByteOrder.BIG_ENDIAN)
                blob.put((byte)0x00);
            else if(blob.order() == ByteOrder.LITTLE_ENDIAN)
                blob.put((byte)0x01);
            else
                throw new IllegalStateException();
            blob.putInt(4326);
            blob.putDouble(mbr.minX);
            blob.putDouble(mbr.minY);
            blob.putDouble(mbr.maxX);
            blob.putDouble(mbr.maxY);
            blob.put((byte)0x7C);

            // class type
            blob.putInt(getBlobGeometryClassType(geometry));
        }
        
        retval += 43;
        
        final BlobClassEncoder classEncoder;
        if(geometry.getDimension() == 2)
            classEncoder = BlobClassEncoder._2D;
        else if(geometry.getDimension() == 3)
            classEncoder = BlobClassEncoder._3D;
        else
            throw new IllegalStateException();

        if(geometry instanceof Point)
            retval += classEncoder.encodePointClass((Point)geometry, blob);  
        else if(geometry instanceof LineString)
            retval += classEncoder.encodeLineStringClass((LineString)geometry, blob);
        else if(geometry instanceof Polygon)
            retval += classEncoder.encodePolygonClass((Polygon)geometry, blob);
        else if(geometry instanceof GeometryCollection)
            retval += classEncoder.encodeCollectionClass(flatten((GeometryCollection)geometry), blob);
        else
            throw new IllegalStateException();
        
        if(blob != null)
            blob.put((byte)0xFE);
        retval += 1;

        return retval;
    }
    
    private static int getBlobGeometryClassType(Geometry geometry) {
        final int classTypeOffset;
        if(geometry.getDimension() == 2) {
            classTypeOffset = 0;
        } else if(geometry.getDimension() == 3) {
            classTypeOffset = 1000;
        } else {
            throw new IllegalStateException();
        }

        final int classType;
        if(geometry instanceof Point) {
            classType = 1;
        } else if(geometry instanceof LineString) {
            classType = 2;
        } else if(geometry instanceof Polygon) {
            classType = 3;
        } else if(geometry instanceof GeometryCollection) {
            classType = 7;
        } else {
            throw new IllegalStateException();
        }
        
        return classType + classTypeOffset;
    }

    private static abstract class BlobClassEncoder {
        public final static BlobClassEncoder _2D = new BlobClassEncoder(2) {
            @Override
            public int encodePointClass(Point point, ByteBuffer blob) {
                if(blob != null) {
                    blob.putDouble(point.getX());
                    blob.putDouble(point.getY());
                }
                
                return this.getCodedPointSize();
            }
        };
        
        public final static BlobClassEncoder _3D = new BlobClassEncoder(3) {
            @Override
            public int encodePointClass(Point point, ByteBuffer blob) {
                if(blob != null) {
                    blob.putDouble(point.getX());
                    blob.putDouble(point.getY());
                    blob.putDouble(point.getZ());
                }
                
                return this.getCodedPointSize();
            }
        };
        
        protected final int dimension;

        private BlobClassEncoder(int dimension) {
            switch(dimension) {
                case 2 :
                case 3 :
                    this.dimension = dimension;
                    break;
                default :
                    throw new IllegalArgumentException();
            }
        }

        public abstract int encodePointClass(Point point, ByteBuffer blob);
        
        public final int encodeLineStringClass(LineString linestring, ByteBuffer blob) {
            final int numPoints = linestring.getNumPoints();
            if(blob != null) {
                blob.putInt(numPoints);
            
                Point p = new Point(0, 0, 0);
                p.setDimension(this.getDimension());

                for(int i = 0; i < numPoints; i++) {
                    linestring.get(p, i);
                    this.encodePointClass(p, blob);
                }
            }
            
            return 4 + (numPoints*this.getCodedPointSize());
        }

        public final int encodePolygonClass(Polygon polygon, ByteBuffer blob) {
            int retval = 0;
            
            int numRings = polygon.getInteriorRings().size();
            if(polygon.getExteriorRing() != null)
                numRings++;

            if(blob != null)
                blob.putInt(numRings);
            retval += 4;
            
            if(polygon.getExteriorRing() != null)
                retval += this.encodeLineStringClass(polygon.getExteriorRing(), blob);
            
            for(LineString ring : polygon.getInteriorRings())
                retval += this.encodeLineStringClass(ring, blob);
            
            return retval;
        }
        
        public final int encodeCollectionClass(GeometryCollection collection, ByteBuffer blob) {
            int retval = 0;

            Collection<Geometry> children = collection.getGeometries();
            
            if(blob != null)
                blob.putInt(children.size());
            retval += 4;
            
            for(Geometry child : children) {
                if(blob != null)
                    blob.put((byte)0x69);
                retval += 1;

                if(blob != null)
                    blob.putInt(getBlobGeometryClassType(child));
                retval += 4;

                if(child instanceof Point)
                    retval += this.encodePointClass((Point)child, blob);
                else if(child instanceof LineString)
                    retval += this.encodeLineStringClass((LineString)child, blob);
                else if(child instanceof Polygon)
                    retval += this.encodePolygonClass((Polygon)child, blob);
                else
                    throw new IllegalArgumentException();
            }
            
            return retval;
        }
        
        public final int getCodedPointSize() {
            return this.getDimension()*8;
        }
        
        public final int getDimension() {
            return this.dimension;
        }
    }

    /**
     * A small Builder class for configuring a SpatialCalculator instance.
     */
    public static class Builder {
        /** Backed by an in-memory database or not */
        private boolean inMemory = false;
        /** Indicator of 2D vs 3D points */
        private int dimension = 2;

        public Builder inMemory() {
            this.inMemory = true;
            return this;
        }

        public Builder includePointZDimension() {
            this.dimension = 3;
            return this;
        }

        public SpatialCalculator build() {
            return new SpatialCalculator(this);
        }
    }
}
