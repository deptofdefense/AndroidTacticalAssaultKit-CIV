package com.atakmap.map.gpkg;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.gpkg.TileTable.TileMatrixSet;
import com.atakmap.map.gpkg.TileTable.ZoomLevelRow;
import com.atakmap.spatial.SpatiaLiteDB;

import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;

/**
 * Format reader class for parsing and accessing data from the OGC GeoPackage
 * file format. For more information on GeoPackage, see http://www.geopackage.org/.
 * To find the format specification, see http://www.opengeospatial.org/standards/geopackage.
 */
public class GeoPackage {
    
    private static final String TAG = "GeoPackage";
    
    public final static Map<String, Collection<String>> GPKG_SCHEMA = new HashMap<String, Collection<String>>();
    static {
        GPKG_SCHEMA.put("gpkg_spatial_ref_sys",
                        Arrays.asList("srs_name",
                                      "srs_id",
                                      "organization",
                                      "organization_coordsys_id",
                                      "definition",
                                      "description"));
        GPKG_SCHEMA.put("gpkg_contents",
                        Arrays.asList("table_name",
                                      "data_type",
                                      "identifier",
                                      "description",
                                      "last_change",
                                      "min_x",
                                      "min_y",
                                      "max_x",
                                      "max_y",
                                      "srs_id"));
        GPKG_SCHEMA.put("gpkg_geometry_columns",
                        Arrays.asList("table_name",
                                      "column_name",
                                      "geometry_type_name",
                                      "srs_id",
                                      "z",
                                      "m"));
        GPKG_SCHEMA.put("gpkg_tile_matrix_set",
                        Arrays.asList("table_name",
                                      "srs_id",
                                      "min_x",
                                      "min_y",
                                      "max_x",
                                      "max_y"));    
        GPKG_SCHEMA.put("gpkg_tile_matrix",
                        Arrays.asList("table_name",
                                      "zoom_level",
                                      "matrix_width",
                                      "matrix_height",
                                      "tile_width",
                                      "tile_height",
                                      "pixel_x_size",
                                      "pixel_y_size"));
        GPKG_SCHEMA.put("gpkg_extensions",
                        Arrays.asList("table_name",
                                      "column_name",
                                      "extension_name",
                                      "definition",
                                      "scope"));

    }
    
    DatabaseIface database;
    List<ContentsRow> packageContents;
    List<ExtensionsRow> packageExtensions;
    final File file;

    /**
     * Creates a new GeoPackage object that allows access to the contents
     * of a GeoPackage formatted SQLite database. This constructor
     * may throw an exception if it is used to open a file that is not
     * an SQLite database, so 
     * {@link com.atakmap.map.gpkg.GeoPackage#isGeoPackage(File) isGeoPackage(File)}
     * or {@link com.atakmap.map.gpkg.GeoPackage#isGeoPackage(String) isGeoPackage(String)}
     * should be used first to test if a file is valid.
     * @param database File object pointing to the database that should
     * be opened as a GeoPackage formatted SQLite database.
     * @throws SQLiteException  If the database cannot be opened.
     */
    public GeoPackage(File database){
        this(database, false);
    }

    public GeoPackage(File database, boolean readOnly){
        this.file = database;
        this.database = IOProviderFactory.createDatabase
                (new File(database.getPath ()),
                        readOnly ? DatabaseInformation.OPTION_READONLY : 0);
        checkGeoPackageSupport (this.database);
    }
    
    /**
     * Closes this GeoPackage file. No further reading may be performed after this method has been called.
     */
    public void close(){
        database.close();
    }

    private static void checkGeoPackageSupport (DatabaseIface geoDB)
      {
        CursorIface cursor = null;

        try
          {
            if (SpatiaLiteDB.getMajorVersion (geoDB) < 4
                || SpatiaLiteDB.getMinorVersion (geoDB) < 2)
              {
                throw new SQLiteException ("SpatiaLite 4.2 or later required");
              }

            cursor = geoDB.query ("SELECT HasGeoPackage()", null);
            if (!cursor.moveToNext () || cursor.getInt (0) != 1)
              {
                throw new SQLiteException
                    ("GeoPackage support in SpatiaLite is not enabled");
              }
          }
        catch (SQLiteException e)
          {
            Log.e (TAG, "SpatiaLite GeoPackage support is missing", e);
            geoDB.close ();
            throw e;
          }
        finally
          {
            if (cursor != null)
              { cursor.close (); }
          }
      }

