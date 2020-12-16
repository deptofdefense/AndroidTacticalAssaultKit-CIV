package com.atakmap.map.layer.raster.mosaic;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.CursorIface;
import com.atakmap.database.CursorWrapper;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.StatementIface;
import com.atakmap.map.layer.feature.datastore.FeatureSpatialDatabase;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.math.MathUtils;
import com.atakmap.spatial.QuadBlob;

public class ATAKMosaicDatabase3 implements MosaicDatabase2 {
    public final static MosaicDatabaseSpi2 SPI = new MosaicDatabaseSpi2() {
        @Override
        public String getName() {
            return "atak3";
        }

        @Override
        public MosaicDatabase2 createInstance() {
            return new ATAKMosaicDatabase3();
        }
    };

    private final static QueryParameters EMPTY_PARAMS = new QueryParameters();
    
    private static final String TAG = "ATAKMosaicDatabase3";

    public final static String COLUMN_ID = "id";
    public final static String COLUMN_PATH = "path";
    public final static String COLUMN_TYPE = "type";
    public final static String COLUMN_MIN_LAT = "minlat";
    public final static String COLUMN_MIN_LON = "minlon";
    public final static String COLUMN_MAX_LAT = "maxlat";
    public final static String COLUMN_MAX_LON = "maxlon";
    public final static String COLUMN_UL_LAT = "ullat";
    public final static String COLUMN_UL_LON = "ullon";
    public final static String COLUMN_UR_LAT = "urlat";
    public final static String COLUMN_UR_LON = "urlon";
    public final static String COLUMN_LR_LAT = "lrlat";
    public final static String COLUMN_LR_LON = "lrlon";
    public final static String COLUMN_LL_LAT = "lllat";
    public final static String COLUMN_LL_LON = "lllon";
    public final static String COLUMN_MIN_GSD = "mingsd";
    public final static String COLUMN_MAX_GSD = "maxgsd";
    public final static String COLUMN_WIDTH = "width";
    public final static String COLUMN_HEIGHT = "height";
    public final static String COLUMN_COVERAGE_BLOB = "coverageblob";
    public final static String COLUMN_SOURCE = "source";
    public final static String COLUMN_PRECISION_IMAGERY = "precision";
    public final static String COLUMN_SRID = "srid";

    public final static String TABLE_MOSAIC_DATA = "mosaicdata";
    public final static String TABLE_COVERAGE = "coverage";
    
    private final static String TYPE_AGGREGATE_COVERAGE = "<null>";
    
    private final static String INDEX_DB_FILENAME = "index.sqlite";

    private DatabaseIface indexDatabase;
    private Map<String, DatabaseIface> typeDbs;

    private Map<String, Coverage> coverages;
    private Coverage coverage;

    public ATAKMosaicDatabase3() {
        this.indexDatabase = null;
        this.typeDbs = null;

        this.coverages = null;
        this.coverage = null;
    }

    @Override
    public void open(File f){
        DatabaseIface database = IOProviderFactory.createDatabase(new File(f, INDEX_DB_FILENAME),
                DatabaseInformation.OPTION_READONLY);
        open(database);
    }
    
    public static MosaicDatabaseBuilder2 create(File f) {
        return new Builder(f);
    }

    private String createCoverageQuery(){
        return "SELECT * from " + TABLE_COVERAGE;
    }

    private static String createDataQuery(String[] columns, String where, String groupBy, String having, String orderBy, String limit ){
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

                if(blob != null){
                    Geometry coverageBlob = GeometryFactory.parseSpatiaLiteBlob(blob);

                    c = new Coverage(coverageBlob,
                            cursor.getDouble(cursor.getColumnIndex(COLUMN_MIN_GSD)),
                            cursor.getDouble(cursor.getColumnIndex(COLUMN_MAX_GSD)));

                    mbb = coverageBlob.getEnvelope();
                }else{
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
                
                String type = cursor.getString(cursor.getColumnIndex(COLUMN_TYPE));

                this.coverages.put(type, c);
                
                final String typedbPath = cursor.getString(cursor.getColumnIndex(COLUMN_PATH));
                if(typedbPath != null) {
                    try {
                        File databaseFile = new File(typedbPath);
                        DatabaseIface database = IOProviderFactory.createDatabase(
                                new DatabaseInformation(Uri.fromFile(databaseFile),
                                        DatabaseInformation.OPTION_READONLY));
                        this.typeDbs.put(type, database);
                    } catch(SQLiteException e) {
                        Log.e(TAG, "error: ", e);
                    }
                }
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }

        Coverage aggr = this.coverages.remove(TYPE_AGGREGATE_COVERAGE);
        if(aggr == null) {
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

        } else {
            this.coverage = aggr;
        }
    }

