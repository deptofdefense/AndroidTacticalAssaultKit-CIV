
package com.atakmap.map.layer.feature.datastore;

import java.io.File;
import java.util.Set;

import com.atakmap.content.CatalogCurrencyRegistry;
import com.atakmap.content.CatalogDatabase;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.StatementIface;
import com.atakmap.map.layer.feature.FeatureDataSource;

public class FeatureSpatialDatabase extends CatalogDatabase {

    public final static boolean SPATIAL_INDEX_ENABLED = true;

    private final static int DATABASE_VERSION = 11;

    private StatementIface insertFeatureBlobStatement;
    private StatementIface insertFeatureWktStatement;
    private StatementIface insertFeatureWkbStatement;
    private StatementIface insertStyleStatement;

    public FeatureSpatialDatabase(File file, CatalogCurrencyRegistry r) {
        super(openCreateDatabase(file), r);
    }

    /** @hide */
    public DatabaseIface getDatabase() {
        return this.database;
    }

    public synchronized long insertGroup(long catalogId, String provider, String type, String groupName, int minLod, int maxLod) {
        StatementIface stmt = null;
        try {
            stmt = this.database
                    .compileStatement("INSERT INTO groups (file_id,  name, provider, type, visible, visible_version, visible_check, min_lod, max_lod) VALUES(?, ?, ?, ?, 1, 1, 0, ?, ?)");
            stmt.bind(1, catalogId);
            stmt.bind(2, groupName);
            stmt.bind(3, provider);
            stmt.bind(4, type);
            stmt.bind(5, minLod);
            stmt.bind(6, maxLod);

            stmt.execute();
        } finally {
            if (stmt != null)
                stmt.close();
        }

        return Databases.lastInsertRowId(this.database);
    }

    public synchronized long insertStyle(long catalogId, String styleRep) {
        final long styleId = Databases.getNextAutoincrementId(this.database, "Style");

        if (this.insertStyleStatement == null)
            this.insertStyleStatement = this.database
                    .compileStatement("INSERT INTO Style (file_id, style_rep) VALUES (?, ?)");
        this.insertStyleStatement.clearBindings();

        this.insertStyleStatement.bind(1, catalogId);
        this.insertStyleStatement.bind(2, styleRep);
        this.insertStyleStatement.execute();

        return styleId;
    }