    // Helper methods to cut down on some of the boiler plate required
    // to read values that might be null. It looks like the Android's Cursor
    // class (which backs the implementation of CursorIface use here) does
    // not specify whether or not calling getX on a value that is null should
    // return null or throw an exception. These methods should account for
    // both cases.
    private static String getStringOrNull(CursorIface cursor, String colName){
        try{
            int colIndex = cursor.getColumnIndex(colName);
            
            if(cursor.isNull(colIndex)){
                return null;
            }else{
                return cursor.getString(colIndex);
            }
        }catch(Exception e){
            return null;
        }
    }
    
    private static Double getDoubleOrNull(CursorIface cursor, String colName){
        try{
            int colIndex = cursor.getColumnIndex(colName);
            
            if(cursor.isNull(colIndex)){
                return null;
            }else{
                return cursor.getDouble(colIndex);
            }
        }catch(Exception e){
            return null;
        }
    }
    
    private static Integer getIntOrNull(CursorIface cursor, String colName){
        try {
            int colIndex = cursor.getColumnIndex(colName);
            
            if(cursor.isNull(colIndex)){
                return null;
            }else{
                return cursor.getInt(colIndex);
            }
        } catch(Exception e){
            return null;
        }
    }

    static boolean tableExists (DatabaseIface database,
                                String tableName)
      {
        boolean exists = false;
        QueryIface cursor = null;

        try
          {
            cursor = database.compileQuery("SELECT name FROM sqlite_master "
                                     + "WHERE type IN ('table', 'view') AND name=? COLLATE NOCASE LIMIT 1");
            cursor.bind(1, tableName);
            exists = cursor.moveToNext ();
          }
        catch (Exception e)
          { Log.e (TAG, "Error reading sqlite_master", e); }
        finally
          {
            if (cursor != null)
              { cursor.close (); }
          }

        return exists;
      }

    private static boolean hasGeoColumn (DatabaseIface database,
                                         String tableName)
      {
        boolean exists = false;
        CursorIface cursor = null;

        try
          {
            cursor = database.query ("SELECT column_name FROM gpkg_geometry_columns"
                                     + " WHERE table_name = '" + tableName + "' COLLATE NOCASE",
                                     null);
            exists = cursor.moveToNext ();
          }
        catch (Exception e)
          { Log.e (TAG, "Error reading gpkg_geometry_columns", e); }
        finally
          {
            if (cursor != null)
              { cursor.close (); }
          }

        return exists;
      }

    // Methods related to reading data from the gpkg_contents table
    
    public static enum TableType{
        TILES,FEATURES
    }
    
    public static class ContentsRow {
        public String table_name;
        public TableType data_type;
        public String identifier;
        public String description;
        public String last_change;
        public Double min_x;
        public Double min_y;
        public Double max_x;
        public Double max_y;
        public Integer srs_id;
    }
    
    /**
     * Get a list of the data stored in the GeoPackage gpkg_contents table.
     * Each entry in this list represents an individual row in the table. Each
     * row may be either a tiles "layer" or a feature "layer" as indicated by the 
     * data_type member of the result. NOTE: Double and Integer values in the
     * objects returned by this method my be null, and should be checked before
     * usage.
     * @return List of rows in the gpkg_contents table.
     */
    public List<ContentsRow> getPackageContents(){
        if (packageContents == null){
            packageContents = retrievePackageContents();
        }
        return packageContents;
    }