    @Override
    public String getType() {
        return "atak3";
    }

    @Override
    public MosaicDatabase2.Coverage getCoverage() {
        return this.coverage;
    }

    @Override
    public void getCoverages(Map<String, MosaicDatabase2.Coverage> retval) {
        retval.putAll(this.coverages);
    }

    @Override
    public MosaicDatabase2.Coverage getCoverage(String type) {
        if (this.indexDatabase == null)
            throw new IllegalStateException();
        return this.coverages.get(type);
    }

    @Override
    public void close() {
        if (this.indexDatabase == null)
            throw new IllegalStateException();

        this.indexDatabase.close();
        this.indexDatabase = null;
        
        this.coverage = null;
        this.coverages = null;

        for(DatabaseIface typedb : this.typeDbs.values())
            typedb.close();
        this.typeDbs = null;
    }

    private static MosaicDatabase2.Cursor query(Collection<DatabaseIface> dbs, String[] columns, String selection, String[] selectionArgs,
            String groupBy, String having, String orderBy, String limit, MosaicDatabase2.QueryParameters.Order order) {

        try {
            final String query = createDataQuery(columns, selection, groupBy, having, orderBy, limit);

            Collection<MosaicDatabase2.Cursor> results = new LinkedList<MosaicDatabase2.Cursor>();
            for(DatabaseIface db : dbs)
                results.add(new Cursor(db.query(query, selectionArgs)));
            return new MultiplexingMosaicDatabaseCursor2(results, order);
        } catch (SQLiteException e) {
            Log.e(TAG, "error: ", e);
            throw e;
        }
    }

    @Override
    public MosaicDatabase2.Cursor query(QueryParameters params) {
        if (this.indexDatabase == null)
            throw new IllegalStateException();

        if (params == null)
            params = EMPTY_PARAMS;

        SelectionBuilder selection = new SelectionBuilder();

        Collection<DatabaseIface> queryDbs = null;
        if (params.types != null) {
            queryDbs = new HashSet<DatabaseIface>();
            DatabaseIface typedb;
            for (String type : params.types) {
                typedb = this.typeDbs.get(type);
                if (typedb != null)
                    queryDbs.add(typedb);
            }
        } else {
            queryDbs = this.typeDbs.values();
        }

        // path
        if (params.path != null)
            selection.append(COLUMN_PATH + " = \'" + params.path + "\'");
        // resolution
        if (!Double.isNaN(params.minGsd)) {
            String col = COLUMN_MAX_GSD;
            if (params.minGsdCompare == QueryParameters.GsdCompare.MinimumGsd)
                col = COLUMN_MIN_GSD;
            selection.append(col + " <= " + params.minGsd);
        }
        if (!Double.isNaN(params.maxGsd)) {
            String col = COLUMN_MAX_GSD;
            if (params.minGsdCompare == QueryParameters.GsdCompare.MinimumGsd)
                col = COLUMN_MIN_GSD;
            selection.append(col + " >= " + params.maxGsd);
        }
        // SRID
        if (params.srid > 0)
            selection.append(COLUMN_SRID + " = " + params.srid);
        // precision imagery
        if (params.precisionImagery != null)
            selection.append(COLUMN_PRECISION_IMAGERY + " = " + (params.precisionImagery.booleanValue() ? 1 : 0));

        // spatial filter
        if (params.spatialFilter != null) {
            final Envelope roi = params.spatialFilter.getEnvelope();

            StringBuilder sb = new StringBuilder();
            sb.append("ROWID IN (SELECT ROWID FROM SpatialIndex WHERE f_table_name = \'");
            sb.append(TABLE_MOSAIC_DATA);
            sb.append("\' AND search_frame = BuildMbr(");
            sb.append(roi.minX);
            sb.append(", ");
            sb.append(roi.minY);
            sb.append(", ");
            sb.append(roi.maxX);
            sb.append(", ");
            sb.append(roi.maxY);
            sb.append(", 4326))");

            selection.append(sb.toString());
        }

        String gsdOrderCol = COLUMN_MAX_GSD;
        String orderOrder = "ASC";
        switch (params.order) {
            case MaxGsdAsc:
                gsdOrderCol = COLUMN_MAX_GSD;
                orderOrder = "ASC";
                break;
            case MaxGsdDesc :
                gsdOrderCol = COLUMN_MAX_GSD;
                orderOrder = "DESC";
                break;
            case MinGsdAsc :
                gsdOrderCol = COLUMN_MIN_GSD;
                orderOrder = "ASC";
                break;
            case MinGsdDesc :
                gsdOrderCol = COLUMN_MIN_GSD;
                orderOrder = "DESC";
                break;
            default :
                gsdOrderCol = COLUMN_MAX_GSD;
                orderOrder = "DESC";
                break;
        }

        return query(queryDbs, null, selection.getSelection(), null, null, null, gsdOrderCol + ", "
                + COLUMN_TYPE + " " + orderOrder, null, params.order);
    }

