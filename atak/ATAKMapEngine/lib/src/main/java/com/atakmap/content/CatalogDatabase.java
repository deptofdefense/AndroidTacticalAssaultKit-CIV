
package com.atakmap.content;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.database.CursorIface;
import com.atakmap.database.CursorWrapper;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.StatementIface;
import com.atakmap.coremap.filesystem.FileSystemUtils;

public class CatalogDatabase {

    protected final static int CATALOG_VERSION = 2;

    protected final static String TABLE_CATALOG = "catalog";
    protected final static String TABLE_CATALOG_METADATA = "catalog_metadata";

    protected final static String COLUMN_CATALOG_ID = "id";
    protected final static String COLUMN_CATALOG_PATH = "path";
    protected final static String COLUMN_CATALOG_SYNC = "sync";
    protected final static String COLUMN_CATALOG_APP_VERSION = "appversion";
    protected final static String COLUMN_CATALOG_APP_DATA = "appdata";
    protected final static String COLUMN_CATALOG_APP_NAME = "appname";

    protected final static String COLUMN_CATALOG_METADATA_KEY = "key";
    protected final static String COLUMN_CATALOG_METADATA_VALUE = "value";

    protected final static String DEFAULT_PATH_CATALOG_CURSOR_QUERY =
            "SELECT " + COLUMN_CATALOG_PATH + ", " +
                    COLUMN_CATALOG_SYNC + ", " + 
                    COLUMN_CATALOG_APP_VERSION + ", " +
                    COLUMN_CATALOG_APP_DATA + ", " +
                    COLUMN_CATALOG_APP_NAME + " FROM " +
                    TABLE_CATALOG + " WHERE " + COLUMN_CATALOG_PATH + " = ?";

    protected final static String DEFAULT_CURRENCY_CATALOG_CURSOR_QUERY =
            "SELECT " + COLUMN_CATALOG_PATH + ", " +
                    COLUMN_CATALOG_SYNC + ", " + 
                    COLUMN_CATALOG_APP_VERSION + ", " +
                    COLUMN_CATALOG_APP_DATA + ", " +
                    COLUMN_CATALOG_APP_NAME + " FROM " +
                    TABLE_CATALOG + " WHERE " + COLUMN_CATALOG_APP_NAME + " = ?";

    /**************************************************************************/

    protected DatabaseIface database;
    protected CatalogCurrencyRegistry currencyRegistry;

    protected StatementIface updateCatalogEntrySyncStmt;

    /**************************************************************************/

    public CatalogDatabase(DatabaseIface database, CatalogCurrencyRegistry currencyRegistry) {
        this.database = database;
        this.currencyRegistry = currencyRegistry;

        boolean create = !Databases.getTableNames(this.database).contains(TABLE_CATALOG);
        if (!this.checkDatabaseVersion()) {
            this.dropTables();
            create = true;
        }

        if (create) {
            if (this.database.isReadOnly())
                throw new IllegalArgumentException("Database is read-only.");

            this.buildTables();
            this.setDatabaseVersion();
        }
    }

    public synchronized void close() {
        if (this.updateCatalogEntrySyncStmt != null)
            this.updateCatalogEntrySyncStmt.close();

        this.database.close();
    }

    /**
     * Returns <code>true</code> if the database version is current, <code>false</code> otherwise.
     * If this method returns <code>false</code>, the database will be rebuilt with a call to
     * {@link #dropTables()} followed by {@link #buildTables()}. Subclasses may override this method
     * to support their own versioning mechanism.
     * <P>
     * The default implementation compares the version of the database via
     * <code>this.database</code>.{@link DatabaseIface#getVersion()} against
     * {@link #CATALOG_VERSION}.
     * 
     * @return <code>true</code> if the database version is current, <code>false</code> otherwise.
     */
    protected boolean checkDatabaseVersion() {
        return (this.database.getVersion() == CATALOG_VERSION);
    }

    /**
     * Sets the database versioning to the current version. Subclasses may override this method to
     * support their own versioning mechanism.
     * <P>
     * The default implementation sets the version of the database via <code>this.database</code>.
     * {@link DatabaseIface#setVersion(int)} with {@link #CATALOG_VERSION}.
     */
    protected void setDatabaseVersion() {
        this.database.setVersion(CATALOG_VERSION);
    }

    /**
     * Drops the tables present in the database. This method is invoked in the constructor when the
     * database version is not current.
     */
    protected void dropTables() {
        this.database.execute("DROP TABLE IF EXISTS " + TABLE_CATALOG, null);
        this.database.execute("DROP TABLE IF EXISTS " + TABLE_CATALOG_METADATA, null);
    }

    /**
     * Builds the tables for the database. This method is invoked in the constructor when the
     * database lacks the catalog table or when if the database version is not current.
     * <P>
     * The default implementation invokes {@link #createCatalogTable()} and returns.
     */
    protected void buildTables() {
        this.createCatalogTable();
    }