    public void insertFeature(long catalogId, long groupId, FeatureDataSource.FeatureDefinition feature, long styleId,
            int minLod, int maxLod) {
        switch (feature.geomCoding) {
            case FeatureDataSource.FeatureDefinition.GEOM_SPATIALITE_BLOB :
                this.insertFeatureBlob(catalogId, groupId, feature.name, (byte[]) feature.rawGeom,
                        styleId, minLod, maxLod);
                break;
            case FeatureDataSource.FeatureDefinition.GEOM_WKB :
                this.insertFeatureWkb(catalogId, groupId, feature.name, (byte[]) feature.rawGeom,
                        styleId, minLod, maxLod);
                break;
            case FeatureDataSource.FeatureDefinition.GEOM_WKT :
                this.insertFeatureWkt(catalogId, groupId, feature.name, (String) feature.rawGeom,
                        styleId, minLod, maxLod);
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private synchronized void insertFeatureBlob(long catalogId, long groupId, String name,
            byte[] blob, long styleId, int minLod, int maxLod) {
        if (this.insertFeatureBlobStatement == null)
            this.insertFeatureBlobStatement = this.database
                    .compileStatement("INSERT INTO Geometry (file_id, group_id, name, geom, style_id, min_lod, max_lod, visible, group_visible_version) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0)");

        try {
            this.insertFeatureBlobStatement.clearBindings();

            int idx = 1;
            this.insertFeatureBlobStatement.bind(idx++, catalogId);
            this.insertFeatureBlobStatement.bind(idx++, groupId);
            this.insertFeatureBlobStatement.bind(idx++, name);
            this.insertFeatureBlobStatement.bind(idx++, blob);
            if (styleId > 0)
                this.insertFeatureBlobStatement.bind(idx++, styleId);
            else
                this.insertFeatureBlobStatement.bindNull(idx++);
            this.insertFeatureBlobStatement.bind(idx++, minLod);
            this.insertFeatureBlobStatement.bind(idx++, maxLod);

            this.insertFeatureBlobStatement.execute();
        } finally {
            this.insertFeatureBlobStatement.clearBindings();
        }
    }

    private synchronized void insertFeatureWkt(long catalogId, long groupId, String name,
            String wkt, long styleId, int minLod, int maxLod) {
        if (this.insertFeatureWktStatement == null)
            this.insertFeatureWktStatement = this.database
                    .compileStatement("INSERT INTO Geometry (file_id, group_id, name, geom, style_id, min_lod, max_lod, visible, group_visible_version) VALUES (?, ?, ?, GeomFromText(?, 4326), ?, ?, ?, 1, 0)");

        try {
            this.insertFeatureWktStatement.clearBindings();

            int idx = 1;
            this.insertFeatureWktStatement.bind(idx++, catalogId);
            this.insertFeatureWktStatement.bind(idx++, groupId);
            this.insertFeatureWktStatement.bind(idx++, name);
            this.insertFeatureWktStatement.bind(idx++, wkt);
            if (styleId > 0)
                this.insertFeatureWktStatement.bind(idx++, styleId);
            else
                this.insertFeatureWktStatement.bindNull(idx++);
            this.insertFeatureWktStatement.bind(idx++, minLod);
            this.insertFeatureWktStatement.bind(idx++, maxLod);

            this.insertFeatureWktStatement.execute();
        } finally {
            this.insertFeatureWktStatement.clearBindings();
        }
    }

    private synchronized void insertFeatureWkb(long catalogId, long groupId, String name,
            byte[] wkb, long styleId, int minLod, int maxLod) {
        if (this.insertFeatureWkbStatement == null)
            this.insertFeatureWkbStatement = this.database
                    .compileStatement("INSERT INTO Geometry (file_id, group_id, name, geom, style_id, min_lod, max_lod, visible, group_visible_version) VALUES (?, ?, ?, GeomFromWkb(?, 4326), ?, ?, ?, 1, 0)");

        try {
            this.insertFeatureWkbStatement.clearBindings();

            int idx = 1;

            this.insertFeatureWkbStatement.bind(idx++, catalogId);
            this.insertFeatureWkbStatement.bind(idx++, groupId);
            this.insertFeatureWkbStatement.bind(idx++, name);
            this.insertFeatureWkbStatement.bind(idx++, wkb);
            if (styleId > 0)
                this.insertFeatureWkbStatement.bind(idx++, styleId);
            else
                this.insertFeatureWkbStatement.bindNull(idx++);
            this.insertFeatureWkbStatement.bind(idx++, minLod);
            this.insertFeatureWkbStatement.bind(idx++, maxLod);

            this.insertFeatureWkbStatement.execute();
        } finally {
            this.insertFeatureWkbStatement.clearBindings();
        }
    }

    private void createIndicesNoSync() {
        this.database
                .execute(
                        "CREATE INDEX IF NOT EXISTS IdxGeometryLevelOfDetail ON Geometry(min_lod, max_lod)",
                        null);
        //
        // Is IdxGeometryFileId really necessary given the index below on group_id & name?
        //
        this.database.execute("CREATE INDEX IF NOT EXISTS IdxGeometryFileId ON Geometry(file_id)",
                null);
        this.database.execute(
                "CREATE INDEX IF NOT EXISTS IdxGeometryGroupIdName ON Geometry(group_id, name)",
                null);
        this.database.execute("CREATE INDEX IF NOT EXISTS IdxGroupName ON groups(name)", null);

        CursorIface result = null;
        try {
            result = this.database.query("SELECT CreateSpatialIndex(\'Geometry\', \'geom\')", null);
            result.moveToNext();
        } finally {
            if (result != null)
                result.close();
        }
    }

    private void dropIndicesNoSync() {
        this.database.execute("DROP INDEX IF EXISTS IdxGeometryLevelOfDetail", null);
        this.database.execute("DROP INDEX IF EXISTS IdxGeometryGroupIdName", null);
        //
        // Is IdxGeometryFileId really necessary?
        //
        this.database.execute("DROP INDEX IF EXISTS IdxGeometryFileId", null);
        this.database.execute("DROP INDEX IF EXISTS IdxGroupName", null);

        if (Databases.getTableNames(this.database).contains("idx_Geometry_geom")) {
            CursorIface result = null;
            try {
                result = this.database.query("SELECT DisableSpatialIndex(\'Geometry\', \'geom\')",
                        null);
                result.moveToNext();
            } finally {
                if (result != null)
                    result.close();
            }

            this.database.execute("DROP TABLE idx_Geometry_geom", null);
        }
    }
    
    private void createTriggersNoSync() {
        this.database.execute("CREATE TRIGGER IF NOT EXISTS Geometry_visible_update AFTER UPDATE OF visible ON Geometry " +
                              "BEGIN " +
                              "UPDATE groups SET visible_check = 1 WHERE id = OLD.group_id; " + 
                              "UPDATE Geometry SET group_visible_version = (SELECT visible_version FROM groups WHERE id = OLD.group_id) WHERE id = OLD.id; " + 
                              "END;", null);
        this.database.execute("CREATE TRIGGER IF NOT EXISTS groups_visible_update AFTER UPDATE OF visible ON groups " +
                              "BEGIN " +
                              "UPDATE groups SET visible_version = (OLD.visible_version+1), visible_check = 0 WHERE id = OLD.id; " + 
                              "END;", null);
    }

    private void dropTriggersNoSync() {
        this.database.execute("DROP TRIGGER IF EXISTS Geometry_visible_update", null);
        this.database.execute("DROP TRIGGER IF EXISTS groups_visible_update", null);
    }
    
    /**************************************************************************/
    // Catalog Database

    @Override
    protected boolean checkDatabaseVersion() {
        return (this.database.getVersion() == databaseVersion());
    }

    @Override
    protected void setDatabaseVersion() {
        this.database.setVersion(databaseVersion());
    }

    @Override
    public synchronized void close() {
        try {
            if (this.insertFeatureBlobStatement != null) {
                this.insertFeatureBlobStatement.close();
                this.insertFeatureBlobStatement = null;
            }

            if (this.insertFeatureWkbStatement != null) {
                this.insertFeatureWkbStatement.close();
                this.insertFeatureWkbStatement = null;
            }

            if (this.insertFeatureWktStatement != null) {
                this.insertFeatureWktStatement.close();
                this.insertFeatureWktStatement = null;
            }

            if (this.insertStyleStatement != null) {
                this.insertStyleStatement.close();
                this.insertStyleStatement = null;
            }
        } finally {
            super.close();
        }
    }

    @Override
    protected void dropTables() {
        // it will be much quicker to simply delete the database if we need to
        // drop the tables. attempt to delete it; if this fails, perform a
        // legacy-style table drop
        String dbPath = Databases.getDatabaseFile(this.database);
        if (dbPath != null) {
            File dbFile = new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(dbPath));
            if (IOProviderFactory.exists(dbFile)) {
                this.database.close();
                final boolean deleted = IOProviderFactory.delete(dbFile, IOProvider.SECURE_DELETE);
                this.database = openCreateDatabase(dbFile);
                if (deleted)
                    return;
            }
        }

        this.dropTablesLegacy();
    }

    private void dropTablesLegacy() {
        this.dropIndicesNoSync();
        this.dropTriggersNoSync();

        Set<String> geometryColumns = Databases.getColumnNames(this.database, "Geometry");
        if (geometryColumns != null && geometryColumns.contains("geom")) {
            CursorIface result = null;
            try {
                result = this.database.query(
                        "SELECT DiscardGeometryColumn(\'Geometry\', \'geom\')", null);

                result.moveToNext();
            } finally {
                if (result != null)
                    result.close();
            }
        }

        this.database.execute("DROP TABLE IF EXISTS File", null);
        this.database.execute("DROP TABLE IF EXISTS Geometry", null);
        this.database.execute("DROP TABLE IF EXISTS Style", null);
        this.database.execute("DROP TABLE IF EXISTS groups", null);

        super.dropTables();
    }

    @Override
    protected void buildTables() {
        CursorIface result;

        final int major = getSpatialiteMajorVersion(this.database);
        final int minor = getSpatialiteMinorVersion(this.database);

        final String initSpatialMetadataSql;
        if (major > 4 || (major == 4 && minor >= 1))
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
                        "CREATE TABLE Geometry (id INTEGER PRIMARY KEY AUTOINCREMENT, file_id INTEGER, group_id INTEGER, name TEXT COLLATE NOCASE, style_id INTEGER, min_lod INTEGER, max_lod INTEGER, visible INTEGER, group_visible_version INTEGER)",
                        null);

        result = null;
        try {
            result = this.database.query(
                    "SELECT AddGeometryColumn(\'Geometry\', \'geom\', 4326, \'GEOMETRY\', \'XY\')",
                    null);
            result.moveToNext();
        } finally {
            if (result != null)
                result.close();
        }

        this.database
                .execute(
                        "CREATE TABLE Style (id INTEGER PRIMARY KEY AUTOINCREMENT, style_name TEXT, file_id INTEGER, style_rep TEXT)",
                        null);

        this.database
                .execute(
                        "CREATE TABLE groups (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT COLLATE NOCASE, file_id INTEGER, provider TEXT, type TEXT, visible INTEGER, visible_version INTEGER, visible_check INTEGER, min_lod INTEGER, max_lod INTEGER)",
                        null);

        this.createIndicesNoSync();
        this.createTriggersNoSync();
    }

