#ifndef ATAKMAP_RASTER_MOSAIC_ATAKMOSAICDATABASE2_H_INCLUDED
#define ATAKMAP_RASTER_MOSAIC_ATAKMOSAICDATABASE2_H_INCLUDED

#include <map>
#include <vector>

#include "raster/mosaic/MosaicDatabase.h"

namespace atakmap {
    namespace db {
        class Database;
    }
    namespace raster {
        namespace mosaic {
            class MosaicDatabaseBuilder;

            class ATAKMosaicDatabase2 : public MosaicDatabase
            {
            public :
                ATAKMosaicDatabase2();
                ~ATAKMosaicDatabase2();
            public :
                virtual void open(const char *path);
            public :
                static MosaicDatabaseBuilder *createBuilder(const char *path);
                static MosaicDatabaseBuilder *destroyBuilder(MosaicDatabaseBuilder *builder);
            private :
                virtual const char *createCoverageQuery();
            private :
                static const char *createDataQuery(std::vector<const char *> columns, const char *where, const char *groupBy, const char *having, const char *orderBy, const char *limit);
            private :
                void open(atakmap::db::Database *opened);
            public :
                const char *getType();
                MosaicDatabase::Coverage *getCoverage();
                std::map<std::string, MosaicDatabase::Coverage*> getCoverages();
                MosaicDatabase::Coverage *getCoverage(const char *type);
                void close();
                virtual MosaicDatabase::Cursor *query() const;
                virtual MosaicDatabase::Cursor *query(const std::string *path) const;
                virtual MosaicDatabase::Cursor *query(const core::GeoPoint *p, double minGSD, double maxGSD) const;
                virtual MosaicDatabase::Cursor *query(const std::set<std::string> *types, const core::GeoPoint *p, double minGSD, double maxGSD) const;
                virtual MosaicDatabase::Cursor *query(const core::GeoPoint *roiUL, const core::GeoPoint *roiLR, double minGSD, double maxGSD) const;
                virtual Cursor *query(const std::set<std::string> *types, const core::GeoPoint *roiUL, const core::GeoPoint *roiLR, double minGSD, double maxGSD) const;

            private :
                static MosaicDatabase::Cursor *query(std::vector<atakmap::db::Database *> dbs, std::vector<const char *> columns, const char *selection, std::vector<const char *> selectionArgs, const char *groupBy, const char *having, const char *orderBy, const char *limit);

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

                private static class Builder implements MosaicDatabaseBuilder {
                    private File dir;

                    private DatabaseIface indexDatabase;

                    private int id = 0;

                    private QuadBlob quadBlob;

                    private int transactionStack;

                    private Map<String, TypeDbSpec> typeDbs;

                    public Builder(File f) {
                        if (f.exists() || f.length() > 0)
                            throw new IllegalArgumentException("A mosaic database may only be created, not edited.");

                        this.dir = f;

                        this.dir.delete();
                        this.dir.mkdir();

                        this.indexDatabase = createSpatialDb(new File(this.dir, INDEX_DB_FILENAME));

                        this.quadBlob = new QuadBlob();

                        this.indexDatabase.execute("CREATE TABLE " + TABLE_COVERAGE + " (" +
                            COLUMN_TYPE + " TEXT PRIMARYKEY, " +
                            COLUMN_MIN_LAT + " REAL, " +
                            COLUMN_MIN_LON + " REAL, " +
                            COLUMN_MAX_LAT + " REAL, " +
                            COLUMN_MAX_LON + " REAL, " +
                            COLUMN_MIN_GSD + " REAL, " +
                            COLUMN_MAX_GSD + " REAL, " +
                            COLUMN_PATH + ")", null);

                        CursorIface result = null;
                        try {
                            result = this.indexDatabase.query("SELECT AddGeometryColumn(\'" + TABLE_COVERAGE + "\', \'" + COLUMN_COVERAGE_BLOB + "\', 4326, \'GEOMETRY\', \'XY\')", null);
                            result.moveToNext();
                        }
                        finally {
                            if (result != null)
                                result.close();
                        }

                        this.typeDbs = new HashMap<String, TypeDbSpec>();

                        this.transactionStack = 0;
                    }