    /**************************************************************************/

    public final static class Cursor extends CursorWrapper implements MosaicDatabase2.Cursor {

        public Cursor(CursorIface filter) {
            super(filter);
        }

        private double getDouble(String col) {
            return this.getDouble(this.getColumnIndex(col));
        }

        private String getString(String col) {
            return this.getString(this.getColumnIndex(col));
        }

        private int getInt(String col) {
            return this.getInt(this.getColumnIndex(col));
        }

        private GeoPoint getPoint(String latCol, String lonCol) {
            return new GeoPoint(this.getDouble(latCol), this.getDouble(lonCol));
        }
        
        @Override
        public GeoPoint getUpperLeft() {
            return this.getPoint(COLUMN_UL_LAT, COLUMN_UL_LON);
        }

        @Override
        public GeoPoint getUpperRight() {
            return this.getPoint(COLUMN_UR_LAT, COLUMN_UR_LON);
        }

        @Override
        public GeoPoint getLowerRight() {
            return this.getPoint(COLUMN_LR_LAT, COLUMN_LR_LON);
        }

        @Override
        public GeoPoint getLowerLeft() {
            return this.getPoint(COLUMN_LL_LAT, COLUMN_LL_LON);
        }

        @Override
        public double getMinLat() {
            return this.getDouble(COLUMN_MIN_LAT);
        }

        @Override
        public double getMinLon() {
            return this.getDouble(COLUMN_MIN_LON);
        }

        @Override
        public double getMaxLat() {
            return this.getDouble(COLUMN_MAX_LAT);
        }

        @Override
        public double getMaxLon() {
            return this.getDouble(COLUMN_MAX_LON);
        }

        @Override
        public String getPath() {
            return this.getString(COLUMN_PATH);
        }

        @Override
        public String getType() {
            return this.getString(COLUMN_TYPE);
        }

        @Override
        public double getMinGSD() {
            return this.getDouble(COLUMN_MIN_GSD);
        }

        @Override
        public double getMaxGSD() {
            return this.getDouble(COLUMN_MAX_GSD);
        }

        @Override
        public int getWidth() {
            return this.getInt(COLUMN_WIDTH);
        }

        @Override
        public int getHeight() {
            return this.getInt(COLUMN_HEIGHT);
        }

        @Override
        public int getId() {
            return this.getInt(COLUMN_ID);
        }

        @Override
        public MosaicDatabase2.Frame asFrame() {
            return new Frame(this);
        }

        @Override
        public boolean isPrecisionImagery() {
            return (this.getInt(COLUMN_PRECISION_IMAGERY) != 0);
        }

        @Override
        public int getSrid() {
            return this.getInt(COLUMN_SRID);
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
            
            this.avgLatExtent = ((this.avgLatExtent*this.count)+(maxLat-minLat))/(this.count+1);
            this.avgLonExtent = ((this.avgLonExtent*this.count)+(maxLon-minLon))/(this.count+1);
            this.count++;
        }
    }


    /**************************************************************************/

    private static class SelectionBuilder {
        private StringBuilder selection;

        public SelectionBuilder() {
            this.selection = new StringBuilder();
        }
        
        public void append(String s) {
            if (this.selection.length() > 0)
                this.selection.append(" AND ");
            this.selection.append(s);
        }

