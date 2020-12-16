
package com.atakmap.map.layer.raster;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.CursorIface;
import com.atakmap.database.CursorWrapper;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.StatementIface;
import com.atakmap.map.layer.feature.datastore.FeatureSpatialDatabase;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.content.CatalogCurrency;
import com.atakmap.content.CatalogCurrencyRegistry;
import com.atakmap.content.CatalogDatabase;

final class LayersDatabase extends CatalogDatabase {

    public static final String TAG = "LayersDatabase";

    private final static int DATABASE_VERSION = 11;
    
    final static String TABLE_LAYERS = "layers";
    final static String COLUMN_LAYERS_ID = "id";
    final static String COLUMN_LAYERS_PATH = "path";
    final static String COLUMN_LAYERS_CATALOG_LINK = "cataloglink";
    final static String COLUMN_LAYERS_INFO = "info";
    final static String COLUMN_LAYERS_NAME = "name";
    final static String COLUMN_LAYERS_DATASET_TYPE = "datasettype";
    final static String COLUMN_LAYERS_PROVIDER = "provider";
    final static String COLUMN_LAYERS_SRID = "srid";
    final static String COLUMN_LAYERS_MAX_LAT = "maxlat";
    final static String COLUMN_LAYERS_MIN_LON = "minlon";
    final static String COLUMN_LAYERS_MIN_LAT = "minlat";
    final static String COLUMN_LAYERS_MAX_LON = "maxlon";
    final static String COLUMN_LAYERS_MIN_GSD = "mingsd";
    final static String COLUMN_LAYERS_MAX_GSD = "maxgsd";
    final static String COLUMN_LAYERS_REMOTE = "remote";
    final static String COLUMN_LAYERS_COVERAGE = "coverage";
    
    final static String TABLE_IMAGERY_TYPES = "imagerytypes";
    final static String COLUMN_IMAGERY_TYPES_NAME = "name";
    final static String COLUMN_IMAGERY_TYPES_LAYER_ID = "layerid";
    final static String COLUMN_IMAGERY_TYPES_GEOM = "geom";
    final static String COLUMN_IMAGERY_TYPES_MIN_GSD = "mingsd";
    final static String COLUMN_IMAGERY_TYPES_MAX_GSD = "maxgsd";
    