    private List<ContentsRow> retrievePackageContents(){
        List<ContentsRow> resultList = new ArrayList<ContentsRow>();
        CursorIface result = null;
        try{
            result = database.query("SELECT * FROM gpkg_contents", null);
            while (result.moveToNext()){
                ContentsRow row = new ContentsRow();
                row.table_name = result.getString(result.getColumnIndex("table_name"));
                
                if (!tableExists (database, row.table_name)) {
                    // Failure of GeoPackage requirement 14.
                    Log.w (TAG, "Skipping non-existent table in contents: "
                                + row.table_name);
                    continue;
                }
                String data_type = result.getString(result.getColumnIndex("data_type"));
                if(data_type.equals("features")){
                    row.data_type = TableType.FEATURES;
                    if (!hasGeoColumn (database, row.table_name)) {
                        // Failure of GeoPackage requirement 22.
                        Log.w (TAG, "Skipping feature without geometry column: "
                                    + row.table_name);
                        continue;
                    }
                }else if(data_type.equals("tiles")){
                    row.data_type = TableType.TILES;
                }else{
                    // Unsupported data_type
                    continue;
                }
                
                row.identifier = getStringOrNull(result, "identifier");
                row.description = getStringOrNull(result, "description");
                
                row.last_change = result.getString(result.getColumnIndex("last_change"));
                
                row.min_x = getDoubleOrNull(result, "min_x");
                row.min_y = getDoubleOrNull(result, "min_y");
                row.max_x = getDoubleOrNull(result, "max_x");
                row.max_y = getDoubleOrNull(result, "max_y");
                
                row.srs_id = getIntOrNull(result, "srs_id");
                
                resultList.add(row);
            }
        }catch(Exception e){
            Log.e(TAG, "Error reading PackageContents Entry", e);
        }finally{
            if (result != null){
                result.close();
            }
        }
        
        return resultList;
    }

    public enum ScopeType
      { READ_WRITE, WRITE_ONLY }

    // Data from the gpkg_extensions table.
    public static class ExtensionsRow {
      public String table_name;         // If null, extends whole GeoPackage.
      public String column_name;        // If null, extends whole table.
      public String extension_name;     // Not null. <author>_<extension_name>
      public String definition;         // Not null.
      public ScopeType scope;
    }

    /**
     * Get a list of the data stored in the gpgk_extensions table. Each entry in
     * this list represents a row in the table.  The values in this table are
     * described in section 2.3.2.1.1 of the GeoPackage specification.
     *  @return List of rows in the gpkg_extensions table.
     */
    public List<ExtensionsRow> getPackageExtensions ()
      {
        if (packageExtensions == null)
          {
            packageExtensions = retrievePackageExtensions ();
          }

        return packageExtensions;
      }

    private List<ExtensionsRow> retrievePackageExtensions ()
      {
        List<ExtensionsRow> resultList = new ArrayList<ExtensionsRow> ();

        if (tableExists (database, "gpkg_extensions"))
          {
            CursorIface cursor = null;

            try
              {
                cursor = database.query ("SELECT * FROM gpkg_extensions", null);

                int extIndex = cursor.getColumnIndex ("extension_name");
                int defIndex = cursor.getColumnIndex ("definition");
                int scopeIndex = cursor.getColumnIndex ("scope");

                while (cursor.moveToNext ())
                  {
                    ExtensionsRow row = new ExtensionsRow ();

                    row.table_name = getStringOrNull (cursor, "table_name");
                    row.column_name = getStringOrNull (cursor, "column_name");
                    if (row.table_name == null && row.column_name != null)
                      {
                        Log.w (TAG,
                               "Skipping invalid GeoPackage extension spec "
                               + "with column name but null table name");
                        continue;
                      }
                    row.extension_name = cursor.getString (extIndex);
                    row.definition = cursor.getString (defIndex);
                    row.scope = cursor.getString (scopeIndex).equals ("read-write")
                        ? ScopeType.READ_WRITE
                        : ScopeType.WRITE_ONLY;

                    resultList.add (row);
                  }
              }
            catch (Exception e)
              { Log.e (TAG, "Error reading Package Extensions", e); }
            finally
              {
                if (cursor != null)
                  { cursor.close (); }
              }
          }
        
        return resultList;
      }

    /**
     * Get an object allowing access to a FeatureTable by its name. This
     * name must match a value that will have been found in the "table_name" field
     * of an entry in the gpkg_contents table with a data_type of "features".
     * @param featureTableName Name of the feature table to access.
     * @return Object allowing read access to the data in a feature table, or
     * null if a table with the matching name cannot be found.
     */
    public FeatureTable getFeatureTable(String featureTableName){
        List<ContentsRow> contents = getPackageContents();
        for(ContentsRow r : contents){
            if(r.table_name.equals(featureTableName) && r.data_type.equals(TableType.FEATURES)){
                return new FeatureTable(database, r);
            }
        }
        
        return null;
    }
    
