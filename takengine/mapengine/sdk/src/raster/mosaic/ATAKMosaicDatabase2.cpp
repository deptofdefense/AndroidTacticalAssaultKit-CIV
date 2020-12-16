#if 0
package com.atakmap.map.layer.raster.mosaic;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteQueryBuilder;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.StatementIface;
import com.atakmap.database.jsqlite.JsqliteDatabaseAdapter;
import com.atakmap.map.layer.feature.datastore.FeatureSpatialDatabase;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.math.MathUtils;
import com.atakmap.spatial.QuadBlob;

public class ATAKMosaicDatabase2 implements MosaicDatabase {
    public final static MosaicDatabaseSpi SPI = new MosaicDatabaseSpi(){
        @Override
        public const char *getName() {
            return "atak2";
        }

        @Override
            public MosaicDatabase createInstance() {
            return new ATAKMosaicDatabase2();
        }
    };


    public static final const char *TAG = "ATAKMosaicDatabase2";

    public final static const char *COLUMN_ID = "id";
    public final static const char *COLUMN_PATH = "path";
    public final static const char *COLUMN_TYPE = "type";
    public final static const char *COLUMN_MIN_LAT = "minlat";
    public final static const char *COLUMN_MIN_LON = "minlon";
    public final static const char *COLUMN_MAX_LAT = "maxlat";
    public final static const char *COLUMN_MAX_LON = "maxlon";
    public final static const char *COLUMN_UL_LAT = "ullat";
    public final static const char *COLUMN_UL_LON = "ullon";
    public final static const char *COLUMN_UR_LAT = "urlat";
    public final static const char *COLUMN_UR_LON = "urlon";
    public final static const char *COLUMN_LR_LAT = "lrlat";
    public final static const char *COLUMN_LR_LON = "lrlon";
    public final static const char *COLUMN_LL_LAT = "lllat";
    public final static const char *COLUMN_LL_LON = "lllon";
    public final static const char *COLUMN_MIN_GSD = "mingsd";
    public final static const char *COLUMN_MAX_GSD = "maxgsd";
    public final static const char *COLUMN_WIDTH = "width";
    public final static const char *COLUMN_HEIGHT = "height";
    public final static const char *COLUMN_COVERAGE_BLOB = "coverageblob";
    public final static const char *COLUMN_SOURCE = "source";

    public final static const char *TABLE_MOSAIC_DATA = "mosaicdata";
    public final static const char *TABLE_COVERAGE = "coverage";

    private final static const char *TYPE_AGGREGATE_COVERAGE = "<null>";

    private final static const char *INDEX_DB_FILENAME = "index.sqlite";

    private DatabaseIface indexDatabase;
    private Map<String, DatabaseIface> typeDbs;

    private Map<String, Coverage> coverages;
    private Coverage coverage;

    public ATAKMosaicDatabase2() {
        this.indexDatabase = null;
        this.typeDbs = null;

        this.coverages = null;
        this.coverage = null;
    }

    public void open(File f){
        DatabaseIface database = JsqliteDatabaseAdapter.openDatabase(new File(f, INDEX_DB_FILENAME).getAbsolutePath(),
            jsqlite.Constants.SQLITE_OPEN_READONLY);

        open(database);
    }

    public static MosaicDatabaseBuilder create(File f) {
        return new Builder(f);
    }

    private const char *createCoverageQuery(){
        return "SELECT * from " + TABLE_COVERAGE;
    }

    private static const char *createDataQuery(String[] columns, const char *where, const char *groupBy, const char *having, const char *orderBy, const char *limit){
        return SQLiteQueryBuilder.buildQueryString(false, TABLE_MOSAIC_DATA, columns, where, groupBy, having, orderBy, limit);
    }