                    @Override
                        public void insertRow(const char *path,
                        const char *type,
                        GeoPoint ul,
                        GeoPoint ur,
                        GeoPoint lr,
                        GeoPoint ll,
                        double minGsd,
                        double maxGsd,
                        int width,
                        int height) {

                        if (this.indexDatabase == null)
                            throw new IllegalStateException();

                        TypeDbSpec typedb = this.typeDbs.get(type);
                        if (typedb == null)
                            this.typeDbs.put(type, typedb = new TypeDbSpec(this.typeDbs.size()));

                        typedb.insertStmt.clearBindings();

                        typedb.insertStmt.bind(1, this.id++);
                        typedb.insertStmt.bind(2, type);
                        typedb.insertStmt.bind(3, path);
                        typedb.insertStmt.bind(8, ul.getLatitude());
                        typedb.insertStmt.bind(9, ul.getLongitude());
                        typedb.insertStmt.bind(10, ur.getLatitude());
                        typedb.insertStmt.bind(11, ur.getLongitude());
                        typedb.insertStmt.bind(12, lr.getLatitude());
                        typedb.insertStmt.bind(13, lr.getLongitude());
                        typedb.insertStmt.bind(14, ll.getLatitude());
                        typedb.insertStmt.bind(15, ll.getLongitude());
                        typedb.insertStmt.bind(20, this.quadBlob.getBlob(ul, ur, lr, ll));

                        final double minLat = MathUtils.min(ul.getLatitude(), ur.getLatitude(), lr.getLatitude(),
                            ll.getLatitude());
                        final double minLon = MathUtils.min(ul.getLongitude(), ur.getLongitude(),
                            lr.getLongitude(), ll.getLongitude());
                        final double maxLat = MathUtils.max(ul.getLatitude(), ur.getLatitude(), lr.getLatitude(),
                            ll.getLatitude());
                        final double maxLon = MathUtils.max(ul.getLongitude(), ur.getLongitude(),
                            lr.getLongitude(), ll.getLongitude());

                        typedb.insertStmt.bind(4, minLat);
                        typedb.insertStmt.bind(5, minLon);
                        typedb.insertStmt.bind(6, maxLat);
                        typedb.insertStmt.bind(7, maxLon);

                        typedb.insertStmt.bind(16, minGsd);
                        typedb.insertStmt.bind(17, maxGsd);

                        typedb.insertStmt.bind(18, width);
                        typedb.insertStmt.bind(19, height);

                        typedb.insertStmt.execute();

                        typedb.coverage.add(ul.getLatitude(), ul.getLongitude(),
                            ur.getLatitude(), ur.getLongitude(),
                            lr.getLatitude(), lr.getLongitude(),
                            ll.getLatitude(), ll.getLongitude(),
                            minGsd, maxGsd);
                    }

                    @Override
                        public void beginTransaction() {
                        if (this.indexDatabase == null)
                            throw new IllegalStateException();

                        this.transactionStack++;
                        if (!this.indexDatabase.inTransaction())
                            this.indexDatabase.beginTransaction();

                        for (TypeDbSpec entry : this.typeDbs.values())
                            if (!entry.database.inTransaction())
                                entry.database.beginTransaction();
                    }

                    @Override
                        public void setTransactionSuccessful() {
                        if (this.indexDatabase == null)
                            throw new IllegalStateException();
                        this.indexDatabase.setTransactionSuccessful();

                        for (TypeDbSpec entry : this.typeDbs.values())
                            entry.database.setTransactionSuccessful();
                    }

                    @Override
                        public void endTransaction() {
                        this.transactionStack--;
                        if (this.transactionStack < 1) {
                            if (this.indexDatabase.inTransaction())
                                this.indexDatabase.endTransaction();

                            for (TypeDbSpec entry : this.typeDbs.values())
                                if (entry.database.inTransaction())
                                    entry.database.endTransaction();
                        }
                    }