        public String getSelection() {
            if (this.selection.length() < 1)
                return null;
            return this.selection.toString();
        }
    }
    
    /**************************************************************************/

    private static class Builder implements MosaicDatabaseBuilder2 {
        private File dir;

        private DatabaseIface indexDatabase;

        private int id = 0;

        private QuadBlob quadBlob;

        private int transactionStack;
        
        private Map<String, TypeDbSpec> typeDbs;

        public Builder(File f) {
            if (IOProviderFactory.exists(f)|| IOProviderFactory.length(f) > 0)
                throw new IllegalArgumentException("A mosaic database may only be created, not edited.");
            
            this.dir = f;

            FileSystemUtils.delete(this.dir);
            if (!IOProviderFactory.mkdir(this.dir)) {
               Log.e(TAG, "could not make directory: " + this.dir);
            }

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
            } finally {
                if(result != null)
                    result.close();
            }
            
            this.typeDbs = new HashMap<String, TypeDbSpec>();
            
            this.transactionStack = 0;
        }

        @Override
        public void insertRow(String path,
                              String type,
                              boolean precisionImagery,
                              GeoPoint ul,
                              GeoPoint ur,
                              GeoPoint lr,
                              GeoPoint ll,
                              double minGsd,
                              double maxGsd,
                              int width,
                              int height,
                              int srid) {

            if (this.indexDatabase == null)
                throw new IllegalStateException();

            TypeDbSpec typedb = this.typeDbs.get(type);
            if (typedb == null)
                this.typeDbs.put(type, typedb=new TypeDbSpec(this.typeDbs.size()));
            
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

            typedb.insertStmt.bind(21, srid);
            typedb.insertStmt.bind(22, precisionImagery ? 1 : 0);
            
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
            if(!this.indexDatabase.inTransaction())
                this.indexDatabase.beginTransaction();
            
            for(TypeDbSpec entry : this.typeDbs.values())
                if(!entry.database.inTransaction())
                    entry.database.beginTransaction();
        }

        @Override
        public void setTransactionSuccessful() {
            if (this.indexDatabase == null)
                throw new IllegalStateException();
            this.indexDatabase.setTransactionSuccessful();
            
            for(TypeDbSpec entry : this.typeDbs.values())
                entry.database.setTransactionSuccessful();
        }

        @Override
        public void endTransaction() {
            this.transactionStack--;
            if(this.transactionStack < 1) {
                if(this.indexDatabase.inTransaction())
                    this.indexDatabase.endTransaction();
                
                for(TypeDbSpec entry : this.typeDbs.values())
                    if(entry.database.inTransaction())
                        entry.database.endTransaction();
            }
        }

        @Override
        public void createIndices() {
            // XXX - deferred to close()
        }