    private final static String SQL_INSERT_LAYER_STMT =
            "INSERT INTO " + TABLE_LAYERS +
                "("  +  COLUMN_LAYERS_PATH + ", " +         // 1
                        COLUMN_LAYERS_CATALOG_LINK + ", " + // 2
                        COLUMN_LAYERS_INFO + ", " +         // 3
                        COLUMN_LAYERS_NAME + ", " +         // 4
                        COLUMN_LAYERS_PROVIDER + ", " +     // 5
                        COLUMN_LAYERS_DATASET_TYPE + ", " + // 6
                        COLUMN_LAYERS_SRID + ", " +         // 7
                        COLUMN_LAYERS_MAX_LAT + ", " +      // 8
                        COLUMN_LAYERS_MIN_LON + ", " +      // 9
                        COLUMN_LAYERS_MIN_LAT + ", " +      // 10
                        COLUMN_LAYERS_MAX_LON + ", " +      // 11
                        COLUMN_LAYERS_MIN_GSD + ", " +      // 12
                        COLUMN_LAYERS_MAX_GSD + ", " +      // 13
                        COLUMN_LAYERS_REMOTE  + ", " +      // 14
                        COLUMN_LAYERS_COVERAGE + ") " +     // 15
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, GeomFromWKB(?, 4326))";
    
    private final static String SQL_INSERT_TYPE_STMT =
            "INSERT INTO " + TABLE_IMAGERY_TYPES +
                "("  +  COLUMN_IMAGERY_TYPES_LAYER_ID + ", " +  // 1
                        COLUMN_IMAGERY_TYPES_NAME + ", " +      // 2
                        COLUMN_IMAGERY_TYPES_GEOM + ", " +      // 3
                        COLUMN_IMAGERY_TYPES_MIN_GSD + ", " +   // 4
                        COLUMN_IMAGERY_TYPES_MAX_GSD + ") " +   // 5
            "VALUES (?, ?, ?, ?, ?)";

    /**************************************************************************/

    public LayersDatabase(File databaseFile, CatalogCurrencyRegistry r) {
        super(createDatabase(databaseFile), r);
    }

    /**
     * Responsible for renaming the Old DB if necessary, and returns the Database.
     * @param databaseFile the file to use as the database.
     * @return the database.
     */
    private static DatabaseIface createDatabase(File databaseFile){
        DatabaseIface db = IOProviderFactory.createDatabase(databaseFile,
                DatabaseInformation.OPTION_ENSURE_PARENT_DIRS);
        if(db == null && IOProviderFactory.exists(databaseFile)) {
            IOProviderFactory.delete(databaseFile, IOProvider.SECURE_DELETE);
            db = IOProviderFactory.createDatabase(databaseFile);
        }
        return db;
    }

    private static int databaseVersion() {
        return (CATALOG_VERSION | (DATABASE_VERSION << 16));
    }

    @Override
    public void dropTables() {
        super.dropTables();

        this.database.execute("DROP TABLE IF EXISTS " + TABLE_LAYERS, null);
        this.database.execute("DROP TABLE IF EXISTS " + TABLE_IMAGERY_TYPES, null);

        if (Databases.getTableNames(this.database).contains("resources")) {
            final LinkedList<String> invalidResources = new LinkedList<String>();

            CursorIface result = null;
            try {
                result = this.database.query("SELECT path FROM resources", null);
                while (result.moveToNext())
                    invalidResources.add(result.getString(0));
            } finally {
                if (result != null)
                    result.close();
            }

            if (invalidResources.size() > 0) {
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        File f;
                        for (String s : invalidResources) {
                            FileSystemUtils.delete(s);
                        }
                    }
                };
                t.setPriority(Thread.NORM_PRIORITY);
                t.setName("LayersDatabase-resource-cleanup-"
                        + Integer.toString(t.hashCode(), 16));
                t.start();
            }
        }

        this.database.execute("DROP TABLE IF EXISTS resources", null);
    }

    @Override
    protected boolean checkDatabaseVersion() {
        return (this.database.getVersion() == databaseVersion());
    }

    @Override
    protected void setDatabaseVersion() {
        this.database.setVersion(databaseVersion());
    }

    @Override
    public void buildTables() {
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
        
        super.buildTables();

        this.database
                .execute(
                        "CREATE TABLE " + TABLE_LAYERS + " (" +
                                COLUMN_LAYERS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                                COLUMN_LAYERS_PATH + " TEXT, " +
                                COLUMN_LAYERS_CATALOG_LINK + " INTEGER, " +
                                COLUMN_LAYERS_INFO + " BLOB, " + 
                                COLUMN_LAYERS_NAME + " TEXT, " +
                                COLUMN_LAYERS_PROVIDER + " TEXT, " +
                                COLUMN_LAYERS_DATASET_TYPE + " TEXT, " +
                                COLUMN_LAYERS_SRID + " INTEGER, " +
                                COLUMN_LAYERS_MAX_LAT + " REAL, " +
                                COLUMN_LAYERS_MIN_LON + " REAL, " +
                                COLUMN_LAYERS_MIN_LAT + " REAL, " +
                                COLUMN_LAYERS_MAX_LON + " REAL, " +
                                COLUMN_LAYERS_MIN_GSD + " REAL, " +
                                COLUMN_LAYERS_MAX_GSD + " REAL, " +
                                COLUMN_LAYERS_REMOTE + " INTEGER)",
                        null);
        result = null;
        try {
            result = this.database.query("SELECT AddGeometryColumn(\'" + TABLE_LAYERS + "\', \'" + COLUMN_LAYERS_COVERAGE + "\', 4326, \'GEOMETRY\', \'XY\')", null);
            result.moveToNext();
        } finally {
            if(result != null)
                result.close();
        }
        this.database.execute("CREATE TABLE resources (path TEXT, link INTEGER)", null);
        this.database.execute("CREATE TABLE " + TABLE_IMAGERY_TYPES + " (" +
                                COLUMN_IMAGERY_TYPES_LAYER_ID + " INTEGER, " +
                                COLUMN_IMAGERY_TYPES_NAME + " TEXT, " +
                                COLUMN_IMAGERY_TYPES_GEOM + " BLOB, " +
                                COLUMN_IMAGERY_TYPES_MIN_GSD + " REAL, " +
                                COLUMN_IMAGERY_TYPES_MAX_GSD + " REAL)", null);
    }

    public void addLayer(File derivedFrom, DatasetDescriptor info, File workingDir, CatalogCurrency currency)
            throws IOException {
        this.addLayers(derivedFrom, Collections.singleton(info), workingDir, currency);
    }

    protected boolean checkCatalogEntryExists(File derivedFrom) {
        CatalogCursor result = null;
        try {
            result = this.queryCatalog(derivedFrom);
            return result.moveToNext();
        } finally {
            if (result != null)
                result.close();
        }
    }

    public synchronized void addLayers(File derivedFrom, Set<DatasetDescriptor> layers,
            File workingDir, CatalogCurrency currency) throws IOException {

        if (this.checkCatalogEntryExists(derivedFrom))
            throw new IllegalStateException("entry already exists: "
                    + derivedFrom.getAbsolutePath());

        //this.database.beginTransaction();
        try {
            // add the catalog entry
            final long catalogId = this.addCatalogEntryNoSync(derivedFrom, currency);

            long layerId = Databases.getNextAutoincrementId(this.database, TABLE_LAYERS);

            StatementIface insertLayerStmt = null;
            StatementIface insertTypeStmt = null;
            
            try {
                insertLayerStmt = this.database.compileStatement(SQL_INSERT_LAYER_STMT);
                insertTypeStmt = this.database.compileStatement(SQL_INSERT_TYPE_STMT);

                Iterator<DatasetDescriptor> iter = layers.iterator();
                DatasetDescriptor info;
                Envelope mbb;
                int idx;
                Geometry coverage;
                ByteBuffer coverageWkb;
                while (iter.hasNext()) {
                    info = iter.next();

                    coverage = info.getCoverage(null);
                    mbb = coverage.getEnvelope();
                    coverageWkb= ByteBuffer.wrap(new byte[coverage.computeWkbSize()]);
                    coverage.toWkb(coverageWkb);


                    idx = 1;
                    insertLayerStmt.clearBindings();
                    insertLayerStmt.bind(idx++, derivedFrom.getAbsolutePath());
                    insertLayerStmt.bind(idx++, catalogId);
                    insertLayerStmt.bind(idx++, info.encode(layerId));
                    insertLayerStmt.bind(idx++, info.getName());
                    insertLayerStmt.bind(idx++, info.getProvider());
                    insertLayerStmt.bind(idx++, info.getDatasetType());
                    insertLayerStmt.bind(idx++, info.getSpatialReferenceID());
                    insertLayerStmt.bind(idx++, mbb.maxY);
                    insertLayerStmt.bind(idx++, mbb.minX);
                    insertLayerStmt.bind(idx++, mbb.minY);
                    insertLayerStmt.bind(idx++, mbb.maxX);
                    insertLayerStmt.bind(idx++, info.getMinResolution(null));
                    insertLayerStmt.bind(idx++, info.getMaxResolution(null));
                    insertLayerStmt.bind(idx++, info.isRemote() ? 1 : 0);
                    insertLayerStmt.bind(idx++, coverageWkb.array());

                    insertLayerStmt.execute();
                    
                    for(String type : info.getImageryTypes()) {
                        idx = 1;
                        insertTypeStmt.clearBindings();
                        insertTypeStmt.bind(idx++, layerId);
                        insertTypeStmt.bind(idx++, type);
                        coverage = info.getCoverage(type);
                        coverageWkb= ByteBuffer.wrap(new byte[coverage.computeWkbSize()]);
                        coverage.toWkb(coverageWkb);
                        insertTypeStmt.bind(idx++, coverageWkb.array());
                        insertTypeStmt.bind(idx++, info.getMinResolution(type));
                        insertTypeStmt.bind(idx++, info.getMaxResolution(type));
                        insertTypeStmt.execute();
                    }
    
                    layerId++; // increment next autoincrement ID
                }
            } finally {
                insertLayerStmt.close();
                insertTypeStmt.close();
            }

            StatementIface stmt = null;
            try {
                stmt = this.database
                        .compileStatement("INSERT INTO resources (link, path) VALUES (?, ?)");
                stmt.bind(1, catalogId);
                stmt.bind(2, workingDir.getAbsolutePath());

                stmt.execute();
            } finally {
                if (stmt != null)
                    stmt.close();
            }

            //this.database.setTransactionSuccessful();
        } finally {
            //this.database.endTransaction();
        }
    }

    public synchronized DatasetDescriptor getLayer(long id) throws IOException {
        LayerCursor result = null;
        try {
            result = this.getLayerImpl(id);
            if (!result.moveToNext())
                return null;
            return result.getLayerInfo();
        } finally {
            if (result != null)
                result.close();
        }
    }

    private LayerCursor getLayerImpl(long id) {
        return new LayerCursor(this.queryLayersNoSync(new String[] {
                COLUMN_LAYERS_INFO
        }, COLUMN_LAYERS_ID + " = ?", new String[] {
                String.valueOf(id)
        }, null, null, null, null));
    }

    public synchronized Set<DatasetDescriptor> getLayers(File derivedFrom) throws IOException {
        Set<DatasetDescriptor> retval = new HashSet<DatasetDescriptor>();
        CursorIface result = null;
        try {
            result = this.queryLayersNoSync(new String[] {COLUMN_LAYERS_INFO},
                    COLUMN_LAYERS_PATH + " = ?", new String[] {
                    derivedFrom.getAbsolutePath()
            }, null, null, null, null);
            while (result.moveToNext())
                retval.add(DatasetDescriptor.decode(result.getBlob(0)));
            return retval;
        } finally {
            if (result != null)
                result.close();
        }
    }

    public synchronized CursorIface queryLayers(String[] columns, String selection,
            String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
        
        return this.queryLayersNoSync(columns, selection, selectionArgs, groupBy, having, orderBy, limit);
    }

    @Override
    public synchronized CursorIface query(String table, String[] columns, String selection,
                                          String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
        
        return super.query(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
    }

    private CursorIface queryLayersNoSync(String[] columns, String selection,
            String[] selectionArgs,
            String groupBy, String having, String orderBy, String limit) {

        return super.query(TABLE_LAYERS, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
    }

    @Override
    protected void onCatalogEntryRemoved(long catalogId, boolean automated) {
        if (catalogId > 0) {
            StatementIface stmt;

            stmt = null;
            try {
                stmt = this.database.compileStatement("DELETE FROM " + TABLE_IMAGERY_TYPES + " WHERE " + COLUMN_IMAGERY_TYPES_LAYER_ID + " IN (SELECT " + COLUMN_LAYERS_ID + " FROM " + TABLE_LAYERS + " WHERE " + COLUMN_LAYERS_CATALOG_LINK + " = ?)");
                stmt.bind(1, catalogId);
                stmt.execute();
            } finally {
                if(stmt != null)
                    stmt.close();
            }

            // remove the invalid layers
            stmt = null;
            try {
                stmt = this.database.compileStatement("DELETE FROM " + TABLE_LAYERS + " WHERE " + COLUMN_LAYERS_CATALOG_LINK + " = ?");
                stmt.bind(1, catalogId);

                stmt.execute();
            } finally {
                if (stmt != null)
                    stmt.close();
            }

            // delete resources associated with deleted layers
            CursorIface result = null;
            try {
                result = this.database.query(
                        "SELECT path FROM resources WHERE link = " + String.valueOf(catalogId),
                        null);
                while (result.moveToNext()) {
                    FileSystemUtils.delete(result.getString(0));
                }
            } finally {
                if (result != null)
                    result.close();
            }

            stmt = null;
            try {
                stmt = this.database.compileStatement("DELETE FROM resources WHERE link = ?");
                stmt.bind(1, catalogId);

                stmt.execute();
            } finally {
                if (stmt != null)
                    stmt.close();
            }
        } else {
            StatementIface stmt;

            stmt = null;
            try {
                stmt = this.database.compileStatement("DELETE FROM " + TABLE_IMAGERY_TYPES +
                            " WHERE " + COLUMN_IMAGERY_TYPES_LAYER_ID +
                                " IN (SELECT " + COLUMN_LAYERS_ID +
                                        " FROM " + TABLE_LAYERS + " LEFT JOIN " + TABLE_CATALOG +
                                          " on " + TABLE_LAYERS + "." + COLUMN_LAYERS_CATALOG_LINK + " = " +
                                                   TABLE_CATALOG + "." + COLUMN_CATALOG_ID +
                                          " WHERE " + TABLE_CATALOG + "." + COLUMN_CATALOG_ID + " IS NULL)");

                stmt.execute();
            } finally {
                if (stmt != null)
                    stmt.close();
            }
            
            // remove all layers without a satisfied link
            stmt = null;
            try {
                stmt = this.database
                        .compileStatement("DELETE FROM layers WHERE cataloglink IN (SELECT cataloglink FROM layers LEFT JOIN "
                                + TABLE_CATALOG
                                + " on layers.cataloglink = "
                                + TABLE_CATALOG
                                + "."
                                + COLUMN_CATALOG_ID
                                + " WHERE "
                                + TABLE_CATALOG
                                + "."
                                + COLUMN_CATALOG_ID + " IS NULL)");

                stmt.execute();
            } finally {
                if (stmt != null)
                    stmt.close();
            }

            // delete resources associated with deleted layers
            CursorIface result = null;
            try {
                result = this.database.query(
                        "SELECT path FROM resources WHERE link IN (SELECT link FROM resources LEFT JOIN "
                                + TABLE_CATALOG + " on resources.link = " + TABLE_CATALOG + "."
                                + COLUMN_CATALOG_ID + " WHERE " + TABLE_CATALOG + "."
                                + COLUMN_CATALOG_ID + " IS NULL)", null);
                while (result.moveToNext()) {
                    FileSystemUtils.delete(result.getString(0));
                }
            } finally {
                if (result != null)
                    result.close();
            }

            stmt = null;
            try {
                stmt = this.database
                        .compileStatement("DELETE FROM resources WHERE link IN (SELECT link FROM resources LEFT JOIN "
                                + TABLE_CATALOG
                                + " on resources.link="
                                + TABLE_CATALOG
                                + "."
                                + COLUMN_CATALOG_ID
                                + " WHERE "
                                + TABLE_CATALOG
                                + "."
                                + COLUMN_CATALOG_ID + " IS NULL)");

                stmt.execute();
            } finally {
                if (stmt != null)
                    stmt.close();
            }
        }
    }

    /**************************************************************************/

    public static final class LayerCursor extends CursorWrapper {
        private LayerCursor(CursorIface filter) {
            super(filter);
        }

        public final String getPath() {
            return this.getString(this.getColumnIndex(COLUMN_LAYERS_PATH));
        }

        public final DatasetDescriptor getLayerInfo() {
            DatasetDescriptor info = null;
            try {
                info = DatasetDescriptor.decode(this.getBlob(this.getColumnIndex(COLUMN_LAYERS_INFO)));
                if (info.getLayerId() != this.getLong(this.getColumnIndex(COLUMN_LAYERS_ID)))
                    throw new IllegalStateException("layerid=" + info.getLayerId() + " databaseid="
                            + this.getLong(this.getColumnIndex(COLUMN_LAYERS_ID)));

            } catch (IOException e) {
                Log.e(TAG, "error: ", e);
            }
            return info;
        }

        public final String getName() {
            return this.getString(this.getColumnIndex(COLUMN_LAYERS_NAME));
        }

        public final String getDatasetType() {
            return this.getString(this.getColumnIndex(COLUMN_LAYERS_DATASET_TYPE));
        }
        
        public final String getProvider() {
            return this.getString(this.getColumnIndex(COLUMN_LAYERS_PROVIDER));
        }

        public final int getSpatialReferenceId() {
            return this.getInt(this.getColumnIndex(COLUMN_LAYERS_SRID));
        }

        public final double getMinLat() {
            return this.getDouble(this.getColumnIndex(COLUMN_LAYERS_MIN_LAT));
        }

        public final double getMinLon() {
            return this.getDouble(this.getColumnIndex(COLUMN_LAYERS_MIN_LON));
        }

        public final double getMaxLat() {
            return this.getDouble(this.getColumnIndex(COLUMN_LAYERS_MAX_LAT));
        }

        public final double getMaxLon() {
            return this.getDouble(this.getColumnIndex(COLUMN_LAYERS_MAX_LON));
        }

        public final double getMinGSD() {
            return this.getDouble(this.getColumnIndex(COLUMN_LAYERS_MIN_GSD));
        }

        public final double getMaxGSD() {
            return this.getDouble(this.getColumnIndex(COLUMN_LAYERS_MAX_GSD));
        }

        final long getCatalogLink() {
            return this.getLong(this.getColumnIndex(COLUMN_LAYERS_CATALOG_LINK));
        }
    }

    public LayersDatabase.LayerCursor queryLayers() {
        return new LayerCursor(this.queryLayersNoSync(null, null, null, null, null, null, null));
    }

    public LayersDatabase.LayerCursor queryLayers(String name) {
        return new LayerCursor(this.queryLayersNoSync(null, "name = ?", new String[] {
                name
        }, null, null, null, null));
    }

    public LayersDatabase.LayerCursor queryLayers(int srid) {
        return new LayerCursor(this.queryLayersNoSync(null, "srid = ?", new String[] {
                String.valueOf(srid)
        }, null, null, null, null));
    }

    public LayersDatabase.LayerCursor queryLayers(GeoPoint roiUL, GeoPoint roiLR, double minGSD,
            double maxGSD) {
        return this.queryLayers(null, roiUL, roiLR, minGSD, maxGSD, -1);
    }

    public LayersDatabase.LayerCursor queryLayers(GeoPoint roiUL, GeoPoint roiLR, double minGSD,
            double maxGSD, int srid) {
        return this.queryLayers(null, roiUL, roiLR, minGSD, maxGSD, srid);
    }

    public LayersDatabase.LayerCursor queryLayers(String type, GeoPoint roiUL, GeoPoint roiLR,
            double minGSD, double maxGSD, int srid) {
        SelectionBuilder selection = new SelectionBuilder();
        selection.append(
                COLUMN_LAYERS_MIN_LAT + " <= " + sqliteFriendlyFloatingPointString(roiUL.getLatitude()) + " AND " +
                COLUMN_LAYERS_MAX_LAT + " >= " + sqliteFriendlyFloatingPointString(roiLR.getLatitude()) + " AND " +
                COLUMN_LAYERS_MIN_LON + " <= " + sqliteFriendlyFloatingPointString(roiLR.getLongitude()) + " AND " +
                COLUMN_LAYERS_MAX_LON + " >= " + sqliteFriendlyFloatingPointString(roiUL.getLongitude()));

        if (type != null)
            selection.append(COLUMN_LAYERS_DATASET_TYPE + " = \'" + type + "\'");

        if (srid != -1)
            selection.append(COLUMN_LAYERS_SRID + " = " + srid);

        if (!Double.isNaN(minGSD))
            selection.append(COLUMN_LAYERS_MIN_GSD + " >= " + sqliteFriendlyFloatingPointString(minGSD));
        if (!Double.isNaN(maxGSD))
            selection.append(COLUMN_LAYERS_MAX_GSD + " <= " + sqliteFriendlyFloatingPointString(maxGSD));

        return new LayerCursor(this.queryLayersNoSync(null, selection.getSelection(), null,
                null, null, null, null));
    }
    
    private static String sqliteFriendlyFloatingPointString(double v) {
        if(Double.isNaN(v))
            return "NULL"; // SQLite NULL will do what we want for comparisons
        else
            return String.valueOf(v);
    }

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
}