    /**
     * Creates the catalog table.
     */
    protected final void createCatalogTable() {
        this.database.execute("CREATE TABLE " + TABLE_CATALOG + " (" +
                COLUMN_CATALOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_CATALOG_PATH + " TEXT, " +
                COLUMN_CATALOG_SYNC + " INTEGER, " +
                COLUMN_CATALOG_APP_VERSION + " INTEGER, " +
                COLUMN_CATALOG_APP_DATA + " BLOB, " +
                COLUMN_CATALOG_APP_NAME + " TEXT)", null);

        this.database.execute("CREATE TABLE " + TABLE_CATALOG_METADATA + " (" +
                COLUMN_CATALOG_METADATA_KEY + " TEXT, " +
                COLUMN_CATALOG_METADATA_VALUE + " TEXT)", null);
    }

    protected void onCatalogEntryMarkedValid(long catalogId) {
    }

    protected void onCatalogEntryRemoved(long catalogId, boolean automated) {
    }

    protected void onCatalogEntryAdded(long catalogId) {
    }

    public synchronized long addCatalogEntry(File derivedFrom, CatalogCurrency currency) {
        return this.addCatalogEntryNoSync(derivedFrom, currency);
    }

    protected long addCatalogEntryNoSync(File derivedFrom, CatalogCurrency currency) {
        final long nextCatalogId = Databases.getNextAutoincrementId(this.database, TABLE_CATALOG);

        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("INSERT INTO " + TABLE_CATALOG +
                    " (" + COLUMN_CATALOG_PATH + ", " +
                    COLUMN_CATALOG_SYNC + ", " +
                    COLUMN_CATALOG_APP_NAME + ", " +
                    COLUMN_CATALOG_APP_VERSION + ", " +
                    COLUMN_CATALOG_APP_DATA + ") " +
                    "VALUES (?, ?, ?, ?, ?)");
            stmt.bind(1, derivedFrom.getAbsolutePath());
            stmt.bind(2, 0);
            stmt.bind(3, currency.getName());
            stmt.bind(4, currency.getAppVersion());
            stmt.bind(5, currency.getAppData(derivedFrom));

            stmt.execute();
        } finally {
            if (stmt != null)
                stmt.close();
        }