                    @Override
                        public void close() {
                        // XXX - 
                        if (this.transactionStack > 0)
                            this.endTransaction();

                        if (!this.typeDbs.isEmpty()) {
                            this.indexDatabase.beginTransaction();
                            try {
                                StatementIface insertStmt;
                                CursorIface result;
                                int idx;
                                TypeDbSpec typedb;

                                // NOTE: attaching type database to index to consolidate union
                                // into a single insert has performed worse than the two step
                                insertStmt = null;
                                try {
                                    insertStmt = this.indexDatabase.compileStatement("INSERT INTO " + TABLE_COVERAGE + " (" +
                                        COLUMN_TYPE + ", " +
                                        COLUMN_COVERAGE_BLOB + ", " +
                                        COLUMN_MIN_LAT + ", " +
                                        COLUMN_MIN_LON + ", " +
                                        COLUMN_MAX_LAT + ", " +
                                        COLUMN_MAX_LON + ", " +
                                        COLUMN_MIN_GSD + ", " +
                                        COLUMN_MAX_GSD + ", " +
                                        COLUMN_PATH + ") VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)");

                                    for (Map.Entry<String, TypeDbSpec> entry : this.typeDbs.entrySet()) {
                                        typedb = entry.getValue();

                                        typedb.database.beginTransaction();
                                        try {
                                            // generate spatial index
                                            result = null;
                                            try {
                                                result = typedb.database.query("SELECT CreateSpatialIndex(\'" + TABLE_MOSAIC_DATA + "\', \'" + COLUMN_COVERAGE_BLOB + "\')", null);
                                                result.moveToNext();
                                            }
                                            finally {
                                                if (result != null)
                                                    result.close();
                                            }

                                            // generate unions and insert into index
                                            result = null;
                                            try {
                                                //result = entry.getValue().database.query("SELECT Buffer(GUnion(" + TABLE_MOSAIC_DATA + "." + COLUMN_COVERAGE_BLOB + "), (? / 111000) * 3) FROM " + TABLE_MOSAIC_DATA, new String[] {String.valueOf(typedb.coverage.minGSD)});
                                                result = typedb.database.query("SELECT Buffer(UnaryUnion(Collect(" + TABLE_MOSAIC_DATA + "." + COLUMN_COVERAGE_BLOB + ")), (? / 111000) * 3) FROM " + TABLE_MOSAIC_DATA, new String[] {String.valueOf(typedb.coverage.minGSD)});
                                                if (result.moveToNext())
                                                    typedb.coverage.blob = result.getBlob(0);
                                            }
                                            finally {
                                                if (result != null)
                                                    result.close();
                                            }
                                            typedb.database.setTransactionSuccessful();
                                        }
                                        finally {
                                            typedb.database.endTransaction();
                                        }

                                        insertStmt.clearBindings();

                                        idx = 1;
                                        insertStmt.bind(idx++, entry.getKey());
                                        insertStmt.bind(idx++, typedb.coverage.blob);
                                        insertStmt.bind(idx++, typedb.coverage.minLat);
                                        insertStmt.bind(idx++, typedb.coverage.minLon);
                                        insertStmt.bind(idx++, entry.getValue().coverage.maxLat);
                                        insertStmt.bind(idx++, entry.getValue().coverage.maxLon);
                                        insertStmt.bind(idx++, entry.getValue().coverage.minGSD);
                                        insertStmt.bind(idx++, entry.getValue().coverage.maxGSD);
                                        insertStmt.bind(idx++, entry.getValue().path);

                                        insertStmt.execute();

                                    }
                                }
                                finally {
                                    if (insertStmt != null)
                                        insertStmt.close();
                                }

                                insertStmt = null;
                                try {
                                    insertStmt = this.indexDatabase.compileStatement("INSERT INTO " + TABLE_COVERAGE + " (" +
                                        COLUMN_TYPE + ", " +
                                        COLUMN_COVERAGE_BLOB + ", " +
                                        COLUMN_MIN_LAT + ", " +
                                        COLUMN_MIN_LON + ", " +
                                        COLUMN_MAX_LAT + ", " +
                                        COLUMN_MAX_LON + ", " +
                                        COLUMN_MIN_GSD + ", " +
                                        COLUMN_MAX_GSD + ", " +
                                        COLUMN_PATH + ") SELECT \'" + TYPE_AGGREGATE_COVERAGE + "\', GUnion(" + COLUMN_COVERAGE_BLOB + "), min(" + COLUMN_MIN_LAT + "), min(" + COLUMN_MIN_LON + "), max(" + COLUMN_MAX_LAT + "), max(" + COLUMN_MAX_LON + "), max(" + COLUMN_MIN_GSD + "), min(" + COLUMN_MAX_GSD + "), NULL FROM " + TABLE_COVERAGE);

                                    insertStmt.execute();


                                }
                                finally {
                                    if (insertStmt != null)
                                        insertStmt.close();

                                }
                                this.indexDatabase.setTransactionSuccessful();
                            }
                            finally {
                                this.indexDatabase.endTransaction();
                            }
                        }

                        for (TypeDbSpec mosaicdb : this.typeDbs.values())
                            mosaicdb.close();
                        this.typeDbs.clear();

                        this.indexDatabase.close();
                        this.indexDatabase = null;

                        this.typeDbs = null;
                    }

