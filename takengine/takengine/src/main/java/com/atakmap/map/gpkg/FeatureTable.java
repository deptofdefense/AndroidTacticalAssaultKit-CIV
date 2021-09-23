package com.atakmap.map.gpkg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;

import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.map.gpkg.GeoPackage.ContentsRow;


/**
 * Class representing the tables required to access feature information from a
 * GeoPackage formatted SQLite database.  For more information about the tables
 * related to feature data in a GeoPackage database, see section 2.1 of the
 * GeoPackage specification at
 * http://www.opengeospatial.org/standards/geopackage.
 */
public
class FeatureTable
    extends GeoPackageContentsTable
  {
    //==================================
    //
    //  PUBLIC INTERFACE
    //
    //==================================


    //
    // Information about a column in the feature table from the table_info
    // pragma and possibly the gpkg_data_columns table.
    //
    public
    enum ColumnType
      {
        GEOMETRY, INTEGER, FLOAT, STRING, BLOB
      }


    public static
    class ColumnInfo
      {
        public String columnName;
        public ColumnType type;
        public boolean nullable;
        public String shortName;        // Optional gpkg_data_columns.name
        public String title;            // Optional gpkg_data_columns.title
        public String description;      // Optional gpkg_data_columns.description
      }


    public
    enum ValuesRequirement
      { Prohibited, Required, Optional }


    public synchronized
    List<ColumnInfo>
    getColumnInfo ()
      {
        if (columns == null)
          {
            columns = new ArrayList<ColumnInfo> ();
            
            CursorIface cursor = null;

            try
              {
                Map<String, ColumnInfo> columnMap
                    = new HashMap<String, ColumnInfo> ();

                cursor = database.query ("PRAGMA table_info(" + getName () + ")",
                                         null);
                while (cursor.moveToNext ())
                  {
                    ColumnInfo info = new ColumnInfo ();

                    info.columnName = cursor.getString (1);
                    info.type = convertType (cursor.getString (2));
                    info.nullable = cursor.getInt (3) == 0;
                    columns.add (info);
                    columnMap.put (info.columnName, info);
                  }
                if (GeoPackage.tableExists (database, "gpkg_data_columns"))
                  {
                    cursor.close ();
                    cursor = database.query ("SELECT column_name, name, title, description FROM gpkg_data_columns WHERE table_name='"
                                             + getName () + "'",
                                             null);
                    while (cursor.moveToNext ())
                      {
                        ColumnInfo info = columnMap.get (cursor.getString (0));

                        if (info != null)
                          {
                            info.shortName = cursor.getString (1);
                            info.title = cursor.getString (2);
                            info.description = cursor.getString (3);
                          }
                      }
                  }
              }
            catch (Exception e)
              { Log.e (TAG, "Error reading gpkg_data_columns entry"); }
            finally
              {
                if (cursor != null)
                  { cursor.close (); }
              }
          }

        return columns;
      }


    //
    // Information from feature table's row in the gpkg_geometry_columns table.
    //

 
    public
    String
    getGeometryColumnName ()
      { return geoRow.geometryColumnName; }


    public
    int
    getGeometrySRID ()
      { return geoRow.geometrySRID; }


    public
    String
    getGeometryTypeName ()
      { return geoRow.geometryTypeName; }


    public
    ValuesRequirement
    getM_Requirement ()
      { return geoRow.mValues; }


    public
    ValuesRequirement
    getZ_Requirement ()
      { return geoRow.zValues; }


    //==================================
    //
    //  PACKAGE INTERFACE
    //
    //==================================


    // Package Private visibility, should only be able to create
    // instances of this class from within the GeoPackage class.
    FeatureTable (DatabaseIface db,
                  ContentsRow contents)
      {
        super (contents);
        database = db;
        geoRow = getGeometryColumnsRow ();
      }


    //==================================
    //
    //  PRIVATE IMPLEMENTATION
    //
    //==================================


    private static
    class GeometryColumnsRow
      {
        String geometryColumnName;
        String geometryTypeName;
        int geometrySRID;
        ValuesRequirement zValues;
        ValuesRequirement mValues;
      }
    

    private static
    ColumnType
    convertType (String typeString)
      {
        //
        // This mimics the behavior of the sqlite3AffinityType function, except
        // that it uses BLOB as the default type rather than NUMERIC and it
        // recognizes a GEOMETRY type comprising GeoPackage Geometry types.
        //

        ColumnType result = ColumnType.BLOB;

        typeString = typeString.toUpperCase (LocaleUtil.getCurrent());
        if (typeString.contains ("INT"))
          {
            result = typeString.contains ("POINT")
                ? ColumnType.GEOMETRY
                : ColumnType.INTEGER;
          }
        else if (typeString.contains ("TEXT")
                 || typeString.contains ("CHAR")
                 || typeString.contains ("CLOB"))
          {
            result = ColumnType.STRING;
          }
        else if (typeString.contains ("REAL")
                 || typeString.contains ("FLOA")
                 || typeString.contains ("DOUB"))
          {
            result = ColumnType.FLOAT;
          }
        else if (typeString.contains ("GEOM")
                 // || typeString.contains ("POINT")
                 || typeString.contains ("LINE")
                 || typeString.contains ("POLY")
                 || typeString.contains ("CIRC")
                 || typeString.contains ("CURV")
                 || typeString.contains ("SURF"))
          {
            result = ColumnType.GEOMETRY;
          }

        return result;
      }


    private
    GeometryColumnsRow
    getGeometryColumnsRow ()
      {
        GeometryColumnsRow row = null;
        CursorIface cursor = null;

        try
          {
            cursor = database.query ("SELECT * FROM gpkg_geometry_columns"
                                     + " WHERE table_name = '" + getName() + "'",
                                     null);
            if (cursor.moveToNext ())
              {
                int nameIndex = cursor.getColumnIndex ("column_name");
                int typeIndex = cursor.getColumnIndex ("geometry_type_name");
                int srsIndex = cursor.getColumnIndex ("srs_id");
                int zIndex = cursor.getColumnIndex ("z");
                int mIndex = cursor.getColumnIndex ("m");

                row = new GeometryColumnsRow ();
                row.geometryColumnName = cursor.getString (nameIndex);
                row.geometryTypeName = cursor.getString (typeIndex);
                row.geometrySRID = cursor.getInt (srsIndex);
                row.zValues = ValuesRequirement.values ()[cursor.getInt (zIndex)];
                row.mValues = ValuesRequirement.values ()[cursor.getInt (mIndex)];
              }
          }
        catch (Exception e)
          { Log.e (TAG, "Error reading gpkg_geometry_columns entry"); }
        finally
          {
            if (cursor != null)
              {
                cursor.close ();
              }
          }

        return row;
      }


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    private static final String TAG = "FeatureTable";
    
    private DatabaseIface database;
    private GeometryColumnsRow geoRow;
    private List<ColumnInfo> columns;
  }