        return nextCatalogId;
    }

    public synchronized void validateCatalog() {
        CatalogCursor result = null;
        try {
            result = this.queryCatalog();
            this.validateCatalogNoSync(result);
        } finally {
            if (result != null)
                result.close();
        }
    }

    public synchronized void validateCatalog(String appName) {
        CatalogCursor result = null;
        try {
            result = this.queryCatalog(appName);
            this.validateCatalogNoSync(result);
        } finally {
            if (result != null)
                result.close();
        }
    }

    public synchronized void validateCatalog(File file) {
        CatalogCursor result = null;
        try {
            result = this.queryCatalog(file);
            this.validateCatalogNoSync(result);
        } finally {
            if (result != null)
                result.close();
        }
    }

    protected void validateCatalogNoSync(CatalogCursor result) {
        final boolean createTransaction = !this.database.inTransaction();

        if (createTransaction)
            this.database.beginTransaction();
        try {
            while (result.moveToNext()) {
                // the entry is valid
                if (this.validateCatalogRowNoSync(result))
                    continue;

                // the entry is not valid; remove it
                this.deleteCatalogPath(result.getPath(), true);
            }

            if (createTransaction)
                this.database.setTransactionSuccessful();
        } finally {
            if (createTransaction)
                this.database.endTransaction();
        }
    }
    
    protected boolean validateCatalogRowNoSync(CatalogCursor row) {
        CatalogCurrency currency = this.currencyRegistry.get(row.getAppName());
        File file = new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(row.getPath()));
        return IOProviderFactory.exists(file) &&
                (currency != null &&
                 currency.isValidApp(file,
                        row.getAppVersion(),
                        row.getAppData()));
    }

    public synchronized void markCatalogEntryValid(File file) {
        if (this.updateCatalogEntrySyncStmt == null)
            this.updateCatalogEntrySyncStmt = this.database.compileStatement("UPDATE "
                    + TABLE_CATALOG + " SET sync = ? WHERE path = ?");

        this.updateCatalogEntrySyncStmt.bind(1, 0);
        this.updateCatalogEntrySyncStmt.bind(2, file.getAbsolutePath());

        this.updateCatalogEntrySyncStmt.execute();
        this.updateCatalogEntrySyncStmt.clearBindings();

        this.onCatalogEntryMarkedValid(-1);
    }

    public CatalogCursor queryCatalog() {
        return new CatalogCursor(this.database.query("SELECT * FROM " + TABLE_CATALOG, null));
    }
    
    // Temporary

    public CatalogCursor queryCatalog(File file) {
        return this.queryCatalogPath(file.getAbsolutePath());
    }

    protected CatalogCursor queryCatalogPath(String path) {
        return this.queryRawCatalog(DEFAULT_PATH_CATALOG_CURSOR_QUERY, path);
    }

    public CatalogCursor queryCatalog(String appName) {
        return this.queryRawCatalog(DEFAULT_CURRENCY_CATALOG_CURSOR_QUERY, appName);
    }

    protected CatalogCursor queryRawCatalog(String rawQuery, String arg) {
        return new CatalogCursor(this.database.query(rawQuery, new String[] {
                arg
        }));
    }
    
    /**
     * Performs an arbitrary query on the underlying database.
     * 
     * <P>It is recommended that the baked query functions be used in most
     * circumstances as the underlying schemas are subject to change.
     * 
     * @param table         The table
     * @param columns       The columns to return
     * @param selection     The where clause
     * @param selectionArgs The where arguments
     * @param groupBy       The group by clause
     * @param having        The having clause
     * @param orderBy       The order by clause
     * @param limit         The limit clause
     * 
     * @return  The result
     */
    public CursorIface query(String table, String[] columns, String selection,
            String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {

        StringBuilder sql = new StringBuilder("SELECT ");
        if (columns == null) {
            sql.append("* ");
        } else if (columns.length > 0) {
            sql.append(columns[0]);
            for (int i = 1; i < columns.length; i++) {
                sql.append(", ");
                sql.append(columns[i]);
            }
            sql.append(" ");
        }

        sql.append("FROM " + table);
        if (selection != null)
            sql.append(" WHERE " + selection);
        if (groupBy != null)
            sql.append(" GROUP BY " + groupBy);
        if (having != null)
            sql.append(" HAVING " + having);
        if (orderBy != null)
            sql.append(" ORDER BY " + orderBy);
        if (limit != null)
            sql.append(" LIMIT " + limit);

        //System.out.println("EXECUTE SQL: " + sql.toString());

        return this.database.query(sql.toString(), selectionArgs);
    }

    public void deleteCatalog(File file) {
        this.deleteCatalogPath(file.getAbsolutePath(), false);
    }

    protected void deleteCatalogPath(String path, boolean automated) {
        CursorIface result = null;
        final long catalogId;
        try {
            result = this.database.query("SELECT " + COLUMN_CATALOG_ID + " FROM " + TABLE_CATALOG
                    + " WHERE " + COLUMN_CATALOG_PATH + " = ?", new String[] {
                    path
            });
            if (!result.moveToNext())
                return;
            catalogId = result.getLong(0);
        } finally {
            if (result != null)
                result.close();
        }

        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("DELETE FROM " + TABLE_CATALOG + " WHERE "
                    + COLUMN_CATALOG_PATH + " = ?");
            stmt.bind(1, path);

            stmt.execute();
        } finally {
            if (stmt != null)
                stmt.close();
        }

        this.onCatalogEntryRemoved(catalogId, automated);
    }

    public void deleteCatalog(String appName) {
        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("DELETE FROM " + TABLE_CATALOG + " WHERE "
                    + COLUMN_CATALOG_APP_NAME + " = ?");
            stmt.bind(1, appName);

            stmt.execute();
        } finally {
            if (stmt != null)
                stmt.close();
        }

        final int numDeleted = Databases.lastChangeCount(this.database);
        if (numDeleted > 0)
            this.onCatalogEntryRemoved(-1, true);
    }

    /**
     * Bumps the database version so it will be recreated on next startup
     */
    public void deleteAll(){
        this.database.setVersion(Integer.MAX_VALUE);
    }
    
    public List<String> queryFiles() {
        List<String> filepaths = new ArrayList<String>();
        
        CatalogCursor result = null;
        try {
            result = this.queryCatalog();
            while (result.moveToNext()) {
                filepaths.add(result.getPath());
            }           
        } finally {
            if (result != null)
                result.close();
        }
        
        return filepaths;
    }
    
    /**************************************************************************/

    public static class CatalogCursor extends CursorWrapper {

        public CatalogCursor(CursorIface cursor) {
            super(cursor);
        }

        public final String getPath() {
            return this.getString(this.getColumnIndex(COLUMN_CATALOG_PATH));
        }

        public final int getAppVersion() {
            return this.getInt(this.getColumnIndex(COLUMN_CATALOG_APP_VERSION));
        }

        public final String getAppName() {
            return this.getString(this.getColumnIndex(COLUMN_CATALOG_APP_NAME));
        }

        public final byte[] getAppData() {
            return this.getBlob(this.getColumnIndex(COLUMN_CATALOG_APP_DATA));
        }
        
        public final int getSyncVersion() {
            return this.getInt(this.getColumnIndex(COLUMN_CATALOG_SYNC));
        }
        
    }
}