    public File getFile() {
        return this.file;
    }
    
    /**
     * Get an object allowing access to a TileTable by its name. This
     * name must match a value that will have been found in the "table_name" field
     * of an entry in the gpkg_contents table with a data_type of "tiles".
     * @param tileTableName Name of the tile table to access.
     * @return Object allowing read access to the data in a tile table, or null
     * if a table with a matching name cannot be found.
     */
    public TileTable getTileTable(String tileTableName){
        List<ContentsRow> contents = getPackageContents();
        for(ContentsRow r : contents){
            if(r.table_name.equals(tileTableName) && r.data_type.equals(TableType.TILES)){
                return new TileTable(database, r);
            }
        }
        
        return null;
    }
    
    // Methods for reading data from the gpkg_spatial_ref_sys table
    
    public static class SRS{
        public String srs_name;
        public int srs_id;
        public String organization;
        public int organization_coordsys_id;
        public String definition;
        public String description;
    }
    
    /**
     * Get an entry stored in the GeoPackage gpkg_spatial_ref_sys table.
     * @param id ID value that will be used to look up a row in the gpkg_spatial_ref_sys
     * table. This value will be matched against the srs_id column.
     * @return The specified row from the gpkg_spatial_ref_sys table, or null.
     */
    public SRS getSRSInfo(int id){
        CursorIface result = null;
        try{
            result = database.query("SELECT * FROM gpkg_spatial_ref_sys WHERE srs_id=\"" + id + "\"", null);
            
            SRS returnSRS = new SRS();
            
            if(!result.moveToNext()){
                return null;
            }
            
            returnSRS.srs_name = result.getString(result.getColumnIndex("srs_name"));
            returnSRS.srs_id = result.getInt(result.getColumnIndex("srs_id"));
            returnSRS.organization = result.getString(result.getColumnIndex("organization"));
            returnSRS.organization_coordsys_id = result.getInt(result.getColumnIndex("organization_coordsys_id"));
            returnSRS.definition = result.getString(result.getColumnIndex("definition"));
            returnSRS.description = getStringOrNull(result, "description");
            
            return returnSRS;
        }catch(Exception e){
            Log.e(TAG, "Error reading SRS Entry", e);
        }finally{
            if (result != null){
                result.close();
            }
        }
        
        return null;
    }
    
    //  isGeoPackage checks
    
    /**
     * Check if a file is a GeoPackage formatted SQLite database.
     * @param file Absolute or relative path to a file on disk that should
     * be tested to see if it is a GeoPackage formatted SQLite database.
     * @return True if the file is a GeoPackage formatted SQLite database, false
     * otherwise.
     */
    public static boolean isGeoPackage(String file){
        return isGeoPackage(new File(file));
    }
    
    /**
     * Check if a file is a GeoPackage formatted SQLite database.
     * @param file File object pointing to a file on disk that should
     * be tested to see if it is a GeoPackage formatted SQLite database.
     * @return True if the file is a GeoPackage formatted SQLite database, false
     * otherwise.
     */
    public static boolean isGeoPackage(File file){
        if(!IOProviderFactory.exists(file) || IOProviderFactory.isDirectory(file)){
            return false;
        }
        
        InputStream is = null;
        try{
            is = new BufferedInputStream(IOProviderFactory.getInputStream(file));
            
            byte[] headerData = new byte[72];
            int r = is.read(headerData);
            if (r != headerData.length)
                Log.d(TAG, "headerData, read: " + r + " expected: " + headerData.length);
            
            String headerString = new String(Arrays.copyOfRange(headerData, 0, 16), FileSystemUtils.UTF8_CHARSET);
            if(!"SQLite format 3".equals(headerString.trim())){
                return false;
            }
            
            // Example data didn't contain the Application ID "GP10", so the
            // implementation below is a work around. If the example data gets
            // fixed, this code should be uncommented and the workaround removed.
//            String appID = new String(Arrays.copyOfRange(headerData, 68, 72), FileSystemUtils.UTF8_CHARSET);
//            if(!"GP10".equals(appID)){
//                return false;
//            }
            
            // --- Begin Workaround
            try{
                is.close();
                is = null;
            }catch(Exception e){
                is = null;
            }
            
            DatabaseIface database = null;
            try {
                database = IOProviderFactory.createDatabase(file, DatabaseInformation.OPTION_READONLY);
                return tableExists (database, "gpkg_contents");
            }catch(Exception e){
                return false;
            } finally {
                if(database != null){
                    database.close();
                }
            }
            // --- End Workaround
        }catch(Exception e){
            Log.d(TAG, "An Error occurred when testing the file " 
                            + file.getName() 
                            + " to see if it is a GeoPackage File!", e);
            return false;
        }finally{
            if(is != null){
                try{
                    is.close();
                }catch(IOException e){
                    // Ignore
                }
            }
        }
    }