        @Override
        public void close() {

            try { 
                // XXX - 
                if(this.transactionStack > 0)
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
                            
                            for(Map.Entry<String, TypeDbSpec> entry : this.typeDbs.entrySet()) {
                                typedb = entry.getValue();
                                
                                typedb.database.beginTransaction();
                                try {
                                    // generate spatial index
                                    result = null;
                                    try {
                                        result = typedb.database.query("SELECT CreateSpatialIndex(\'" + TABLE_MOSAIC_DATA + "\', \'" + COLUMN_COVERAGE_BLOB + "\')", null);
                                        result.moveToNext();
                                    } finally {
                                        if (result != null)
                                            result.close();
                                    }
                                    
                                    // generate unions and insert into index
                                    result = null;
                                    try {
                                        //result = entry.getValue().database.query("SELECT Buffer(GUnion(" + TABLE_MOSAIC_DATA + "." + COLUMN_COVERAGE_BLOB + "), (? / 111000) * 3) FROM " + TABLE_MOSAIC_DATA, new String[] {String.valueOf(typedb.coverage.minGSD)});
                                        result = typedb.database.query("SELECT Buffer(UnaryUnion(Collect(" + TABLE_MOSAIC_DATA + "." + COLUMN_COVERAGE_BLOB + ")), (? / 111000) * 3) FROM " + TABLE_MOSAIC_DATA, new String[] {String.valueOf(typedb.coverage.minGSD)});
                                        if(result.moveToNext())
                                            typedb.coverage.blob = result.getBlob(0);
                                    } finally {
                                        if(result != null)
                                            result.close();
                                    }
                                    typedb.database.setTransactionSuccessful();
                                } finally {
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
                        } finally {
                            if(insertStmt != null)
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
        
                            
                        } finally {
                            if(insertStmt != null)
                                insertStmt.close();
                            
                        }
                        this.indexDatabase.setTransactionSuccessful();
                    } finally {
                        this.indexDatabase.endTransaction();
                    }
                }
            } finally { 

                for(TypeDbSpec mosaicdb : this.typeDbs.values())
                    mosaicdb.close();
                this.typeDbs.clear();
                
                this.indexDatabase.close();
                this.indexDatabase = null;

                this.typeDbs = null;
            }
        }

        private static DatabaseIface createSpatialDb(File file) {
            DatabaseIface retval = IOProviderFactory.createDatabase(file);

            final int major = FeatureSpatialDatabase.getSpatialiteMajorVersion(retval);
            final int minor = FeatureSpatialDatabase.getSpatialiteMinorVersion(retval);
            
            final String initSpatialMetadataSql;
            if(major > 4 || (major == 4 && minor >= 2))
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
            } finally {
                if (result != null)
                    result.close();
            }
            
            result = null;
            try {
                result = retval.query("PRAGMA journal_mode=OFF", null);
                // XXX - is this needed?
                if (result.moveToNext())
                    result.getString(0);
            } finally {
                if (result != null)
                    result.close();
            }

            result = null;
            try {
                result = retval.query("PRAGMA temp_store=MEMORY", null);
                // XXX - is this needed?
                if (result.moveToNext())
                    result.getString(0);
            } finally {
                if (result != null)
                    result.close();
            }

            result = null;
            try {
                result = retval.query("PRAGMA synchronous=OFF", null);
                // XXX - is this needed?
                if (result.moveToNext())
                    result.getString(0);
            } finally {
                if (result != null)
                    result.close();
            }

            result = null;
            try {
                result = retval.query("PRAGMA cache_size=8192", null);
                // XXX - is this needed?
                if (result.moveToNext())
                    result.getString(0);
            } finally {
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
                                        COLUMN_SRID + " INTEGER, " +
                                        COLUMN_PRECISION_IMAGERY + " INTEGER, " +
                                        COLUMN_WIDTH + " INTEGER, " +
                                        COLUMN_HEIGHT + " INTEGER)", null);
            
            result = null;
            try {
                result = mosaicdb.query("SELECT AddGeometryColumn(\'" + TABLE_MOSAIC_DATA + "\', \'" + COLUMN_COVERAGE_BLOB + "\', 4326, \'GEOMETRY\', \'XY\')", null);
                result.moveToNext();
            } finally {
                if(result != null)
                    result.close();
            }
            
            mosaicdb.execute("CREATE TABLE " + TABLE_COVERAGE + " (" +
                                    COLUMN_ID + " INTEGER PRIMARYKEY)", null);

            result = null;
            try {
                result = mosaicdb.query("SELECT AddGeometryColumn(\'" + TABLE_COVERAGE + "\', \'" + COLUMN_COVERAGE_BLOB + "\', 4326, \'GEOMETRY\', \'XY\')", null);
                result.moveToNext();
            } finally {
                if(result != null)
                    result.close();
            }
        }
        
        private class TypeDbSpec {
            public DatabaseIface database;
            public StatementIface insertStmt;
            public CoverageDiscovery coverage;
            public final String path;

            public TypeDbSpec(int id) {
                final File file = new File(Builder.this.dir, String.valueOf(id));
                this.path = file.getAbsolutePath();
                this.database = createSpatialDb(file);
                createMosaicDataTable(this.database);
                
                if(Builder.this.transactionStack > 0)
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
                                        COLUMN_COVERAGE_BLOB + ", " + // 20
                                        COLUMN_SRID + ", " + // 21
                                        COLUMN_PRECISION_IMAGERY + // 22
                                        ")" +
                                        "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                this.coverage = new CoverageDiscovery();
            }
            
            public void close() {
                if(this.insertStmt != null) {
                    this.insertStmt.close();
                    this.insertStmt = null;
                }
                if(this.database != null) {
                    this.database.close();
                    this.database = null;
                }
            }
        }
    }
}