    @Override
    protected void onCatalogEntryRemoved(long catalogId, boolean automated) {
        if (catalogId > 0) {
            StatementIface stmt;

            stmt = null;
            try {
                stmt = this.database.compileStatement("DELETE FROM Style WHERE file_id = ?");
                stmt.bind(1, catalogId);
                stmt.execute();
            } finally {
                if (stmt != null)
                    stmt.close();
            }

            stmt = null;
            try {
                stmt = this.database.compileStatement("DELETE FROM Geometry WHERE file_id = ?");
                stmt.bind(1, catalogId);
                stmt.execute();
            } finally {
                if (stmt != null)
                    stmt.close();
            }

            stmt = null;
            try {
                stmt = this.database.compileStatement("DELETE FROM groups WHERE file_id = ?");
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
                stmt = this.database
                        .compileStatement("DELETE FROM Geometry WHERE file_id IN (SELECT file_id FROM Geometry LEFT JOIN "
                                + TABLE_CATALOG
                                + " on Geometry.file_id = "
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

            stmt = null;
            try {
                stmt = this.database
                        .compileStatement("DELETE FROM Style WHERE file_id IN (SELECT file_id FROM Style LEFT JOIN "
                                + TABLE_CATALOG
                                + " on Style.file_id = "
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

            stmt = null;
            try {
                stmt = this.database
                        .compileStatement("DELETE FROM groups WHERE file_id IN (SELECT file_id FROM groups LEFT JOIN "
                                + TABLE_CATALOG
                                + " on groups.file_id = "
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

    private static DatabaseIface openCreateDatabase(File file) {
        return IOProviderFactory.createDatabase(file);
    }

    private static int databaseVersion() {
        return (CATALOG_VERSION | (DATABASE_VERSION << 16));
    }

    /**************************************************************************/

    public static int getSpatialiteMajorVersion(DatabaseIface db) {
        return getSpatialiteVersion(db, 0);
    }

    public static int getSpatialiteMinorVersion(DatabaseIface db) {
        return getSpatialiteVersion(db, 1);
    }

    private static int getSpatialiteVersion(DatabaseIface db, int idx) {
        CursorIface result = null;
        try {
            result = db.query("SELECT spatialite_version()", null);
            if (!result.moveToNext())
                return -1;
            String verStr = result.getString(0).split("\\.")[idx];
            if (!verStr.matches("\\d+"))
                return -1;
            return Integer.parseInt(verStr);
        } finally {
            if (result != null)
                result.close();
        }
    }
}