    public DatabaseIface getDatabase ()
      { return database; }

    public boolean hasTable (String tableName)
      { return tableExists (database, tableName); }
    
    /**************************************************************************/
    
    private void insertContentsRow(ContentsRow content) {
        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("INSERT INTO gpkg_contents (table_name, data_type, identifier, description, last_change, min_x, min_y, max_x, max_y, srs_id) VALUES(?, ?, ?, ?, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), ?, ?, ?, ?, ?)");
            stmt.bind(1, content.table_name);
            {
                String data_type = null;
                if(content.data_type == TableType.FEATURES)
                    data_type = "features";
                else if(content.data_type == TableType.TILES)
                    data_type = "tiles";
                else
                    data_type = "aspatial";
                stmt.bind(2, data_type);
            }
            stmt.bind(3, content.identifier);
            stmt.bind(4, content.description);
            if(content.min_x != null)   stmt.bind(5, content.min_x);
            else                        stmt.bindNull(5);
            if(content.min_y != null)   stmt.bind(6, content.min_y);
            else                        stmt.bindNull(6);
            if(content.max_x != null)   stmt.bind(7, content.max_x);
            else                        stmt.bindNull(7);
            if(content.max_y != null)   stmt.bind(8, content.max_y);
            else                        stmt.bindNull(8);
            stmt.bind(9, content.srs_id);
            
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
    }
    public void insertTileTable(ContentsRow content, TileMatrixSet matrix, ZoomLevelRow[] zoomLevels) {
        // XXX - verify that the various inputs are consistent

        StatementIface stmt;
        
        this.insertContentsRow(content);
        
        stmt = null;
        try {
            stmt = this.database.compileStatement("INSERT INTO gpkg_tile_matrix_set (table_name, min_x, min_y, max_x, max_y, srs_id) VALUES (?, ?, ?, ?, ?, ?)");
            stmt.bind(1, matrix.table_name);
            stmt.bind(2, matrix.min_x);
            stmt.bind(3, matrix.min_y);
            stmt.bind(4, matrix.max_x);
            stmt.bind(5, matrix.max_y);
            stmt.bind(6, matrix.srs_id);
            
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }

        for(int i = 0; i < zoomLevels.length; i++) {
            stmt = null;
            try {
                stmt = this.database.compileStatement("INSERT INTO gpkg_tile_matrix (table_name, zoom_level, matrix_width, matrix_height, tile_width, tile_height, pixel_x_size, pixel_y_size) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
                stmt.bind(1, matrix.table_name);
                stmt.bind(2, zoomLevels[i].zoom_level);
                stmt.bind(3, zoomLevels[i].matrix_width);
                stmt.bind(4, zoomLevels[i].matrix_height);
                stmt.bind(5, zoomLevels[i].tile_width);
                stmt.bind(6, zoomLevels[i].tile_height);
                stmt.bind(7, zoomLevels[i].pixel_x_size);
                stmt.bind(8, zoomLevels[i].pixel_y_size);

                stmt.execute();
            } finally {
                if(stmt != null)
                    stmt.close();
            }
        }
        
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ");
        sql.append(content.table_name);
        sql.append(" (id INTEGER PRIMARY KEY AUTOINCREMENT, zoom_level INTEGER NOT NULL, tile_column INTEGER NOT NULL, tile_row INTEGER NOT NULL, tile_data BLOB NOT NULL, UNIQUE (zoom_level, tile_column, tile_row))");
              
        this.database.execute(sql.toString(), null);
    }
    
    public void updateContentBounds(String table, Double min_x, Double min_y, Double max_x, Double max_y) {
        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("UPDATE gpkg_contents SET min_x = ?, min_y = ?, max_x = ?, max_y = ? WHERE table_name = ?");
            if(min_x != null)   stmt.bind(1, min_x);
            else                stmt.bindNull(1);
            if(min_y != null)   stmt.bind(2, min_y);
            else                stmt.bindNull(2);
            if(max_x != null)   stmt.bind(3, max_x);
            else                stmt.bindNull(3);
            if(max_y != null)   stmt.bind(4, max_y);
            else                stmt.bindNull(4);
            stmt.bind(5, table);
            
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
    }

    public void insertFeatureTable(ContentsRow content) {
        throw new UnsupportedOperationException();
    }
    
    public void insertExtension(ExtensionsRow extension, boolean insertTable) {
        throw new UnsupportedOperationException();
    }
    
    public void createRTreeIndex(String featureTable) {
        throw new UnsupportedOperationException();
    }
    
    public void deleteRTreeIndex(String featureTable) {
        throw new UnsupportedOperationException();
    }
    
    public boolean insertSRS(int srid) {
        final String wkt = GdalLibrary.getWkt(srid);
        SRS srs = new SRS();
        srs.srs_id = srid;
        srs.organization_coordsys_id = srid;
        srs.organization = "EPSG";
        srs.definition = (wkt != null) ? wkt : "undefined";
        srs.description = "EPSG:" + srid;
        srs.srs_name = "EPSG:" + srid;
        return this.insertSRS(srs);
    }
    
    public boolean insertSRS(SRS srs) {
        QueryIface query;
        
        // check for already existing
        query = null;
        try {
            query = this.database.compileQuery("SELECT 1 FROM gpkg_spatial_ref_sys WHERE srs_id = ? LIMIT 1");
            query.bind(1, srs.srs_id);
            if(query.moveToNext())
                return false;
        } finally {
            if(query != null)
                query.close();
        }

        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("INSERT INTO gpkg_spatial_ref_sys (srs_id, definition, description, organization, organization_coordsys_id, srs_name) VALUES(?, ?, ?, ?, ?, ?)");
            stmt.bind(1, srs.srs_id);
            stmt.bind(2, srs.definition);
            stmt.bind(3, srs.description);
            stmt.bind(4, srs.organization);
            stmt.bind(5, srs.organization_coordsys_id);
            stmt.bind(6, srs.srs_name);
            
            stmt.execute();

            return true;            
        } finally {
            if(stmt != null)
                stmt.close();
        }
    }
    