                    private static DatabaseIface createSpatialDb(File file) {
                        final DatabaseIface retval = JsqliteDatabaseAdapter.openOrCreateDatabase(file.getAbsolutePath());

                        final int major = FeatureSpatialDatabase.getSpatialiteMajorVersion(retval);
                        final int minor = FeatureSpatialDatabase.getSpatialiteMinorVersion(retval);

                        final const char *initSpatialMetadataSql;
                        if (major > 4 || (major == 4 && minor >= 2))
                            initSpatialMetadataSql = "SELECT InitSpatialMetadata(1, \'WGS84\')";
                        else if (major > 4 || (major == 4 && minor >= 1))
                            initSpatialMetadataSql = "SELECT InitSpatialMetadata(1)";
                        else
                            initSpatialMetadataSql = "SELECT InitSpatialMetadata()";

                        CursorIface result;

                        result = null;
                        try {
                            result = retval.query(initSpatialMetadataSql, null);
                            result.moveToNext();
                        }
                        finally {
                            if (result != null)
                                result.close();
                        }

                        result = null;
                        try {
                            result = retval.query("PRAGMA journal_mode=OFF", null);
                            // XXX - is this needed?
                            if (result.moveToNext())
                                result.getString(0);
                        }
                        finally {
                            if (result != null)
                                result.close();
                        }

                        result = null;
                        try {
                            result = retval.query("PRAGMA temp_store=MEMORY", null);
                            // XXX - is this needed?
                            if (result.moveToNext())
                                result.getString(0);
                        }
                        finally {
                            if (result != null)
                                result.close();
                        }

                        result = null;
                        try {
                            result = retval.query("PRAGMA synchronous=OFF", null);
                            // XXX - is this needed?
                            if (result.moveToNext())
                                result.getString(0);
                        }
                        finally {
                            if (result != null)
                                result.close();
                        }

                        result = null;
                        try {
                            result = retval.query("PRAGMA cache_size=8192", null);
                            // XXX - is this needed?
                            if (result.moveToNext())
                                result.getString(0);
                        }
                        finally {
                            if (result != null)
                                result.close();
                        }

                        return retval;
                    }

                    private static void createMosaicDataTable(DatabaseIface mosaicdb) {
                        CursorIface result;

                        mosaicdb.execute("CREATE TABLE " + TABLE_MOSAIC_DATA + " (" +
                            COLUMN_ID + " INTEGER PRIMARYKEY, " +
                            COLUMN_TYPE + " TEXT, " +
                            COLUMN_PATH + " TEXT, " +
                            COLUMN_MIN_LAT + " REAL, " +
                            COLUMN_MIN_LON + " REAL, " +
                            COLUMN_MAX_LAT + " REAL, " +
                            COLUMN_MAX_LON + " REAL, " +
                            COLUMN_UL_LAT + " REAL, " +
                            COLUMN_UL_LON + "  REAL, " +
                            COLUMN_UR_LAT + " REAL, " +
                            COLUMN_UR_LON + "  REAL, " +
                            COLUMN_LR_LAT + " REAL, " +
                            COLUMN_LR_LON + "  REAL, " +
                            COLUMN_LL_LAT + " REAL, " +
                            COLUMN_LL_LON + "  REAL, " +
                            COLUMN_MIN_GSD + " REAL, " +
                            COLUMN_MAX_GSD + " REAL, " +
                            COLUMN_WIDTH + " INTEGER, " +
                            COLUMN_HEIGHT + " INTEGER)", null);

                        result = null;
                        try {
                            result = mosaicdb.query("SELECT AddGeometryColumn(\'" + TABLE_MOSAIC_DATA + "\', \'" + COLUMN_COVERAGE_BLOB + "\', 4326, \'GEOMETRY\', \'XY\')", null);
                            result.moveToNext();
                        }
                        finally {
                            if (result != null)
                                result.close();
                        }

                        mosaicdb.execute("CREATE TABLE " + TABLE_COVERAGE + " (" +
                            COLUMN_ID + " INTEGER PRIMARYKEY)", null);

                        result = null;
                        try {
                            result = mosaicdb.query("SELECT AddGeometryColumn(\'" + TABLE_COVERAGE + "\', \'" + COLUMN_COVERAGE_BLOB + "\', 4326, \'GEOMETRY\', \'XY\')", null);
                            result.moveToNext();
                        }
                        finally {
                            if (result != null)
                                result.close();
                        }
                    }

                    private class TypeDbSpec {
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
                    }
                }

            public :
                static MosaicDatabase::Spi
            private :
                atakmap::db::DatabaseIface indexDatabase;
                std::map<const char *, atakmap::db::Database *> typeDbs;
                std::map<const char *, MosaicDatabase::Coverage *> coverages;
                private Coverage coverage;

            };

        }
    }
}

#endif // ATAKMAP_RASTER_MOSAIC_ATAKMOSAICDATABASE2_H_INCLUDED