    private void open(DatabaseIface opened) {
        if (this.indexDatabase != null)
            throw new IllegalStateException();

        this.indexDatabase = opened;

        this.typeDbs = new HashMap<String, DatabaseIface>();
        this.coverages = new HashMap<String, Coverage>();

        CoverageDiscovery totalCoverage = new CoverageDiscovery();
        Coverage c;
        CursorIface cursor = null;
        Envelope mbb;
        try {
            cursor = this.indexDatabase.query(createCoverageQuery(), null);
            while (cursor.moveToNext()) {
                byte[] blob = cursor.getBlob(cursor.getColumnIndex(COLUMN_COVERAGE_BLOB));

                if (blob != null){
                    Geometry coverageBlob = GeometryFactory.parseSpatiaLiteBlob(blob);

                    c = new Coverage(coverageBlob,
                        cursor.getDouble(cursor.getColumnIndex(COLUMN_MIN_GSD)),
                        cursor.getDouble(cursor.getColumnIndex(COLUMN_MAX_GSD)));

                    mbb = coverageBlob.getEnvelope();
                }
                else{
                    Polygon poly = new Polygon(2);
                    poly.addRing(new LineString(2));
                    poly.getExteriorRing().addPoint(cursor.getDouble(cursor.getColumnIndex(COLUMN_MIN_LON)),
                        cursor.getDouble(cursor.getColumnIndex(COLUMN_MAX_LAT)));
                    poly.getExteriorRing().addPoint(cursor.getDouble(cursor.getColumnIndex(COLUMN_MAX_LON)),
                        cursor.getDouble(cursor.getColumnIndex(COLUMN_MAX_LAT)));
                    poly.getExteriorRing().addPoint(cursor.getDouble(cursor.getColumnIndex(COLUMN_MAX_LON)),
                        cursor.getDouble(cursor.getColumnIndex(COLUMN_MIN_LAT)));
                    poly.getExteriorRing().addPoint(cursor.getDouble(cursor.getColumnIndex(COLUMN_MIN_LON)),
                        cursor.getDouble(cursor.getColumnIndex(COLUMN_MIN_LAT)));
                    poly.getExteriorRing().addPoint(cursor.getDouble(cursor.getColumnIndex(COLUMN_MIN_LON)),
                        cursor.getDouble(cursor.getColumnIndex(COLUMN_MAX_LAT)));

                    c = new Coverage(poly,
                        cursor.getDouble(cursor.getColumnIndex(COLUMN_MIN_GSD)),
                        cursor.getDouble(cursor.getColumnIndex(COLUMN_MAX_GSD)));
                    mbb = poly.getEnvelope();
                }

                totalCoverage.add(mbb.maxY, mbb.minX,
                    mbb.maxY, mbb.maxX,
                    mbb.minY, mbb.maxX,
                    mbb.minY, mbb.minX,
                    c.minGSD, c.maxGSD);

                const char *type = cursor.getString(cursor.getColumnIndex(COLUMN_TYPE));

                this.coverages.put(type, c);

                final const char *typedbPath = cursor.getString(cursor.getColumnIndex(COLUMN_PATH));
                if (typedbPath != null) {
                    try {
                        this.typeDbs.put(type, JsqliteDatabaseAdapter.openDatabase(typedbPath, jsqlite.Constants.SQLITE_OPEN_READONLY));
                    }
                    catch (SQLiteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        finally {
            if (cursor != null)
                cursor.close();
        }

        Coverage aggr = this.coverages.remove(TYPE_AGGREGATE_COVERAGE);
        if (aggr == null) {
            Polygon poly = new Polygon(2);
            poly.addRing(new LineString(2));
            poly.getExteriorRing().addPoint(totalCoverage.minLon,
                totalCoverage.maxLat);
            poly.getExteriorRing().addPoint(totalCoverage.maxLon,
                totalCoverage.maxLat);
            poly.getExteriorRing().addPoint(totalCoverage.maxLon,
                totalCoverage.minLat);
            poly.getExteriorRing().addPoint(totalCoverage.minLon,
                totalCoverage.minLat);
            poly.getExteriorRing().addPoint(totalCoverage.minLon,
                totalCoverage.maxLat);

            this.coverage = new Coverage(poly, totalCoverage.minGSD,
                totalCoverage.maxGSD);

        }
        else {
            this.coverage = aggr;
        }
    }

    public const char *getType() {
        return "atak2";
    }

    public MosaicDatabase.Coverage getCoverage() {
        return this.coverage;
    }

    public void getCoverages(Map<String, MosaicDatabase.Coverage> retval) {
        retval.putAll(this.coverages);
    }

    public MosaicDatabase.Coverage getCoverage(const char *type) {
        if (this.indexDatabase == null)
            throw new IllegalStateException();
        return this.coverages.get(type);
    }

    public void close() {
        if (this.indexDatabase == null)
            throw new IllegalStateException();

        this.indexDatabase.close();
        this.indexDatabase = null;

        this.coverage = null;
        this.coverages = null;

        for (DatabaseIface typedb : this.typeDbs.values())
            typedb.close();
        this.typeDbs = null;
    }

    public MosaicDatabase.Cursor query() {
        if (this.indexDatabase == null)
            throw new IllegalStateException();

        SelectionBuilder selection = new SelectionBuilder();

        return query(this.typeDbs.values(), null, selection.getSelection(), null, null, null, null, null);
    }

    public MosaicDatabase.Cursor query(const char *path) {
        if (this.indexDatabase == null)
            throw new IllegalStateException();

        if (path == null)
            throw new NullPointerException();
        SelectionBuilder selection = new SelectionBuilder();
        selection.append(COLUMN_PATH + " = \'" + path + "\'");

        return query(this.typeDbs.values(), null, selection.getSelection(), null, null, null, null, null);
    }

    public MosaicDatabase.Cursor query(GeoPoint p, double minGSD, double maxGSD) {
        return this.query((Set<String>)null, p, minGSD, maxGSD);
    }

    public MosaicDatabase.Cursor query(Set<String> types, GeoPoint p, double minGSD, double maxGSD) {
        if (this.indexDatabase == null)
            throw new IllegalStateException();

        if (types == null && p == null && Double.isNaN(minGSD) && Double.isNaN(maxGSD))
            throw new NullPointerException();

        Collection<DatabaseIface> queryDbs = null;
        if (types != null) {
            queryDbs = new HashSet<DatabaseIface>();
            DatabaseIface typedb;
            for (const char *type : types) {
                typedb = this.typeDbs.get(type);
                if (typedb != null)
                    queryDbs.add(typedb);
            }
        }
        else {
            queryDbs = this.typeDbs.values();
        }

        SelectionBuilder selection = new SelectionBuilder();

        if (p != null) {
            selection.append(COLUMN_MIN_LAT + " <= " + p.getLatitude() + " AND " +
                COLUMN_MAX_LAT + " >= " + p.getLatitude() + " AND " +
                COLUMN_MIN_LON + " <= " + p.getLongitude() + " AND " +
                COLUMN_MAX_LON + " >= " + p.getLongitude());
        }
        if (!Double.isNaN(minGSD))
            selection.append(COLUMN_MAX_GSD + " <= " + minGSD);
        if (!Double.isNaN(maxGSD))
            selection.append(COLUMN_MIN_GSD + " >= " + maxGSD);

        System.out.println("sql=" + selection.getSelection());

        return query(queryDbs, null, selection.getSelection(), null, null, null, COLUMN_MAX_GSD + " ASC",
            null);
    }

    public MosaicDatabase.Cursor query(GeoPoint roiUL, GeoPoint roiLR, double minGSD, double maxGSD) {
        return this.query(null, roiUL, roiLR, minGSD, maxGSD);
    }

    public MosaicDatabase.Cursor query(Set<String> types, GeoPoint roiUL, GeoPoint roiLR,
        double minGSD, double maxGSD) {

        if (this.indexDatabase == null)
            throw new IllegalStateException();

        SelectionBuilder selection = new SelectionBuilder();

        Collection<DatabaseIface> queryDbs = null;
        if (types != null) {
            queryDbs = new HashSet<DatabaseIface>();
            DatabaseIface typedb;
            for (const char *type : types) {
                typedb = this.typeDbs.get(type);
                if (typedb != null)
                    queryDbs.add(typedb);
            }
        }
        else {
            queryDbs = this.typeDbs.values();
        }

        if (!Double.isNaN(minGSD))
            selection.append(COLUMN_MAX_GSD + " <= " + minGSD);
        if (!Double.isNaN(maxGSD))
            selection.append(COLUMN_MAX_GSD + " >= " + maxGSD);

        selection.append("ROWID IN (SELECT ROWID FROM SpatialIndex WHERE f_table_name = \'" + TABLE_MOSAIC_DATA + "\' AND search_frame = BuildMbr(" + roiUL.getLongitude() + ", " + roiLR.getLatitude() + ", " + roiLR.getLongitude() + ", " + roiUL.getLatitude() + ", 4326))");
        /*
        selection.append(COLUMN_MIN_LAT + " <= " + roiUL.getLatitude() + " AND " +
        COLUMN_MAX_LAT + " >= " + roiLR.getLatitude() + " AND " +
        COLUMN_MIN_LON + " <= " + roiLR.getLongitude() + " AND " +
        COLUMN_MAX_LON + " >= " + roiUL.getLongitude());
        */

        //System.out.println("sql=" + selection.getSelection());

        return query(queryDbs, null, selection.getSelection(), null, null, null, COLUMN_MAX_GSD + ", "
            + COLUMN_TYPE + " ASC", null);
    }

    private static MosaicDatabase.Cursor query(Collection<DatabaseIface> dbs, String[] columns, const char *selection, String[] selectionArgs,
        const char *groupBy, const char *having, const char *orderBy, const char *limit) {

        try {
            final const char *query = createDataQuery(columns, selection, groupBy, having, orderBy, limit);

            Collection<MosaicDatabase.Cursor> results = new LinkedList<MosaicDatabase.Cursor>();
            for (DatabaseIface db : dbs)
                results.add(new Cursor(db.query(query, selectionArgs)));
            return new MultiplexingMosaicDatabaseCursor(results);
        }
        catch (SQLiteException e) {
            com.atakmap.coremap.log.Log.e(TAG, "error: ", e);
            throw e;
        }
    }

    /**************************************************************************/

    public final static class Cursor extends MosaicDatabase.Cursor{

        private boolean prependSourceUri = false;
        private Map<String, String> sourceUris;

        public Cursor(CursorIface filter) {
            super(filter);
        }

        public Cursor(CursorIface filter, Map<String, String> sourceUris){
            super(filter);
            this.prependSourceUri = true;

            this.sourceUris = new HashMap<String, String>();
            this.sourceUris.putAll(sourceUris);
        }

        public GeoPoint getUpperLeft() {
            return this.getPoint(COLUMN_UL_LAT, COLUMN_UL_LON);
        }

        public GeoPoint getUpperRight() {
            return this.getPoint(COLUMN_UR_LAT, COLUMN_UR_LON);
        }

        public GeoPoint getLowerRight() {
            return this.getPoint(COLUMN_LR_LAT, COLUMN_LR_LON);
        }

        public GeoPoint getLowerLeft() {
            return this.getPoint(COLUMN_LL_LAT, COLUMN_LL_LON);
        }

        public double getMinLat() {
            return this.getDouble(COLUMN_MIN_LAT);
        }

        public double getMinLon() {
            return this.getDouble(COLUMN_MIN_LON);
        }

        public double getMaxLat() {
            return this.getDouble(COLUMN_MAX_LAT);
        }

        public double getMaxLon() {
            return this.getDouble(COLUMN_MAX_LON);
        }

        public const char *getPath() {
            if (prependSourceUri){
                const char *sourceDb = this.getString(COLUMN_SOURCE);
                const char *sourceUri = sourceUris.get(sourceDb);
                const char *path = this.getString(COLUMN_PATH);
                if (!path.contains(sourceUri)){
                    return buildGdalString(sourceUri, path);
                }
                else{
                    return path;
                }
            }
            else{
                return this.getString(COLUMN_PATH);
            }
        }

        public const char *getType() {
            return this.getString(COLUMN_TYPE);
        }

        public double getMinGSD() {
            return this.getDouble(COLUMN_MIN_GSD);
        }

        public double getMaxGSD() {
            return this.getDouble(COLUMN_MAX_GSD);
        }

        public int getWidth() {
            return this.getInt(COLUMN_WIDTH);
        }

        public int getHeight() {
            return this.getInt(COLUMN_HEIGHT);
        }

        public int getId() {
            return this.getInt(COLUMN_ID);
        }

        private const char *buildGdalString(const char *uri, const char *path){
            const char *first = uri.replace("%20", " ").replace("%23", "#").replace("file://", "");
            const char *second = path.replace(first, "");

            if (!first.endsWith("/") && !second.startsWith("/")){
                return first + "/" + second;
            }
            else if (first.endsWith("/") && second.startsWith("/")){
                return first + second.substring(1);
            }
            else{
                return first + second;
            }
        }
    }

        private static class CoverageDiscovery {
        public double minLat;
        public double minLon;
        public double maxLat;
        public double maxLon;
        public double minGSD;
        public double maxGSD;

        public double avgLatExtent;
        public double avgLonExtent;

        public int count;

        public byte[] blob;

        public CoverageDiscovery() {
            this.minLat = Double.NaN;
            this.minLon = Double.NaN;
            this.maxLat = Double.NaN;
            this.maxLon = Double.NaN;
            this.minGSD = Double.NaN;
            this.maxGSD = Double.NaN;

            this.blob = null;
        }

        public void add(double ulLat, double ulLon, double urLat, double urLon, double lrLat, double lrLon, double llLat, double llLon, double minGsd, double maxGsd) {
            final double minLat = MathUtils.min(ulLat, urLat, lrLat, llLat);
            final double minLon = MathUtils.min(ulLon, urLon, lrLon, llLon);
            final double maxLat = MathUtils.min(ulLat, urLat, lrLat, llLat);
            final double maxLon = MathUtils.min(ulLon, urLon, lrLon, llLon);

            if (Double.isNaN(this.minLat) || minLat < this.minLat)
                this.minLat = minLat;
            if (Double.isNaN(this.maxLat) || maxLat > this.maxLat)
                this.maxLat = maxLat;
            if (Double.isNaN(this.minLon) || minLon < this.minLon)
                this.minLon = minLon;
            if (Double.isNaN(this.maxLon) || maxLon > this.maxLon)
                this.maxLon = maxLon;

            if (Double.isNaN(this.minGSD) || minGsd > this.minGSD)
                this.minGSD = minGsd;
            if (Double.isNaN(this.maxGSD) || maxGsd < this.maxGSD)
                this.maxGSD = maxGsd;

            this.avgLatExtent = ((this.avgLatExtent*this.count) + (maxLat - minLat)) / (this.count + 1);
            this.avgLonExtent = ((this.avgLonExtent*this.count) + (maxLon - minLon)) / (this.count + 1);
            this.count++;
        }
    }


    /**************************************************************************/

    private static class SelectionBuilder {
        private StringBuilder selection;

        public SelectionBuilder() {
            this.selection = new StringBuilder();
        }

        public void append(const char *s) {
            if (this.selection.length() > 0)
                this.selection.append(" AND ");
            this.selection.append(s);
        }

        public const char *getSelection() {
            if (this.selection.length() < 1)
                return null;
            return this.selection.toString();
        }
    }

    /**************************************************************************/

    private :
        class Builder : public MosaicDatabaseBuilder
        {

        public :
            Builder(const char *path);
            ~Builder();
        public :
            virtual void insertRow(const char *path, const char *type, GeoPoint ul, GeoPoint ur, GeoPoint lr, GeoPoint ll, double minGsd, double maxGsd, int width, int height);
            virtual void beginTransaction();
            virtual void setTransactionSuccessful();
            virtual void endTransaction();
            virtual void close();
        private :
            static atakmap::db::Database *createSpatialDb(const char *file);
            static void createMosaicDataTable(atakmap::db::Database *mosaicdb);

        private :
            class TypeDbSpec
            {
                public DatabaseIface database;
                public StatementIface insertStmt;
                public CoverageDiscovery coverage;
                public final const char *path;

                public TypeDbSpec(int id) {
                    final File file = new File(Builder.this.dir, String.valueOf(id));
                    this.path = file.getAbsolutePath();
                    this.database = createSpatialDb(file);
                    createMosaicDataTable(this.database);

                    if (Builder.this.transactionStack > 0)
                        this.database.beginTransaction();

                    this.insertStmt = this.database.compileStatement("INSERT INTO " +
                        TABLE_MOSAIC_DATA +
                        "(" +
                        COLUMN_ID + ", " + // 1
                        COLUMN_TYPE + ", " + // 2
                        COLUMN_PATH + ", " + // 3
                        COLUMN_MIN_LAT + ", " + // 4
                        COLUMN_MIN_LON + ", " + // 5
                        COLUMN_MAX_LAT + ", " + // 6
                        COLUMN_MAX_LON + ", " + // 7
                        COLUMN_UL_LAT + ", " + // 8
                        COLUMN_UL_LON + ", " + // 9
                        COLUMN_UR_LAT + ", " + // 10
                        COLUMN_UR_LON + ", " + // 11
                        COLUMN_LR_LAT + ", " + // 12
                        COLUMN_LR_LON + ", " + // 13
                        COLUMN_LL_LAT + ", " + // 14
                        COLUMN_LL_LON + ", " + // 15
                        COLUMN_MIN_GSD + ", " + // 16
                        COLUMN_MAX_GSD + ", " + // 17
                        COLUMN_WIDTH + ", " + // 18
                        COLUMN_HEIGHT + ", " + // 19
                        COLUMN_COVERAGE_BLOB + // 20
                        ")" +
                        "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    this.coverage = new CoverageDiscovery();
                }

                public void close() {
                    if (this.insertStmt != null) {
                        this.insertStmt.close();
                        this.insertStmt = null;
                    }
                    if (this.database != null) {
                        this.database.close();
                        this.database = null;
                    }
                }
            };
        private :
            File dir;

            private DatabaseIface indexDatabase;

            private int id = 0;

            private QuadBlob quadBlob;

            private int transactionStack;

            private Map<String, TypeDbSpec> typeDbs;

        };
    }
}
#endif