    public void insertTile(String tileTable, int zoom, int column, int row, Bitmap bitmap, Bitmap.CompressFormat fmt) {
        ByteArrayOutputStream blob = null;
        try {
            blob = new ByteArrayOutputStream(bitmap.getWidth()*bitmap.getHeight()*2);
            if(!bitmap.compress(fmt, 75, blob))
                throw new RuntimeException("Failed to compress tile");
            insertTile(tileTable, zoom, column, row, blob.toByteArray());
        } finally {
            if(blob != null)
                try {
                    blob.close();
                } catch(IOException ignored) {}
        }
    }
    
    public void insertTile(String tileTable, int zoom, int column, int row, byte[] blob) {
        StatementIface stmt = null;
        try {
            StringBuilder sql = new StringBuilder();
            if(!containsTile(tileTable, zoom, column, row)) {
                sql.append("INSERT INTO ");
                sql.append(tileTable);
                sql.append(" (tile_data, zoom_level, tile_column, tile_row) VALUES(?, ?, ?, ?)");
            } else {
                sql.append("UPDATE ");
                sql.append(tileTable);
                sql.append(" SET tile_data = ? WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?");
            }
            
            stmt = this.database.compileStatement(sql.toString());
            stmt.bind(1, blob);
            stmt.bind(2, zoom);
            stmt.bind(3, column);
            stmt.bind(4, row);
            
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
    }
    
    private boolean containsTile(String tileTable, int zoom, int column, int row) {
        QueryIface result = null;
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT 1 FROM ");
            sql.append(tileTable);
            sql.append(" WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?");
            
            result = this.database.compileQuery(sql.toString());
            result.bind(1, zoom);
            result.bind(2, column);
            result.bind(3, row);
            
            return result.moveToNext();
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    public static void createNewGeoPackage(String path) {
        DatabaseIface db = null;
        try {
            db = IOProviderFactory.createDatabase(new File(path));

            // Annex C  Table Definition SQL
            db.execute("CREATE TABLE gpkg_spatial_ref_sys (" +
                       "srs_name TEXT NOT NULL, " +
                       "srs_id INTEGER NOT NULL PRIMARY KEY, " +
                       "organization TEXT NOT NULL, " +
                       "organization_coordsys_id INTEGER NOT NULL, " +
                       "definition  TEXT NOT NULL, " +
                       "description TEXT " +
                       ");", null);
            
            db.execute("CREATE TABLE gpkg_contents (" +
                       "table_name TEXT NOT NULL PRIMARY KEY, " +
                       "data_type TEXT NOT NULL, " +
                       "identifier TEXT UNIQUE, " +
                       "description TEXT DEFAULT '', " +
                       "last_change DATETIME NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')), " +
                       "min_x DOUBLE, " +
                       "min_y DOUBLE, " +
                       "max_x DOUBLE, " +
                       "max_y DOUBLE, " +
                       "srs_id INTEGER, " +
                       "CONSTRAINT fk_gc_r_srs_id FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id) " +
                       ");", null);
            
            db.execute("CREATE TABLE gpkg_geometry_columns (" +
                       "table_name TEXT NOT NULL, " +
                       "column_name TEXT NOT NULL, " +
                       "geometry_type_name TEXT NOT NULL, " +
                       "srs_id INTEGER NOT NULL, " +
                       "z TINYINT NOT NULL, " +
                       "m TINYINT NOT NULL, " +
                       "CONSTRAINT pk_geom_cols PRIMARY KEY (table_name, column_name), " +
                       "CONSTRAINT uk_gc_table_name UNIQUE (table_name), " +
                       "CONSTRAINT fk_gc_tn FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name), " +
                       "CONSTRAINT fk_gc_srs FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys (srs_id) " +
                       "); ", null);
            
            db.execute("CREATE TABLE gpkg_tile_matrix_set (" +
                       "table_name TEXT NOT NULL PRIMARY KEY, " +
                       "srs_id INTEGER NOT NULL, " +
                       "min_x DOUBLE NOT NULL, " +
                       "min_y DOUBLE NOT NULL, " +
                       "max_x DOUBLE NOT NULL, " +
                       "max_y DOUBLE NOT NULL, " +
                       "CONSTRAINT fk_gtms_table_name FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name), " +
                       "CONSTRAINT fk_gtms_srs FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys (srs_id) " +
                       ");", null);
           
            db.execute("CREATE TABLE gpkg_tile_matrix (" +
                       "table_name TEXT NOT NULL, " +
                       "zoom_level INTEGER NOT NULL, " +
                       "matrix_width INTEGER NOT NULL, " +
                       "matrix_height INTEGER NOT NULL, " +
                       "tile_width INTEGER NOT NULL, " +
                       "tile_height INTEGER NOT NULL, " +
                       "pixel_x_size DOUBLE NOT NULL, " +
                       "pixel_y_size DOUBLE NOT NULL, " +
                       "CONSTRAINT pk_ttm PRIMARY KEY (table_name, zoom_level), " +
                       "CONSTRAINT fk_tmm_table_name FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name) " +
                       ");", null);
            
            db.execute("CREATE TABLE gpkg_extensions (" +
                       "table_name TEXT, " +
                       "column_name TEXT, " +
                       "extension_name TEXT NOT NULL, " +
                       "definition TEXT NOT NULL, " +
                       "scope TEXT NOT NULL, " +
                       "CONSTRAINT ge_tce UNIQUE (table_name, column_name, extension_name) " +
                       ");", null);

        } finally {
            if(db != null)
                db.close();
        }
    }
}
